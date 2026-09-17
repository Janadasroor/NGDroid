/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jnd.ngdroid.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownImage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import com.jnd.ngdroid.agent.stripMarkdownImagesForDisplay
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

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
fun AssistantMarkdownWithMath(
    text: String,
    onImageClick: ((String) -> Unit)? = null,
    error: Boolean = false
) {
    val normalized = remember(text) {
        normalizeMarkdownForChat(stripMarkdownImagesForDisplay(text))
    }
    val segments = remember(normalized) { parseDocSegments(normalized) }
    val colors = if (error) chatMarkdownErrorColors() else chatMarkdownColors()
    val typography = chatMarkdownTypography()
    // Inline images have no click API in mikepenz 0.35.0: override the image
    // component with a clickable wrapper that opens the full viewer.
    // findChildOfTypeRecursive is internal, so walk the AST ourselves.
    val components = remember(onImageClick) {
        markdownComponents(
            image = { model ->
                val link = findImageLink(model.content, model.node)
                if (link != null && onImageClick != null) {
                    Box(modifier = Modifier.clickable { onImageClick(link) }) {
                        MarkdownImage(model.content, model.node)
                    }
                } else {
                    MarkdownImage(model.content, model.node)
                }
            },
            checkbox = {
                MarkdownCheckBox(it.content, it.node, it.typography.text)
            }
        )
    }
    if (segments.none { it is DocSegment.DisplayMath }) {
        val pretty = remember(normalized) { prettifyInlineMath(normalized) }
        Markdown(
            pretty,
            colors = colors,
            typography = typography,
            modifier = chatMarkdownModifier(),
            imageTransformer = AssistantImageTransformer,
            components = components
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
                                imageTransformer = AssistantImageTransformer,
                                components = components
                            )
                        }
                    is DocSegment.DisplayMath -> MathDisplayCard(latex = seg.latex)
                }
            }
        }
    }
}

private fun findImageLink(content: String, node: ASTNode): String? {
    val dest = findLinkDestination(node) ?: return null
    return runCatching { dest.getUnescapedTextInNode(content) }
        .getOrNull()?.takeIf { it.isNotBlank() }
}

private fun findLinkDestination(node: ASTNode): ASTNode? {
    if (node.type == MarkdownElementTypes.LINK_DESTINATION) return node
    for (child in node.children) {
        val found = findLinkDestination(child)
        if (found != null) return found
    }
    return null
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
