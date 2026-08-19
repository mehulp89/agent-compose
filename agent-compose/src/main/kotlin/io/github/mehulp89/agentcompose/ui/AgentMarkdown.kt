package io.github.mehulp89.agentcompose.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Lightweight Markdown renderer optimized for text that grows while an LLM is streaming.
 *
 * Supported blocks include headings, ordered and nested unordered lists, quotes, dividers,
 * GitHub-style tables, and fenced code with a copy action and lightweight syntax highlighting.
 */
@Composable
public fun AgentMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    codeContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onCopyCode: ((String) -> Unit)? = null,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val context = LocalContext.current
    val copyCode: (String) -> Unit = onCopyCode ?: { code ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AgentCompose code", code))
    }

    SelectionContainer(modifier = modifier) {
        Column {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> MarkdownHeading(block, color)
                    is MarkdownBlock.Paragraph -> Text(
                        text = inlineMarkdown(block.text),
                        modifier = Modifier.padding(vertical = 3.dp),
                        color = color,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    is MarkdownBlock.Bullet -> MarkdownListRow(
                        marker = "•",
                        text = block.text,
                        depth = block.depth,
                        color = color,
                    )
                    is MarkdownBlock.Ordered -> MarkdownListRow(
                        marker = "${block.number}.",
                        text = block.text,
                        depth = block.depth,
                        color = color,
                    )
                    is MarkdownBlock.Quote -> Row(Modifier.padding(vertical = 4.dp)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = inlineMarkdown(block.text),
                            color = color,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is MarkdownBlock.Code -> MarkdownCodeBlock(
                        block = block,
                        color = color,
                        containerColor = codeContainerColor,
                        onCopy = copyCode,
                    )
                    is MarkdownBlock.Table -> MarkdownTable(block, color)
                    MarkdownBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun MarkdownHeading(block: MarkdownBlock.Heading, color: Color) {
    Text(
        text = inlineMarkdown(block.text),
        modifier = Modifier.padding(top = 8.dp, bottom = 3.dp),
        color = color,
        style = when (block.level) {
            1 -> MaterialTheme.typography.headlineSmall
            2 -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.titleMedium
        },
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun MarkdownListRow(
    marker: String,
    text: String,
    depth: Int,
    color: Color,
) {
    Row(
        modifier = Modifier.padding(
            start = (depth.coerceAtMost(4) * 16).dp,
            top = 2.dp,
            bottom = 2.dp,
        ),
    ) {
        Text(text = marker, color = color, modifier = Modifier.widthIn(min = 22.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = inlineMarkdown(text),
            color = color,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun MarkdownCodeBlock(
    block: MarkdownBlock.Code,
    color: Color,
    containerColor: Color,
    onCopy: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = block.language.ifBlank { "code" },
                    modifier = Modifier.padding(top = 9.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                TextButton(onClick = { onCopy(block.code) }) {
                    Text("Copy")
                }
            }
            Text(
                text = highlightCode(block.code, block.language),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun MarkdownTable(block: MarkdownBlock.Table, color: Color) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            MarkdownTableRow(block.headers, color, header = true)
            HorizontalDivider()
            block.rows.forEachIndexed { index, cells ->
                MarkdownTableRow(cells, color, header = false)
                if (index != block.rows.lastIndex) HorizontalDivider(color = Color(0x22808080))
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(cells: List<String>, color: Color, header: Boolean) {
    Row {
        cells.forEach { cell ->
            Text(
                text = inlineMarkdown(cell),
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 240.dp)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                color = color,
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String, val depth: Int = 0) : MarkdownBlock
    data class Ordered(val number: Int, val text: String, val depth: Int = 0) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val language: String, val code: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

private val orderedListPattern = Regex("^(\\s*)(\\d+)\\.\\s+(.+)$")
private val tableDividerCell = Regex("^:?-{3,}:?$")

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isBlank()) return emptyList()
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var codeLanguage = ""
    var inCode = false
    var index = 0

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

    while (index < lines.size) {
        val rawLine = lines[index]
        val line = rawLine.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                flushParagraph()
                codeLanguage = line.trimStart().removePrefix("```").trim()
                inCode = true
            }
            index++
            continue
        }
        if (inCode) {
            code += rawLine
            index++
            continue
        }

        if (index + 1 < lines.size && isTableHeader(line, lines[index + 1])) {
            flushParagraph()
            val headers = tableCells(line)
            val rows = mutableListOf<List<String>>()
            index += 2
            while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                val cells = tableCells(lines[index])
                rows += headers.indices.map { cellIndex -> cells.getOrElse(cellIndex) { "" } }
                index++
            }
            blocks += MarkdownBlock.Table(headers, rows)
            continue
        }

        val ordered = orderedListPattern.matchEntire(line)
        when {
            line.isBlank() -> flushParagraph()
            line.trim() in setOf("---", "***", "___") -> {
                flushParagraph()
                blocks += MarkdownBlock.Divider
            }
            line.startsWith("### ") -> addHeading(blocks, paragraph, 3, line.removePrefix("### "))
            line.startsWith("## ") -> addHeading(blocks, paragraph, 2, line.removePrefix("## "))
            line.startsWith("# ") -> addHeading(blocks, paragraph, 1, line.removePrefix("# "))
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                flushParagraph()
                val indent = line.length - line.trimStart().length
                blocks += MarkdownBlock.Bullet(line.trimStart().drop(2).trim(), indent / 2)
            }
            ordered != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Ordered(
                    number = ordered.groupValues[2].toInt(),
                    text = ordered.groupValues[3].trim(),
                    depth = ordered.groupValues[1].length / 2,
                )
            }
            line.startsWith("> ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(line.drop(2).trim())
            }
            else -> paragraph += line.trim()
        }
        index++
    }
    flushParagraph()
    if (inCode || code.isNotEmpty()) flushCode()
    return blocks
}

private fun addHeading(
    blocks: MutableList<MarkdownBlock>,
    paragraph: MutableList<String>,
    level: Int,
    text: String,
) {
    if (paragraph.isNotEmpty()) {
        blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ").trim())
        paragraph.clear()
    }
    blocks += MarkdownBlock.Heading(level, text)
}

private fun isTableHeader(line: String, divider: String): Boolean {
    if (!line.contains('|') || !divider.contains('|')) return false
    val headers = tableCells(line)
    val dividers = tableCells(divider)
    return headers.isNotEmpty() && dividers.size == headers.size && dividers.all {
        tableDividerCell.matches(it)
    }
}

private fun tableCells(line: String): List<String> = line
    .trim()
    .removePrefix("|")
    .removeSuffix("|")
    .split('|')
    .map(String::trim)

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
                } else append(text[index++])
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x1A808080)))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else append(text[index++])
            }
            text[index] == '[' -> {
                val labelEnd = text.indexOf(']', index + 1)
                val urlStart = if (labelEnd >= 0) text.indexOf('(', labelEnd + 1) else -1
                val urlEnd = if (urlStart >= 0) text.indexOf(')', urlStart + 1) else -1
                if (labelEnd > index && urlStart == labelEnd + 1 && urlEnd > urlStart) {
                    pushStyle(SpanStyle(color = Color(0xFF1565C0), textDecoration = TextDecoration.Underline))
                    append(text.substring(index + 1, labelEnd))
                    pop()
                    index = urlEnd + 1
                } else append(text[index++])
            }
            else -> append(text[index++])
        }
    }
}

private val syntaxTokenPattern = Regex(
    """("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|//[^\n]*|#[^\n]*|\b\d+(?:\.\d+)?\b|\b(?:class|data|fun|val|var|when|if|else|for|while|return|object|interface|override|private|public|internal|suspend|import|package|true|false|null)\b)""",
)

private fun highlightCode(code: String, language: String): AnnotatedString {
    if (language.lowercase() !in setOf("kotlin", "kt", "java", "json", "xml", "yaml", "yml")) {
        return AnnotatedString(code)
    }
    return buildAnnotatedString {
        var cursor = 0
        syntaxTokenPattern.findAll(code).forEach { match ->
            append(code.substring(cursor, match.range.first))
            val token = match.value
            val tokenColor = when {
                token.startsWith("//") || token.startsWith("#") -> Color(0xFF6A9955)
                token.startsWith('"') || token.startsWith('\'') -> Color(0xFFCE9178)
                token.firstOrNull()?.isDigit() == true -> Color(0xFFB5CEA8)
                else -> Color(0xFF569CD6)
            }
            pushStyle(SpanStyle(color = tokenColor))
            append(token)
            pop()
            cursor = match.range.last + 1
        }
        append(code.substring(cursor))
    }
}
