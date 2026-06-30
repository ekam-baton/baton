package com.ekam.baton.feature.chat

import androidx.compose.foundation.text.selection.SelectionContainer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

sealed class MarkdownPart {
    data class Text(val content: String) : MarkdownPart()
    data class CodeBlock(val language: String, val content: String) : MarkdownPart()
}

fun parseMarkdownBlocks(text: String): List<MarkdownPart> {
    val parts = mutableListOf<MarkdownPart>()
    val codeBlockRegex = Regex("```(.*?)\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
    var currentIndex = 0

    codeBlockRegex.findAll(text).forEach { matchResult ->
        val textBefore = text.substring(currentIndex, matchResult.range.first)
        if (textBefore.isNotEmpty()) {
            parts.add(MarkdownPart.Text(textBefore))
        }
        val language = matchResult.groupValues[1].trim()
        val codeContent = matchResult.groupValues[2].trimEnd()
        parts.add(MarkdownPart.CodeBlock(language, codeContent))
        currentIndex = matchResult.range.last + 1
    }

    if (currentIndex < text.length) {
        parts.add(MarkdownPart.Text(text.substring(currentIndex)))
    }

    return parts
}

@Composable
fun MarkdownRenderer(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val blocks = parseMarkdownBlocks(text)
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { part ->
            when (part) {
                is MarkdownPart.Text -> {
                    var cleanText = part.content
                    val inlineStyles = buildAnnotatedString {
                        append(cleanText)
                        Regex("\\*\\*(.*?)\\*\\*").findAll(cleanText).forEach { match ->
                            addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                        }
                        Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)").findAll(cleanText).forEach { match ->
                            addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
                        }
                        Regex("`(.*?)`").findAll(cleanText).forEach { match ->
                            addStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = Color.Gray.copy(alpha = 0.3f)
                                ), 
                                match.range.first, 
                                match.range.last + 1
                            )
                        }
                    }
                    Text(
                        text = inlineStyles,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is MarkdownPart.CodeBlock -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = part.language.ifEmpty { "code" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = {
                                        val clip = ClipData.newPlainText("Copied Code", part.content)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = part.content,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}
