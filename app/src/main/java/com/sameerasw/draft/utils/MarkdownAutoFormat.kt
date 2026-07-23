package com.sameerasw.draft.utils

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

object MarkdownAutoFormat {

    fun processBodyChange(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text
        val cursor = newValue.selection.end

        // 1. Notion-style live triggers on typing space:
        // # Header 1, ## Header 2, ### Header 3, #### H4, ##### H5, ###### H6,
        // - or * Bullet, [] or [ ] Checkbox, [x] Checked, > Quote, ``` Code block
        if (newText.length > oldText.length && cursor > 0) {
            val justTypedChar = newText[cursor - 1]
            if (justTypedChar == ' ') {
                val lineStart = newText.lastIndexOf('\n', cursor - 2) + 1
                val prefix = newText.substring(lineStart, cursor)

                // Headers: # , ## , ### , #### , ##### , ###### 
                if (prefix == "# ") {
                    val formattedText = newText.replaceRange(lineStart, cursor, "♯ ")
                    return TextFieldValue(text = formattedText, selection = TextRange(lineStart + 2))
                } else if (prefix == "## ") {
                    val formattedText = newText.replaceRange(lineStart, cursor, "⌗ ")
                    return TextFieldValue(text = formattedText, selection = TextRange(lineStart + 2))
                } else if (prefix == "### ") {
                    val formattedText = newText.replaceRange(lineStart, cursor, "### ")
                    return TextFieldValue(text = formattedText, selection = TextRange(lineStart + 4))
                }

                // Code block trigger: ``` 
                if (prefix == "``` ") {
                    val formattedText = newText.replaceRange(lineStart, cursor, "```\n\n```")
                    return TextFieldValue(text = formattedText, selection = TextRange(lineStart + 4))
                }

                // Bullet lists: '- ' or '* ' -> '• '
                if (prefix == "- " || prefix == "* ") {
                    val formattedText = newText.replaceRange(lineStart, cursor, "• ")
                    return TextFieldValue(text = formattedText, selection = TextRange(lineStart + 2))
                }

                // Checkboxes: '[] ' or '[ ] ' -> '☐ '
                if (prefix == "[] " || prefix == "[ ] ") {
                    val formattedText = newText.replaceRange(lineStart, cursor, "☐ ")
                    return TextFieldValue(text = formattedText, selection = TextRange(lineStart + 2))
                }

                // Checked box: '[x] ' -> '☑ '
                if (prefix.equals("[x] ", ignoreCase = true)) {
                    val formattedText = newText.replaceRange(lineStart, cursor, "☑ ")
                    return TextFieldValue(text = formattedText, selection = TextRange(lineStart + 2))
                }

                // Blockquotes: '> ' -> '│ '
                if (prefix == "> ") {
                    val formattedText = newText.replaceRange(lineStart, cursor, "│ ")
                    return TextFieldValue(text = formattedText, selection = TextRange(lineStart + 2))
                }

                // Numbered list: '1. ' -> '1. '
                val numberedMatch = Regex("^(\\d+)\\.\\s$").find(prefix)
                if (numberedMatch != null) {
                    val num = numberedMatch.groupValues[1].toIntOrNull() ?: 1
                    val formattedText = newText.replaceRange(lineStart, cursor, "$num. ")
                    return TextFieldValue(text = formattedText, selection = TextRange(lineStart + "$num. ".length))
                }
            }
        }

        // 2. Notion-style Enter continuation & Backspace clearing for lists & quotes
        if (newText.length > oldText.length && cursor > 0 && newText[cursor - 1] == '\n') {
            val prevLineStart = oldText.lastIndexOf('\n', oldValue.selection.end - 1).let { if (it == -1) 0 else it + 1 }
            val prevLine = oldText.substring(prevLineStart, oldValue.selection.end)

            // Bullet continuation
            if (prevLine.startsWith("• ")) {
                if (prevLine.trim() == "•") {
                    val textWithoutBullet = oldText.replaceRange(prevLineStart, oldValue.selection.end, "")
                    return TextFieldValue(text = textWithoutBullet, selection = TextRange(prevLineStart))
                } else {
                    val textWithBullet = newText.replaceRange(cursor, cursor, "• ")
                    return TextFieldValue(text = textWithBullet, selection = TextRange(cursor + 2))
                }
            }
            // Checkbox continuation
            else if (prevLine.startsWith("☐ ") || prevLine.startsWith("☑ ")) {
                if (prevLine.trim() == "☐" || prevLine.trim() == "☑") {
                    val textWithoutBox = oldText.replaceRange(prevLineStart, oldValue.selection.end, "")
                    return TextFieldValue(text = textWithoutBox, selection = TextRange(prevLineStart))
                } else {
                    val textWithBox = newText.replaceRange(cursor, cursor, "☐ ")
                    return TextFieldValue(text = textWithBox, selection = TextRange(cursor + 2))
                }
            }
            // Quote continuation
            else if (prevLine.startsWith("│ ")) {
                if (prevLine.trim() == "│") {
                    val textWithoutQuote = oldText.replaceRange(prevLineStart, oldValue.selection.end, "")
                    return TextFieldValue(text = textWithoutQuote, selection = TextRange(prevLineStart))
                } else {
                    val textWithQuote = newText.replaceRange(cursor, cursor, "│ ")
                    return TextFieldValue(text = textWithQuote, selection = TextRange(cursor + 2))
                }
            }
            // Numbered list continuation
            else {
                val numMatch = Regex("^(\\d+)\\.\\s(.*)").find(prevLine)
                if (numMatch != null) {
                    val currentNum = numMatch.groupValues[1].toIntOrNull() ?: 1
                    val content = numMatch.groupValues[2]
                    if (content.isBlank()) {
                        val textWithoutNum = oldText.replaceRange(prevLineStart, oldValue.selection.end, "")
                        return TextFieldValue(text = textWithoutNum, selection = TextRange(prevLineStart))
                    } else {
                        val nextNumStr = "${currentNum + 1}. "
                        val textWithNum = newText.replaceRange(cursor, cursor, nextNumStr)
                        return TextFieldValue(text = textWithNum, selection = TextRange(cursor + nextNumStr.length))
                    }
                }
            }
        }

        // 3. Backspace clearing of prefix in a single press
        if (newText.length < oldText.length && oldValue.selection.end > 0) {
            val lineStart = oldText.lastIndexOf('\n', oldValue.selection.end - 1).let { if (it == -1) 0 else it + 1 }
            val oldCursor = oldValue.selection.end

            if (oldCursor == lineStart + 2) {
                val currentPrefix = oldText.substring(lineStart, oldCursor)
                if (currentPrefix == "• " || currentPrefix == "☐ " || currentPrefix == "☑ " || currentPrefix == "│ " || currentPrefix == "♯ " || currentPrefix == "⌗ ") {
                    val textCleared = oldText.removeRange(lineStart, lineStart + 2)
                    return TextFieldValue(text = textCleared, selection = TextRange(lineStart))
                }
            }
        }

        return newValue
    }
}
