package io.nikdmitryuk.ultraclient.presentation.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

class DesktopClipboardReader : ClipboardReader {
    override fun readText(): String? =
        runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull()
}
