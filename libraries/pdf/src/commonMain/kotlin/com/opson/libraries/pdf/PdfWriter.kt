package com.opson.libraries.pdf

/**
 * Lays [PdfDocument]s out on pages and returns the PDF's bytes. Android draws with
 * `android.graphics.pdf.PdfDocument` and iOS with `UIGraphicsPDFRenderer`, both in the system
 * font, so text in any script a food name or note is written in comes out as it was typed.
 *
 * Runs on the caller's thread and takes a moment for a long document: call it off main.
 */
fun interface PdfWriter {
    fun write(document: PdfDocument): ByteArray
}

/**
 * A document of text blocks, top to bottom. [footer] goes at the foot of every page, followed by
 * the page number. [title] is the file's own title where the platform records one.
 */
data class PdfDocument(
    val title: String,
    val blocks: List<PdfBlock>,
    val footer: String,
)

/**
 * One paragraph, wrapped to the page. A block is never split across pages; a paragraph taller
 * than a page's text area would be cut, which nothing in the app writes.
 */
data class PdfBlock(
    val text: String,
    val style: PdfStyle = PdfStyle.Body,
    /** Under the block before it, as a meal's foods sit under the meal. */
    val indented: Boolean = false,
)

/**
 * The type scale. Sizes and gaps are in points (1/72 inch). [spaceBefore] is skipped for the
 * first block on a page, so a page never starts with a gap.
 */
enum class PdfStyle(val size: Float, val bold: Boolean, val muted: Boolean, val spaceBefore: Float) {
    Title(size = 20f, bold = true, muted = false, spaceBefore = 0f),
    Subtitle(size = 11f, bold = false, muted = true, spaceBefore = 4f),
    Heading(size = 13f, bold = true, muted = false, spaceBefore = 16f),
    Label(size = 10.5f, bold = true, muted = false, spaceBefore = 6f),
    Body(size = 10.5f, bold = false, muted = false, spaceBefore = 3f),
    Small(size = 9f, bold = false, muted = true, spaceBefore = 3f),
}

/** US Letter with a three-quarter inch margin: what a provider's printer most likely holds. */
object PdfPage {
    const val WIDTH: Float = 612f
    const val HEIGHT: Float = 792f
    const val MARGIN: Float = 54f
    const val INDENT: Float = 14f

    /** Room kept at the foot of each page for the footer. */
    const val FOOTER: Float = 28f

    const val LINE_SPACING: Float = 1.25f

    /** Where text may go: between the margins, and above the footer. */
    const val TEXT_WIDTH: Float = WIDTH - 2 * MARGIN
    const val TEXT_BOTTOM: Float = HEIGHT - MARGIN - FOOTER
}

/**
 * Which page each block lands on, given the measured height of the block at each index, at the
 * text width less any indent. Shared by both platforms so pagination is the same everywhere.
 * Returns, per page, the blocks' indices and the y each one starts at.
 */
fun paginate(blocks: List<PdfBlock>, heightOf: (index: Int) -> Float): List<List<Placed>> {
    val pages = mutableListOf<MutableList<Placed>>()
    var page = mutableListOf<Placed>()
    var y = PdfPage.MARGIN
    blocks.forEachIndexed { index, block ->
        val height = heightOf(index)
        val gap = if (page.isEmpty()) 0f else block.style.spaceBefore
        if (page.isNotEmpty() && y + gap + height > PdfPage.TEXT_BOTTOM) {
            pages += page
            page = mutableListOf()
            y = PdfPage.MARGIN
        }
        val top = if (page.isEmpty()) y else y + gap
        page += Placed(index, top)
        y = top + height
    }
    if (page.isNotEmpty() || pages.isEmpty()) pages += page
    return pages
}

/** Block [index] of the document, drawn with its top at [top] points from the page's top. */
data class Placed(val index: Int, val top: Float)
