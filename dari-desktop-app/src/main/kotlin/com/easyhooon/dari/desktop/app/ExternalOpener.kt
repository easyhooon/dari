package com.easyhooon.dari.desktop.app

import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI

/** Opens links and folders in the system's apps. AWT can't on Linux desktops other than GNOME, so it falls back to `xdg-open`. */
object ExternalOpener {
    fun browse(uri: URI) {
        if (supports(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(uri) else xdgOpen(uri.toString())
    }

    fun open(directory: File) {
        if (supports(Desktop.Action.OPEN)) Desktop.getDesktop().open(directory) else xdgOpen(directory.absolutePath)
    }

    private fun supports(action: Desktop.Action) = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action)

    private fun xdgOpen(target: String) {
        if (!System.getProperty("os.name").startsWith("Linux")) return
        try {
            ProcessBuilder("xdg-open", target).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        } catch (e: IOException) {
            AppLog.error("xdg-open failed", e)
        }
    }
}
