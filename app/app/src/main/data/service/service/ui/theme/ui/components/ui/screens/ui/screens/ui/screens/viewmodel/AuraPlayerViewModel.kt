package com.luxury.auraplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.luxury.auraplayer.data.AudioRepository
import com.luxury.auraplayer.data.AudioTrack
import com.luxury.auraplayer.service.AudioDspState
import com.luxury.auraplayer.service.AuraAudioEffectsEngine
import com.luxury.auraplayer.service.AuraMediaService
import com.luxury.auraplayer.service.AuraMediaService.Companion.toMediaItem
import com.luxury.auraplayer.service.EqPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AuraPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application.applicationContext)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressPollingJob: Job? = null

    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracks: StateFlow<List<AudioTrack>> = _tracks.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_ALL)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    val dspState: StateFlow<AudioDspState> = AuraAudioEffectsEngine.dspState
    val visualizerMagnitudes: StateFlow<List<Float>> = AuraAudioEffectsEngine.visualizerMagnitudes
    val sleepTimerRemainingMs: StateFlow<Long?> = AuraMediaService.sleepTimerRemainingMs

    init {
        _favoriteIds.value = repository.getFavoriteTrackIds()
        initializeMediaController()
        refreshAudioLibrary()
    }

    /**
     * الاتصال بخدمة التشغيل في الخلفية (AuraMediaService) لضمان استمرار الموسيقى حتى بعد إغلاق الواجهة
     */
    private fun initializeMediaController() {
        val context = getApplication<Application>().applicationContext
        val sessionToken = SessionToken(context, ComponentName(context, AuraMediaService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        viewModelScope.launch {
            try {
                val controller = future.await()
                mediaController = controller
                setupPlayerListener(controller)
                syncControllerState(controller)
            } catch (_: Exception) {
            }
        }
    }

    private fun setupPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressPolling()
                } else {
                    stopProgressPolling()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId?.toLongOrNull()
                if (mediaId != null) {
                    _currentTrack.value = _tracks.value.find { it.id == mediaId }
                }
                _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffleEnabled.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
        })
    }

    private fun syncControllerState(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _isShuffleEnabled.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode
        _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)

        val currentMediaId = controller.currentMediaItem?.mediaId?.toLongOrNull()
        if (currentMediaId != null && _tracks.value.isNotEmpty()) {
            _currentTrack.value = _tracks.value.find { it.id == currentMediaId }
        }

        if (controller.isPlaying) {
            startProgressPolling()
        }
    }

    /**
     * فحص ذاكرة الهاتف وجلب جميع الأغاني بأسمائها الحقيقية وصور الغلاف المدمجة
     */
    fun refreshAudioLibrary() {
        viewModelScope.launch {
            val loadedTracks = repository.loadAllAudioTracks()
            _tracks.value = loadedTracks

            val controller = mediaController
            val activeMediaId = controller?.currentMediaItem?.mediaId?.toLongOrNull()
            if (activeMediaId != null) {
                _currentTrack.value = loadedTracks.find { it.id == activeMediaId }
            } else if (_currentTrack.value == null && loadedTracks.isNotEmpty()) {
                _currentTrack.value = loadedTracks.first()
            }
        }
    }

    /**
     * تشغيل أغنية محددة مع تمرير القائمة الكاملة للمشغل لدعم الانتقال التلقائي والـ Crossfade
     */
    fun playTrack(selectedTrack: AudioTrack) {
        val controller = mediaController ?: return
        val currentList = _tracks.value
        if (currentList.isEmpty()) return

        _currentTrack.value = selectedTrack
        val targetIndex = currentList.indexOfFirst { it.id == selectedTrack.id }.coerceAtLeast(0)

        // التحقق مما إذا كانت القائمة محملة مسبقاً في ExoPlayer
        val isSameQueue = controller.mediaItemCount == currentList.size &&
                controller.getMediaItemAt(targetIndex).mediaId == selectedTrack.id.toString()

        if (!isSameQueue) {
            val mediaItems = currentList.map { it.toMediaItem() }
            controller.setMediaItems(mediaItems, targetIndex, 0L)
            controller.prepare()
        } else {
            controller.seekToDefaultPosition(targetIndex)
        }

        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) {
            val fallbackTrack = _currentTrack.value ?: _tracks.value.firstOrNull() ?: return
            playTrack(fallbackTrack)
            return
        }

        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        }
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3500L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs.coerceAtLeast(0L))
        _currentPositionMs.value = positionMs.coerceAtLeast(0L)
    }

    fun toggleFavorite(trackId: Long) {
        _favoriteIds.value = repository.toggleFavorite(trackId)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
        controller.repeatMode = nextMode
    }

    fun setSleepTimer(minutes: Int) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putInt(AuraMediaService.EXTRA_SLEEP_MINUTES, minutes)
        }
        controller.sendCustomCommand(
            SessionCommand(AuraMediaService.CMD_SET_SLEEP_TIMER, Bundle.EMPTY),
            args
        )
    }

    // التحكم بمعادل الصوت 10-Band EQ والمؤثرات الصوتية
    fun setDspEnabled(enabled: Boolean) = AuraAudioEffectsEngine.setDspEnabled(enabled)
    fun applyPreset(preset: EqPreset) = AuraAudioEffectsEngine.applyPreset(preset)
    fun setBandGain(bandIndex: Int, gainDb: Float) = AuraAudioEffectsEngine.setBandGain(bandIndex, gainDb)
    fun setBassBoost(strength: Float) = AuraAudioEffectsEngine.setBassBoost(strength)
    fun setVirtualizer(strength: Float) = AuraAudioEffectsEngine.setVirtualizer(strength)
    fun setCrossfadeDuration(seconds: Int) = AuraAudioEffectsEngine.setCrossfadeDuration(seconds)

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                    }
                }
                delay(100L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollingJob?.cancel()
    }

    override fun onCleared() {
        stopProgressPolling()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
