package io.nikdmitryuk.ultraclient.presentation.platform

import platform.UIKit.UIPasteboard

class IosClipboardReader : ClipboardReader {
    override fun readText(): String? = UIPasteboard.generalPasteboard.string
}
