package io.github.portalappinspector.android

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class PortalCopyUrlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra(ExtraUrl) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Portal App Inspector URL", url))
        Toast.makeText(context, "Portal URL copied", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ActionCopyUrl = "io.github.portalappinspector.COPY_URL"
        const val ExtraUrl = "portal_url"
    }
}
