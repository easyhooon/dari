package com.easyhooon.dari.desktop.app

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Color
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI
import javax.swing.plaf.basic.BasicMenuBarUI
import javax.swing.plaf.basic.BasicMenuItemUI
import javax.swing.plaf.basic.BasicMenuUI
import javax.swing.plaf.basic.BasicPopupMenuSeparatorUI
import javax.swing.plaf.basic.BasicRadioButtonMenuItemUI

val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows")

/**
 * On Windows the title bar is drawn by the OS and the menu bar by Swing's system look, so neither follows
 * Dari's appearance. This darkens the title bar through DWM and paints the menus in Dari's colors.
 */
object WindowsChrome {
    data class Palette(val background: Color, val selected: Color, val border: Color, val text: Color, val textTertiary: Color)

    fun apply(window: Window, dark: Boolean, palette: Palette) {
        if (!isWindows) return
        // Logged, not thrown: a missing DWM or JNA only costs the look.
        runCatching { setDarkTitleBar(window, dark) }.onFailure { AppLog.error("Couldn't set the title bar appearance", it) }
        (window as? JFrame)?.jMenuBar?.let { style(it, palette) }
    }

    // JNA binds each method to the native function of the same name.
    @Suppress("FunctionNaming")
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    @Suppress("FunctionNaming")
    private interface User32 : Library {
        fun SetWindowPos(hwnd: Pointer, after: Pointer?, x: Int, y: Int, width: Int, height: Int, flags: Int): Boolean
    }

    private val dwm: Dwm by lazy { Native.load("dwmapi", Dwm::class.java) }
    private val user32: User32 by lazy { Native.load("user32", User32::class.java) }

    private fun setDarkTitleBar(window: Window, dark: Boolean) {
        if (!window.isDisplayable) {
            window.addWindowListener(object : WindowAdapter() {
                override fun windowOpened(e: WindowEvent) {
                    window.removeWindowListener(this)
                    setDarkTitleBar(window, dark)
                }
            })
            return
        }
        val hwnd = Native.getComponentPointer(window) ?: return
        val value = IntByReference(if (dark) 1 else 0)
        // DWMWA_USE_IMMERSIVE_DARK_MODE is 20 from Windows 10 20H1, 19 before it.
        if (dwm.DwmSetWindowAttribute(hwnd, 20, value, 4) != 0) dwm.DwmSetWindowAttribute(hwnd, 19, value, 4)
        // Windows 10 repaints the caption only when told the frame changed.
        user32.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED)
    }

    private fun style(bar: JMenuBar, palette: Palette) {
        bar.setUI(BasicMenuBarUI())
        bar.isOpaque = true
        bar.background = palette.background
        bar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, palette.border)
        for (index in 0 until bar.menuCount) bar.getMenu(index)?.let { style(it, palette, topLevel = true) }
    }

    private fun style(item: JMenuItem, palette: Palette, topLevel: Boolean = false) {
        item.setUI(
            when (item) {
                is JMenu -> MenuUI(palette)
                is JCheckBoxMenuItem -> CheckBoxItemUI(palette)
                is JRadioButtonMenuItem -> RadioItemUI(palette)
                else -> ItemUI(palette)
            },
        )
        item.isOpaque = true
        item.background = palette.background
        item.foreground = palette.text
        item.border = if (topLevel) BorderFactory.createEmptyBorder(3, 8, 3, 8) else BorderFactory.createEmptyBorder(3, 4, 3, 12)
        if (item is JMenu) stylePopup(item, palette)
    }

    private fun stylePopup(menu: JMenu, palette: Palette) {
        val popup = menu.popupMenu
        menu.putClientProperty(PALETTE, palette)
        popup.background = palette.background
        popup.border = BorderFactory.createLineBorder(palette.border)
        styleChildren(popup, palette)
        // Compose adds and renames items as state changes, so restyle each time the menu opens.
        if (popup.getClientProperty(PALETTE) == null) {
            popup.putClientProperty(PALETTE, true)
            popup.addPopupMenuListener(object : PopupMenuListener {
                override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
                    (menu.getClientProperty(PALETTE) as? Palette)?.let { styleChildren(popup, it) }
                }

                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) = Unit

                override fun popupMenuCanceled(e: PopupMenuEvent) = Unit
            })
        }
    }

    private fun styleChildren(popup: JPopupMenu, palette: Palette) {
        popup.components.forEach { child ->
            when (child) {
                is JMenuItem -> style(child, palette)
                is JPopupMenu.Separator -> {
                    child.setUI(BasicPopupMenuSeparatorUI())
                    child.foreground = palette.border
                    child.background = palette.background
                }
            }
        }
    }

    // The Basic UIs read their selection and disabled colors into protected fields at install time.
    private class MenuUI(private val palette: Palette) : BasicMenuUI() {
        override fun installDefaults() {
            super.installDefaults()
            selectionBackground = palette.selected
            selectionForeground = palette.text
            disabledForeground = palette.textTertiary
        }
    }

    private class ItemUI(private val palette: Palette) : BasicMenuItemUI() {
        override fun installDefaults() {
            super.installDefaults()
            selectionBackground = palette.selected
            selectionForeground = palette.text
            disabledForeground = palette.textTertiary
            acceleratorForeground = palette.textTertiary
            acceleratorSelectionForeground = palette.text
        }
    }

    private class CheckBoxItemUI(private val palette: Palette) : BasicCheckBoxMenuItemUI() {
        override fun installDefaults() {
            super.installDefaults()
            selectionBackground = palette.selected
            selectionForeground = palette.text
            disabledForeground = palette.textTertiary
            acceleratorForeground = palette.textTertiary
            acceleratorSelectionForeground = palette.text
        }
    }

    private class RadioItemUI(private val palette: Palette) : BasicRadioButtonMenuItemUI() {
        override fun installDefaults() {
            super.installDefaults()
            selectionBackground = palette.selected
            selectionForeground = palette.text
            disabledForeground = palette.textTertiary
            acceleratorForeground = palette.textTertiary
            acceleratorSelectionForeground = palette.text
        }
    }

    private const val PALETTE = "dari.menuPalette"
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
}
