@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package dev.herdr.handheld.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import dev.herdr.handheld.R

private fun variableFamily(resource: Int)=FontFamily(*listOf(400,500,600,700).map { weight ->
    Font(resource,weight=FontWeight(weight),variationSettings=FontVariation.Settings(FontVariation.weight(weight)))
}.toTypedArray())

val DisplayFont=variableFamily(R.font.space_grotesk)
val ReaderFont=variableFamily(R.font.jetbrains_mono)
val HandheldTypography=Typography(
    bodyLarge=TextStyle(fontFamily=DisplayFont,fontSize=18.sp,lineHeight=25.sp),
    bodyMedium=TextStyle(fontFamily=DisplayFont,fontSize=16.sp,lineHeight=22.sp),
    bodySmall=TextStyle(fontFamily=DisplayFont,fontSize=13.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontFamily=DisplayFont,fontSize=16.sp,fontWeight=FontWeight.SemiBold),
    titleLarge=TextStyle(fontFamily=DisplayFont,fontSize=24.sp,fontWeight=FontWeight.Bold)
)
