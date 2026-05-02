package io.nikdmitryuk.ultraclient.presentation.platform

import android.content.ClipboardManager
import android.content.Context

class AndroidClipboardReader(
    private val context: Context,
) : ClipboardReader {
    override fun readText(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip
            ?.getItemAt(0)
            ?.text
            ?.toString()
    }
}
