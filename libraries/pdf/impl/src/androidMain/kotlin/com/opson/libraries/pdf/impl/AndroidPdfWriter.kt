package com.opson.libraries.pdf.impl

import android.graphics.Typeface
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.opson.libraries.pdf.PdfBlock
import com.opson.libraries.pdf.PdfDocument
import com.opson.libraries.pdf.PdfPage
import com.opson.libraries.pdf.PdfStyle
import com.opson.libraries.pdf.PdfWriter
import com.opson.libraries.pdf.Placed
import com.opson.libraries.pdf.paginate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import java.io.ByteArrayOutputStream

/**
 * `android.graphics.pdf.PdfDocument`, one point per canvas unit, with each block laid out by a
 * `StaticLayout` so it wraps and shapes like any other Android text.
 */
@ContributesBinding(AppScope::class)
class AndroidPdfWriter @Inject constructor() : PdfWriter {

    override fun write(document: PdfDocument): ByteArray {
        val layouts = document.blocks.map(::layout)
        val pages = pages(document.blocks, layouts)
        val pdf = AndroidPdfDocument()
        try {
            pages.forEachIndexed { number, placed ->
                val info = AndroidPdfDocument.PageInfo.Builder(PdfPage.WIDTH.toInt(), PdfPage.HEIGHT.toInt(), number + 1).create()
                val page = pdf.startPage(info)
                val canvas = page.canvas
                placed.forEach { (index, top) ->
                    val left = PdfPage.MARGIN + if (document.blocks[index].indented) PdfPage.INDENT else 0f
                    canvas.save()
                    canvas.translate(left, top)
                    layouts[index].draw(canvas)
                    canvas.restore()
                }
                val footer = layout(PdfBlock("${document.footer}  ${number + 1}", PdfStyle.Small))
                canvas.save()
                canvas.translate(PdfPage.MARGIN, PdfPage.HEIGHT - PdfPage.MARGIN - footer.height)
                footer.draw(canvas)
                canvas.restore()
                pdf.finishPage(page)
            }
            return ByteArrayOutputStream().also(pdf::writeTo).toByteArray()
        } finally {
            pdf.close()
        }
    }

    /** Where each block lands, as measured by Android's own text layout. Its own step so a test can reach it. */
    internal fun pages(document: PdfDocument): List<List<Placed>> = pages(document.blocks, document.blocks.map(::layout))

    private fun pages(blocks: List<PdfBlock>, layouts: List<StaticLayout>): List<List<Placed>> =
        paginate(blocks) { index -> layouts[index].height.toFloat() }

    private fun layout(block: PdfBlock): StaticLayout {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = block.style.size
            typeface = if (block.style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = if (block.style.muted) MUTED else INK
        }
        val width = (PdfPage.TEXT_WIDTH - if (block.indented) PdfPage.INDENT else 0f).toInt()
        return StaticLayout.Builder.obtain(block.text, 0, block.text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, PdfPage.LINE_SPACING)
            .setIncludePad(false)
            .build()
    }

    private companion object {
        const val INK = 0xFF1F2328.toInt()
        const val MUTED = 0xFF5F6670.toInt()
    }
}
