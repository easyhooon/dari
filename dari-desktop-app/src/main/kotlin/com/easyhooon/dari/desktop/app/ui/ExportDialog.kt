package com.easyhooon.dari.desktop.app.ui

import com.easyhooon.dari.desktop.app.DesktopSettings
import com.easyhooon.dari.desktop.app.ExportFormat
import com.easyhooon.dari.desktop.app.MessageExport
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native save dialog with the in-app file name; null when cancelled. Opens where the last export went. */
fun chooseExportFile(owner: Frame?, format: ExportFormat, settings: DesktopSettings): File? {
    val dialog = FileDialog(owner, "Export Messages", FileDialog.SAVE).apply {
        directory = exportStartDirectory(settings.lastExportDirectory).path
        file = MessageExport.suggestedFileName(format)
    }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    settings.saveLastExportDirectory(dialog.directory)
    val extension = ".${format.extension}"
    return File(dialog.directory, if (name.endsWith(extension)) name else name + extension)
}

/**
 * The last export folder while it still exists, else Downloads, else home. Without one the dialog opens in the
 * app's working directory, which is /Applications for an installed macOS app.
 */
internal fun exportStartDirectory(last: String?, home: File = File(System.getProperty("user.home"))): File =
    listOfNotNull(last?.let(::File), File(home, "Downloads")).firstOrNull { it.isDirectory } ?: home
