package com.jnd.ngdroid.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown

/**
 * Assistant message body with native math support and chat-scaled markdown.
 *
 * Plain messages render exactly as before (single [Markdown] call).
 * Messages with display math (`$$…$$`, `\[…\]`) render as prose cards
 * interleaved with centered [MathDisplayCard]s; inline math (`$…$`, `\(…\)`)
 * is prettified to readable Unicode inside the prose so lists and tables
 * keep working. No WebView: offline, themed, and list-friendly.
 *
 * Headings use [chatMarkdownTypography] (compact 22→12sp bold ladder, not
 * full-page display sizes) and [chatMarkdownColors] (light/dark-aware), and
 * `#Title` without a space is fixed by [normalizeMarkdownForChat].
 */
@Composable
fun AssistantMarkdownWithMath(text: String) {
    val normalized = remember(text) { normalizeMarkdownForChat(text) }
    val segments = remember(normalized) { parseDocSegments(normalized) }
    val colors = chatMarkdownColors()
    val typography = chatMarkdownTypography()
    if (segments.none { it is DocSegment.DisplayMath }) {
        val pretty = remember(normalized) { prettifyInlineMath(normalized) }
        Markdown(
            pretty,
            colors = colors,
            typography = typography,
            modifier = chatMarkdownModifier(),
            imageTransformer = AssistantImageTransformer
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            segments.forEach { seg ->
                when (seg) {
                    is DocSegment.Prose ->
                        if (seg.text.isNotBlank()) {
                            Markdown(
                                remember(seg.text) { prettifyInlineMath(seg.text) },
                                colors = colors,
                                typography = typography,
                                modifier = chatMarkdownModifier(),
                                imageTransformer = AssistantImageTransformer
                            )
                        }
                    is DocSegment.DisplayMath -> MathDisplayCard(latex = seg.latex)
                }
            }
        }
    }
}

/**
 * Centered equation card: large serif type on a tonal surface, horizontally
 * scrollable for wide formulas, with a copy button for the raw LaTeX.
 */
@Composable
fun MathDisplayCard(latex: String) {
    val context = LocalContext.current
    val pretty = remember(latex) { prettyMath(latex) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = pretty,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    lineHeight = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = {
                    copyToClipboard(context, latex.trim())
                    Toast.makeText(context, "Equation copied (LaTeX)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy equation LaTeX",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
