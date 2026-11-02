package com.luxury.auraplayer.ui.screens

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.luxury.auraplayer.data.AudioTrack
import com.luxury.auraplayer.ui.theme.AuraBlack
import com.luxury.auraplayer.ui.theme.AuraCardDark
import com.luxury.auraplayer.ui.theme.AuraCardElevated
import com.luxury.auraplayer.ui.theme.AuraGlassBorder
import com.luxury.auraplayer.ui.theme.AuraGoldLight
import com.luxury.auraplayer.ui.theme.AuraGoldPrimary
import com.luxury.auraplayer.ui.theme.AuraTextGray
import com.luxury.auraplayer.ui.theme.AuraTextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NowPlayingScreen(
    track: AudioTrack,
    isPlaying: Boolean,
    currentPositionMs: Long,
    isFavorite: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    visualizerMagnitudes: List<Float>,
    sleepTimerRemainingMs: Long?,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onSetSleepTimer: (Int) -> Unit
) {
    val context = LocalContext.current
    var dominantAmbientColor by remember { mutableStateOf(Color(0xFF3A270B)) }
    var showLyricsView by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // استخراج اللون السائد من غلاف الأغنية الحقيقي باستخدام Palette API
    LaunchedEffect(track.artworkUri) {
        if (track.artworkUri == null) {
            dominantAmbientColor = Color(0xFF3A270B)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(track.artworkUri)
                    .allowHardware(false)
                    .build()
                val result = (loader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val extracted = palette.getVibrantColor(
                        palette.getDominantColor(0xFF3A270B.toInt())
                    )
                    dominantAmbientColor = Color(extracted)
                }
            } catch (_: Exception) {
                dominantAmbientColor = Color(0xFF3A270B)
            }
        }
    }

    val animatedAmbientColor by animateColorAsState(
        targetValue = dominantAmbientColor,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "AmbientGlow"
    )

    // البحث عن السطر النشط في الكلمات المتزامنة
    val activeLyricIndex = remember(currentPositionMs, track.syncedLyrics) {
        track.syncedLyrics.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)
    }

    val lyricsListState = rememberLazyListState()
    LaunchedEffect(activeLyricIndex, showLyricsView) {
        if (showLyricsView && track.syncedLyrics.isNotEmpty()) {
            lyricsListState.animateScrollToItem((activeLyricIndex - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedAmbientColor.copy(alpha = 0.55f),
                        Color(0xFF0B0B10),
                        AuraBlack
                    )
                )
            )
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // الشريط العلوي (إغلاق الشاشة + شارة الجودة الفائقة + مؤقت النوم ومعادل الصوت)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(44.dp)
                        .background(AuraCardElevated.copy(alpha = 0.7f), CircleShape)
                        .border(1.dp, AuraGlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = AuraTextWhite
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        color = AuraGoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AuraGoldPrimary.copy(alpha = 0.18f))
                            .border(1.dp, AuraGoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${track.qualityBadge} • ${track.sampleRateHz / 1000}kHz",
                            color = AuraGoldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showSleepTimerDialog = true },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (sleepTimerRemainingMs != null) AuraGoldPrimary.copy(alpha = 0.25f)
                                else AuraCardElevated.copy(alpha = 0.7f),
                                CircleShape
                            )
                            .border(1.dp, AuraGlassBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerRemainingMs != null) AuraGoldPrimary else AuraTextWhite
                        )
                    }

                    IconButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier
                            .size(44.dp)
                            .background(AuraCardElevated.copy(alpha = 0.7f), CircleShape)
                            .border(1.dp, AuraGoldPrimary.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "10-Band EQ",
                            tint = AuraGoldPrimary
                        )
                    }
                }
            }

            if (sleepTimerRemainingMs != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val remMin = (sleepTimerRemainingMs / 60000L)
                val remSec = (sleepTimerRemainingMs % 60000L) / 1000L
                Text(
                    text = String.format("مؤقت النوم نشط: %02d:%02d", remMin, remSec),
                    color = AuraGoldPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // منطقة العرض الوسطى: التبديل بين الغلاف الفاخر أو الكلمات المتزامنة (Synced Lyrics)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!showLyricsView) {
                    LuxuryArtworkCard(
                        track = track,
                        isPlaying = isPlaying,
                        ambientColor = animatedAmbientColor,
                        onClick = { showLyricsView = true }
                    )
                } else {
                    SyncedLyricsCard(
                        lyrics = track.syncedLyrics,
                        activeIndex = activeLyricIndex,
                        listState = lyricsListState,
                        onLineClick = { lineTimeMs -> onSeekTo(lineTimeMs) },
                        onCloseLyrics = { showLyricsView = false }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // الموجات الصوتية التفاعلية الحية (32-Bar Audio Visualizer)
            LiveAudioVisualizer(
                magnitudes = visualizerMagnitudes,
                accentColor = animatedAmbientColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // معلومات الأغنية الحقيقية وزر المفضلة وزر الكلمات
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = AuraTextWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${track.artist} — ${track.album}",
                        color = AuraTextGray,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { showLyricsView = !showLyricsView }) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Synced Lyrics",
                        tint = if (showLyricsView) AuraGoldPrimary else AuraTextGray
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF3366) else AuraTextWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // شريط التقدم الزمني الدقيق
            val durationSafe = track.durationMs.coerceAtLeast(1L)
            val sliderPosition = (currentPositionMs.toFloat() / durationSafe.toFloat()).coerceIn(0f, 1f)

            Slider(
                value = sliderPosition,
                onValueChange = { fraction ->
                    onSeekTo((fraction * durationSafe).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = AuraGoldPrimary,
                    activeTrackColor = AuraGoldPrimary,
                    inactiveTrackColor = AuraGlassBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMillisToMinutes(currentPositionMs),
                    color = AuraTextGray,
                    fontSize = 12.sp
                )
                Text(
                    text = formatMillisToMinutes(track.durationMs),
                    color = AuraTextGray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // أزرار التحكم الرئيسية بالتشغيل (Shuffle, Previous, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) AuraGoldPrimary else AuraTextGray
                    )
                }

                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .size(54.dp)
                        .background(AuraCardElevated, CircleShape)
                        .border(1.dp, AuraGlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = AuraTextWhite,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // زر التشغيل الملكي المتوهج
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(24.dp, CircleShape, spotColor = AuraGoldPrimary)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AuraGoldLight, AuraGoldPrimary, Color(0xFFAA7C11))
                            )
                        )
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = AuraBlack,
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(54.dp)
                        .background(AuraCardElevated, CircleShape)
                        .border(1.dp, AuraGlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = AuraTextWhite,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = if (repeatMode == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != 0) AuraGoldPrimary else AuraTextGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerSelectionDialog(
            onDismiss = { showSleepTimerDialog = false },
            onSelectMinutes = { mins ->
                onSetSleepTimer(mins)
                showSleepTimerDialog = false
            }
        )
    }
}

@Composable
private fun LuxuryArtworkCard(
    track: AudioTrack,
    isPlaying: Boolean,
    ambientColor: Color,
    onClick: () -> Unit
) {
    val scaleAnim = if (isPlaying) 1.0f else 0.92f
    Box(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
            }
            .shadow(32.dp, RoundedCornerShape(32.dp), spotColor = ambientColor)
            .clip(RoundedCornerShape(32.dp))
            .background(AuraCardDark)
            .border(1.5.dp, AuraGoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(32.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = track.artworkUri,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = {
                // صورة غلاف افتراضية فخمة جداً في حال عدم وجود غلاف مدمج
                DefaultLuxuryArtworkPlaceholder(trackTitle = track.title)
            }
        )
    }
}

@Composable
fun DefaultLuxuryArtworkPlaceholder(trackTitle: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DiscRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2B2210), Color(0xFF0E0E14), AuraBlack)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Album,
            contentDescription = null,
            tint = AuraGoldPrimary.copy(alpha = 0.75f),
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer { rotationZ = rotation }
        )
        Text(
            text = trackTitle.take(2).uppercase(),
            color = AuraTextWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SyncedLyricsCard(
    lyrics: List<com.luxury.auraplayer.data.LyricLine>,
    activeIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLineClick: (Long) -> Unit,
    onCloseLyrics: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(AuraCardDark.copy(alpha = 0.75f))
            .border(1.dp, AuraGlassBorder, RoundedCornerShape(28.dp))
            .padding(18.dp)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 45.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isCurrent = index == activeIndex
                Text(
                    text = line.text,
                    color = if (isCurrent) AuraGoldPrimary else AuraTextGray.copy(alpha = 0.55f),
                    fontSize = if (isCurrent) 20.sp else 15.sp,
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLineClick(line.timeMs) }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveAudioVisualizer(
    magnitudes: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barCount = magnitudes.size.coerceAtLeast(1)
        val totalSpacing = size.width * 0.28f
        val barWidth = (size.width - totalSpacing) / barCount
        val spaceBetween = totalSpacing / (barCount + 1)

        magnitudes.forEachIndexed { index, magnitude ->
            val clampedMag = magnitude.coerceIn(0.08f, 1f)
            val barHeight = size.height * clampedMag
            val xOffset = spaceBetween + index * (barWidth + spaceBetween)
            val yOffset = (size.height - barHeight) / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(AuraGoldLight, AuraGoldPrimary, accentColor)
                ),
                topLeft = Offset(xOffset, yOffset),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun SleepTimerSelectionDialog(
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit
) {
    val options = listOf(0 to "إيقاف المؤقت", 15 to "15 دقيقة", 30 to "30 دقيقة", 45 to "45 دقيقة", 60 to "ساعة كاملة")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraCardElevated,
        title = {
            Text("مؤقت النوم الذكي", color = AuraGoldPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (mins, label) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AuraCardDark)
                            .clickable { onSelectMinutes(mins) }
                            .padding(14.dp)
                    ) {
                        Text(label, color = AuraTextWhite, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = AuraGoldPrimary)
            }
        }
    )
}

private fun formatMillisToMinutes(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
