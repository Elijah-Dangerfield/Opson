package com.opson.features.settings.impl.summary

import androidx.compose.runtime.Composable
import com.opson.libraries.pdf.PdfBlock
import com.opson.libraries.pdf.PdfDocument
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.summary_doc_footer
import opson.libraries.resources.generated.resources.summary_doc_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** Resolves copy outside composition, for the PDF. The real one is `getString`; tests pass the key. */
fun interface SummaryStrings {
    suspend fun get(resource: StringResource, args: List<String>): String
}

@Inject
@ContributesBinding(AppScope::class)
class ComposeSummaryStrings : SummaryStrings {
    override suspend fun get(resource: StringResource, args: List<String>): String = getString(resource, *args.toTypedArray())
}

suspend fun SummaryText.resolve(strings: SummaryStrings): String = when (this) {
    is SummaryText.Plain -> text
    is SummaryText.Res -> strings.get(resource, args.map { it.resolve(strings) })
    is SummaryText.Joined -> parts.map { it.resolve(strings) }.joinToString(separator)
}

@Composable
fun SummaryText.resolve(): String = when (this) {
    is SummaryText.Plain -> text
    is SummaryText.Res -> stringResource(resource, *args.map { it.resolve() }.toTypedArray())
    is SummaryText.Joined -> parts.map { it.resolve() }.joinToString(separator)
}

/** The lines as the PDF writer takes them: each numbers line on one line, its parts separated. */
suspend fun List<SummaryLine>.toPdf(strings: SummaryStrings): PdfDocument = PdfDocument(
    title = strings.get(Res.string.summary_doc_title, emptyList()),
    blocks = map { line ->
        PdfBlock(
            text = line.parts.map { it.text.resolve(strings) }.joinToString(SummaryLines.NUMBERS_SEPARATOR),
            style = line.style,
            indented = line.indented,
        )
    },
    footer = strings.get(Res.string.summary_doc_footer, emptyList()),
)
