package com.luxury.auraplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.luxury.auraplayer.MainActivity
import com.luxury.auraplayer.data.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AuraMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var crossfadeWatcherJob: Job? = null
    private var fadeTransitionJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var isCrossfadeInProgress = false

    companion object {
        const val CMD_SET_SLEEP_TIMER = "com.luxury.auraplayer.CMD_SET_SLEEP_TIMER"
        const val EXTRA_SLEEP_MINUTES = "EXTRA_SLEEP_MINUTES"

        private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
        val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

        /**
         * تحويل نموذج الأغنية الحقيقي (AudioTrack) إلى MediaItem متكامل يظهر في شاشة القفل وإشعار النظام
         */
        fun AudioTrack.toMediaItem(): MediaItem {
            val extras = Bundle().apply {
                putLong("track_id", id)
                putInt("bitrate_kbps", bitrateKbps)
                putInt("sample_rate_hz", sampleRateHz)
                putString("file_path", filePath)
                putBoolean("is_cloud", isCloudStream)
            }

            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(title) // الاسم الحقيقي للأغنية يظهر في الإشعار وشاشة القفل
                .setArtist(artist)
                .setAlbumTitle(album)
                .setDisplayTitle(title)
                .setSubtitle(artist)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(extras)

            artworkUri?.let { uri ->
                metadataBuilder.setArtworkUri(uri) // صورة الغلاف الأصلية تظهر في إشعار النظام وشاشة القفل
            }

            return MediaItem.Builder()
                .setMediaId(id.toString())
                .setUri(contentUri)
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // إدارة تلقائية لتركيز الصوت عند المكالمات
            .setHandleAudioBecomingNoisy(true) // إيقاف مؤقت تلقائي عند فصل السماعات
            .setWakeMode(C.WAKE_MODE_NETWORK) // منع خمول المعالج أو الشبكة أثناء التشغيل بالخلفية
            .build()
            .apply {
                // تفعيل التشغيل بدون فواصل (Gapless Playback)
                pauseAtEndOfMediaItems = false
                repeatMode = Player.REPEAT_MODE_ALL
            }

        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AuraAudioEffectsEngine.bindToAudioSession(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                AuraAudioEffectsEngine.updatePlaybackActiveState(isPlaying)
                if (isPlaying && fadeTransitionJob?.isActive != true) {
                    smoothVolumeFadeIn()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                ) {
                    smoothVolumeFadeIn()
                }
            }
        })

        // ربط جلسة الصوت فور الإنشاء إذا كانت متاحة
        if (exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            AuraAudioEffectsEngine.bindToAudioSession(exoPlayer.audioSessionId)
        }

        // إعداد النقر على الإشعار الخارجي لفتح التطبيق مباشرة
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(AuraMediaSessionCallback())
            .build()

        startCrossfadeMonitor()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * مراقب ذكي لتنفيذ الدمج السلس (Crossfade) قبل انتهاء الأغنية الحالية بالثواني المحددة
     */
    private fun startCrossfadeMonitor() {
        crossfadeWatcherJob?.cancel()
        crossfadeWatcherJob = serviceScope.launch {
            while (isActive) {
                val crossfadeSec = AuraAudioEffectsEngine.dspState.value.crossfadeDurationSec
                if (exoPlayer.isPlaying && crossfadeSec > 0 && !isCrossfadeInProgress) {
                    val duration = exoPlayer.duration
                    val position = exoPlayer.currentPosition
                    val crossfadeMs = crossfadeSec * 1000L

                    if (duration > crossfadeMs * 2 && (duration - position) in 350L..crossfadeMs) {
                        if (exoPlayer.hasNextMediaItem()) {
                            performCrossfadeToNext(crossfadeMs)
                        }
                    }
                }
                delay(400L)
            }
        }
    }

    private fun performCrossfadeToNext(crossfadeMs: Long) {
        if (isCrossfadeInProgress) return
        isCrossfadeInProgress = true
        fadeTransitionJob?.cancel()

        fadeTransitionJob = serviceScope.launch {
            try {
                val steps = 16
                val stepDelay = (crossfadeMs / 2) / steps
                // خفض تدريجي ناعم لأغنية النهاية
                for (step in steps downTo 1) {
                    exoPlayer.volume = (step.toFloat() / steps.toFloat()).coerceIn(0.05f, 1f)
                    delay(stepDelay)
                }
                if (exoPlayer.hasNextMediaItem()) {
                    exoPlayer.seekToNextMediaItem()
                }
                // رفع تدريجي ناعم للأغنية الجديدة
                for (step in 1..steps) {
                    exoPlayer.volume = (step.toFloat() / steps.toFloat()).coerceIn(0.05f, 1f)
                    delay(stepDelay)
                }
                exoPlayer.volume = 1.0f
            } finally {
                exoPlayer.volume = 1.0f
                isCrossfadeInProgress = false
            }
        }
    }

    private fun smoothVolumeFadeIn() {
        if (isCrossfadeInProgress) return
        fadeTransitionJob?.cancel()
        fadeTransitionJob = serviceScope.launch {
            val crossfadeSec = AuraAudioEffectsEngine.dspState.value.crossfadeDurationSec
            if (crossfadeSec <= 0) {
                exoPlayer.volume = 1.0f
                return@launch
            }
            val steps = 10
            exoPlayer.volume = 0.2f
            for (step in 2..steps) {
                exoPlayer.volume = (step.toFloat() / steps.toFloat()).coerceIn(0.2f, 1.0f)
                delay(35L)
            }
            exoPlayer.volume = 1.0f
        }
    }

    /**
     * مؤقت النوم الذكي (Sleep Timer) مع تلاشي الصوت تدريجياً في آخر 5 ثوانٍ
     */
    private fun configureSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingMs.value = null
            exoPlayer.volume = 1.0f
            return
        }

        val totalDurationMs = minutes * 60_000L
        _sleepTimerRemainingMs.value = totalDurationMs

        sleepTimerJob = serviceScope.launch {
            var remaining = totalDurationMs
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining -= 1000L
                _sleepTimerRemainingMs.value = remaining

                // خفض الصوت تدريجياً في آخر 5 ثوانٍ لعدم إزعاج المستمع أثناء النوم
                if (remaining in 1..5000L && exoPlayer.isPlaying) {
                    exoPlayer.volume = (remaining.toFloat() / 5000f).coerceIn(0.05f, 1f)
                }
            }
            if (isActive) {
                exoPlayer.pause()
                exoPlayer.volume = 1.0f
                _sleepTimerRemainingMs.value = null
            }
        }
    }

    private inner class AuraMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SET_SLEEP_TIMER, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_SET_SLEEP_TIMER) {
                val minutes = args.getInt(EXTRA_SLEEP_MINUTES, 0)
                configureSleepTimer(minutes)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // الحفاظ على تشغيل الموسيقى في الخلفية إذا كانت قيد التشغيل، وإغلاق الخدمة فقط إذا كانت متوقفة
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        crossfadeWatcherJob?.cancel()
        fadeTransitionJob?.cancel()
        sleepTimerJob?.cancel()
        serviceScope.cancel()
        AuraAudioEffectsEngine.releaseHardwareEffects()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
