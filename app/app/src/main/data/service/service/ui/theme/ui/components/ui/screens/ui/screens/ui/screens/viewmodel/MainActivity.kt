package com.luxury.auraplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.luxury.auraplayer.ui.components.MiniPlayer
import com.luxury.auraplayer.ui.screens.EqualizerSheet
import com.luxury.auraplayer.ui.screens.MainScreen
import com.luxury.auraplayer.ui.screens.NowPlayingScreen
import com.luxury.auraplayer.ui.theme.AuraBlack
import com.luxury.auraplayer.ui.theme.AuraPlayerTheme
import com.luxury.auraplayer.viewmodel.AuraPlayerViewModel

class MainActivity : ComponentActivity() {

    private val playerViewModel: AuraPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تفعيل العرض الكامل Edge-to-Edge مع شريط حالة مظلم ملكي
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Black.toArgb())
        )

        setContent {
            AuraPlayerTheme {
                RequestAndroid16Permissions(
                    onPermissionsResult = {
                        playerViewModel.refreshAudioLibrary()
                    }
                )

                AuraLuxuryAppContainer(viewModel = playerViewModel)
            }
        }
    }
}

@Composable
private fun RequestAndroid16Permissions(
    onPermissionsResult: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsResult()
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            onPermissionsResult()
        }
    }
}

@Composable
private fun AuraLuxuryAppContainer(
    viewModel: AuraPlayerViewModel
) {
    val tracks by viewModel.tracks.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val dspState by viewModel.dspState.collectAsState()
    val visualizerMagnitudes by viewModel.visualizerMagnitudes.collectAsState()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsState()

    var isNowPlayingExpanded by rememberSaveable { mutableStateOf(false) }
    var isEqualizerVisible by rememberSaveable { mutableStateOf(false) }

    // دعم زر الرجوع في أندرويد 16 لإغلاق النوافذ المفتوحة بنعومة
    BackHandler(enabled = isEqualizerVisible || isNowPlayingExpanded) {
        when {
            isEqualizerVisible -> isEqualizerVisible = false
            isNowPlayingExpanded -> isNowPlayingExpanded = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. الشاشة الرئيسية ومكتبة الأغاني
        MainScreen(
            tracks = tracks,
            currentTrack = currentTrack,
            favoriteIds = favoriteIds,
            onTrackClick = { selectedTrack ->
                viewModel.playTrack(selectedTrack)
            },
            onToggleFavorite = { trackId ->
                viewModel.toggleFavorite(trackId)
            },
            onOpenEqualizer = {
                isEqualizerVisible = true
            }
        )

        // 2. المشغل المصغر العائم في أسفل الشاشة (MiniPlayer)
        AnimatedVisibility(
            visible = currentTrack != null && !isNowPlayingExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MiniPlayer(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                playbackPositionMs = currentPositionMs,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onNextClick = { viewModel.skipToNext() },
                onExpandClick = { isNowPlayingExpanded = true }
            )
        }

        // 3. شاشة المشغل السينمائية الكاملة (Now Playing Screen) بحركة انزلاق 120Hz
        AnimatedVisibility(
            visible = isNowPlayingExpanded && currentTrack != null,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            currentTrack?.let { activeTrack ->
                NowPlayingScreen(
                    track = activeTrack,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    isFavorite = favoriteIds.contains(activeTrack.id),
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    visualizerMagnitudes = visualizerMagnitudes,
                    sleepTimerRemainingMs = sleepTimerRemainingMs,
                    onCollapse = { isNowPlayingExpanded = false },
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.skipToNext() },
                    onPrevious = { viewModel.skipToPrevious() },
                    onSeekTo = { targetMs -> viewModel.seekTo(targetMs) },
                    onToggleFavorite = { viewModel.toggleFavorite(activeTrack.id) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleRepeat = { viewModel.toggleRepeatMode() },
                    onOpenEqualizer = { isEqualizerVisible = true },
                    onSetSleepTimer = { minutes -> viewModel.setSleepTimer(minutes) }
                )
            }
        }

        // 4. لوحة معادل الصوت الاحترافي (10-Band EQ & Master DSP)
        AnimatedVisibility(
            visible = isEqualizerVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            EqualizerSheet(
                dspState = dspState,
                onToggleEnabled = { enabled -> viewModel.setDspEnabled(enabled) },
                onSelectPreset = { preset -> viewModel.applyPreset(preset) },
                onBandGainChanged = { index, gain -> viewModel.setBandGain(index, gain) },
                onBassBoostChanged = { strength -> viewModel.setBassBoost(strength) },
                onVirtualizerChanged = { strength -> viewModel.setVirtualizer(strength) },
                onCrossfadeChanged = { seconds -> viewModel.setCrossfadeDuration(seconds) },
                onClose = { isEqualizerVisible = false }
            )
        }
    }
}
