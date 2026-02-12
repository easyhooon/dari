package com.easyhooon.dari.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Chucker-style blue TopBar color */
val DariBlue = Color(0xFF2D6AB1)

object DariTopBarColors {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun colors() = TopAppBarColors(
        containerColor = DariBlue,
        scrolledContainerColor = DariBlue,
        navigationIconContentColor = Color.White,
        titleContentColor = Color.White,
        actionIconContentColor = Color.White,
    )
}