package com.lagradost.quicknovel.util.translation

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object TranslationsUtils {
    const val TAG_DELIMITER = "\n\n\n\nKJHHYQ3TVI4FPHT\n\n\n\n"
    private val leafTags = setOf("img", "hr", "br")
    private val blockTags = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "tr", "td", "th")
    private val containerTags = setOf("div", "main", "section", "article", "body", "table", "tbody", "ul", "ol", "center")
    private val structuralTags = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "center")

    fun htmlToTranslatableList(node: Node, out: MutableList<String>) {
        if (node is Element) {
            val tagName = node.tagName().lowercase()

            // Atomic Content (Leaf tags)
            if (leafTags.contains(tagName)) {
                out.add(node.outerHtml())
                return
            }

            // Unit of translation. We go inside only if there are nested BLOCKS or BRs.
            if (blockTags.contains(tagName)) {
                // Check if it has internal structure that SHOULD be separated (like <br>)
                val hasSeparators = node.childNodes().any {
                    (it is Element && it.tagName().lowercase() == "br") || (it is Element && it.isBlock)
                }

                if (hasSeparators) processChildrenGroupingInline(node, out)
                else out.add(node.outerHtml())
                return
            }

            // Container tags: Always go inside
            if (containerTags.contains(tagName)) {
                processChildrenGroupingInline(node, out)
                return
            }

            // Default for inline tags (b, i, span, etc.): treat as atomic if they reached here
            out.add(node.outerHtml())
        } else if (node is TextNode) {
            val text = node.text()
            if (text.isNotBlank()) out.add("<p>${node.outerHtml().trim()}</p>")
        }
    }

    private fun processChildrenGroupingInline(parent: Element, out: MutableList<String>) {
        val currentGroup = StringBuilder()

        fun flush() {
            if (currentGroup.isNotBlank()) {
                val groupHtml = currentGroup.toString().trim()
                if (groupHtml.isNotEmpty()) {
                    // Wrap inline groups in <p> to ensure they become a block
                    out.add("<p>$groupHtml</p>")
                }
                currentGroup.setLength(0)
            }
        }

        for (child in parent.childNodes()) {
            if (child is Element) {
                val tagName = child.tagName().lowercase()
                val isBlock = child.isBlock || tagName == "br" || tagName == "hr" || tagName == "img"

                if (isBlock) {
                    flush()
                    htmlToTranslatableList(child, out)
                } else currentGroup.append(child.outerHtml())

            } else if (child is TextNode) currentGroup.append(child.outerHtml())
        }
        flush()
    }

    fun extractDeepShell(html: String): Triple<String, String, List<String>> {
        return try {
            val doc = Jsoup.parseBodyFragment(html)
            val body = doc.body()

            if (!body.hasText()) return Triple("%s", html, emptyList())

            var deepestElement = body
            while (deepestElement.children().size == 1 && deepestElement.ownText().isBlank()) {
                val child = deepestElement.children().first()!!
                if (structuralTags.contains(child.tagName().lowercase())) deepestElement = child
                else break
            }

            val innerHtml = deepestElement.html()
            val tags = mutableListOf<String>()
            val tagRegex = Regex("<[^>]+>")

            // Protect all internal tags by replacing them with the delimiter
            val protectedContent = tagRegex.replace(innerHtml) { match ->
                tags.add(match.value)
                TAG_DELIMITER
            }

            deepestElement.empty()
            deepestElement.text("%s")
            Triple(body.html(), protectedContent, tags)
        } catch (e: Exception) {
            Triple("%s", html, emptyList())
        }
    }

    // Remove zero-width spaces, marks and other non-printable control characters
    fun sanitize(text: String) = text.replace(Regex("[\u200B-\u200F\uFEFF]"), "")

    fun isTranslatable(text: String, isHtml: Boolean): Boolean {
        if (text.isBlank()) return false
        val sanitized = sanitize(text)
        return if (isHtml) {
            val plainText = Jsoup.parse(sanitized).text()
            plainText.isNotBlank() && plainText.any { it.isLetter() }
        } else sanitized.trim().any { it.isLetter() }
    }
}
