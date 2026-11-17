package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.ThemePreference
import com.example.ui.components.ProvideSafeTextToolbar

@Immutable
data class VaultExtendedColors(
    val isDark: Boolean,
    val glassSurface: Color,
    val glassBarSurface: Color,
    val glassSheenStart: Color,
    val glassSheenEnd: Color,
    val glassBorder: Color,
    val glassHighlightBorder: Color,
    val glassShadowBorder: Color,
    val purchaseAccent: Color,
    val purchaseContainer: Color,
    val medicineAccent: Color,
    val medicineContainer: Color,
    val goldAccent: Color,
    val goldContainer: Color,
    val statusValid: Color,
    val statusWarning: Color,
    val statusExpired: Color,
    val brandButtonGradient: List<Color>,
    val purchaseButtonGradient: List<Color>,
    val medicineButtonGradient: List<Color>,
    val goldButtonGradient: List<Color>,
    val dangerButtonGradient: List<Color>
)

val LocalVaultColors = staticCompositionLocalOf {
    VaultExtendedColors(
        isDark = true,
        glassSurface = ObsidianCardGlass,
        glassBarSurface = ObsidianSurface,
        glassSheenStart = ObsidianGlassSheenStart,
        glassSheenEnd = ObsidianGlassSheenEnd,
        glassBorder = ObsidianBorder,
        glassHighlightBorder = ObsidianHighlightBorder,
        glassShadowBorder = ObsidianShadowBorder,
        purchaseAccent = PurchasePurpleBright,
        purchaseContainer = PurchasePurpleDark,
        medicineAccent = MedicineGreenBright,
        medicineContainer = MedicineGreenDark,
        goldAccent = VaultGold,
        goldContainer = VaultGoldSoft,
        statusValid = StatusValid,
        statusWarning = StatusWarning,
        statusExpired = StatusExpired,
        brandButtonGradient = listOf(ButtonBrandStart, ButtonBrandMid, ButtonBrandEnd),
        purchaseButtonGradient = listOf(ButtonPurchaseStart, ButtonPurchaseMid, ButtonPurchaseEnd),
        medicineButtonGradient = listOf(ButtonMedicineStart, ButtonMedicineMid, ButtonMedicineEnd),
        goldButtonGradient = listOf(ButtonGoldStart, ButtonGoldMid, ButtonGoldEnd),
        dangerButtonGradient = listOf(ButtonDangerStart, ButtonDangerMid, ButtonDangerEnd)
    )
}

private val VaultDarkColorScheme = darkColorScheme(
    primary = VaultIndigo,
    onPrimary = Color.White,
    primaryContainer = PurchasePurpleDark,
    onPrimaryContainer = PurchasePurpleBright,
    secondary = MedicineGreen,
    onSecondary = Color.White,
    secondaryContainer = MedicineGreenDark,
    onSecondaryContainer = MedicineGreenBright,
    tertiary = VaultGold,
    onTertiary = ObsidianDeep,
    background = ObsidianDeep,
    onBackground = TextObsidianPrimary,
    surface = ObsidianSurface,
    onSurface = TextObsidianPrimary,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TextObsidianSecondary,
    outline = ObsidianBorder,
    error = StatusExpired,
    onError = Color.White
)

private val VaultLightColorScheme = lightColorScheme(
    primary = PurchasePurple,
    onPrimary = Color.White,
    primaryContainer = PurchasePurpleLightBg,
    onPrimaryContainer = Color(0xFF3B137D),
    secondary = MedicineGreen,
    onSecondary = Color.White,
    secondaryContainer = MedicineGreenLightBg,
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = Color(0xFFD49B1A),
    onTertiary = ObsidianDeep,
    background = AlabasterMist,
    onBackground = TextAlabasterPrimary,
    surface = AlabasterSurface,
    onSurface = TextAlabasterPrimary,
    surfaceVariant = AlabasterElevated,
    onSurfaceVariant = TextAlabasterSecondary,
    outline = AlabasterBorder,
    error = Color(0xFFDC2626),
    onError = Color.White
)

val VaultoraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun VaultoraTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) VaultDarkColorScheme else VaultLightColorScheme
    val extendedColors = if (useDark) {
        VaultExtendedColors(
            isDark = true,
            glassSurface = ObsidianCardGlass,
            glassBarSurface = ObsidianSurface,
            glassSheenStart = ObsidianGlassSheenStart,
            glassSheenEnd = ObsidianGlassSheenEnd,
            glassBorder = ObsidianBorder,
            glassHighlightBorder = ObsidianHighlightBorder,
            glassShadowBorder = ObsidianShadowBorder,
            purchaseAccent = PurchasePurpleBright,
            purchaseContainer = PurchasePurpleDark,
            medicineAccent = MedicineGreenBright,
            medicineContainer = MedicineGreenDark,
            goldAccent = VaultGold,
            goldContainer = VaultGoldSoft,
            statusValid = StatusValid,
            statusWarning = StatusWarning,
            statusExpired = StatusExpired,
            brandButtonGradient = listOf(ButtonBrandStart, ButtonBrandMid, ButtonBrandEnd),
            purchaseButtonGradient = listOf(ButtonPurchaseStart, ButtonPurchaseMid, ButtonPurchaseEnd),
            medicineButtonGradient = listOf(ButtonMedicineStart, ButtonMedicineMid, ButtonMedicineEnd),
            goldButtonGradient = listOf(ButtonGoldStart, ButtonGoldMid, ButtonGoldEnd),
            dangerButtonGradient = listOf(ButtonDangerStart, ButtonDangerMid, ButtonDangerEnd)
        )
    } else {
        VaultExtendedColors(
            isDark = false,
            glassSurface = AlabasterCardGlass,
            glassBarSurface = AlabasterSurface,
            glassSheenStart = AlabasterGlassSheenStart,
            glassSheenEnd = AlabasterGlassSheenEnd,
            glassBorder = AlabasterBorder,
            glassHighlightBorder = AlabasterHighlightBorder,
            glassShadowBorder = AlabasterShadowBorder,
            purchaseAccent = PurchasePurple,
            purchaseContainer = PurchasePurpleLightBg,
            medicineAccent = Color(0xFF059669),
            medicineContainer = MedicineGreenLightBg,
            goldAccent = Color(0xFFB38212),
            goldContainer = Color(0x2EF3CE74),
            statusValid = Color(0xFF059669),
            statusWarning = Color(0xFFD97706),
            statusExpired = Color(0xFFDC2626),
            brandButtonGradient = listOf(ButtonBrandStart, ButtonBrandMid, ButtonBrandEnd),
            purchaseButtonGradient = listOf(ButtonPurchaseStart, ButtonPurchaseMid, ButtonPurchaseEnd),
            medicineButtonGradient = listOf(ButtonMedicineStart, ButtonMedicineMid, ButtonMedicineEnd),
            goldButtonGradient = listOf(ButtonGoldStart, ButtonGoldMid, ButtonGoldEnd),
            dangerButtonGradient = listOf(ButtonDangerStart, ButtonDangerMid, ButtonDangerEnd)
        )
    }

    CompositionLocalProvider(LocalVaultColors provides extendedColors) {
        ProvideSafeTextToolbar {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = VaultoraTypography,
                shapes = VaultoraShapes,
                content = content
            )
        }
    }
}
