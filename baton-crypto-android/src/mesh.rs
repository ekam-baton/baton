use serde::{Deserialize, Serialize};

pub const BATON_MAGIC: [u8; 4] = *b"BATN";
pub const PROTOCOL_VERSION: u8 = 1;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[repr(u8)]
pub enum PacketType {
    HandshakeInit = 1,
    HandshakeAck = 2,
    SwarmGossip = 3,
    AgentTaskDelegation = 4,
    AirGapChunk = 5,
    Heartbeat = 6,
    Unknown = 255,
}

impl From<u8> for PacketType {
    fn from(val: u8) -> Self {
        match val {
            1 => PacketType::HandshakeInit,
            2 => PacketType::HandshakeAck,
            3 => PacketType::SwarmGossip,
            4 => PacketType::AgentTaskDelegation,
            5 => PacketType::AirGapChunk,
            6 => PacketType::Heartbeat,
            _ => PacketType::Unknown,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MeshFrame {
    pub packet_type: PacketType,
    pub session_id: [u8; 16],
    pub sequence: u64,
    pub nonce: [u8; 12],
    pub payload: Vec<u8>,
}

#[derive(Debug, PartialEq, Eq)]
pub enum MeshError {
    InvalidMagic,
    UnsupportedVersion,
    IncompleteHeader,
    PayloadTruncated,
    UnknownPacketType,
}

impl MeshFrame {
    pub fn new(packet_type: PacketType, session_id: [u8; 16], sequence: u64, nonce: [u8; 12], payload: Vec<u8>) -> Self {
        Self {
            packet_type,
            session_id,
            sequence,
            nonce,
            payload,
        }
    }

    /// Encode the frame into a compact binary wire format.
    /// Format:
    /// [0..4]   Magic b"BATN"
    /// [4]      Version (1)
    /// [5]      Packet Type (u8)
    /// [6..22]  Session ID (16 bytes)
    /// [22..30] Sequence (u64 big-endian)
    /// [30..42] Nonce (12 bytes)
    /// [42..46] Payload Length (u32 big-endian)
    /// [46..]   Payload (N bytes)
    pub fn encode(&self) -> Vec<u8> {
        let payload_len = self.payload.len() as u32;
        let mut buf = Vec::with_capacity(46 + self.payload.len());

        buf.extend_from_slice(&BATON_MAGIC);
        buf.push(PROTOCOL_VERSION);
        buf.push(self.packet_type as u8);
        buf.extend_from_slice(&self.session_id);
        buf.extend_from_slice(&self.sequence.to_be_bytes());
        buf.extend_from_slice(&self.nonce);
        buf.extend_from_slice(&payload_len.to_be_bytes());
        buf.extend_from_slice(&self.payload);

        buf
    }

    /// Decode a binary wire buffer into a MeshFrame.
    pub fn decode(bytes: &[u8]) -> Result<Self, MeshError> {
        if bytes.len() < 46 {
            return Err(MeshError::IncompleteHeader);
        }

        if &bytes[0..4] != &BATON_MAGIC {
            return Err(MeshError::InvalidMagic);
        }

        if bytes[4] != PROTOCOL_VERSION {
            return Err(MeshError::UnsupportedVersion);
        }

        let packet_type = PacketType::from(bytes[5]);
        if packet_type == PacketType::Unknown {
            return Err(MeshError::UnknownPacketType);
        }

        let mut session_id = [0u8; 16];
        session_id.copy_from_slice(&bytes[6..22]);

        let sequence = u64::from_be_bytes(bytes[22..30].try_into().unwrap());

        let mut nonce = [0u8; 12];
        nonce.copy_from_slice(&bytes[30..42]);

        let payload_len = u32::from_be_bytes(bytes[42..46].try_into().unwrap()) as usize;

        if bytes.len() < 46 + payload_len {
            return Err(MeshError::PayloadTruncated);
        }

        let payload = bytes[46..46 + payload_len].to_vec();

        Ok(Self {
            packet_type,
            session_id,
            sequence,
            nonce,
            payload,
        })
    }
}

/// Air-Gap QR code chunker for transmitting binary SDP/data over visual frames.
pub struct AirGapChunker;

impl AirGapChunker {
    pub const DEFAULT_CHUNK_SIZE: usize = 300; // Optimal QR code capacity for reliable scanning

    pub fn split_payload(payload: &[u8], chunk_size: usize) -> Vec<String> {
        let size = if chunk_size == 0 { Self::DEFAULT_CHUNK_SIZE } else { chunk_size };
        let chunks: Vec<&[u8]> = payload.chunks(size).collect();
        let total = chunks.len();

        chunks
            .into_iter()
            .enumerate()
            .map(|(idx, chunk)| {
                let encoded_data = hex::encode(chunk);
                format!("{}:{}:{}", idx + 1, total, encoded_data)
            })
            .collect()
    }

    pub fn reassemble_chunk(chunks: &[String]) -> Result<Vec<u8>, &'static str> {
        if chunks.is_empty() {
            return Err("No chunks provided");
        }

        let mut parsed: Vec<(usize, usize, Vec<u8>)> = Vec::new();

        for chunk_str in chunks {
            let parts: Vec<&str> = chunk_str.split(':').collect();
            if parts.len() != 3 {
                return Err("Malformed chunk format");
            }

            let idx: usize = parts[0].parse().map_err(|_| "Invalid chunk index")?;
            let total: usize = parts[1].parse().map_err(|_| "Invalid chunk total")?;
            let data = hex::decode(parts[2]).map_err(|_| "Invalid hex data")?;

            parsed.push((idx, total, data));
        }

        let total = parsed[0].1;
        parsed.sort_by_key(|&(idx, _, _)| idx);

        if parsed.len() != total {
            return Err("Missing chunks for complete reassembly");
        }

        let mut result = Vec::new();
        for (i, (idx, _, data)) in parsed.into_iter().enumerate() {
            if idx != i + 1 {
                return Err("Chunk gap or duplicate index detected");
            }
            result.extend(data);
        }

        Ok(result)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mesh_frame_roundtrip() {
        let frame = MeshFrame::new(
            PacketType::SwarmGossip,
            [0xAA; 16],
            42,
            [0xBB; 12],
            b"Hello Sovereign Agent Swarm".to_vec(),
        );

        let encoded = frame.encode();
        let decoded = MeshFrame::decode(&encoded).expect("Decode should succeed");

        assert_eq!(decoded.packet_type, PacketType::SwarmGossip);
        assert_eq!(decoded.sequence, 42);
        assert_eq!(decoded.payload, b"Hello Sovereign Agent Swarm".to_vec());
    }

    #[test]
    fn test_air_gap_chunking_and_reassembly() {
        let original_data = b"Sovereign WebRTC SDP Offer with 2048-bit cryptographic key parameters for air-gap sync";
        let chunks = AirGapChunker::split_payload(original_data, 20);

        assert!(chunks.len() > 1);

        // Reorder chunks to prove order-independent reassembly
        let mut shuffled = chunks.clone();
        shuffled.reverse();

        let reconstructed = AirGapChunker::reassemble_chunk(&shuffled).expect("Reassembly failed");
        assert_eq!(reconstructed, original_data.to_vec());
    }
}
