package com.luxury.auraplayer.data

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: Uri,
    val artworkUri: Uri?,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val filePath: String,
    val dateAddedSec: Long,
    val isCloudStream: Boolean = false,
    val syncedLyrics: List<LyricLine> = emptyList()
) {
    val isHiRes: Boolean
        get() = bitrateKbps >= 320 || filePath.endsWith(".flac", ignoreCase = true) || filePath.endsWith(".wav", ignoreCase = true)

    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val qualityBadge: String
        get() = when {
            filePath.endsWith(".flac", true) || filePath.endsWith(".wav", true) -> "LOSSLESS HI-RES"
            bitrateKbps >= 320 -> "HQ 320kbps"
            bitrateKbps > 0 -> "${bitrateKbps}kbps"
            isCloudStream -> "CLOUD MASTER"
            else -> "STANDARD"
        }
}

enum class SmartPlaylistType(val titleAr: String) {
    ALL_TRACKS("كل الأغاني"),
    FAVORITES("المفضلة الملكية"),
    HI_RES_AUDIO("جودة فائقة Hi-Res"),
    RECENTLY_ADDED("المضافة حديثاً"),
    CLOUD_STREAMS("البث السحابي")
}

class AudioRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aura_luxury_prefs", Context.MODE_PRIVATE)

    private val artworkCacheDir: File by lazy {
        File(context.cacheDir, "embedded_artworks").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * استخراج كافة الأغاني المحلية بأسمائها الحقيقية وصور أغلفتها عالية الدقة + مقاطع سحابية جاهزة للتجربة
     */
    suspend fun loadAllAudioTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.BITRATE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val bitrateCol = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val rawTitle = cursor.getString(titleCol).orEmpty()
                val rawArtist = cursor.getString(artistCol).orEmpty()
                val rawAlbum = cursor.getString(albumCol).orEmpty()
                val albumId = cursor.getLong(albumIdCol)
                val duration = cursor.getLong(durationCol)
                val filePath = cursor.getString(dataCol).orEmpty()
                val dateAdded = cursor.getLong(dateAddedCol)
                val storeBitrate = if (bitrateCol != -1) cursor.getInt(bitrateCol) / 1000 else 0

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val fallbackAlbumArtUri = ContentUris.withAppendedId(albumArtBaseUri, albumId)

                // فحص عميق للملف لاستخراج الاسم الحقيقي والصورة المدمجة عالية الدقة
                val deepMeta = extractDeepMetadataAndArtwork(
                    trackId = id,
                    contentUri = contentUri,
                    filePath = filePath,
                    fallbackTitle = rawTitle,
                    fallbackArtist = rawArtist,
                    fallbackAlbum = rawAlbum,
                    fallbackBitrate = storeBitrate,
                    fallbackAlbumArtUri = fallbackAlbumArtUri
                )

                val syncedLyrics = loadSyncedLyricsForTrack(filePath, deepMeta.realTitle)

                tracks.add(
                    AudioTrack(
                        id = id,
                        title = deepMeta.realTitle,
                        artist = deepMeta.realArtist,
                        album = deepMeta.realAlbum,
                        durationMs = duration,
                        contentUri = contentUri,
                        artworkUri = deepMeta.bestArtworkUri,
                        bitrateKbps = deepMeta.bitrateKbps,
                        sampleRateHz = deepMeta.sampleRateHz,
                        filePath = filePath,
                        dateAddedSec = dateAdded,
                        isCloudStream = false,
                        syncedLyrics = syncedLyrics
                    )
                )
            }
        }

        // إضافة مقاطع سحابية فائقة الجودة لتجربة البث السحابي والكلمات المتزامنة فوراً
        tracks.addAll(getLuxuryCloudTracks())
        tracks
    }

    private data class DeepMetadataResult(
        val realTitle: String,
        val realArtist: String,
        val realAlbum: String,
        val bitrateKbps: Int,
        val sampleRateHz: Int,
        val bestArtworkUri: Uri?
    )

    private fun extractDeepMetadataAndArtwork(
        trackId: Long,
        contentUri: Uri,
        filePath: String,
        fallbackTitle: String,
        fallbackArtist: String,
        fallbackAlbum: String,
        fallbackBitrate: Int,
        fallbackAlbumArtUri: Uri
    ): DeepMetadataResult {
        val cachedArtFile = File(artworkCacheDir, "art_$trackId.jpg")
        val retriever = MediaMetadataRetriever()

        return try {
            if (filePath.isNotBlank() && File(filePath).exists()) {
                retriever.setDataSource(filePath)
            } else {
                retriever.setDataSource(context, contentUri)
            }

            val id3Title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val id3Artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val id3Album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val id3Bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()?.div(1000) ?: fallbackBitrate
            val id3SampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull() ?: 44100

            val cleanTitle = when {
                !id3Title.isNullOrBlank() -> id3Title.trim()
                fallbackTitle.isNotBlank() && !fallbackTitle.startsWith("AUD-", true) -> fallbackTitle.trim()
                else -> File(filePath).nameWithoutExtension.replace("_", " ").trim()
            }

            val cleanArtist = when {
                !id3Artist.isNullOrBlank() && id3Artist != "<unknown>" -> id3Artist.trim()
                fallbackArtist.isNotBlank() && fallbackArtist != "<unknown>" -> fallbackArtist.trim()
                else -> "فنان غير معروف"
            }

            val cleanAlbum = when {
                !id3Album.isNullOrBlank() && id3Album != "<unknown>" -> id3Album.trim()
                fallbackAlbum.isNotBlank() && fallbackAlbum != "<unknown>" -> fallbackAlbum.trim()
                else -> "ألبوم منفرد"
            }

            // استخراج وحفظ صورة الغلاف المدمجة بأعلى دقة إذا لم تكن محفوظة مسبقاً
            val artworkUri: Uri = if (cachedArtFile.exists() && cachedArtFile.length() > 0) {
                Uri.fromFile(cachedArtFile)
            } else {
                val embeddedBytes = retriever.embeddedPicture
                if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
                    FileOutputStream(cachedArtFile).use { out ->
                        out.write(embeddedBytes)
                    }
                    Uri.fromFile(cachedArtFile)
                } else {
                    fallbackAlbumArtUri
                }
            }

            DeepMetadataResult(
                realTitle = cleanTitle,
                realArtist = cleanArtist,
                realAlbum = cleanAlbum,
                bitrateKbps = if (id3Bitrate > 0) id3Bitrate else 320,
                sampleRateHz = id3SampleRate,
                bestArtworkUri = artworkUri
            )
        } catch (e: Exception) {
            DeepMetadataResult(
                realTitle = fallbackTitle.ifBlank { "مقطع صوتي" },
                realArtist = if (fallbackArtist == "<unknown>" || fallbackArtist.isBlank()) "فنان غير معروف" else fallbackArtist,
                realAlbum = if (fallbackAlbum == "<unknown>" || fallbackAlbum.isBlank()) "ألبوم عام" else fallbackAlbum,
                bitrateKbps = if (fallbackBitrate > 0) fallbackBitrate else 256,
                sampleRateHz = 44100,
                bestArtworkUri = fallbackAlbumArtUri
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * قراءة ملف الكلمات المتزامنة (.lrc) المطابق للأغنية أو توليد كلمات متزامنة افتراضية
     */
    private fun loadSyncedLyricsForTrack(filePath: String, trackTitle: String): List<LyricLine> {
        if (filePath.isNotBlank()) {
            val audioFile = File(filePath)
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                val parsed = parseLrcContent(lrcFile.readText())
                if (parsed.isNotEmpty()) return parsed
            }
        }
        return listOf(
            LyricLine(0L, "♪ مقدمة موسيقية - $trackTitle ♪"),
            LyricLine(8000L, "انغمس في تفاصيل الصوت المحيطي الفائق"),
            LyricLine(16000L, "يمكنك إضافة ملف .lrc بنفس اسم الأغنية"),
            LyricLine(25000L, "لعرض الكلمات المتزامنة بالثانية والجزء من الثانية"),
            LyricLine(35000L, "♪ استمتع بتجربة Aura Luxury Player ♪")
        )
    }

    fun parseLrcContent(lrcText: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
        lrcText.lineSequence().forEach { line ->
            val match = regex.find(line.trim())
            if (match != null) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionRaw = match.groupValues[3]
                val millis = if (fractionRaw.length == 2) {
                    (fractionRaw.toLongOrNull() ?: 0L) * 10L
                } else {
                    fractionRaw.toLongOrNull() ?: 0L
                }
                val text = match.groupValues[4].trim()
                val totalTimeMs = (minutes * 60_000L) + (seconds * 1000L) + millis
                if (text.isNotEmpty()) {
                    result.add(LyricLine(totalTimeMs, text))
                }
            }
        }
        return result.sortedBy { it.timeMs }
    }

    /**
     * إدارة قائمة المفضلة الملكية (حفظ واسترجاع فوري)
     */
    fun getFavoriteTrackIds(): Set<Long> {
        val rawSet = prefs.getStringSet("favorite_track_ids", emptySet()) ?: emptySet()
        return rawSet.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggleFavorite(trackId: Long): Set<Long> {
        val current = getFavoriteTrackIds().toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        prefs.edit().putStringSet("favorite_track_ids", current.map { it.toString() }.toSet()).apply()
        return current
    }

    /**
     * مقاطع سحابية مدمجة عالية الدقة مع أغلفة وكلمات متزامنة لتجربة البث السحابي
     */
    private fun getLuxuryCloudTracks(): List<AudioTrack> {
        return listOf(
            AudioTrack(
                id = -101L,
                title = "Midnight Velvet Odyssey",
                artist = "Aura Symphony Orchestra",
                album = "Royal OLED Sessions",
                durationMs = 372_000L,
                contentUri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
                artworkUri = Uri.parse("https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1000&q=85"),
                bitrateKbps = 320,
                sampleRateHz = 48000,
                filePath = "cloud://midnight_velvet.flac",
                dateAddedSec = System.currentTimeMillis() / 1000,
                isCloudStream = true,
                syncedLyrics = listOf(
                    LyricLine(0L, "♪ Midnight Velvet Odyssey - Cloud Master ♪"),
                    LyricLine(6000L, "في سكون الليل تتراقص الموجات الذهبية"),
                    LyricLine(14000L, "نقاء صوتي يتجاوز الحدود التقليدية"),
                    LyricLine(22000L, "تشعر بكل نغمة وكأنك في قلب المسرح"),
                    LyricLine(32000L, "إضاءة محيطية تتنفس مع إيقاع الموسيقى"),
                    LyricLine(45000L, "♪ عزف منفرد بتقنية الصوت المحيطي 3D ♪")
                )
            ),
            AudioTrack(
                id = -102L,
                title = "Golden Horizon Synth",
                artist = "Chronos & The Alchemist",
                album = "Aurelia 120Hz",
                durationMs = 425_000L,
                contentUri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
                artworkUri = Uri.parse("https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1000&q=85"),
                bitrateKbps = 320,
                sampleRateHz = 48000,
                filePath = "cloud://golden_horizon.flac",
                dateAddedSec = System.currentTimeMillis() / 1000,
                isCloudStream = true,
                syncedLyrics = listOf(
                    LyricLine(0L, "♪ Golden Horizon - Hi-Res Stream ♪"),
                    LyricLine(5000L, "أفق ذهبي يمتد عبر الترددات العميقة"),
                    LyricLine(12000L, "جرب تفعيل Bass Boost من معادل الصوت"),
                    LyricLine(20000L, "لتسمع نبضات الإيقاع بوضوح غير مسبوق"),
                    LyricLine(30000L, "♪ انتقال سلس بدون أي فواصل ♪")
                )
            )
        )
    }
}
