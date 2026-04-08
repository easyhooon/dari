package com.easyhooon.dari.ui.theme

import androidx.compose.ui.graphics.Color
import com.easyhooon.dari.MessageStatus

/**
 * Single source of truth for the colors used to communicate a message's
 * status (in-progress / success / error). Used by both the list-item status
 * label and the status filter chips so the two stay visually consistent.
 */
internal fun MessageStatus.color(): Color = when (this) {
    MessageStatus.IN_PROGRESS -> Color(0xFFFFC107) // amber
    MessageStatus.SUCCESS -> Color(0xFF4CAF50) // green
    MessageStatus.ERROR -> Color(0xFFF44336) // red
}
