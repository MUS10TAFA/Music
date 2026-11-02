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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.luxury.auraplayer.data.AudioTrack
import com.luxury.auraplayer.data.SmartPlaylistType
import com.luxury.auraplayer.ui.theme.AuraCardDark
import com.luxury.auraplayer.ui.theme.AuraCardElevated
import com.luxury.auraplayer.ui.theme.AuraGlassBorder
import com.luxury.auraplayer.ui.theme.AuraGoldPrimary
import com.luxury.auraplayer.ui.theme.AuraSurfaceDark
import com.luxury.auraplayer.ui.theme.AuraTextGray
import com.luxury.auraplayer.ui.theme.AuraTextWhite

@Composable
fun MainScreen(
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    favoriteIds: Set<Long>,
    onTrackClick: (AudioTrack) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onOpenEqualizer: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf(SmartPlaylistType.ALL_TRACKS) }

    val filteredTracks = remember(tracks, searchQuery, selectedPlaylist, favoriteIds) {
        tracks.filter { track ->
            val matchesSearch = track.title.contains(searchQuery, true) ||
                    track.artist.contains(searchQuery, true) ||
                    track.album.contains(searchQuery, true)

            val matchesPlaylist = when (selectedPlaylist) {
                SmartPlaylistType.ALL_TRACKS -> true
                SmartPlaylistType.FAVORITES -> favoriteIds.contains(track.id)
                SmartPlaylistType.HI_RES_AUDIO -> track.isHiRes
                SmartPlaylistType.RECENTLY_ADDED -> true
                SmartPlaylistType.CLOUD_STREAMS -> track.isCloudStream
            }
            matchesSearch && matchesPlaylist
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(AuraSurfaceDark, Color(0xFF020204), Color.Black)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // شريط العنوان الملكي وزر معادل الصوت
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AURA LUXURY",
                        color = AuraGoldPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "مكتبتك الموسيقية",
                        color = AuraTextWhite,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onOpenEqualizer,
                    modifier = Modifier
                        .size(48.dp)
                        .background(AuraCardElevated, CircleShape)
                        .border(1.dp, AuraGoldPrimary.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizer",
                        tint = AuraGoldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // شريط البحث الفوري الفاخر
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث عن أغنية، فنان، أو ألبوم...", color = AuraTextGray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AuraGoldPrimary) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuraGoldPrimary,
                    unfocusedBorderColor = AuraGlassBorder,
                    focusedContainerColor = AuraCardDark.copy(alpha = 0.6f),
                    unfocusedContainerColor = AuraCardDark.copy(alpha = 0.4f),
                    focusedTextColor = AuraTextWhite,
                    unfocusedTextColor = AuraTextWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // قوائم التشغيل الذكية الأفقية
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(SmartPlaylistType.values()) { playlistType ->
                    val isSelected = selectedPlaylist == playlistType
                    val bgCol = if (isSelected) AuraGoldPrimary else AuraCardElevated
                    val textCol = if (isSelected) Color.Black else AuraTextWhite

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgCol)
                            .border(1.dp, if (isSelected) Color.Transparent else AuraGlassBorder, RoundedCornerShape(12.dp))
                            .clickable { selectedPlaylist = playlistType }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = playlistType.titleAr,
                            color = textCol,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // قائمة الأغاني الحقيقية
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 110.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredTracks, key = { it.id }) { track ->
                    val isCurrent = currentTrack?.id == track.id
                    val isFav = favoriteIds.contains(track.id)

                    AudioTrackItem(
                        track = track,
                        isCurrentPlaying = isCurrent,
                        isFavorite = isFav,
                        onClick = { onTrackClick(track) },
                        onFavoriteClick = { onToggleFavorite(track.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AudioTrackItem(
    track: AudioTrack,
    isCurrentPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val borderColor = if (isCurrentPlaying) AuraGoldPrimary else AuraGlassBorder
    val bgBrush = if (isCurrentPlaying) {
        Brush.horizontalGradient(listOf(AuraCardElevated, Color(0xFF2A2210)))
    } else {
        Brush.horizontalGradient(listOf(AuraCardDark, AuraCardDark))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // غلاف الأغنية الحقيقي عالي الدقة
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, AuraGoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // البيانات الحقيقية والوسم الصوتي
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isCurrentPlaying) AuraGoldPrimary else AuraTextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${track.artist} • ${track.album}",
                    color = AuraTextGray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AuraGoldPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = track.qualityBadge,
                            color = AuraGoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = track.formattedDuration,
                        color = AuraTextGray,
                        fontSize = 11.sp
                    )
                }
            }

            // زر المفضلة
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color.Red.copy(alpha = 0.9f) else AuraTextGray
                )
            }
        }
    }
}
