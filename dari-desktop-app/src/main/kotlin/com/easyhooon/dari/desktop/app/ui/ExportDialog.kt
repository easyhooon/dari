package com.easyhooon.dari.desktop.app.ui

import com.easyhooon.dari.desktop.app.ExportFormat
import com.easyhooon.dari.desktop.app.MessageExport
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native save dialog with the in-app file name; null when cancelled. */
fun chooseExportFile(owner: Frame?, format: ExportFormat): File? {
    val dialog = FileDialog(owner, "Export Messages", FileDialog.SAVE).apply {
        file = MessageExport.suggestedFileName(format)
    }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val extension = ".${format.extension}"
    return File(dialog.directory, if (name.endsWith(extension)) name else name + extension)
}
