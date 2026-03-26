package me.app.oneclickpr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import me.app.oneclickpr.MainViewModel

/**
 * 极致轻量的自研 Markdown 解析引擎
 * 拥有支持解析本地虚拟内存附件直显的杀手级功能。
 */
@Composable
fun SimpleMarkdown(content: String, fileNodes: List<MainViewModel.FileNode>) {
    val blocks = content.split(Regex("\n{2,}"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            val lines = block.split('\n')
            val firstLine = lines.first().trim()

            when {
                firstLine.startsWith("#") -> {
                    val level = firstLine.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val text = firstLine.removePrefix("#".repeat(level)).trim()
                    val fontSize = (24 - (level - 1) * 2).sp
                    Text(text = parseInline(text), fontSize = fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                firstLine.startsWith("- ") || firstLine.startsWith("* ") -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lines.forEach { line ->
                            val text = line.trim().removePrefix("- ").removePrefix("* ").trim()
                            if (text.isNotEmpty()) {
                                Row {
                                    Text(" • ", modifier = Modifier.padding(end = 4.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = parseInline(text), color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
                firstLine.startsWith(">") -> {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(8.dp)) {
                        Text(text = parseInline(block.replace(Regex("^>\\s?", RegexOption.MULTILINE), "")), fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                firstLine.matches(Regex("^!\\[(.*?)]\\((.*?)\\)$")) -> {
                    val match = Regex("^!\\[(.*?)]\\((.*?)\\)$").find(firstLine)!!
                    val alt = match.groupValues[1]
                    val url = match.groupValues[2]
                    
                    var model: Any = url
                    // 智能拦截：将虚拟附件直接映射回手机相册真实的本地 URI 进行光速预览！
                    if (url.startsWith("pr_attachments/")) {
                        val fileName = url.removePrefix("pr_attachments/")
                        val fileItem = fileNodes.find { !it.isDirectory && (it.virtualPath == url || it.name == fileName) }
                        if (fileItem != null) {
                            model = fileItem.uri
                        }
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = alt,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                else -> {
                    Text(text = parseInline(block.replace("\n", " ")), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun parseInline(text: String): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant
    
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                text.startsWith("*", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1 && end > i + 1 && text[i+1] != ' ') {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBgColor)) { append(" ${text.substring(i + 1, end)} ") }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                text.startsWith("[", i) -> {
                    val closeBracket = text.indexOf("](", i + 1)
                    if (closeBracket != -1) {
                        val closeParen = text.indexOf(")", closeBracket + 2)
                        if (closeParen != -1) {
                            val linkText = text.substring(i + 1, closeBracket)
                            withStyle(SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline)) { append(linkText) }
                            i = closeParen + 1
                            continue
                        }
                    }
                    append(text[i]); i++
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}