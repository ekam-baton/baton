use serde::{Deserialize, Serialize};

/// High-speed SIMD-friendly cosine similarity computation between two float vectors.
pub fn cosine_similarity(a: &[f32], b: &[f32]) -> f32 {
    if a.is_empty() || b.is_empty() || a.len() != b.len() {
        return 0.0;
    }

    let mut dot = 0.0f32;
    let mut norm_a = 0.0f32;
    let mut norm_b = 0.0f32;

    // Loop with 4-way unrolling for compiler auto-vectorization (ARM NEON / AVX)
    let chunks_a = a.chunks_exact(4);
    let chunks_b = b.chunks_exact(4);
    let rem_a = chunks_a.remainder();
    let rem_b = chunks_b.remainder();

    for (ca, cb) in chunks_a.zip(chunks_b) {
        dot += ca[0] * cb[0] + ca[1] * cb[1] + ca[2] * cb[2] + ca[3] * cb[3];
        norm_a += ca[0] * ca[0] + ca[1] * ca[1] + ca[2] * ca[2] + ca[3] * ca[3];
        norm_b += cb[0] * cb[0] + cb[1] * cb[1] + cb[2] * cb[2] + cb[3] * cb[3];
    }

    for (x, y) in rem_a.iter().zip(rem_b.iter()) {
        dot += x * y;
        norm_a += x * x;
        norm_b += y * y;
    }

    let denominator = norm_a.sqrt() * norm_b.sqrt();
    if denominator <= f32::EPSILON {
        0.0
    } else {
        (dot / denominator).clamp(-1.0, 1.0)
    }
}

/// Euclidean distance between two vectors.
pub fn euclidean_distance(a: &[f32], b: &[f32]) -> f32 {
    if a.len() != b.len() || a.is_empty() {
        return f32::MAX;
    }

    let sum_sq: f32 = a.iter().zip(b.iter()).map(|(&x, &y)| (x - y) * (x - y)).sum();
    sum_sq.sqrt()
}

/// Individual vector memory entry representing an agent's stored thought or fact.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VectorMemoryEntry {
    pub id: String,
    pub layer: String,
    pub title: String,
    pub content: String,
    pub embedding: Vec<f32>,
    pub timestamp: i64,
}

/// Search result holding matched memory metadata and its semantic similarity score.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemorySearchResult {
    pub id: String,
    pub layer: String,
    pub title: String,
    pub content: String,
    pub score: f32,
}

/// In-memory vector index for rapid semantic agent memory retrieval.
#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct VectorMemoryIndex {
    pub entries: Vec<VectorMemoryEntry>,
}

impl VectorMemoryIndex {
    pub fn new() -> Self {
        Self { entries: Vec::new() }
    }

    pub fn insert(&mut self, entry: VectorMemoryEntry) {
        // Upsert by ID
        if let Some(existing) = self.entries.iter_mut().find(|e| e.id == entry.id) {
            *existing = entry;
        } else {
            self.entries.push(entry);
        }
    }

    pub fn remove(&mut self, id: &str) -> bool {
        let initial_len = self.entries.len();
        self.entries.retain(|e| e.id != id);
        self.entries.len() < initial_len
    }

    pub fn clear(&mut self) {
        self.entries.clear();
    }

    pub fn count(&self) -> usize {
        self.entries.len()
    }

    /// Perform a semantic Top-K nearest-neighbor search with an optional minimum threshold.
    pub fn search(&self, query_embedding: &[f32], top_k: usize, min_score: f32) -> Vec<MemorySearchResult> {
        if query_embedding.is_empty() || self.entries.is_empty() {
            return Vec::new();
        }

        let mut scored: Vec<MemorySearchResult> = self
            .entries
            .iter()
            .map(|entry| {
                let score = cosine_similarity(query_embedding, &entry.embedding);
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

        // Sort descending by score
        scored.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));

        if scored.len() > top_k {
            scored.truncate(top_k);
        }

        scored
    }

    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(self)
    }

    pub fn from_json(json_str: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(json_str)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cosine_similarity_identical() {
        let v1 = vec![1.0, 2.0, 3.0, 4.0];
        let sim = cosine_similarity(&v1, &v1);
        assert!((sim - 1.0).abs() < 1e-5);
    }

    #[test]
    fn test_cosine_similarity_orthogonal() {
        let v1 = vec![1.0, 0.0, 0.0];
        let v2 = vec![0.0, 1.0, 0.0];
        let sim = cosine_similarity(&v1, &v2);
        assert!(sim.abs() < 1e-5);
    }

    #[test]
    fn test_cosine_similarity_opposite() {
        let v1 = vec![1.0, 2.0, 3.0];
        let v2 = vec![-1.0, -2.0, -3.0];
        let sim = cosine_similarity(&v1, &v2);
        assert!((sim - (-1.0)).abs() < 1e-5);
    }

    #[test]
    fn test_vector_index_search_ranking() {
        let mut index = VectorMemoryIndex::new();

        index.insert(VectorMemoryEntry {
            id: "mem1".to_string(),
            layer: "working".to_string(),
            title: "Task Plan".to_string(),
            content: "We need to refactor the crypto subsystem".to_string(),
            embedding: vec![0.9, 0.1, 0.0],
            timestamp: 1000,
        });

        index.insert(VectorMemoryEntry {
            id: "mem2".to_string(),
            layer: "episodic".to_string(),
            title: "Meeting Notes".to_string(),
            content: "Discussing UI layout and colors".to_string(),
            embedding: vec![0.1, 0.9, 0.0],
            timestamp: 2000,
        });

        // Query closer to mem1
        let query = vec![0.85, 0.15, 0.0];
        let results = index.search(&query, 5, 0.5);

        assert_eq!(results.len(), 1);
        assert_eq!(results[0].id, "mem1");
        assert!(results[0].score > 0.95);
    }

    #[test]
    fn test_serialization_roundtrip() {
        let mut index = VectorMemoryIndex::new();
        index.insert(VectorMemoryEntry {
            id: "test1".to_string(),
            layer: "core".to_string(),
            title: "Security Policy".to_string(),
            content: "Air gap verification enabled".to_string(),
            embedding: vec![0.5, 0.5, 0.5, 0.5],
            timestamp: 123456,
        });

        let json = index.to_json().expect("Serialization failed");
        let restored = VectorMemoryIndex::from_json(&json).expect("Deserialization failed");

        assert_eq!(restored.count(), 1);
        assert_eq!(restored.entries[0].title, "Security Policy");
    }
}
