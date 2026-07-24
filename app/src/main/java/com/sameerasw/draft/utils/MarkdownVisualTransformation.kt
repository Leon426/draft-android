package com.sameerasw.draft.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp

class MarkdownVisualTransformation(
    private val headerColor: Color,
    private val codeBackground: Color,
    private val quoteColor: Color,
    private val quoteBackground: Color
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val annotatedString = buildAnnotatedString {
            append(text.text)

            val lines = text.text.split("\n")
            var currentOffset = 0

            for (line in lines) {
                val lineLength = line.length
                val lineEnd = currentOffset + lineLength

                when {
                    // H1 Header (♯ )
                    line.startsWith("♯ ") || line.startsWith("# ") -> {
                        addStyle(
                            SpanStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = headerColor
                            ),
                            currentOffset,
                            lineEnd
                        )
                    }
                    // H2 Header (⌗ )
                    line.startsWith("⌗ ") || line.startsWith("## ") -> {
                        addStyle(
                            SpanStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = headerColor
                            ),
                            currentOffset,
                            lineEnd
                        )
                    }
                    // H3 Header (### )
                    line.startsWith("### ") -> {
                        addStyle(
                            SpanStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = headerColor
                            ),
                            currentOffset,
                            lineEnd
                        )
                    }
                    // Blockquotes (│ )
                    line.startsWith("│ ") || line.startsWith("> ") -> {
                        addStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = quoteColor,
                                background = quoteBackground
                            ),
                            currentOffset,
                            lineEnd
                        )
                    }
                    // Code blocks (``` or lines starting with ```)
                    line.startsWith("```") || line.startsWith("    ") -> {
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                                fontSize = 14.sp
                            ),
                            currentOffset,
                            lineEnd
                        )
                    }
                }

                // Inline code formatting `code`
                var codeStart = line.indexOf('`')
                while (codeStart != -1) {
                    val codeEnd = line.indexOf('`', codeStart + 1)
                    if (codeEnd != -1) {
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                                fontSize = 14.sp
                            ),
                            currentOffset + codeStart,
                            currentOffset + codeEnd + 1
                        )
                        codeStart = line.indexOf('`', codeEnd + 1)
                    } else {
                        break
                    }
                }

                currentOffset += lineLength + 1
            }
        }

        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}
