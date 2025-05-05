package com.mycookbuddy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF00ACC1),
    secondary = Color(0xFF26C6DA),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00ACC1),
    secondary = Color(0xFF26C6DA),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun MyApplicationTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) DarkColors else LightColors

    val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = com.mycookbuddy.app.R.array.com_google_android_gms_fonts_certs
    )

    val googleFont = GoogleFont("Poppins")
    val fontFamily = FontFamily(Font(googleFont, provider))

    val typography = Typography(
        displayLarge = TextStyle(fontFamily = fontFamily, fontSize = 30.sp),
        titleMedium = TextStyle(fontFamily = fontFamily, fontSize = 20.sp),
        bodyLarge = TextStyle(fontFamily = fontFamily, fontSize = 16.sp),
        labelLarge = TextStyle(fontFamily = fontFamily, fontSize = 14.sp)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
