package io.github.mehulp89.agentcompose.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** Lightweight Markdown renderer optimized for text that grows while an LLM is streaming. */
@Composable
public fun AgentMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    codeContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    SelectionContainer(modifier = modifier) {
        Column {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> Text(
                        text = inlineMarkdown(block.text),
                        modifier = Modifier.padding(top = 6.dp, bottom = 3.dp),
                        color = color,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    is MarkdownBlock.Paragraph -> Text(
                        text = inlineMarkdown(block.text),
                        modifier = Modifier.padding(vertical = 3.dp),
                        color = color,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    is MarkdownBlock.Bullet -> Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(text = "•", color = color)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = inlineMarkdown(block.text),
                            color = color,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    is MarkdownBlock.Quote -> Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Text(text = "▌", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = inlineMarkdown(block.text),
                            color = color,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is MarkdownBlock.Code -> Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        color = codeContainerColor,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (block.language.isNotBlank()) {
                                Text(
                                    text = block.language,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                text = block.code,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                color = color,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val language: String, val code: String) : MarkdownBlock
}

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isBlank()) return emptyList()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var codeLanguage = ""
    var inCode = false

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ").trim())
            paragraph.clear()
        }
    }

    fun flushCode() {
        blocks += MarkdownBlock.Code(codeLanguage, code.joinToString("\n"))
        code.clear()
        codeLanguage = ""
    }

    markdown.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        if (line.startsWith("```")) {
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                flushParagraph()
                codeLanguage = line.removePrefix("```").trim()
                inCode = true
            }
            return@forEach
        }
        if (inCode) {
            code += rawLine
            return@forEach
        }
        when {
            line.isBlank() -> flushParagraph()
            line.startsWith("### ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(3, line.removePrefix("### "))
            }
            line.startsWith("## ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(2, line.removePrefix("## "))
            }
            line.startsWith("# ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(1, line.removePrefix("# "))
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(line.drop(2).trim())
            }
            line.startsWith("> ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(line.drop(2).trim())
            }
            else -> paragraph += line.trim()
        }
    }
    flushParagraph()
    if (inCode || code.isNotEmpty()) flushCode()
    return blocks
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append(text[index++])
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x1A808080),
                        ),
                    )
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index++])
                }
            }
            text[index] == '[' -> {
                val labelEnd = text.indexOf(']', index + 1)
                val urlStart = if (labelEnd >= 0) text.indexOf('(', labelEnd + 1) else -1
                val urlEnd = if (urlStart >= 0) text.indexOf(')', urlStart + 1) else -1
                if (labelEnd > index && urlStart == labelEnd + 1 && urlEnd > urlStart) {
                    pushStyle(
                        SpanStyle(
                            color = Color(0xFF1565C0),
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                    append(text.substring(index + 1, labelEnd))
                    pop()
                    append(" (")
                    append(text.substring(urlStart + 1, urlEnd))
                    append(")")
                    index = urlEnd + 1
                } else {
                    append(text[index++])
                }
            }
            else -> append(text[index++])
        }
    }
}
