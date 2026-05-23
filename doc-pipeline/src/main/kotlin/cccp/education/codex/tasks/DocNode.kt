package codex.tasks

import kotlinx.serialization.Serializable

/**
 * JSON-serializable node for the Logical Document Description format.
 *
 * Represents a hierarchical document element with optional text content
 * (for paragraphs) and nested children (for sections).
 *
 * @property title section title (or paragraph text)
 * @property level section depth in the hierarchy (0 = root, -1 = paragraph)
 * @property type "paragraph" for leaf text nodes, null for section nodes
 * @property text alternative text content field
 * @property children nested child nodes
 */
@Serializable
data class LddNode(
    val title: String = "",
    val level: Int = 0,
    val type: String? = null,
    val text: String? = null,
    val children: List<LddNode> = emptyList()
)

/**
 * Mutable document node used during tree construction.
 *
 * Used as an intermediate representation during parsing before conversion
 * to the serializable [LddNode] format.
 *
 * @property title section title
 * @property level section depth (-1 for paragraphs)
 * @property children mutable list of nested child nodes
 */
data class DocNode(
    val title: String,
    val level: Int,
    val children: MutableList<DocNode> = mutableListOf()
) {
    /** True if this node represents a paragraph (level == -1). */
    val isParagraph: Boolean get() = level == -1
}

/** Converts this [LddNode] (serializable) to a mutable [DocNode]. */
fun LddNode.toDocNode(): DocNode {
    return if (type == "paragraph" || text != null) {
        DocNode(title = text ?: "", level = -1)
    } else {
        val docChildren = children.map { it.toDocNode() }.toMutableList()
        DocNode(title = title, level = level, children = docChildren)
    }
}

/** Converts this [DocNode] (mutable) to a serializable [LddNode]. */
fun DocNode.toLddNode(): LddNode {
    if (isParagraph) {
        return LddNode(type = "paragraph", text = title)
    }
    return LddNode(
        title = title,
        level = level,
        children = if (children.isEmpty()) emptyList()
        else children.map { it.toLddNode() }
    )
}
