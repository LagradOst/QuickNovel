package com.lagradost.quicknovel.compose

// Source: https://github.com/recloudstream/cloudstream/pull/2829

import android.content.Context
import android.os.Build

import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.colorResource
import androidx.preference.PreferenceManager

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.fontResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.lagradost.quicknovel.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

enum class CloudStreamThemeMode {
    /** "Black" standard dark, #111111 backgrounds */
    Dark,

    /** "Amoled" / "AmoledLight" pure black (#000000) */
    Amoled,

    /** "AmoledLight" pure black (#000000) */
    AmoledLight,

    /** "Light" white/gray backgrounds, dark text */
    Light,

    /** "Dracula" */
    Dracula,

    /** "Lavender" */
    Lavender,

    /** "SilentBlue" */
    SilentBlue,

    /** "System" resolved on each platform via [isSystemInDarkTheme] */
    FollowSystem,

    /**
     * Uses platform dynamic color system, Material You on Android 12+,
     * falls back to [Dark] on unsupported platforms.
     */
    Dynamic,
}

enum class CloudStreamPrimaryColor(val color: Color) {
    NORMAL(Color(0xFF3D50FA)),
    BLUE(Color(0xFF5664B7)),
    PURPLE(Color(0xFF6200EA)),
    GREEN(Color(0xFF00BFA5)),
    GREEN_APPLE(Color(0xFF48E484)),
    RED(Color(0xFFD50000)),
    BANANA(Color(0xFFE4D448)),
    PARTY(Color(0xFFEA596E)),
    PINK(Color(0xFFFF1493)),
    CARNATION_PINK(Color(0xFFBD5DA5)),
    MAROON(Color(0xFF451010)),
    DARK_GREEN(Color(0xFF004500)),
    NAVY_BLUE(Color(0xFF000080)),
    GREY(Color(0xFF515151)),
    WHITE(Color(0xFFFFFFFF)),
    BROWN(Color(0xFF622C00)),
    ORANGE(Color(0xFFCE8500)),
    DANDELION_YELLOW(Color(0xFFF5BB00)),
    COOL_BLUE(Color(0xFF408CAC)),
    LAVENDER(Color(0xFF6F55AF)),
    DYNAMIC(Color(0xFF3D50FA)),
    DYNAMIC_TWO(Color(0xFF3D50FA)),
}


internal object CloudStreamPalette {
    // Default dark (AppTheme / Black)
    val Primary = Color(0xFF3D50FA)
    val PrimaryDark = Color(0xFF3700B3)
    val Ongoing = Color(0xFFF53B66)

    val DarkPrimaryGrayBg = Color(0xFF2B2C30)
    val DarkBlackBg = Color(0xFF111111)
    val DarkIconGrayBg = Color(0xFF1C1C20)
    val DarkBoxItemBg = Color(0xFF161616)
    val DarkText = Color(0xFFE9EAEE)
    val DarkGrayText = Color(0xFF9BA0A4)
    val DarkIcon = Color(0xFF9BA0A6)

    // Amoled
    val AmoledBlack = Color(0xFF000000)
    val AmoledNearBlack = Color(0xFF121213)

    // Light
    val LightPrimaryGrayBg = Color(0xFFF1F1F1)
    val LightBlackBg = Color(0xFFFFFFFF)
    val LightIconGrayBg = Color(0xFFEEEEEE)
    val LightBoxItemBg = Color(0xFFEEEEEE)
    val LightText = Color(0xFF202125)
    val LightGrayText = Color(0xFF5F6267)
    val LightIcon = Color(0xFF5F6267)

    // Dracula
    val DraculaPrimaryGrayBg = Color(0xFF414450)
    val DraculaBlackBg = Color(0xFF282A36)
    val DraculaIconGrayBg = Color(0xFF44475A)
    val DraculaBoxItemBg = Color(0xFF373844)
    val DraculaText = Color(0xFFF8F8F2)
    val DraculaGrayText = Color(0xFF6272A4)
    val DraculaIcon = Color(0xFF6272A4)

    // Lavender Dreams
    val LavenderPrimaryGrayBg = Color(0xFFF7EEFC)
    val LavenderBlackBg = Color(0xFFFDF0FB)
    val LavenderIconGrayBg = Color(0xFFB794F6)
    val LavenderBoxItemBg = Color(0xFFF8F5FF)
    val LavenderText = Color(0xFF2D1B47)
    val LavenderGrayText = Color(0xFF9AB3FF)
    val LavenderIcon = Color(0xFF7C3AED)

    // Silent Blue
    val SilentBluePrimaryGrayBg = Color(0xFF282F49)
    val SilentBlueBlackBg = Color(0xFF151A30)
    val SilentBlueIconGrayBg = Color(0xFF3A446A)
    val SilentBlueBoxItemBg = Color(0xFF3A446A)
    val SilentBlueText = Color(0xFFE0E1F3)
    val SilentBlueGrayText = Color(0xFF7B83B0)
    val SilentBlueIcon = Color(0xFF7B83B0)
}

fun perfToMode(perf: String?) =
    when (perf) {
        "System" -> CloudStreamThemeMode.FollowSystem
        "Black" -> CloudStreamThemeMode.Dark
        "Light" -> CloudStreamThemeMode.Light
        "Amoled" -> CloudStreamThemeMode.Amoled
        "AmoledLight" -> CloudStreamThemeMode.AmoledLight
        "Dracula" -> CloudStreamThemeMode.Dracula
        "Lavender" -> CloudStreamThemeMode.Lavender
        "SilentBlue" -> CloudStreamThemeMode.SilentBlue
        "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            CloudStreamThemeMode.Dynamic
        } else {
            CloudStreamThemeMode.Dark
        }

        else -> CloudStreamThemeMode.Dark
    }

fun perfToColor(perf : String?) = when (perf) {
    "Normal" -> CloudStreamPrimaryColor.NORMAL
    "Blue" -> CloudStreamPrimaryColor.BLUE
    "Purple" -> CloudStreamPrimaryColor.PURPLE
    "Green" -> CloudStreamPrimaryColor.GREEN
    "GreenApple" -> CloudStreamPrimaryColor.GREEN_APPLE
    "Red" -> CloudStreamPrimaryColor.RED
    "Banana" -> CloudStreamPrimaryColor.BANANA
    "Party" -> CloudStreamPrimaryColor.PARTY
    "Pink" -> CloudStreamPrimaryColor.PINK
    "CarnationPink" -> CloudStreamPrimaryColor.CARNATION_PINK
    "Maroon" -> CloudStreamPrimaryColor.MAROON
    "DarkGreen" -> CloudStreamPrimaryColor.DARK_GREEN
    "NavyBlue" -> CloudStreamPrimaryColor.NAVY_BLUE
    "Grey" -> CloudStreamPrimaryColor.GREY
    "White" -> CloudStreamPrimaryColor.WHITE
    "Brown" -> CloudStreamPrimaryColor.BROWN
    "Orange" -> CloudStreamPrimaryColor.ORANGE
    "DandelionYellow" -> CloudStreamPrimaryColor.DANDELION_YELLOW
    "CoolBlue" -> CloudStreamPrimaryColor.COOL_BLUE
    "Lavender" -> CloudStreamPrimaryColor.LAVENDER
    "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        CloudStreamPrimaryColor.DYNAMIC
    } else {
        CloudStreamPrimaryColor.NORMAL
    }

    "Monet2" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        CloudStreamPrimaryColor.DYNAMIC_TWO
    } else {
        CloudStreamPrimaryColor.NORMAL
    }

    else -> CloudStreamPrimaryColor.NORMAL
}

fun Context.loadThemeMode(): CloudStreamThemeMode {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    return perfToMode(prefs.getString("theme_key", "AmoledLight"))
}

fun Context.loadPrimaryColor(): CloudStreamPrimaryColor {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    return perfToColor(prefs.getString("primary_color_key", "Normal"))
}

@Composable
@ReadOnlyComposable
fun resolveDynamicTheme(): CloudStreamColorScheme {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        buildMonetScheme()
    } else {
        darkScheme()
    }
}

@Composable
@ReadOnlyComposable
@RequiresApi(Build.VERSION_CODES.S)
private fun buildMonetScheme(): CloudStreamColorScheme {
    return if (isSystemInDarkTheme()) {
        CloudStreamColorScheme(
            background = colorResource(android.R.color.system_neutral1_900),
            surfaceVariant = colorResource(android.R.color.system_neutral1_800),
            surface = colorResource(android.R.color.system_neutral1_800),
            surfaceContainer = colorResource(android.R.color.system_neutral1_800),
            onBackground = colorResource(android.R.color.system_neutral1_100),
            onSurfaceVariant = colorResource(android.R.color.system_neutral2_400),
            icon = colorResource(android.R.color.system_neutral1_100),
            primary = colorResource(android.R.color.system_accent1_200),
            ongoing = CloudStreamPalette.Ongoing,
            isLight = false,
        )
    } else {
        CloudStreamColorScheme(
            background = colorResource(android.R.color.system_neutral1_10),
            surfaceVariant = colorResource(android.R.color.system_neutral1_100),
            surface = colorResource(android.R.color.system_neutral1_100),
            surfaceContainer = colorResource(android.R.color.system_neutral1_100),
            onBackground = colorResource(android.R.color.system_neutral1_900),
            onSurfaceVariant = colorResource(android.R.color.system_neutral2_600),
            icon = colorResource(android.R.color.system_neutral1_900),
            primary = colorResource(android.R.color.system_accent1_600),
            ongoing = CloudStreamPalette.Ongoing,
            isLight = true,
        )
    }
}


/**
 * Maps to the XML custom attrs declared in attrs.xml:
 * TODO: Remove this comment when we migrate fully
 *  and attrs.xml will no longer be used at all.
 *
 * | XML ?attr                | Here               |
 * |--------------------------|--------------------|
 * | primaryBlackBackground   | [background]       |
 * | primaryGrayBackground    | [surfaceVariant]   |
 * | iconGrayBackground       | [surface]          |
 * | boxItemBackground        | [surfaceContainer] |
 * | textColor                | [onBackground]     |
 * | grayTextColor            | [onSurfaceVariant] |
 * | iconColor                | [icon]             |
 * | colorPrimary             | [primary]          |
 * | colorOngoing             | [ongoing]          |
 *
 * All fields are [MutableState] so Compose recomposes automatically
 * if the scheme is swapped at runtime (e.g. user changes theme without restart).
 */
@Stable
class CloudStreamColorScheme(
    background: Color,
    surfaceVariant: Color,
    surface: Color,
    surfaceContainer: Color,
    onBackground: Color,
    onSurfaceVariant: Color,
    icon: Color,
    primary: Color,
    ongoing: Color,
    isLight: Boolean,
) {
    /** primaryBlackBackground */
    var background by mutableStateOf(background)

    /** primaryGrayBackground */
    var surfaceVariant by mutableStateOf(surfaceVariant)

    /** iconGrayBackground */
    var surface by mutableStateOf(surface)

    /** boxItemBackground */
    var surfaceContainer by mutableStateOf(surfaceContainer)

    /** textColor */
    var onBackground by mutableStateOf(onBackground)

    /** grayTextColor */
    var onSurfaceVariant by mutableStateOf(onSurfaceVariant)

    /** iconColor */
    var icon by mutableStateOf(icon)

    /** colorPrimary */
    var primary by mutableStateOf(primary)

    /** colorOngoing */
    var ongoing by mutableStateOf(ongoing)
    var isLight by mutableStateOf(isLight)

    fun copy(
        background: Color = this.background,
        surfaceVariant: Color = this.surfaceVariant,
        surface: Color = this.surface,
        surfaceContainer: Color = this.surfaceContainer,
        onBackground: Color = this.onBackground,
        onSurfaceVariant: Color = this.onSurfaceVariant,
        icon: Color = this.icon,
        primary: Color = this.primary,
        ongoing: Color = this.ongoing,
        isLight: Boolean = this.isLight,
    ) = CloudStreamColorScheme(
        background, surfaceVariant, surface, surfaceContainer,
        onBackground, onSurfaceVariant, icon, primary, ongoing, isLight,
    )
}

internal fun darkScheme() = CloudStreamColorScheme(
    background = CloudStreamPalette.DarkBlackBg,
    surfaceVariant = CloudStreamPalette.DarkPrimaryGrayBg,
    surface = CloudStreamPalette.DarkIconGrayBg,
    surfaceContainer = CloudStreamPalette.DarkBoxItemBg,
    onBackground = CloudStreamPalette.DarkText,
    onSurfaceVariant = CloudStreamPalette.DarkGrayText,
    icon = CloudStreamPalette.DarkIcon,
    primary = CloudStreamPalette.Primary,
    ongoing = CloudStreamPalette.Ongoing,
    isLight = false,
)

internal fun amoledScheme() = darkScheme().copy(
    background = CloudStreamPalette.AmoledBlack,
    surface = CloudStreamPalette.AmoledBlack,
    surfaceVariant = CloudStreamPalette.AmoledBlack,
    surfaceContainer = CloudStreamPalette.AmoledBlack,
)

internal fun amoledLightScheme() = amoledScheme().copy(
    surfaceVariant = CloudStreamPalette.AmoledNearBlack,
)

internal fun lightScheme() = CloudStreamColorScheme(
    background = CloudStreamPalette.LightBlackBg,
    surfaceVariant = CloudStreamPalette.LightPrimaryGrayBg,
    surface = CloudStreamPalette.LightIconGrayBg,
    surfaceContainer = CloudStreamPalette.LightBoxItemBg,
    onBackground = CloudStreamPalette.LightText,
    onSurfaceVariant = CloudStreamPalette.LightGrayText,
    icon = CloudStreamPalette.LightIcon,
    primary = CloudStreamPalette.Primary,
    ongoing = CloudStreamPalette.Ongoing,
    isLight = true,
)

internal fun draculaScheme() = CloudStreamColorScheme(
    background = CloudStreamPalette.DraculaBlackBg,
    surfaceVariant = CloudStreamPalette.DraculaPrimaryGrayBg,
    surface = CloudStreamPalette.DraculaIconGrayBg,
    surfaceContainer = CloudStreamPalette.DraculaBoxItemBg,
    onBackground = CloudStreamPalette.DraculaText,
    onSurfaceVariant = CloudStreamPalette.DraculaGrayText,
    icon = CloudStreamPalette.DraculaIcon,
    primary = CloudStreamPalette.Primary,
    ongoing = CloudStreamPalette.Ongoing,
    isLight = false,
)

internal fun lavenderScheme() = CloudStreamColorScheme(
    background = CloudStreamPalette.LavenderBlackBg,
    surfaceVariant = CloudStreamPalette.LavenderPrimaryGrayBg,
    surface = CloudStreamPalette.LavenderIconGrayBg,
    surfaceContainer = CloudStreamPalette.LavenderBoxItemBg,
    onBackground = CloudStreamPalette.LavenderText,
    onSurfaceVariant = CloudStreamPalette.LavenderGrayText,
    icon = CloudStreamPalette.LavenderIcon,
    primary = CloudStreamPalette.Primary,
    ongoing = CloudStreamPalette.Ongoing,
    isLight = true,
)

internal fun silentBlueScheme() = CloudStreamColorScheme(
    background = CloudStreamPalette.SilentBlueBlackBg,
    surfaceVariant = CloudStreamPalette.SilentBluePrimaryGrayBg,
    surface = CloudStreamPalette.SilentBlueIconGrayBg,
    surfaceContainer = CloudStreamPalette.SilentBlueBoxItemBg,
    onBackground = CloudStreamPalette.SilentBlueText,
    onSurfaceVariant = CloudStreamPalette.SilentBlueGrayText,
    icon = CloudStreamPalette.SilentBlueIcon,
    primary = CloudStreamPalette.Primary,
    ongoing = CloudStreamPalette.Ongoing,
    isLight = false,
)

@Composable
@ReadOnlyComposable
fun resolveDynamicPrimaryColor(): Color {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorResource(android.R.color.system_accent1_200)
    } else {
        CloudStreamPrimaryColor.NORMAL.color
    }
}

@Composable
@ReadOnlyComposable
fun resolveDynamicSecondaryColor(): Color {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorResource(android.R.color.system_accent2_200)
    } else {
        CloudStreamPrimaryColor.NORMAL.color
    }
}

val LocalCloudStreamColors = staticCompositionLocalOf { darkScheme() }

object CloudStreamTheme {
    val colors: CloudStreamColorScheme @Composable @ReadOnlyComposable get() = LocalCloudStreamColors.current
}

private fun CloudStreamColorScheme.toMaterial3ColorScheme() = if (isLight) {
    lightColorScheme(
        primary = primary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceContainer = surfaceContainer,
        onBackground = onBackground,
        onSurface = onBackground,
        onSurfaceVariant = onSurfaceVariant,
        onPrimary = Color.White,
    )
} else {
    darkColorScheme(
        primary = primary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceContainer = surfaceContainer,
        onBackground = onBackground,
        onSurface = onBackground,
        onSurfaceVariant = onSurfaceVariant,
        onPrimary = Color.White,
    )
}

// Declare the font families
object AppFont {
    val googleSans = FontFamily(
        Font(R.font.google_sans),
        Font(R.font.google_sans, style = FontStyle.Italic),
        Font(R.font.google_sans, FontWeight.Medium),
        Font(R.font.google_sans, FontWeight.Medium, style = FontStyle.Italic),
        Font(R.font.google_sans, FontWeight.Bold),
        Font(R.font.google_sans, FontWeight.Bold, style = FontStyle.Italic)
    )
}

private val defaultTypography = Typography()
val typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = AppFont.googleSans),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = AppFont.googleSans),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = AppFont.googleSans),

    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = AppFont.googleSans),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = AppFont.googleSans),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = AppFont.googleSans),

    titleLarge = defaultTypography.titleLarge.copy(fontFamily = AppFont.googleSans),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = AppFont.googleSans),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = AppFont.googleSans),

    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = AppFont.googleSans),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = AppFont.googleSans),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = AppFont.googleSans),

    labelLarge = defaultTypography.labelLarge.copy(fontFamily = AppFont.googleSans),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = AppFont.googleSans),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = AppFont.googleSans)
)

@Composable
fun modeToTheme(mode : CloudStreamThemeMode, primaryColor: CloudStreamPrimaryColor) : CloudStreamColorScheme {
    val dynamicTheme = resolveDynamicTheme()
    val dynamicPrimary = resolveDynamicPrimaryColor()
    val dynamicSecondary = resolveDynamicSecondaryColor()
    val systemDark = isSystemInDarkTheme()
    val color = remember(mode, primaryColor, systemDark, dynamicTheme, dynamicPrimary, dynamicSecondary) {
        val base = when (mode) {
            CloudStreamThemeMode.Dark -> darkScheme()
            CloudStreamThemeMode.Amoled -> amoledScheme()
            CloudStreamThemeMode.AmoledLight -> amoledLightScheme()
            CloudStreamThemeMode.Light -> lightScheme()
            CloudStreamThemeMode.Dracula -> draculaScheme()
            CloudStreamThemeMode.Lavender -> lavenderScheme()
            CloudStreamThemeMode.SilentBlue -> silentBlueScheme()
            CloudStreamThemeMode.FollowSystem -> if (systemDark) darkScheme() else lightScheme()
            CloudStreamThemeMode.Dynamic -> dynamicTheme
        }
        when {
            mode == CloudStreamThemeMode.Dynamic -> base
            primaryColor == CloudStreamPrimaryColor.DYNAMIC -> base.copy(primary = dynamicPrimary)
            primaryColor == CloudStreamPrimaryColor.DYNAMIC_TWO -> base.copy(primary = dynamicSecondary)
            else -> base.copy(primary = primaryColor.color)
        }
    }
    return color
}

@Composable
fun CloudStreamTheme(
    mode: CloudStreamThemeMode = CloudStreamThemeMode.FollowSystem,
    primaryColor: CloudStreamPrimaryColor = CloudStreamPrimaryColor.NORMAL,
    content: @Composable () -> Unit,
) {

    val csColors = modeToTheme(mode, primaryColor)

    CompositionLocalProvider(LocalCloudStreamColors provides csColors) {
        MaterialTheme(
            colorScheme = csColors.toMaterial3ColorScheme(),
            content = content,
            typography = typography
        )
    }
}