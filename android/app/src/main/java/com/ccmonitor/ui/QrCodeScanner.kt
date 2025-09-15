package com.ccmonitor.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

object QrCodeScanner {
    val QrCodeScanner: ImageVector = Icons.Default.QrCode
}

@Composable
fun QrCodeScannerIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Icon(
        imageVector = Icons.Default.QrCode,
        contentDescription = contentDescription,
        modifier = modifier
    )
}