package com.rpmtech.expressopod.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle as PlatformFontStyle
import android.graphics.fonts.FontVariationAxis
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.rpmtech.expressopod.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalTextApi::class)
private class GoogleSansFlexFont(
    override val weight: FontWeight,
    override val style: FontStyle,
    val roundness: Float = 100f
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = GoogleSansFlexTypefaceLoader,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.slant(if (style == FontStyle.Italic) -10f else 0f),
        FontVariation.Setting("ROND", roundness)
    )
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoogleSansFlexFont) return false
        return weight == other.weight &&
            style == other.style &&
            roundness == other.roundness
    }

    override fun hashCode(): Int {
        var result = weight.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + roundness.hashCode()
        return result
    }

    override fun toString(): String {
        return "GoogleSansFlexFont(weight=$weight, style=$style, roundness=$roundness)"
    }
}

@OptIn(ExperimentalTextApi::class)
private object GoogleSansFlexTypefaceLoader : AndroidFont.TypefaceLoader {
    private val cache = ConcurrentHashMap<String, Typeface>()

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
        val flexFont = font as? GoogleSansFlexFont ?: return null
        val weightVal = flexFont.weight.weight
        val isItalic = flexFont.style == FontStyle.Italic
        val slantVal = if (isItalic) -10f else 0f
        val rondVal = flexFont.roundness
        val cacheKey = "${weightVal}_${isItalic}_${rondVal}"

        return cache.getOrPut(cacheKey) {
            buildVariableTypeface(context, weightVal, isItalic, slantVal, rondVal)
        }
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? {
        return loadBlocking(context, font)
    }

    private fun buildVariableTypeface(
        context: Context,
        weightVal: Int,
        isItalic: Boolean,
        slantVal: Float,
        rondVal: Float
    ): Typeface {
        val axes = arrayOf(
            FontVariationAxis("wght", weightVal.toFloat()),
            FontVariationAxis("ROND", rondVal),
            FontVariationAxis("slnt", slantVal)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val slantStyle = if (isItalic) {
                PlatformFontStyle.FONT_SLANT_ITALIC
            } else {
                PlatformFontStyle.FONT_SLANT_UPRIGHT
            }
            val platformFont = android.graphics.fonts.Font.Builder(
                context.resources,
                R.font.google_sans_flex
            )
                .setWeight(weightVal)
                .setSlant(slantStyle)
                .setFontVariationSettings(axes)
                .build()
            val family = PlatformFontFamily.Builder(platformFont).build()
            return Typeface.CustomFallbackBuilder(family)
                .setSystemFallback("sans-serif")
                .setStyle(PlatformFontStyle(weightVal, slantStyle))
                .build()
        } else {
            val cachedFile = File(context.cacheDir, "google_sans_flex.ttf")
            if (!cachedFile.exists() || cachedFile.length() == 0L) {
                context.resources.openRawResource(R.font.google_sans_flex).use { input ->
                    cachedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return Typeface.Builder(cachedFile)
                .setWeight(weightVal)
                .setItalic(isItalic)
                .setFontVariationSettings(axes)
                .build()
        }
    }
}

private val AllFontWeights = listOf(
    FontWeight.Thin,
    FontWeight.ExtraLight,
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black
)

val GoogleSansFlexFontFamily = FontFamily(
    AllFontWeights.flatMap { weight ->
        listOf(
            GoogleSansFlexFont(weight = weight, style = FontStyle.Normal, roundness = 100f),
            GoogleSansFlexFont(weight = weight, style = FontStyle.Italic, roundness = 100f)
        )
    }
)

private fun TextStyle.withGoogleSansFlex(): TextStyle = copy(
    fontFamily = GoogleSansFlexFontFamily,
    fontSynthesis = FontSynthesis.None
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val DefaultTypography = Typography()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.withGoogleSansFlex(),
    displayMedium = DefaultTypography.displayMedium.withGoogleSansFlex(),
    displaySmall = DefaultTypography.displaySmall.withGoogleSansFlex(),
    headlineLarge = DefaultTypography.headlineLarge.withGoogleSansFlex(),
    headlineMedium = DefaultTypography.headlineMedium.withGoogleSansFlex(),
    headlineSmall = DefaultTypography.headlineSmall.withGoogleSansFlex(),
    titleLarge = DefaultTypography.titleLarge.withGoogleSansFlex(),
    titleMedium = DefaultTypography.titleMedium.withGoogleSansFlex(),
    titleSmall = DefaultTypography.titleSmall.withGoogleSansFlex(),
    bodyLarge = DefaultTypography.bodyLarge.withGoogleSansFlex(),
    bodyMedium = DefaultTypography.bodyMedium.withGoogleSansFlex(),
    bodySmall = DefaultTypography.bodySmall.withGoogleSansFlex(),
    labelLarge = DefaultTypography.labelLarge.withGoogleSansFlex(),
    labelMedium = DefaultTypography.labelMedium.withGoogleSansFlex(),
    labelSmall = DefaultTypography.labelSmall.withGoogleSansFlex(),
    displayLargeEmphasized = DefaultTypography.displayLargeEmphasized.withGoogleSansFlex(),
    displayMediumEmphasized = DefaultTypography.displayMediumEmphasized.withGoogleSansFlex(),
    displaySmallEmphasized = DefaultTypography.displaySmallEmphasized.withGoogleSansFlex(),
    headlineLargeEmphasized = DefaultTypography.headlineLargeEmphasized.withGoogleSansFlex(),
    headlineMediumEmphasized = DefaultTypography.headlineMediumEmphasized.withGoogleSansFlex(),
    headlineSmallEmphasized = DefaultTypography.headlineSmallEmphasized.withGoogleSansFlex(),
    titleLargeEmphasized = DefaultTypography.titleLargeEmphasized.withGoogleSansFlex(),
    titleMediumEmphasized = DefaultTypography.titleMediumEmphasized.withGoogleSansFlex(),
    titleSmallEmphasized = DefaultTypography.titleSmallEmphasized.withGoogleSansFlex(),
    bodyLargeEmphasized = DefaultTypography.bodyLargeEmphasized.withGoogleSansFlex(),
    bodyMediumEmphasized = DefaultTypography.bodyMediumEmphasized.withGoogleSansFlex(),
    bodySmallEmphasized = DefaultTypography.bodySmallEmphasized.withGoogleSansFlex(),
    labelLargeEmphasized = DefaultTypography.labelLargeEmphasized.withGoogleSansFlex(),
    labelMediumEmphasized = DefaultTypography.labelMediumEmphasized.withGoogleSansFlex(),
    labelSmallEmphasized = DefaultTypography.labelSmallEmphasized.withGoogleSansFlex()
)
