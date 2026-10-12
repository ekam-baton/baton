use crate::vector_memory::{cosine_similarity, MemorySearchResult, VectorMemoryEntry};
use std::fs::{File, OpenOptions};
use std::io::{BufReader, BufWriter, Read, Write};
use std::path::Path;

const MAGIC_HEADER: &[u8; 8] = b"BATONVEC";
const FORMAT_VERSION: u32 = 1;

/// High-performance persistent on-disk binary vector database for sovereign agent memory.
#[derive(Debug, Clone)]
pub struct PersistentVectorStore {
    pub dimension: u16,
    pub entries: Vec<VectorMemoryEntry>,
}

impl PersistentVectorStore {
    pub fn new(dimension: u16) -> Self {
        Self {
            dimension,
            entries: Vec::new(),
        }
    }

    pub fn upsert(&mut self, entry: VectorMemoryEntry) -> Result<(), String> {
        if entry.embedding.len() != self.dimension as usize && !entry.embedding.is_empty() {
            return Err(format!(
                "Dimension mismatch: expected {}, got {}",
                self.dimension,
                entry.embedding.len()
            ));
        }

        if let Some(existing) = self.entries.iter_mut().find(|e| e.id == entry.id) {
            *existing = entry;
        } else {
            self.entries.push(entry);
        }
        Ok(())
    }

    pub fn delete(&mut self, id: &str) -> bool {
        let initial = self.entries.len();
        self.entries.retain(|e| e.id != id);
        self.entries.len() < initial
    }

    pub fn count(&self) -> usize {
        self.entries.len()
    }

    /// Semantic search with optional layer filtering and score thresholding.
    pub fn search(
        &self,
        query: &[f32],
        top_k: usize,
        min_score: f32,
        layer_filter: Option<&str>,
    ) -> Vec<MemorySearchResult> {
        if query.len() != self.dimension as usize || self.entries.is_empty() {
            return Vec::new();
        }

        let mut scored: Vec<MemorySearchResult> = self
            .entries
            .iter()
            .filter(|e| {
                if let Some(layer) = layer_filter {
                    e.layer.eq_ignore_ascii_case(layer)
                } else {
                    true
                }
            })
            .map(|entry| {
                let score = cosine_similarity(query, &entry.embedding);
                MemorySearchResult {
                    id: entry.id.clone(),
                    layer: entry.layer.clone(),
                    title: entry.title.clone(),
                    content: entry.content.clone(),
                    score,
                }
            })
            .filter(|res| res.score >= min_score)
            .collect();

        scored.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));

        if scored.len() > top_k {
            scored.truncate(top_k);
        }
        scored
    }

    /// Atomically persists memory records to disk using a crash-resilient `.tmp` rename pattern.
    pub fn save_to_file<P: AsRef<Path>>(&self, path: P) -> std::io::Result<()> {
        let path = path.as_ref();
        let tmp_path = path.with_extension("tmp");

        let file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .open(&tmp_path)?;
        let mut writer = BufWriter::new(file);

        // 1. Header
        writer.write_all(MAGIC_HEADER)?;
        writer.write_all(&FORMAT_VERSION.to_le_bytes())?;
        writer.write_all(&self.dimension.to_le_bytes())?;
        writer.write_all(&(self.entries.len() as u32).to_le_bytes())?;

        // 2. Entries
        for entry in &self.entries {
            // ID
            let id_bytes = entry.id.as_bytes();
            writer.write_all(&(id_bytes.len() as u16).to_le_bytes())?;
            writer.write_all(id_bytes)?;

            // Layer
            let layer_bytes = entry.layer.as_bytes();
            writer.write_all(&(layer_bytes.len() as u8).to_le_bytes())?;
            writer.write_all(layer_bytes)?;

            // Title
            let title_bytes = entry.title.as_bytes();
            writer.write_all(&(title_bytes.len() as u16).to_le_bytes())?;
            writer.write_all(title_bytes)?;

            // Content
            let content_bytes = entry.content.as_bytes();
            writer.write_all(&(content_bytes.len() as u32).to_le_bytes())?;
            writer.write_all(content_bytes)?;

            // Timestamp
            writer.write_all(&entry.timestamp.to_le_bytes())?;

            // Embedding
            for &val in &entry.embedding {
                writer.write_all(&val.to_le_bytes())?;
            }
        }

        writer.flush()?;
        drop(writer);

        // Atomic replace
        std::fs::rename(tmp_path, path)?;
        Ok(())
    }

    /// Loads memory records from binary file, verifying header and dimension integrity.
    pub fn load_from_file<P: AsRef<Path>>(path: P) -> std::io::Result<Self> {
        let file = File::open(path)?;
        let mut reader = BufReader::new(file);

        // 1. Verify Magic
        let mut magic = [0u8; 8];
        reader.read_exact(&mut magic)?;
        if &magic != MAGIC_HEADER {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidData,
                "Invalid BATON vector file header",
            ));
        }

        // 2. Read Version
        let mut version_bytes = [0u8; 4];
        reader.read_exact(&mut version_bytes)?;
        let version = u32::from_le_bytes(version_bytes);
        if version != FORMAT_VERSION {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidData,
                format!("Unsupported vector format version: {version}"),
            ));
        }

        // 3. Read Dimension & Count
        let mut dim_bytes = [0u8; 2];
        reader.read_exact(&mut dim_bytes)?;
        let dimension = u16::from_le_bytes(dim_bytes);

        let mut count_bytes = [0u8; 4];
        reader.read_exact(&mut count_bytes)?;
        let count = u32::from_le_bytes(count_bytes) as usize;

        let mut entries = Vec::with_capacity(count);

        for _ in 0..count {
            // ID
            let mut id_len_buf = [0u8; 2];
            reader.read_exact(&mut id_len_buf)?;
            let id_len = u16::from_le_bytes(id_len_buf) as usize;
            let mut id_buf = vec![0u8; id_len];
            reader.read_exact(&mut id_buf)?;
            let id = String::from_utf8(id_buf).map_err(|e| {
                std::io::Error::new(std::io::ErrorKind::InvalidData, e)
            })?;

            // Layer
            let mut layer_len_buf = [0u8; 1];
            reader.read_exact(&mut layer_len_buf)?;
            let layer_len = layer_len_buf[0] as usize;
            let mut layer_buf = vec![0u8; layer_len];
            reader.read_exact(&mut layer_buf)?;
            let layer = String::from_utf8(layer_buf).map_err(|e| {
                std::io::Error::new(std::io::ErrorKind::InvalidData, e)
            })?;

            // Title
            let mut title_len_buf = [0u8; 2];
            reader.read_exact(&mut title_len_buf)?;
            let title_len = u16::from_le_bytes(title_len_buf) as usize;
            let mut title_buf = vec![0u8; title_len];
            reader.read_exact(&mut title_buf)?;
            let title = String::from_utf8(title_buf).map_err(|e| {
                std::io::Error::new(std::io::ErrorKind::InvalidData, e)
            })?;

            // Content
            let mut content_len_buf = [0u8; 4];
            reader.read_exact(&mut content_len_buf)?;
            let content_len = u32::from_le_bytes(content_len_buf) as usize;
            let mut content_buf = vec![0u8; content_len];
            reader.read_exact(&mut content_buf)?;
            let content = String::from_utf8(content_buf).map_err(|e| {
                std::io::Error::new(std::io::ErrorKind::InvalidData, e)
            })?;

            // Timestamp
            let mut ts_buf = [0u8; 8];
            reader.read_exact(&mut ts_buf)?;
            let timestamp = i64::from_le_bytes(ts_buf);

            // Embedding
            let mut embedding = Vec::with_capacity(dimension as usize);
            for _ in 0..dimension {
                let mut f_buf = [0u8; 4];
                reader.read_exact(&mut f_buf)?;
                embedding.push(f32::from_le_bytes(f_buf));
            }

            entries.push(VectorMemoryEntry {
                id,
                layer,
                title,
                content,
                embedding,
                timestamp,
            });
        }

        Ok(Self { dimension, entries })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_binary_roundtrip_persistence() {
        let dir = std::env::temp_dir();
        let file_path = dir.join(format!("test_baton_vectors_{}.bin", rand::random::<u32>()));

        let mut store = PersistentVectorStore::new(4);
        store
            .upsert(VectorMemoryEntry {
                id: "agent_fact_1".to_string(),
                layer: "episodic".to_string(),
                title: "Security Attestation".to_string(),
                content: "Local node hardware key backed".to_string(),
                embedding: vec![0.1, 0.2, 0.3, 0.4],
                timestamp: 1700000000,
            })
            .expect("Upsert failed");

        store
            .upsert(VectorMemoryEntry {
                id: "agent_fact_2".to_string(),
                layer: "working".to_string(),
                title: "Active Task".to_string(),
                content: "Verifying peer DID signature".to_string(),
                embedding: vec![0.5, 0.6, 0.7, 0.8],
                timestamp: 1700000050,
            })
            .expect("Upsert failed");

        // Save
        store.save_to_file(&file_path).expect("Save failed");

        // Load back
        let loaded = PersistentVectorStore::load_from_file(&file_path).expect("Load failed");
        assert_eq!(loaded.count(), 2);
        assert_eq!(loaded.dimension, 4);
        assert_eq!(loaded.entries[0].id, "agent_fact_1");
        assert_eq!(loaded.entries[1].content, "Verifying peer DID signature");

        // Clean up
        let _ = std::fs::remove_file(file_path);
    }

    #[test]
    fn test_search_with_layer_filter() {
        let mut store = PersistentVectorStore::new(3);

        store
            .upsert(VectorMemoryEntry {
                id: "core1".to_string(),
                layer: "core".to_string(),
                title: "System Identity".to_string(),
                content: "I am Baton".to_string(),
                embedding: vec![1.0, 0.0, 0.0],
                timestamp: 100,
            })
            .unwrap();

        store
            .upsert(VectorMemoryEntry {
                id: "work1".to_string(),
                layer: "working".to_string(),
                title: "Meeting".to_string(),
                content: "Discussing encryption".to_string(),
                embedding: vec![0.9, 0.1, 0.0],
                timestamp: 200,
            })
            .unwrap();

        let query = vec![1.0, 0.0, 0.0];

        // Search only "working" layer
        let working_results = store.search(&query, 5, 0.0, Some("working"));
        assert_eq!(working_results.len(), 1);
        assert_eq!(working_results[0].id, "work1");

        // Search all layers
        let all_results = store.search(&query, 5, 0.0, None);
        assert_eq!(all_results.len(), 2);
        assert_eq!(all_results[0].id, "core1");
    }
}
