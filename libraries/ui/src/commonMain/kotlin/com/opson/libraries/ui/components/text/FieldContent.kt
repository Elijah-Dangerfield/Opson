package com.opson.libraries.ui.components.text

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * What a field holds, so the phone can offer to fill it: a saved email and password, a new strong
 * password, a code from a text, or the birthday on the person's own contact card.
 *
 * Android's autofill reads it from the semantics tree ([fieldContent]); iOS reads the text input's
 * `textContentType`, which Compose only sets from [KeyboardOptions.platformImeOptions]
 * ([withContent]). Compose's own fallback on iOS knows only email, password and phone, from the
 * keyboard type, so a birthday or a new password needs both.
 */
enum class FieldContent {
    Email,
    Username,
    Password,
    NewPassword,
    OneTimeCode,
    Name,
    BirthDate,
    BirthDay,
    BirthMonth,
    BirthYear,
}

/** [content] as Android's autofill hint, on the field's semantics. Nothing for null. */
fun Modifier.fieldContent(content: FieldContent?): Modifier =
    if (content == null) this else semantics { contentType = content.autofillType }

/** [this] with iOS's text content type for [content]. Unchanged for null, and on Android. */
fun KeyboardOptions.withContent(content: FieldContent?): KeyboardOptions {
    val options = content?.let(::platformImeOptionsFor) ?: return this
    return copy(platformImeOptions = options)
}

/** iOS's `textContentType` for [content]; null on Android, where the semantics carry it. */
internal expect fun platformImeOptionsFor(content: FieldContent): PlatformImeOptions?

private val FieldContent.autofillType: ContentType
    get() = when (this) {
        FieldContent.Email -> ContentType.EmailAddress
        FieldContent.Username -> ContentType.Username
        FieldContent.Password -> ContentType.Password
        FieldContent.NewPassword -> ContentType.NewPassword
        FieldContent.OneTimeCode -> ContentType.SmsOtpCode
        FieldContent.Name -> ContentType.PersonFullName
        FieldContent.BirthDate -> ContentType.BirthDateFull
        FieldContent.BirthDay -> ContentType.BirthDateDay
        FieldContent.BirthMonth -> ContentType.BirthDateMonth
        FieldContent.BirthYear -> ContentType.BirthDateYear
    }
