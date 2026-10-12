use serde::{Deserialize, Serialize};

/// Type of markdown block detected during incremental token stream parsing.
#[derive(Debug, PartialEq, Eq, Clone, Serialize, Deserialize)]
pub enum MarkdownBlockType {
    Paragraph,
    Heading(u8),
    CodeBlock { language: String, is_closed: bool },
    MathBlock { is_display: bool, is_closed: bool },
    Blockquote,
    ListItem,
}

/// A parsed markdown block segment.
#[derive(Debug, PartialEq, Eq, Clone, Serialize, Deserialize)]
pub struct ParsedBlock {
    pub block_type: MarkdownBlockType,
    pub content: String,
}

/// Result of streaming parsing containing segregated thinking trace and structured markdown blocks.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StreamingParseResult {
    pub thinking_trace: Option<String>,
    pub is_thinking_active: bool,
    pub blocks: Vec<ParsedBlock>,
    pub estimated_tokens: usize,
    pub has_unclosed_code_block: bool,
}

/// Zero-copy streaming markdown parser for real-time mobile LLM token rendering.
pub struct StreamingMarkdownParser;

impl StreamingMarkdownParser {
    /// Parses incoming raw LLM output, extracting `<think>` tags and structuring markdown.
    pub fn parse_stream(raw_text: &str) -> StreamingParseResult {
        let (thinking_trace, is_thinking_active, visible_text) = Self::extract_thinking(raw_text);

        let mut blocks = Vec::new();
        let mut estimated_tokens = visible_text.split_whitespace().count();
        if estimated_tokens == 0 && !visible_text.is_empty() {
            estimated_tokens = 1;
        }

        let mut lines = visible_text.lines().peekable();
        let mut has_unclosed_code_block = false;

        while let Some(line) = lines.next() {
            let trimmed = line.trim();

            // 1. Fenced Code Block
            if trimmed.starts_with("```") {
                let language = trimmed.trim_start_matches('`').trim().to_string();
                let mut code_lines = Vec::new();
                let mut is_closed = false;

                while let Some(code_line) = lines.next() {
                    if code_line.trim().starts_with("```") {
                        is_closed = true;
                        break;
                    }
                    code_lines.push(code_line);
                }

                if !is_closed {
                    has_unclosed_code_block = true;
                }

                blocks.push(ParsedBlock {
                    block_type: MarkdownBlockType::CodeBlock {
                        language,
                        is_closed,
                    },
                    content: code_lines.join("\n"),
                });
                continue;
            }

            // 2. Math Block ($$...$$)
            if trimmed.starts_with("$$") {
                let mut math_lines = Vec::new();
                let mut is_closed = false;

                // Check single-line display math: $$x = 1$$
                if trimmed.len() > 4 && trimmed.ends_with("$$") {
                    let inner = trimmed[2..trimmed.len() - 2].trim();
                    blocks.push(ParsedBlock {
                        block_type: MarkdownBlockType::MathBlock {
                            is_display: true,
                            is_closed: true,
                        },
                        content: inner.to_string(),
                    });
                    continue;
                }

                while let Some(math_line) = lines.next() {
                    if math_line.trim().ends_with("$$") {
                        let inner = math_line.trim().trim_end_matches('$').trim();
                        if !inner.is_empty() {
                            math_lines.push(inner);
                        }
                        is_closed = true;
                        break;
                    }
                    math_lines.push(math_line);
                }

                blocks.push(ParsedBlock {
                    block_type: MarkdownBlockType::MathBlock {
                        is_display: true,
                        is_closed,
                    },
                    content: math_lines.join("\n"),
                });
                continue;
            }

            // 3. Headings (#, ##, ###)
            if trimmed.starts_with('#') {
                let hashes = trimmed.chars().take_while(|&c| c == '#').count();
                if hashes <= 6 && trimmed.chars().nth(hashes) == Some(' ') {
                    let heading_text = trimmed[hashes..].trim().to_string();
                    blocks.push(ParsedBlock {
                        block_type: MarkdownBlockType::Heading(hashes as u8),
                        content: heading_text,
                    });
                    continue;
                }
            }

            // 4. Blockquotes (> ...)
            if trimmed.starts_with('>') {
                let quote_text = trimmed.trim_start_matches('>').trim().to_string();
                blocks.push(ParsedBlock {
                    block_type: MarkdownBlockType::Blockquote,
                    content: quote_text,
                });
                continue;
            }

            // 5. List items (*, -, 1.)
            if trimmed.starts_with("- ") || trimmed.starts_with("* ") {
                let item_text = trimmed[2..].trim().to_string();
                blocks.push(ParsedBlock {
                    block_type: MarkdownBlockType::ListItem,
                    content: item_text,
                });
                continue;
            }

            // 6. Regular Paragraph
            if !trimmed.is_empty() {
                blocks.push(ParsedBlock {
                    block_type: MarkdownBlockType::Paragraph,
                    content: line.to_string(),
                });
            }
        }

        StreamingParseResult {
            thinking_trace,
            is_thinking_active,
            blocks,
            estimated_tokens,
            has_unclosed_code_block,
        }
    }

    /// Extracts `<think>...</think>` tags from LLM output.
    fn extract_thinking(raw: &str) -> (Option<String>, bool, String) {
        if let Some(start_idx) = raw.find("<think>") {
            let after_start = &raw[start_idx + 7..];
            if let Some(end_idx) = after_start.find("</think>") {
                let thinking = after_start[..end_idx].trim().to_string();
                let remaining = format!("{}{}", &raw[..start_idx], &after_start[end_idx + 8..]);
                (Some(thinking), false, remaining.trim().to_string())
            } else {
                // Currently streaming inside <think>
                let thinking = after_start.trim().to_string();
                let remaining = raw[..start_idx].trim().to_string();
                (Some(thinking), true, remaining)
            }
        } else {
            (None, false, raw.to_string())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_thinking_tag_extraction_closed() {
        let raw = "<think>Analyzing user query for security</think>Here is the response.";
        let result = StreamingMarkdownParser::parse_stream(raw);

        assert_eq!(result.thinking_trace, Some("Analyzing user query for security".to_string()));
        assert!(!result.is_thinking_active);
        assert_eq!(result.blocks.len(), 1);
        assert_eq!(result.blocks[0].content, "Here is the response.");
    }

    #[test]
    fn test_thinking_tag_extraction_streaming_active() {
        let raw = "<think>I am currently reasoning about crypto keys...";
        let result = StreamingMarkdownParser::parse_stream(raw);

        assert_eq!(result.thinking_trace, Some("I am currently reasoning about crypto keys...".to_string()));
        assert!(result.is_thinking_active);
        assert!(result.blocks.is_empty());
    }

    #[test]
    fn test_code_block_unclosed_streaming() {
        let raw = "Here is the code:\n```kotlin\nval key = ByteArray(32)\n";
        let result = StreamingMarkdownParser::parse_stream(raw);

        assert!(result.has_unclosed_code_block);
        assert_eq!(result.blocks.len(), 2);
        assert_eq!(result.blocks[0].block_type, MarkdownBlockType::Paragraph);
        assert_eq!(
            result.blocks[1].block_type,
            MarkdownBlockType::CodeBlock {
                language: "kotlin".to_string(),
                is_closed: false,
            }
        );
        assert_eq!(result.blocks[1].content, "val key = ByteArray(32)");
    }

    #[test]
    fn test_code_block_closed() {
        let raw = "```rust\nfn main() {}\n```\nDone.";
        let result = StreamingMarkdownParser::parse_stream(raw);

        assert!(!result.has_unclosed_code_block);
        assert_eq!(result.blocks.len(), 2);
        assert_eq!(
            result.blocks[0].block_type,
            MarkdownBlockType::CodeBlock {
                language: "rust".to_string(),
                is_closed: true,
            }
        );
    }
}
