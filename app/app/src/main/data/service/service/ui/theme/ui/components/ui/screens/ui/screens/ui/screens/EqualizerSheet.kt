package com.luxury.auraplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.auraplayer.service.AudioDspState
import com.luxury.auraplayer.service.AuraAudioEffectsEngine
import com.luxury.auraplayer.service.EqPreset
import com.luxury.auraplayer.ui.theme.AuraBlack
import com.luxury.auraplayer.ui.theme.AuraCardDark
import com.luxury.auraplayer.ui.theme.AuraCardElevated
import com.luxury.auraplayer.ui.theme.AuraGlassBorder
import com.luxury.auraplayer.ui.theme.AuraGoldPrimary
import com.luxury.auraplayer.ui.theme.AuraSurfaceDark
import com.luxury.auraplayer.ui.theme.AuraTextGray
import com.luxury.auraplayer.ui.theme.AuraTextWhite

@Composable
fun EqualizerSheet(
    dspState: AudioDspState,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (EqPreset) -> Unit,
    onBandGainChanged: (Int, Float) -> Unit,
    onBassBoostChanged: (Float) -> Unit,
    onVirtualizerChanged: (Float) -> Unit,
    onCrossfadeChanged: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(AuraSurfaceDark, AuraBlack))
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // رأس الصفحة وتفعيل المحرك
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AURA MASTER DSP",
                    color = AuraGoldPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "معادل الصوت 10-Band EQ",
                    color = AuraTextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = dspState.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AuraBlack,
                        checkedTrackColor = AuraGoldPrimary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AuraTextWhite)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // الأوضاع المسبقة الاحترافية (Luxury Presets)
        Text("الأوضاع الصوتية الجاهزة", color = AuraTextGray, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(AuraAudioEffectsEngine.LUXURY_PRESETS) { preset ->
                val isSelected = dspState.activePresetName == preset.nameAr
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AuraGoldPrimary else AuraCardElevated)
                        .border(1.dp, AuraGlassBorder, RoundedCornerShape(12.dp))
                        .clickable { onSelectPreset(preset) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = preset.nameAr,
                        color = if (isSelected) AuraBlack else AuraTextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // مضخم الباص (Bass Boost) والصوت المحيطي (3D Virtualizer) والـ Crossfade
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AuraCardDark)
                .border(1.dp, AuraGlassBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DspControlSlider(
                title = "مضخم الباص العميق (Royal Bass Boost)",
                valueText = "${(dspState.bassBoostStrength * 100).toInt()}%",
                value = dspState.bassBoostStrength,
                valueRange = 0f..1f,
                onValueChange = onBassBoostChanged
            )

            DspControlSlider(
                title = "الصوت المحيطي ثلاثي الأبعاد (3D Stage)",
                valueText = "${(dspState.virtualizerStrength * 100).toInt()}%",
                value = dspState.virtualizerStrength,
                valueRange = 0f..1f,
                onValueChange = onVirtualizerChanged
            )

            DspControlSlider(
                title = "الدمج السلس بين الأغاني (Crossfade)",
                valueText = if (dspState.crossfadeDurationSec == 0) "إيقاف (Gapless)" else "${dspState.crossfadeDurationSec} ثوانٍ",
                value = dspState.crossfadeDurationSec.toFloat(),
                valueRange = 0f..12f,
                onValueChange = { onCrossfadeChanged(it.toInt()) }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // القنوات العشر (10-Band Frequency Sliders)
        Text("التحكم الدقيق بالترددات (-12dB إلى +12dB)", color = AuraTextGray, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AuraCardDark)
                .border(1.dp, AuraGlassBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dspState.bands.forEach { band ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = band.label,
                        color = AuraGoldPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(56.dp)
                    )

                    Slider(
                        value = band.gainDb,
                        onValueChange = { newGain -> onBandGainChanged(band.index, newGain) },
                        valueRange = -12f..12f,
                        colors = SliderDefaults.colors(
                            thumbColor = AuraGoldPrimary,
                            activeTrackColor = AuraGoldPrimary,
                            inactiveTrackColor = AuraGlassBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = String.format("%+.1f dB", band.gainDb),
                        color = AuraTextWhite,
                        fontSize = 11.sp,
                        modifier = Modifier.width(54.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun DspControlSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = AuraTextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(valueText, color = AuraGoldPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = AuraGoldPrimary,
                activeTrackColor = AuraGoldPrimary,
                inactiveTrackColor = AuraGlassBorder
            )
        )
    }
}
