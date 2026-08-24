package com.example.weight.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * WeightWise 2.0 的现代东方色彩骨架。
 *
 * 五套主题只更换品牌强调色，米白纸感背景、墨色文字与朱砂提醒保持一致，
 * 避免换主题后整个产品像换了一套模板。枚举名称继续兼容历史 MMKV 值。
 */
private fun weightWiseLightScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = Color(0xFF9E4738),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF6F261B),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF7F4ED),
    onBackground = Color(0xFF1F2523),
    surface = Color(0xFFF7F4ED),
    onSurface = Color(0xFF1F2523),
    surfaceVariant = Color(0xFFE8E3D9),
    onSurfaceVariant = Color(0xFF5A625E),
    outline = Color(0xFF747C78),
    outlineVariant = Color(0xFFD5D0C6),
    inverseSurface = Color(0xFF2D3330),
    inverseOnSurface = Color(0xFFF4F1EA),
    inversePrimary = Color(0xFF80D5C7),
    surfaceDim = Color(0xFFDCD8D0),
    surfaceBright = Color(0xFFFFFCF6),
    surfaceContainerLowest = Color(0xFFFFFCF6),
    surfaceContainerLow = Color(0xFFF2EFE8),
    surfaceContainer = Color(0xFFEDE9E1),
    surfaceContainerHigh = Color(0xFFE7E2D9),
    surfaceContainerHighest = Color(0xFFE1DCD3),
)

private fun weightWiseDarkScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFF05372F),
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color(0xFF26342F),
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = Color(0xFFFFB4A8),
    onTertiary = Color(0xFF5F160C),
    tertiaryContainer = Color(0xFF7E3024),
    onTertiaryContainer = Color(0xFFFFDAD2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111714),
    onBackground = Color(0xFFE4EAE6),
    surface = Color(0xFF111714),
    onSurface = Color(0xFFE4EAE6),
    surfaceVariant = Color(0xFF414945),
    onSurfaceVariant = Color(0xFFC1C9C4),
    outline = Color(0xFF8B938E),
    outlineVariant = Color(0xFF414945),
    inverseSurface = Color(0xFFE4EAE6),
    inverseOnSurface = Color(0xFF29302D),
    inversePrimary = Color(0xFF176B61),
    surfaceDim = Color(0xFF111714),
    surfaceBright = Color(0xFF343B38),
    surfaceContainerLowest = Color(0xFF0C110F),
    surfaceContainerLow = Color(0xFF19201D),
    surfaceContainer = Color(0xFF1D2421),
    surfaceContainerHigh = Color(0xFF272E2B),
    surfaceContainerHighest = Color(0xFF323936),
)

internal val jadeLightScheme = weightWiseLightScheme(
    primary = Color(0xFF176B61),
    primaryContainer = Color(0xFFCDEBE5),
    onPrimaryContainer = Color(0xFF0C4F47),
    secondary = Color(0xFF52665E),
    secondaryContainer = Color(0xFFD5E8DF),
    onSecondaryContainer = Color(0xFF394E46),
)
internal val jadeDarkScheme = weightWiseDarkScheme(
    primary = Color(0xFF7AD5C5),
    primaryContainer = Color(0xFF0B5048),
    onPrimaryContainer = Color(0xFFB6F2E7),
    secondary = Color(0xFFB8CCC3),
    secondaryContainer = Color(0xFF3B4E47),
    onSecondaryContainer = Color(0xFFD5E8DF),
)

internal val inkLightScheme = weightWiseLightScheme(
    primary = Color(0xFF355E70),
    primaryContainer = Color(0xFFD2E8F1),
    onPrimaryContainer = Color(0xFF204858),
    secondary = Color(0xFF58646A),
    secondaryContainer = Color(0xFFDCE5E8),
    onSecondaryContainer = Color(0xFF404C51),
)
internal val inkDarkScheme = weightWiseDarkScheme(
    primary = Color(0xFF9FCBDA),
    primaryContainer = Color(0xFF244C5D),
    onPrimaryContainer = Color(0xFFCDEAF4),
    secondary = Color(0xFFC0C9CD),
    secondaryContainer = Color(0xFF404B50),
    onSecondaryContainer = Color(0xFFDCE5E8),
)

internal val lotusLightScheme = weightWiseLightScheme(
    primary = Color(0xFF705B73),
    primaryContainer = Color(0xFFF1DDEE),
    onPrimaryContainer = Color(0xFF57435A),
    secondary = Color(0xFF685D66),
    secondaryContainer = Color(0xFFECDDE7),
    onSecondaryContainer = Color(0xFF50464E),
)
internal val lotusDarkScheme = weightWiseDarkScheme(
    primary = Color(0xFFD9BFDA),
    primaryContainer = Color(0xFF57435A),
    onPrimaryContainer = Color(0xFFF1DDEE),
    secondary = Color(0xFFD2C2CD),
    secondaryContainer = Color(0xFF50464E),
    onSecondaryContainer = Color(0xFFECDDE7),
)

internal val rougeLightScheme = weightWiseLightScheme(
    primary = Color(0xFF934B5D),
    primaryContainer = Color(0xFFFFD9E1),
    onPrimaryContainer = Color(0xFF743446),
    secondary = Color(0xFF72575E),
    secondaryContainer = Color(0xFFF5DCE2),
    onSecondaryContainer = Color(0xFF594047),
)
internal val rougeDarkScheme = weightWiseDarkScheme(
    primary = Color(0xFFFFB2C2),
    primaryContainer = Color(0xFF743446),
    onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFE1BEC6),
    secondaryContainer = Color(0xFF594047),
    onSecondaryContainer = Color(0xFFF5DCE2),
)

internal val cinnabarLightScheme = weightWiseLightScheme(
    primary = Color(0xFFA14E36),
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF7D351F),
    secondary = Color(0xFF73594F),
    secondaryContainer = Color(0xFFF5DED5),
    onSecondaryContainer = Color(0xFF594238),
)
internal val cinnabarDarkScheme = weightWiseDarkScheme(
    primary = Color(0xFFFFB59D),
    primaryContainer = Color(0xFF7D351F),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFE2BFB3),
    secondaryContainer = Color(0xFF594238),
    onSecondaryContainer = Color(0xFFF5DED5),
)
