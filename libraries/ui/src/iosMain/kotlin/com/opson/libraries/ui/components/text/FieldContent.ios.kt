package com.opson.libraries.ui.components.text

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions
import platform.UIKit.UITextContentTypeBirthdate
import platform.UIKit.UITextContentTypeBirthdateDay
import platform.UIKit.UITextContentTypeBirthdateMonth
import platform.UIKit.UITextContentTypeBirthdateYear
import platform.UIKit.UITextContentTypeEmailAddress
import platform.UIKit.UITextContentTypeName
import platform.UIKit.UITextContentTypeNewPassword
import platform.UIKit.UITextContentTypeOneTimeCode
import platform.UIKit.UITextContentTypePassword
import platform.UIKit.UITextContentTypeUsername

// The birthday types are iOS 17's; the app's deployment target is newer, so they always exist.
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun platformImeOptionsFor(content: FieldContent): PlatformImeOptions? {
    val type = when (content) {
        FieldContent.Email -> UITextContentTypeEmailAddress
        FieldContent.Username -> UITextContentTypeUsername
        FieldContent.Password -> UITextContentTypePassword
        FieldContent.NewPassword -> UITextContentTypeNewPassword
        FieldContent.OneTimeCode -> UITextContentTypeOneTimeCode
        FieldContent.Name -> UITextContentTypeName
        FieldContent.BirthDate -> UITextContentTypeBirthdate
        FieldContent.BirthDay -> UITextContentTypeBirthdateDay
        FieldContent.BirthMonth -> UITextContentTypeBirthdateMonth
        FieldContent.BirthYear -> UITextContentTypeBirthdateYear
    } ?: return null
    return PlatformImeOptions { textContentType(type) }
}
