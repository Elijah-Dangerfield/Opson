package com.opson.libraries.pdf.impl

import com.opson.libraries.pdf.PdfBlock
import com.opson.libraries.pdf.PdfDocument
import com.opson.libraries.pdf.PdfPage
import com.opson.libraries.pdf.PdfStyle
import com.opson.libraries.pdf.PdfWriter
import com.opson.libraries.pdf.paginate
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import me.tatarka.inject.annotations.Inject
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSAttributedString
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSMutableParagraphStyle
import platform.UIKit.NSParagraphStyleAttributeName
import platform.UIKit.NSStringDrawingUsesLineFragmentOrigin
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.boundingRectWithSize
import platform.UIKit.drawWithRect
import platform.posix.memcpy
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.math.ceil

/**
 * `UIGraphicsPDFRenderer`, with each block an attributed string in the system font, measured and
 * drawn by UIKit so it wraps and falls back to other fonts like any other iOS text.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@ContributesBinding(AppScope::class)
class IosPdfWriter @Inject constructor() : PdfWriter {

    override fun write(document: PdfDocument): ByteArray {
        val texts = document.blocks.map(::attributed)
        val heights = document.blocks.mapIndexed { index, block -> height(texts[index], width(block)) }
        val pages = paginate(document.blocks) { index -> heights[index] }
        val format = UIGraphicsPDFRendererFormat().apply {
            documentInfo = mapOf<Any?, Any?>(TITLE_KEY to document.title)
        }
        val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, PdfPage.WIDTH.toDouble(), PdfPage.HEIGHT.toDouble()), format = format)
        val data = renderer.PDFDataWithActions { context ->
            pages.forEachIndexed { number, placed ->
                context?.beginPage()
                placed.forEach { (index, top) ->
                    val block = document.blocks[index]
                    val left = PdfPage.MARGIN + if (block.indented) PdfPage.INDENT else 0f
                    texts[index].drawWithRect(
                        CGRectMake(left.toDouble(), top.toDouble(), width(block).toDouble(), heights[index].toDouble()),
                        options = NSStringDrawingUsesLineFragmentOrigin,
                        context = null,
                    )
                }
                val footerBlock = PdfBlock("${document.footer}  ${number + 1}", PdfStyle.Small)
                val footer = attributed(footerBlock)
                val footerHeight = height(footer, PdfPage.TEXT_WIDTH)
                footer.drawWithRect(
                    CGRectMake(
                        PdfPage.MARGIN.toDouble(),
                        (PdfPage.HEIGHT - PdfPage.MARGIN - footerHeight).toDouble(),
                        PdfPage.TEXT_WIDTH.toDouble(),
                        footerHeight.toDouble(),
                    ),
                    options = NSStringDrawingUsesLineFragmentOrigin,
                    context = null,
                )
            }
        }
        return data.toByteArray()
    }

    private fun width(block: PdfBlock): Float = PdfPage.TEXT_WIDTH - if (block.indented) PdfPage.INDENT else 0f

    private fun attributed(block: PdfBlock): NSAttributedString {
        val size = block.style.size.toDouble()
        val font = if (block.style.bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)
        val paragraph = NSMutableParagraphStyle().apply { setLineHeightMultiple(PdfPage.LINE_SPACING.toDouble()) }
        val color = if (block.style.muted) MUTED else INK
        return NSAttributedString.create(
            string = block.text,
            attributes = mapOf<Any?, Any?>(
                NSFontAttributeName to font,
                NSForegroundColorAttributeName to color,
                NSParagraphStyleAttributeName to paragraph,
            ),
        )
    }

    private fun height(text: NSAttributedString, width: Float): Float =
        text.boundingRectWithSize(CGSizeMake(width.toDouble(), Double.MAX_VALUE), options = NSStringDrawingUsesLineFragmentOrigin, context = null)
            .useContents { ceil(size.height).toFloat() }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }
    }

    private companion object {
        /** `kCGPDFContextTitle`'s value; the constant itself is a CFString, awkward to bridge here. */
        const val TITLE_KEY = "Title"

        val INK: UIColor = UIColor(red = 0x1F / 255.0, green = 0x23 / 255.0, blue = 0x28 / 255.0, alpha = 1.0)
        val MUTED: UIColor = UIColor(red = 0x5F / 255.0, green = 0x66 / 255.0, blue = 0x70 / 255.0, alpha = 1.0)
    }
}
