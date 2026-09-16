package pt.aguiarvieira.psacc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Root theme. [MaterialExpressiveTheme] gives every screen expressive motion, shapes and the
 * *Emphasized type styles. Dynamic color (Material You) on API 31+, otherwise a teal-green fallback
 * that matches the launcher icon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PsaccTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFF84D6C0),
            onPrimary = Color(0xFF00382D),
            primaryContainer = Color(0xFF005142),
            onPrimaryContainer = Color(0xFFA0F2DC),
            secondary = Color(0xFFB1CCC3),
            secondaryContainer = Color(0xFF334B44),
            onSecondaryContainer = Color(0xFFCDE8DF),
            tertiary = Color(0xFFA9CBE3),
            tertiaryContainer = Color(0xFF284A5F),
            onTertiaryContainer = Color(0xFFC6E7FF),
        )
        else -> expressiveLightColorScheme().copy(
            primary = Color(0xFF006B58),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFA0F2DC),
            onPrimaryContainer = Color(0xFF002019),
            secondary = Color(0xFF4A635B),
            secondaryContainer = Color(0xFFCDE8DF),
            onSecondaryContainer = Color(0xFF062019),
            tertiary = Color(0xFF416277),
            tertiaryContainer = Color(0xFFC6E7FF),
            onTertiaryContainer = Color(0xFF001E2D),
        )
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography(),
        content = content,
    )
}
