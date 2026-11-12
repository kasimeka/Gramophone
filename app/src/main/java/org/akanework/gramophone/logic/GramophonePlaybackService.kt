/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioDeviceInfo
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.HeartRating
import androidx.media3.common.IllegalSeekPositionException
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.Log
import androidx.media3.common.util.Util
import androidx.media3.common.util.Util.isBitmapFactorySupportedMimeType
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.addToCommandQueueThenFlush
import androidx.preference.PreferenceManager
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaConstants
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.guava.await
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.queue.ActiveQueuePersistence
import org.akanework.gramophone.logic.queue.Queue
import org.akanework.gramophone.logic.queue.QueueBookmark
import org.akanework.gramophone.logic.queue.QueueDeduplicationRules
import org.akanework.gramophone.logic.queue.QueueEditOperation
import org.akanework.gramophone.logic.queue.QueueEditResult
import org.akanework.gramophone.logic.queue.QueueOrderEditor
import org.akanework.gramophone.logic.queue.QueuePlaybackAction
import org.akanework.gramophone.logic.queue.QueuePlaybackRules
import org.akanework.gramophone.logic.queue.QueuePlaybackState
import org.akanework.gramophone.logic.queue.QueueRepeatMode
import org.akanework.gramophone.logic.queue.QueueRepository
import org.akanework.gramophone.logic.queue.QueueRestoreRules
import org.akanework.gramophone.logic.queue.QueueSource
import org.akanework.gramophone.logic.queue.QueueSourceKind
import org.akanework.gramophone.logic.queue.QueueTitles
import org.akanework.gramophone.logic.queue.StoredQueueOrderTarget
import org.akanework.gramophone.logic.ui.MeiZuLyricsMediaNotificationProvider
import org.akanework.gramophone.logic.ui.isManualNotificationUpdate
import org.akanework.gramophone.logic.utils.AfFormatInfo
import org.akanework.gramophone.logic.utils.AfFormatTracker
import org.akanework.gramophone.logic.utils.AudioTrackInfo
import org.akanework.gramophone.logic.utils.BtCodecInfo
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.logic.utils.LrcUtils.LrcParserOptions
import org.akanework.gramophone.logic.utils.LrcUtils.extractAndParseLyrics
import org.akanework.gramophone.logic.utils.LrcUtils.loadAndParseLyricsFile
import org.akanework.gramophone.logic.utils.MediaItemList
import org.akanework.gramophone.logic.utils.MediaRowCodec
import org.akanework.gramophone.logic.utils.ReplayGainAudioProcessor
import org.akanework.gramophone.logic.utils.ReplayGainUtil
import org.akanework.gramophone.logic.utils.SemanticLyrics

import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneExtractorsFactory
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneMediaSourceFactory
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneRenderFactory
import org.akanework.gramophone.logic.utils.exoplayer.PlayerQueueOrderTarget
import org.akanework.gramophone.ui.AudioPreviewActivity
import org.akanework.gramophone.ui.LyricWidgetProvider
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.compose.MqState.Companion.CLIENT_QB_REFRESH_ALL
import org.akanework.gramophone.ui.fragments.compose.MqState.Companion.CLIENT_QB_REFRESH_CLEAR
import org.akanework.gramophone.ui.fragments.compose.MqState.Companion.CLIENT_QB_REFRESH_ITEM
import org.akanework.gramophone.ui.fragments.compose.MqState.Companion.CLIENT_QB_REFRESH_LIST
import org.akanework.gramophone.ui.fragments.compose.MqState.Companion.CLIENT_QB_REFRESH_QUEUES
import uk.akane.libphonograph.items.albumId
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.collections.emptyList
import kotlin.collections.map
import kotlin.collections.plus


/**
 * [GramophonePlaybackService] is a server service.
 * It's using exoplayer2 as its player backend.
 */
class GramophonePlaybackService : MediaLibraryService(), MediaSessionService.Listener,
    MediaLibrarySession.Callback, Player.Listener, AnalyticsListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "GramoPlaybackService"
        const val NOTIFY_CHANNEL_ID = "serviceFgsError"
        const val NOTIFY_ID = 1
        private const val FAVE_ID = 2
        private const val PENDING_INTENT_SESSION_ID = 0
        const val PENDING_INTENT_NOTIFY_ID = 1
        const val PENDING_INTENT_WIDGET_ID = 2
        const val PENDING_INTENT_FAVE_ID = 3

        const val SERVICE_SET_TIMER = "set_timer"
        const val SERVICE_QUERY_TIMER = "query_timer"
        const val SERVICE_GET_AUDIO_FORMAT = "get_audio_format"
        const val SERVICE_GET_LYRICS = "get_lyrics"
        const val SERVICE_TIMER_CHANGED = "changed_timer"
        const val SERVICE_SET_MEDIA_ITEMS_SEAMLESSLY = "set_media_items_seamlessly"
        const val SERVICE_SET_MEDIA_ITEMS_ATOMIC = "set_media_items_atomic"

        const val SERVICE_QB_GET_INACTIVE_LIST = "qb_get_inactive_list"
        const val SERVICE_QB_LOAD_QUEUE = "qb_load"
        const val SERVICE_QB_PLAY_QUEUE = "qb_play_queue"
        const val SERVICE_QB_GET_QUEUE_FOR_UI = "qb_get_queue_for_ui"
        const val SERVICE_QB_DEL = "qb_delete"
        const val SERVICE_QB_EDIT = "qb_edit"
        const val SERVICE_QB_SHUFFLE = "qb_shuffle"
        const val SERVICE_QB_UNDO_EDIT = "qb_undo_edit"
        const val SERVICE_QB_CLEAR_EDIT_UNDO = "qb_clear_edit_undo"
        const val SERVICE_QB_RENAME_QUEUE = "qb_rename"

        var instanceForWidgetAndLyricsOnly: GramophonePlaybackService? = null
    }

    private var lastSessionId = 0
    private val internalPlaybackThread =
        HandlerThread("ExoPlayer:Playback", Process.THREAD_PRIORITY_AUDIO)
    private var mediaSession: MediaLibrarySession? = null
    val endedWorkaroundPlayer
        get() = mediaSession?.player as EndedWorkaroundPlayer?

    private lateinit var libraryTreeLoader: LibraryTreeLoader

    private var controller: MediaBrowser? = null
    private var controllerFuture: ListenableFuture<MediaBrowser>? = null

    private var lastQueueEditUndo: QueueEditUndoState? = null
    private data class QueueEditUndoState(
        val queueUuid: UUID,
        val operation: QueueEditOperation,
        val displayedOrder: List<Int>,
        val mediaItems: List<MediaItem>,
        val removedUnderlyingIndex: Int? = null,
    )
    private var lastInactiveQueueEditUndo: InactiveQueueEditUndoState? = null
    private data class InactiveQueueEditUndoState(
        val queueUuid: UUID,
        val rows: List<String>,
        val bookmark: QueueBookmark,
    )
    private val sendLyrics = Runnable { scheduleSendingLyrics(false) }
    var lyrics: SemanticLyrics? = null
        private set
    val syncedLyrics
        get() = lyrics as? SemanticLyrics.SyncedLyrics
    private lateinit var customCommands: List<CommandButton>
    private lateinit var handler: Handler
    private lateinit var mainExecutor: Executor
    private lateinit var playbackHandler: Handler
    private lateinit var nm: NotificationManagerCompat
    private lateinit var activeQueuePersistence: ActiveQueuePersistence
    /** Maps the legacy queue-board ids used by the sheet to durable queue identities. */
    private val queueIds = mutableMapOf<Long, UUID>()
    /** Reverse map, so a durable queue keeps one sheet id for the lifetime of the process. */
    private val queueBoardIds = mutableMapOf<UUID, Long>()
    private val queueRepository: QueueRepository
        get() = gramophoneApplication.queueRepository
    private lateinit var prefs: SharedPreferences
    private var lastSentHighlightedLyric: String? = null
    private var lastSentNotificationLyric: String? = null
    private lateinit var afFormatTracker: AfFormatTracker
    private lateinit var rgAp: ReplayGainAudioProcessor
    private var rgMode = 0 // 0 = disabled, 1 = track, 2 = album, 3 = smart
    private var updatedLyricAtLeastOnce = false
    private val downstreamFormat = hashSetOf<Pair<Any, Pair<Int, Format>>>()
    private val pendingDownstreamFormat = hashSetOf<Pair<Any, Pair<Int, Format>>>()
    private var afTrackFormat: Pair<Any, AfFormatInfo>? = null
    private val pendingAfTrackFormats = hashMapOf<Any, AfFormatInfo>()
    private var audioSinkInputFormat: Format? = null
    private var audioTrackInfo: AudioTrackInfo? = null
    private var audioTrackInfoCounter = 0
    private var audioTrackReleaseCounter = 0

    // only used for formats where this is significant for quality, but not in header (opus)
    private var bitrate: Int? = null
    private var btInfo: BtCodecInfo? = null
    private var proxy: BtCodecInfo.Companion.Proxy? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val queueResolutionMutex = Mutex()
    private var suppressUserModifiedTimelineDepth = 0
    private val lastPlaylistLoaded = CompletableDeferred<Unit>()
    private val lyricsFetcher = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private val bitrateFetcher = CoroutineScope(Dispatchers.IO.limitedParallelism(1))

    private fun getRepeatCommand() =
        when (controller!!.repeatMode) {
            Player.REPEAT_MODE_OFF -> customCommands[0]
            Player.REPEAT_MODE_ALL -> customCommands[1]
            Player.REPEAT_MODE_ONE -> customCommands[2]
            else -> throw IllegalArgumentException()
        }

    private fun getFavoriteCommand(): CommandButton {
        val isFavorite = (controller?.currentMediaItem?.mediaMetadata?.userRating as? HeartRating)?.isHeart == true
        return if (isFavorite) {
            customCommands[4]
        } else {
            customCommands[3]
        }
    }

    private val timer: Runnable = Runnable {
        if (timerPauseOnEnd) {
            endedWorkaroundPlayer!!.exoPlayer.pauseAtEndOfMediaItems = true
        } else {
            controller!!.pause()
        }
        timerDuration = null
    }
    private var timerPauseOnEnd = false
    private var timerDuration: Long? = null
        set(value) {
            field = value
            if (value != null && value > 0) {
                handler.postDelayed(timer, value - SystemClock.elapsedRealtime())
            } else {
                handler.removeCallbacks(timer)
            }
            mediaSession!!.broadcastCustomCommand(
                SessionCommand(SERVICE_TIMER_CHANGED, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }

    private val seekReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val to =
                intent.extras?.getLong("seekTo", C.INDEX_UNSET.toLong()) ?: C.INDEX_UNSET.toLong()
            if (to != C.INDEX_UNSET.toLong())
                controller?.seekTo(to)
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action.equals("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED") &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O /* before 8, only sbc was supported */
            ) {
                btInfo = BtCodecInfo.fromCodecConfig(
                    @SuppressLint("NewApi") IntentCompat.getParcelableExtra(
                        intent,
                        "android.bluetooth.extra.CODEC_STATUS",
                        BluetoothCodecStatus::class.java
                    )?.codecConfig
                )
                Log.d(TAG, "new bluetooth codec config $btInfo")
            }
        }
    }

    override fun onCreate() {
        Log.i(TAG, "+onCreate()")
        super.onCreate()
        instanceForWidgetAndLyricsOnly = this
        internalPlaybackThread.start()
        playbackHandler = Handler(internalPlaybackThread.looper)
        handler = Handler(Looper.getMainLooper())
        mainExecutor = ContextCompat.getMainExecutor(this)
        nm = NotificationManagerCompat.from(this)
        prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        setListener(this)
        setMediaNotificationProvider(
            MeiZuLyricsMediaNotificationProvider(this) { lastSentHighlightedLyric }
        )
        setForegroundServiceTimeoutMs(120000)
        setShowNotificationForEmptyPlayer(SHOW_NOTIFICATION_FOR_EMPTY_PLAYER_AFTER_STOP_OR_ERROR)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(
                NOTIFY_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH
            ).apply {
                setName(getString(R.string.error_in_bg))
                setVibrationEnabled(true)
                setVibrationPattern(longArrayOf(0L, 200L))
                setLightsEnabled(false)
                setShowBadge(false)
                setSound(null, null)
            }.build()
        )

        customCommands =
            listOf(
                CommandButton.Builder(CommandButton.ICON_REPEAT_OFF) // repeat currently disabled, click will repeat all
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_ALL)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_REPEAT_ALL) // repeat all currently enabled, click will repeat one
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_ONE)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_REPEAT_ONE) // repeat one currently enabled, click will disable
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_OFF)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_HEART_UNFILLED) // not favorite, click will favorite
                    .setDisplayName(getString(R.string.favorite))
                    .setSessionCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING), HeartRating(true))
                    .build(),
                CommandButton.Builder(CommandButton.ICON_HEART_FILLED) // favorite, click will unfavorite
                    .setDisplayName(getString(R.string.unfavorite))
                    .setSessionCommand(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING), HeartRating(false))
                    .build(),
            )
        afFormatTracker = AfFormatTracker(this, playbackHandler, handler)
        afFormatTracker.formatChangedCallback = { format, period ->
            if (period != null) {
                handler.post {
                    val currentPeriod = endedWorkaroundPlayer?.exoPlayer?.currentPeriodIndex
                        ?.takeIf { it != C.INDEX_UNSET && (endedWorkaroundPlayer?.exoPlayer
                            ?.currentTimeline?.periodCount ?: 0) > it }?.let {
                                endedWorkaroundPlayer!!.exoPlayer.currentTimeline
                                    .getUidOfPeriod(it) }
                    if (currentPeriod != period) {
                        if (format != null) {
                            pendingAfTrackFormats[period] = format
                        } else {
                            pendingAfTrackFormats.remove(period)
                        }
                    } else {
                        afTrackFormat = format?.let { period to it }
                        mediaSession?.broadcastCustomCommand(
                            SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                            Bundle.EMPTY
                        )
                    }
                }
            } else {
                Log.e(TAG, "mediaPeriodId is NULL in formatChangedCallback!!")
            }
        }
        rgAp = ReplayGainAudioProcessor()
        prefs.registerOnSharedPreferenceChangeListener(this)
        onSharedPreferenceChanged(prefs, null) // read initial values
        val player = EndedWorkaroundPlayer(
            this,
            prefs,
            exoPlayer = ExoPlayer.Builder(
                this,
                GramophoneRenderFactory(
                    this, rgAp, this::onAudioSinkInputFormatChanged,
                    afFormatTracker::setAudioSink
                )
                    .setEnableHighResolutionPcmOutput(true)
                    .setEnableDecoderFallback(true)
                    .setEnableAudioOutputPlaybackParameters(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
                GramophoneMediaSourceFactory(
                    DefaultDataSource.Factory(this),
                    GramophoneExtractorsFactory().also {
                        it.setConstantBitrateSeekingEnabled(true)
                        it.setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
                    })
            )
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true
                )
                .setHandleAudioBecomingNoisy(true)
                .setTrackSelector(DefaultTrackSelector(this).apply {
                    setParameters(
                        buildUponParameters()
                        .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                        .setAudioOffloadPreferences(
                            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                                .apply {
                                    val config =
                                        prefs.getStringStrict("offload", "0")?.toIntOrNull()
                                    if (config != null && config > 0 && Flags.OFFLOAD) {
                                        rgAp.setOffloadEnabled(true)
                                        setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                                        setIsGaplessSupportRequired(config == 2)
                                    }
                                }
                                .build()))
                })
                .setPlaybackLooper(internalPlaybackThread.looper)
                .build(),
            { lyrics },
            getNotificationLyric = { lastSentNotificationLyric }
        )
        player.exoPlayer.addAnalyticsListener(EventLogger())
        player.exoPlayer.addAnalyticsListener(afFormatTracker)
        player.exoPlayer.addAnalyticsListener(this)
        activeQueuePersistence = ActiveQueuePersistence(queueRepository, player)
        libraryTreeLoader = LibraryTreeLoader(
            this,
            gramophoneApplication,
            lifecycleScope,
            prefs
        )

        mediaSession =
            MediaLibrarySession
                .Builder(this, player, this)
                // CacheBitmapLoader is required for MeiZuLyricsMediaNotificationProvider
                .setBitmapLoader(CacheBitmapLoader(object : BitmapLoader {
                    // Coil-based bitmap loader to reuse Coil's caching and to make sure we use
                    // the same cover art as the rest of the app, ie MediaStore's cover

                    private val limit by lazy { MediaSession.getBitmapDimensionLimit(this@GramophonePlaybackService) }

                    // the suppression is correct, we want identity of the byte array as it will
                    // stay the same over one song's lifetime
                    @Suppress("KotlinArrayHashCode")
                    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
                        return CallbackToFutureAdapter.getFuture { completer ->
                            imageLoader.enqueue(
                                ImageRequest.Builder(this@GramophonePlaybackService)
                                    .data(data)
                                    .memoryCacheKey(data.hashCode().toString())
                                    .size(limit, limit)
                                    .allowHardware(false)
                                    .target(
                                        onStart = { _ ->
                                            // We don't need or want a placeholder.
                                        },
                                        onSuccess = { result ->
                                            completer.set((result as BitmapImage).bitmap)
                                        },
                                        onError = { _ ->
                                            completer.setException(
                                                Exception(
                                                    "coil onError called for byte array"
                                                )
                                            )
                                        }
                                    )
                                    .build())
                                .also {
                                    completer.addCancellationListener(
                                        { it.dispose() },
                                        mainExecutor
                                    )
                                }
                            "coil load for ${data.hashCode()}"
                        }
                    }

                    override fun loadBitmap(
                        uri: Uri
                    ): ListenableFuture<Bitmap> {
                        return CallbackToFutureAdapter.getFuture { completer ->
                            imageLoader.enqueue(
                                ImageRequest.Builder(this@GramophonePlaybackService)
                                    .data(uri)
                                    .size(limit, limit)
                                    .allowHardware(false)
                                    .target(
                                        onStart = { _ ->
                                            // We don't need or want a placeholder.
                                        },
                                        onSuccess = { result ->
                                            completer.set((result as BitmapImage).bitmap)
                                        },
                                        onError = { _ ->
                                            completer.setException(
                                                Exception(
                                                    "coil onError called" +
                                                            " (normal if no album art exists)"
                                                )
                                            )
                                        }
                                    )
                                    .build())
                                .also {
                                    completer.addCancellationListener(
                                        { it.dispose() },
                                        mainExecutor
                                    )
                                }
                            "coil load for $uri"
                        }
                    }

                    override fun supportsMimeType(mimeType: String): Boolean {
                        return isBitmapFactorySupportedMimeType(mimeType)
                    }

                    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
                        return metadata.artworkUri?.let { loadBitmap(it) }
                    }
                }))
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        PENDING_INTENT_SESSION_ID,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .setSystemUiPlaybackResumptionOptIn(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                // Workaround for AA bug where content cannot be scrolled (androidx/media#2192)
                .setPeriodicPositionUpdateEnabled(false)
                .build()
        addSession(mediaSession!!)
        val future = MediaBrowser.Builder(this, mediaSession!!.token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (future.isCancelled) return@addListener
            val browser = try {
                future.get()
            } catch (e: Exception) {
                Log.e(TAG, "failed to connect internal controller", e)
                return@addListener
            }
            if (controllerFuture != future || mediaSession == null) {
                browser.release()
                return@addListener
            }
            controller = browser
            browser.addListener(this)
            if (browser.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                onAudioSessionIdChanged(browser.audioSessionId)
            }
        }, mainExecutor)
        ContextCompat.registerReceiver(
            this,
            seekReceiver,
            IntentFilter("$packageName.SEEK_TO"),
            @SuppressLint("WrongConstant") // why is this needed?
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            btReceiver,
            IntentFilter("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"),
            @SuppressLint("WrongConstant") // why is this needed?
            ContextCompat.RECEIVER_EXPORTED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O /* before 8, only sbc was supported */) {
            proxy = BtCodecInfo.getCodec(this) {
                Log.d(TAG, "first bluetooth codec config $btInfo")
                btInfo = it
                mediaSession?.broadcastCustomCommand(
                    SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                    Bundle.EMPTY
                )
            }
        }
        scope.launch {
            val restored = activeQueuePersistence.readRestored()
            if (mediaSession == null) return@launch
            withContext(Dispatchers.Main) {
                if (restored == null) {
                    lastPlaylistLoaded.complete(Unit)
                    return@withContext
                }
                if ((endedWorkaroundPlayer?.mediaItemCount ?: 0) > 0) {
                    // Something already loaded a queue while the database was read.
                    lastPlaylistLoaded.complete(Unit)
                    return@withContext
                }
                val list = mapMediaItemsForFavorites(restored.items)
                try {
                    withoutMarkingUserModified {
                        endedWorkaroundPlayer?.setMediaItems(
                            list,
                            restored.startRow,
                            restored.positionMs,
                            restored.queue.title,
                            restored.ended,
                            restored.queue.repeatMode.value,
                            null,
                        )
                        activeQueuePersistence.adopt(restored.queue)
                        endedWorkaroundPlayer?.currentQueueId?.let {
                            registerQueueId(it, restored.queue.id)
                        }
                        activeQueuePersistence.syncMirroredRowsFromPlayer()
                    }
                } catch (e: IllegalSeekPositionException) {
                    Log.e(TAG, "failed to restore", e)
                    lastPlaylistLoaded.complete(Unit)
                }
                if (mediaSession?.connectedControllers?.find {
                        it.connectionHints.getBoolean("PrepareWhenReady", false)
                    } != null) {
                    handler.post { endedWorkaroundPlayer?.prepare() }
                }
            }
        }
        scope.launch(Dispatchers.Default) {
            gramophoneApplication.favoritesStore.ids.collect { ids ->
                withContext(Dispatchers.Main + NonCancellable) {
                    controller?.let { controller ->
                        for (i in 0..<controller.mediaItemCount) {
                            val item = controller.getMediaItemAt(i)
                            val isHeart = (item.mediaMetadata.userRating as? HeartRating)
                                ?.isHeart == true
                            val shouldBeHeart = ids.contains(item.mediaId)
                            if (isHeart != shouldBeHeart ||
                                item.mediaMetadata.userRating !is HeartRating
                            ) {
                                withoutMarkingUserModified {
                                    controller.replaceMediaItem(
                                        i, item
                                            .buildUpon().setMediaMetadata(
                                                item.mediaMetadata.buildUpon()
                                                    .setUserRating(HeartRating(shouldBeHeart))
                                                    .build()
                                            ).build()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Log.i(TAG, "-onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var extras = intent?.extras
        // Deserialize all extras to be able to log them.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            extras = extras?.deepCopy()
        } else {
            if (extras != null) {
                for (i in extras.keySet()) {
                    @Suppress("deprecation") extras.get(i)
                }
            }
        }
        Log.i(TAG, "onStartCommand(): $intent, ${extras?.toString()}")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaId: String,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        if (rating !is HeartRating) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
        }
        gramophoneApplication.favoritesStore.setFavorite(listOf(mediaId), rating.isHeart)
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        val mediaItemId =
            this.controller?.currentMediaItem?.mediaId ?: return Futures.immediateFuture(
                SessionResult(SessionError.ERROR_INVALID_STATE)
            )
        return onSetRating(session, controller, mediaItemId, rating)
    }

    // When destroying, we should release server side player
    // alongside with the mediaSession.
    override fun onDestroy() {
        Log.i(TAG, "+onDestroy()")
        instanceForWidgetAndLyricsOnly = null
        unregisterReceiver(seekReceiver)
        unregisterReceiver(btReceiver)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        // Important: this must happen before sending stop() as that changes state ENDED -> IDLE
        activeQueuePersistence.stopProgressSaving(save = false)
        activeQueuePersistence.saveNow()
        scope.cancel()
        endedWorkaroundPlayer!!.stop()
        handler.removeCallbacks(timer)
        handler.removeCallbacks(sendLyrics)
        mediaSession!!.setOptOutOfMediaButtonPlaybackResumption(
            controller?.currentTimeline?.isEmpty ?: true
        )
        proxy?.let {
            it.adapter.closeProfileProxy(BluetoothProfile.A2DP, it.a2dp)
        }
        val future = controllerFuture
        controllerFuture = null
        if (future != null && !future.isDone) {
            future.cancel(true)
        }
        controller?.release()
        controller = null
        if (future != null && future.isDone && !future.isCancelled) {
            try {
                val browser = future.get()
                if (browser !== controller) {
                    browser.release()
                }
            } catch (_: Exception) {
            }
        }
        mediaSession!!.release()
        endedWorkaroundPlayer!!.release()
        mediaSession = null
        broadcastAudioSessionClose()
        LyricWidgetProvider.update(this)
        internalPlaybackThread.quitSafely()
        super.onDestroy()
        Log.i(TAG, "-onDestroy()")
    }

    // This onGetSession is a necessary method override needed by
    // MediaSessionService.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    // Configure commands available to the controller in onConnect()
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo)
            : MediaSession.ConnectionResult {
        Log.i(TAG, "onConnect(): $controller")
        val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        val availableSessionCommands =
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
        if (session.isMediaNotificationController(controller)
            || session.isAutoCompanionController(controller)
            || session.isAutomotiveController(controller)
        ) {
            if (this.controller?.currentTimeline?.isEmpty == false) {
                builder.setMediaButtonPreferences(
                    ImmutableList.of(
                        getRepeatCommand(),
                        getFavoriteCommand()
                    )
                )
            }
        }
        if (controller.connectionHints.getBoolean("PrepareWhenReady", false) &&
            endedWorkaroundPlayer?.currentTimeline?.isEmpty == false
        ) {
            handler.post { this.controller?.prepare() }
        }
        availableSessionCommands.add(SessionCommand.COMMAND_CODE_SESSION_SET_RATING)
        availableSessionCommands.add(SessionCommand(SERVICE_SET_TIMER, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QUERY_TIMER, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_SET_MEDIA_ITEMS_SEAMLESSLY, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_SET_MEDIA_ITEMS_ATOMIC, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QB_GET_INACTIVE_LIST, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QB_GET_QUEUE_FOR_UI, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QB_LOAD_QUEUE, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QB_PLAY_QUEUE, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QB_DEL, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QB_EDIT, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QB_SHUFFLE, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QB_UNDO_EDIT, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QB_CLEAR_EDIT_UNDO, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QB_RENAME_QUEUE, Bundle.EMPTY))
        return builder.setAvailableSessionCommands(availableSessionCommands.build()).build()
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        Log.i(TAG, "onPostConnect(): $controller")
        session.sendCustomCommand(
            controller,
            SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY),
            Bundle.EMPTY
        )
        session.sendCustomCommand(
            controller,
            SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        Log.i(TAG, "onDisconnected(): $controller")
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        var restart = false
        if (key == null || key == "notification_lyrics" || key == "status_bar_lyrics") {
            scheduleSendingLyrics(false)
            endedWorkaroundPlayer?.updateLyricNow()
        }
        if (key == null || key == "rg_mode") {
            rgMode = prefs.getStringStrict("rg_mode", "0")!!.toInt()
            restart = !computeRgMode(true)
        }
        if (key == null || key == "rg_drc") {
            val drc = prefs.getBooleanStrict("rg_drc", true)
            restart = !rgAp.setReduceGain(!drc) || restart
        }
        if (key == null || key == "rg_rg_gain") {
            val rgGain = prefs.getIntStrict("rg_rg_gain", 19)
            restart = !rgAp.setRgGain(rgGain - 15) || restart
        }
        if (key == null || key == "rg_no_rg_gain") {
            val nonRgGain = prefs.getIntStrict("rg_no_rg_gain", 0)
            restart = !rgAp.setNonRgGain(-nonRgGain) || restart
        }
        if (key == null || key == "rg_boost_gain") {
            val boostGain = prefs.getIntStrict("rg_boost_gain", 0)
            restart = !rgAp.setBoostGain(boostGain) || restart
        }
        if (restart) {
            controller?.stop()
            controller?.prepare()
        }
    }

    private fun computeRgMode(force: Boolean): Boolean {
        return rgAp.setMode(
            when (rgMode) {
                0 -> ReplayGainUtil.Mode.None
                1 -> ReplayGainUtil.Mode.Track
                2 -> ReplayGainUtil.Mode.Album
                3 -> {
                    val item = controller?.currentMediaItem
                    val idx = controller?.currentMediaItemIndex ?: 0
                    val count = controller?.mediaItemCount ?: 0
                    val next = if (idx + 1 >= count) null else
                        controller?.getMediaItemAt(idx + 1)
                    val prev = if (idx - 1 < 0 || count == 0) null else
                        controller?.getMediaItemAt(idx - 1)
                    if (item != null && (item.mediaMetadata.albumId == next?.mediaMetadata?.albumId ||
                                item.mediaMetadata.albumId == prev?.mediaMetadata?.albumId)
                    )
                        ReplayGainUtil.Mode.Album
                    else ReplayGainUtil.Mode.Track
                }

                else -> throw IllegalArgumentException("invalid rg mode $rgMode")
            }, !force
        )
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (audioSessionId != lastSessionId) {
            broadcastAudioSessionClose()
            lastSessionId = audioSessionId
            broadcastAudioSession()
        }
    }

    private fun broadcastAudioSession() {
        if (lastSessionId != 0) {
            Log.i(TAG, "broadcast audio session open: $lastSessionId")
            sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, lastSessionId)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            })
        } else {
            Log.e(TAG, "session id is 0? why????? THIS MIGHT BREAK EQUALIZER")
        }
    }

    private fun broadcastAudioSessionClose() {
        if (lastSessionId != 0) {
            Log.i(TAG, "broadcast audio session close: $lastSessionId")
            sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, lastSessionId)
            })
            lastSessionId = 0
        }
    }

    private fun activeQueueMediaItems(player: EndedWorkaroundPlayer): List<MediaItem> =
        (0 until player.exoPlayer.mediaItemCount).map { player.exoPlayer.getMediaItemAt(it) }

    private inline fun <T> withoutMarkingUserModified(block: () -> T): T {
        suppressUserModifiedTimelineDepth++
        try {
            return block()
        } finally {
            suppressUserModifiedTimelineDepth--
        }
    }

    /** Create a fresh durable queue for a library play and make it active. */
    private suspend fun createDurableQueue(title: String, source: QueueSource? = null): Queue {
        val created = queueRepository.create(source = source)
        val trimmed = title.trim()
        val queue = if (trimmed.isEmpty() || trimmed == created.title) {
            created
        } else {
            val unique = QueueTitles.uniqueTitle(
                trimmed,
                queueRepository.queues.first().filter { it.id != created.id }.map { it.title },
            )
            queueRepository.mutate(created.id) { it.copy(title = unique) } ?: created
        }
        activeQueuePersistence.activate(queue)
        return queue
    }

    /** Align the legacy player queue id with the durable queue that now owns it. */
    private fun adoptPlayerQueue(queue: Queue) {
        endedWorkaroundPlayer?.currentQueueId?.let { registerQueueId(it, queue.id) }
    }

    private fun registerQueueId(queueId: Long, durableId: UUID) {
        queueIds[queueId] = durableId
        queueBoardIds[durableId] = queueId
    }

    /**
     * The sheet id for a stored queue. The active queue keeps the player's id so existing sheet
     * comparisons remain valid; inactive queues get one stable process-local id.
     */
    private fun queueLongId(queue: Queue): Long {
        val current = endedWorkaroundPlayer?.currentQueueId
        if (queue.id == activeQueuePersistence.activeQueueId() && current != null) {
            registerQueueId(current, queue.id)
            return current
        }
        queueBoardIds[queue.id]?.let { return it }

        val used = queueIds.keys + listOfNotNull(current)
        var candidate = (used.maxOrNull() ?: 0L) + 1
        while (candidate in used) candidate++
        registerQueueId(candidate, queue.id)
        return candidate
    }

    /** The sheet's compatibility view of one durable queue, with its stored single order. */
    private fun toBoardQueue(queue: Queue): MultiQueueObject {
        val items = queue.rows.mapNotNull(MediaRowCodec::decode)
        val bookmark = queue.normalizedBookmark()
        val row = if (items.isEmpty()) 0 else bookmark.row.coerceIn(0, items.lastIndex)
        return MultiQueueObject(
            id = queueLongId(queue),
            title = queue.title,
            queue = ArrayList(items),
            startIndex = row,
            startPositionMs = bookmark.positionMs,
            repeatMode = queue.repeatMode.value,
            ended = bookmark.ended,
        )
    }

    /** Every durable queue except the one loaded in the player, in repository activity order. */
    private suspend fun inactiveQueueSnapshots(): List<MultiQueueObject> {
        val activeId = activeQueuePersistence.activeQueueId()
            ?: endedWorkaroundPlayer?.currentQueueId?.let { queueIds[it] }
        return queueRepository.queues.first()
            .filter { it.id != activeId }
            .map(::toBoardQueue)
    }

    /** Resolve a sheet queue id to its durable queue, or null when it is stale. */
    private suspend fun durableQueueForLong(queueId: Long): Queue? {
        val current = endedWorkaroundPlayer?.currentQueueId
        val durableId = if (queueId == -1L || queueId == current) {
            activeQueuePersistence.activeQueueId()
                ?: current?.let { queueIds[it] }
        } else {
            queueIds[queueId]
        }
        return durableId?.let { queueRepository.queue(it).first() }
    }

    /** Resolve the queue targeted by a command, by durable UUID or by the legacy sheet id. */
    private suspend fun durableQueueForCommand(command: SessionCommand): Queue? {
        val uuid = command.customExtras.getString("queueUuid")?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        return if (uuid != null) {
            queueRepository.queue(uuid).first()
        } else {
            durableQueueForLong(command.customExtras.getLong("queueId"))
        }
    }

    /**
     * Apply one row edit to the active queue. Positions are in the order the queue sheet shows.
     * Returns false without changing anything when the queue identity or a position is stale.
     */
    private suspend fun applyQueueEdit(command: SessionCommand): Boolean {
        val player = endedWorkaroundPlayer ?: return false
        val durable = durableQueueForCommand(command) ?: return false
        val operation = try {
            QueueEditOperation.valueOf(command.customExtras.getString("operation")!!)
        } catch (_: Exception) {
            return false
        }
        val position = command.customExtras.getInt("position")
        val to = command.customExtras.getInt("to")

        return if (isDurableQueueActive(durable.id)) {
            applyActiveQueueEdit(player, durable.id, operation, position, to)
        } else {
            applyInactiveQueueEdit(durable.id, operation, position, to)
        }
    }

    /** Whether [queueUuid] is the queue the player currently holds. */
    private fun isDurableQueueActive(queueUuid: UUID): Boolean =
        queueUuid == activeQueuePersistence.activeQueueId() ||
            endedWorkaroundPlayer?.currentQueueId?.let { queueIds[it] } == queueUuid

    private suspend fun applyActiveQueueEdit(
        player: EndedWorkaroundPlayer,
        queueUuid: UUID,
        operation: QueueEditOperation,
        position: Int,
        to: Int,
    ): Boolean {
        val target = PlayerQueueOrderTarget(player)
        val order = target.displayedOrder()
        if (position !in order.indices) return false

        val mediaItems = activeQueueMediaItems(player)
        when (operation) {
            QueueEditOperation.PLAY_NEXT -> {
                val result = QueueOrderEditor.playNext(order, target.currentPosition(), position)
                if (result !is QueueEditResult.Applied) return false
                lastQueueEditUndo = QueueEditUndoState(
                    queueUuid, operation, order, mediaItems
                )
                target.applyDisplayedOrder(result.order)
            }

            QueueEditOperation.REMOVE -> {
                val result = QueueOrderEditor.remove(order, position)
                if (result !is QueueEditResult.Applied) return false
                lastQueueEditUndo = QueueEditUndoState(
                    queueUuid, operation, order, mediaItems, order[position]
                )
                target.removeDisplayedRow(position)
            }

            QueueEditOperation.MOVE -> {
                val result = QueueOrderEditor.move(order, position, to)
                if (result !is QueueEditResult.Applied) return false
                lastQueueEditUndo = QueueEditUndoState(
                    queueUuid, operation, order, mediaItems
                )
                target.applyDisplayedOrder(result.order)
            }
        }

        lastInactiveQueueEditUndo = null
        if (player.exoPlayer.mediaItemCount == 0) {
            // An empty active queue has no player snapshot to mirror; persist the cleared rows.
            activeQueuePersistence.saveClearedNow(queueUuid)
        } else {
            activeQueuePersistence.saveNow(markModified = true)
        }
        return true
    }

    /**
     * Apply the same single-order edit to an inactive queue through the repository-backed target.
     * The player and the active queue are untouched.
     */
    private suspend fun applyInactiveQueueEdit(
        durableId: UUID,
        operation: QueueEditOperation,
        position: Int,
        to: Int,
    ): Boolean {
        var previous: Queue? = null
        var applied = false

        queueRepository.mutate(durableId) { current ->
            val target = StoredQueueOrderTarget(current)
            val order = target.displayedOrder()
            if (position !in order.indices) return@mutate current
            val result = when (operation) {
                QueueEditOperation.PLAY_NEXT ->
                    QueueOrderEditor.playNext(order, target.currentPosition(), position)

                QueueEditOperation.REMOVE -> QueueOrderEditor.remove(order, position)
                QueueEditOperation.MOVE -> QueueOrderEditor.move(order, position, to)
            }
            if (result !is QueueEditResult.Applied) return@mutate current

            previous = current
            when (operation) {
                QueueEditOperation.REMOVE -> target.removeDisplayedRow(position)
                else -> target.applyDisplayedOrder(result.order)
            }
            applied = true
            target.queue.copy(isModified = true)
        }

        val before = previous ?: return false
        if (!applied) return false
        lastQueueEditUndo = null
        lastInactiveQueueEditUndo = InactiveQueueEditUndoState(
            queueUuid = durableId,
            rows = before.rows,
            bookmark = before.bookmark
        )
        return true
    }

    /**
     * Restore the order captured by the last queue edit. Playback is deliberately left alone:
     * undo is an ordering action, even if the next song already started.
     */
    private suspend fun applyQueueUndo(command: SessionCommand): Boolean {
        val durable = durableQueueForCommand(command) ?: return false
        return if (lastQueueEditUndo?.queueUuid == durable.id) {
            applyActiveQueueUndo(durable.id)
        } else {
            applyInactiveQueueUndo(durable.id)
        }
    }

    private suspend fun applyActiveQueueUndo(queueUuid: UUID): Boolean {
        val player = endedWorkaroundPlayer ?: return false
        val undo = lastQueueEditUndo ?: return false
        if (undo.queueUuid != queueUuid) return false

        val cleared = player.exoPlayer.mediaItemCount == 0
        if (!cleared && !isDurableQueueActive(queueUuid)) return false
        if (cleared) {
            // The edit emptied the active queue, which detached the marker. Re-activate the
            // durable queue before restoring its rows so the timeline listener does not adopt
            // the restored rows as a brand-new queue.
            val queue = queueRepository.queue(queueUuid).first() ?: return false
            activeQueuePersistence.activate(queue)
        }

        val target = PlayerQueueOrderTarget(player)
        val restored = when (undo.operation) {
            QueueEditOperation.PLAY_NEXT, QueueEditOperation.MOVE -> {
                target.restoreDisplayedOrder(undo.displayedOrder, undo.mediaItems)
            }

            QueueEditOperation.REMOVE -> {
                val removedIndex = undo.removedUnderlyingIndex ?: return false
                if (player.exoPlayer.mediaItemCount != undo.mediaItems.size - 1) {
                    return false
                }
                player.exoPlayer.addMediaItem(
                    removedIndex,
                    undo.mediaItems[removedIndex]
                )
                if (!target.restoreDisplayedOrder(undo.displayedOrder, undo.mediaItems)) {
                    player.exoPlayer.removeMediaItem(removedIndex)
                    return false
                }
                true
            }
        }
        if (!restored) return false

        lastQueueEditUndo = null
        if (cleared) {
            activeQueuePersistence.saveNowAwait()
        } else {
            activeQueuePersistence.saveNow()
        }
        return true
    }

    private suspend fun applyInactiveQueueUndo(queueUuid: UUID): Boolean {
        val undo = lastInactiveQueueEditUndo ?: return false
        if (queueUuid != undo.queueUuid) return false

        if (isDurableQueueActive(queueUuid)) {
            // The queue became active after the edit. Reorder the live player so the restored
            // rows are not immediately overwritten by the next active-queue mirror.
            if (!restoreActiveOrderFromRows(undo.rows)) return false
            lastInactiveQueueEditUndo = null
            activeQueuePersistence.saveNowAwait()
            return true
        }

        val restored = queueRepository.mutate(queueUuid) {
            it.copy(rows = undo.rows, bookmark = undo.bookmark)
        }
        if (restored == null) return false
        lastInactiveQueueEditUndo = null
        return true
    }

    /** Reorder the active player to [rows], keeping the current item and position untouched. */
    private fun restoreActiveOrderFromRows(rows: List<String>): Boolean {
        val player = endedWorkaroundPlayer ?: return false
        val currentItems = activeQueueMediaItems(player)
        if (currentItems.size != rows.size) return false
        val currentRows = currentItems.map(MediaRowCodec::encode)
        val remaining = currentRows.indices.toMutableList()
        val order = rows.map { row ->
            val index = remaining.indexOfFirst { currentRows[it] == row }
            if (index == -1) return false
            remaining.removeAt(index)
            index
        }
        PlayerQueueOrderTarget(player).applyDisplayedOrder(order)
        return true
    }

    /**
     * The sheet queue id when [command] targets an inactive queue, or null when the refresh is for
     * the active queue (including the legacy -1 sentinel).
     */
    private fun inactiveQueueIdForRefresh(command: SessionCommand): Long? {
        if (command.customExtras.containsKey("queueUuid")) return null
        val queueId = command.customExtras.getLong("queueId")
        return queueId.takeIf { it != -1L && it != endedWorkaroundPlayer?.currentQueueId }
    }

    private suspend fun broadcastQueueSongsRefresh(
        preserveScroll: Boolean = true,
        queueId: Long? = null,
    ) {
        val plr = endedWorkaroundPlayer ?: return
        val session = mediaSession ?: return
        val inactive = inactiveQueueSnapshots()
        for (controller in session.connectedControllers) {
            val command = SessionCommand(CLIENT_QB_REFRESH_LIST, Bundle.EMPTY).apply {
                if (queueId != null) customExtras.putLong("queueId", queueId)
                customExtras.putBinder(
                    "activeQueue",
                    MultiQueueList(listOf(plr.getActiveQueue()))
                )
                customExtras.putBinder(
                    "inactiveQueues",
                    MultiQueueList(inactive.map { it.copy(queue = ArrayList()) })
                )
                if (preserveScroll) customExtras.putBoolean("preserveScroll", true)
            }
            session.sendCustomCommand(controller, command, Bundle.EMPTY)
        }
    }

    /**
     * Performs the shuffle-all action as a single timeline replacement. Active queues shuffle the
     * player's order in place; inactive queues reorder their stored rows. A normal shuffle keeps the
     * anchor row current, while shuffle-play follows the queue shuffle button rules.
     *
     * Heavy work (permutations, row decode/reorder planning, DB mutate) runs off the main thread so
     * large queues do not ANR; ExoPlayer mutations that must be marshalled to the player happen only
     * for the small apply step. Persistence/refresh are posted after the command returns.
     */
    private suspend fun applyQueueShuffle(command: SessionCommand): Boolean {
        val shufflePlay = command.customExtras.getBoolean("shufflePlay", false)
        val player = endedWorkaroundPlayer ?: return false
        val durable = durableQueueForCommand(command)
        if (durable == null && command.customExtras.containsKey("queueUuid")) return false
        val isActive = durable != null && isDurableQueueActive(durable.id)

        if (isActive || durable == null) {
            if (player.exoPlayer.mediaItemCount <= 0) return false
            if (player.currentMediaItem?.mediaMetadata?.isPlayable == false) return false
            val size = player.exoPlayer.mediaItemCount
            val order = (0 until size).toList()
            val currentRaw = player.exoPlayer.currentMediaItemIndex
            val currentPosition = currentRaw.takeIf { it != C.INDEX_UNSET }

            if (shufflePlay) {
                if (player.isPlaying) {
                    // A song is currently playing: keep it as the current item/position, but
                    // reshuffle the rest so the queue order changes without disruption.
                    val currentIdx = currentPosition?.takeIf { it in order.indices } ?: 0
                    val newOrder = withContext(Dispatchers.Default) {
                        val shuffledOthers = order.indices.filter { it != currentIdx }.shuffled()
                        MutableList(order.size) { 0 }.also { out ->
                            var j = 0
                            for (i in order.indices) {
                                out[i] = if (i == currentIdx) order[i] else order[shuffledOthers[j++]]
                            }
                        }
                    }
                    lastQueueEditUndo = null
                    lastInactiveQueueEditUndo = null
                    val target = PlayerQueueOrderTarget(player)
                    val items = target.snapshotMediaItems()
                    val reordered = withContext(Dispatchers.Default) {
                        newOrder.map { items[it] }
                    }
                    withoutMarkingUserModified {
                        target.applyReorderedItems(items, reordered)
                    }
                    player.play()
                } else {
                    // Active but paused: always play, and start a different song than the last-paused one.
                    val lastPaused = currentPosition
                        ?: durable?.normalizedBookmark()?.row?.takeIf { it in order.indices }
                        ?: 0
                    val anchor = if (order.size <= 1) {
                        lastPaused
                    } else {
                        withContext(Dispatchers.Default) {
                            order.indices.filter { it != lastPaused }.random()
                        }
                    }
                    val result = withContext(Dispatchers.Default) {
                        QueueOrderEditor.shuffleEverything(order, anchor)
                    } as? QueueEditResult.Applied ?: return false
                    // Snapshot items on the player thread once, build reordered list off-main.
                    val target = PlayerQueueOrderTarget(player)
                    val items = target.snapshotMediaItems()
                    val reordered = withContext(Dispatchers.Default) {
                        result.order.map { items[it] }
                    }
                    lastQueueEditUndo = null
                    lastInactiveQueueEditUndo = null
                    withoutMarkingUserModified {
                        player.exoPlayer.setMediaItems(reordered, 0, 0L)
                    }
                    player.play()
                }
                handler.post {
                    activeQueuePersistence.saveNow(markModified = false)
                    scope.launch { broadcastQueueSongsRefresh() }
                }
                return true
            }

            val result = withContext(Dispatchers.Default) {
                QueueOrderEditor.shuffleEverything(order, currentPosition)
            } as? QueueEditResult.Applied ?: return false
            // Shuffle is toast-only feedback: do not retain undo snapshots.
            lastQueueEditUndo = null
            lastInactiveQueueEditUndo = null
            val target = PlayerQueueOrderTarget(player)
            val items = target.snapshotMediaItems()
            val reordered = withContext(Dispatchers.Default) {
                result.order.map { items[it] }
            }
            withoutMarkingUserModified {
                target.applyReorderedItems(items, reordered)
            }
            handler.post {
                activeQueuePersistence.saveNow(markModified = false)
                scope.launch { broadcastQueueSongsRefresh() }
            }
            return true
        }

        return applyInactiveQueueShuffleResult(durable, durable.id, shufflePlay, command)
    }

    /**
     * Shuffle an inactive stored queue. Row decoding and permutation run inside repository IO; if this
     * is a shuffle-play, load the reordered queue afterwards (decoding rows off main).
     */
    private suspend fun applyInactiveQueueShuffleResult(
        durable: Queue,
        durableId: UUID,
        shufflePlay: Boolean,
        command: SessionCommand,
    ): Boolean {
        var applied = false
        var before: Queue? = null
        queueRepository.mutate(durableId) { current ->
            val playable = current.rows.any {
                MediaRowCodec.decode(it)?.mediaMetadata?.isPlayable == true
            }
            if (!playable) return@mutate current
            val target = StoredQueueOrderTarget(current)
            val order = target.displayedOrder()
            val result = if (shufflePlay) {
                val lastPaused = current.normalizedBookmark().row
                    .coerceIn(0, (order.size - 1).coerceAtLeast(0))
                val anchor = if (order.size <= 1) {
                    lastPaused
                } else {
                    order.indices.filter { it != lastPaused }.random()
                }
                QueueOrderEditor.shuffleEverything(order, anchor)
            } else {
                QueueOrderEditor.shuffleEverything(order, target.currentPosition())
            } as? QueueEditResult.Applied ?: return@mutate current
            before = current
            target.applyDisplayedOrder(result.order)
            applied = true
            target.queue
        }
        if (!applied) return false
        lastQueueEditUndo = null
        // A plain shuffle is undoable, like a reorder. Shuffle-play starts a new order and is not.
        lastInactiveQueueEditUndo = if (shufflePlay) null else before?.let {
            InactiveQueueEditUndoState(durableId, it.rows, it.bookmark)
        }
        if (shufflePlay) {
            val started = playDurableQueue(durableId, 0)
            if (!started) return false
            handler.post {
                scope.launch { broadcastQueueSongsRefresh() }
            }
        } else {
            handler.post {
                scope.launch {
                    broadcastQueueSongsRefresh(queueId = inactiveQueueIdForRefresh(command))
                }
            }
        }
        return true
    }

    private suspend fun playDurableQueue(command: SessionCommand): Boolean {
        val queue = durableQueueForCommand(command) ?: return false
        val requestedStart = command.customExtras.getInt("startIndex", C.INDEX_UNSET)
        return playDurableQueue(queue.id, requestedStart)
    }

    /**
     * Apply the queue play/pause decision table for a durable queue. A song-row tap passes
     * [startIndex] to start that row without reordering; no start index means resume, restart,
     * start, pause, or switch as appropriate.
     *
     * Decoding stored rows for inactive queues runs off the main thread to avoid ANRs on large queues.
     */
    private suspend fun playDurableQueue(queueUuid: UUID, startIndex: Int = C.INDEX_UNSET): Boolean {
        val queue = queueRepository.queue(queueUuid).first() ?: return false
        val player = endedWorkaroundPlayer ?: return false
        val isActive = isDurableQueueActive(queue.id)

        data class InactiveDecoded(val items: List<MediaItem>, val displayedToPlayer: IntArray)

        val inactive = if (isActive) {
            null
        } else {
            withContext(Dispatchers.Default) {
                val pairs = queue.rows.mapIndexedNotNull { index, row ->
                    MediaRowCodec.decode(row)?.let { index to it }
                }
                if (pairs.isEmpty()) return@withContext null
                val map = IntArray(queue.rows.size) { -1 }
                pairs.forEachIndexed { playerIdx, pair -> map[pair.first] = playerIdx }
                InactiveDecoded(pairs.map { it.second }, map)
            }
        }
        val items = if (isActive) activeQueueMediaItems(player) else inactive?.items ?: return false
        if (items.isEmpty()) return false

        /** Map a displayed row index to the player index, or null if that row is unresolvable. */
        fun playerIndex(displayedRow: Int): Int? = if (isActive) {
            displayedRow.takeIf { it in items.indices }
        } else {
            inactive!!.displayedToPlayer.getOrNull(displayedRow)?.takeIf { it >= 0 }
        }

        val bookmark = if (isActive) {
            QueueRestoreRules.bookmarkFromPlayer(
                row = player.currentMediaItemIndex,
                positionMs = player.currentPosition,
                ended = player.playbackState == Player.STATE_ENDED,
                rowCount = items.size,
            )
        } else {
            queue.normalizedBookmark()
        }
        val action = if (startIndex >= 0) {
            val row = playerIndex(startIndex) ?: return false
            QueuePlaybackAction.Play(row, 0L)
        } else {
            when (
                val decision = QueuePlaybackRules.queuePlayButton(
                    QueuePlaybackState(
                        rowCount = items.size,
                        bookmark = bookmark,
                        hasBeenPlayed = queue.hasBeenPlayed || isActive,
                        isActive = isActive,
                        isPlaying = isActive && player.isPlaying,
                    )
                )
            ) {
                is QueuePlaybackAction.Play -> {
                    val row = playerIndex(decision.row) ?: return false
                    decision.copy(row = row)
                }

                else -> decision
            }
        }

        when (action) {
            QueuePlaybackAction.Disabled -> return false
            QueuePlaybackAction.Pause -> player.pause()
            is QueuePlaybackAction.Play -> {
                if (isActive) {
                    player.seekTo(action.row, action.positionMs)
                    player.play()
                } else {
                    // Pause and save the outgoing queue before the incoming one becomes active.
                    val outgoing = activeQueuePersistence.activeQueueId()?.let {
                        queueRepository.queue(it).first()
                    }
                    player.pause()
                    activeQueuePersistence.saveNowAwait(markModified = false)
                    activeQueuePersistence.activate(queue)
                    val switched = try {
                        withoutMarkingUserModified {
                            player.setMediaItems(
                                items,
                                action.row,
                                action.positionMs,
                                queue.title,
                                false,
                                queue.repeatMode.value,
                                null,
                            )
                            player.currentQueueId?.let { registerQueueId(it, queue.id) }
                            activeQueuePersistence.syncMirroredRowsFromPlayer()
                        }
                        true
                    } catch (e: IllegalSeekPositionException) {
                        Log.e(TAG, "failed to start queue ${queue.id}", e)
                        false
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "failed to start queue ${queue.id}", e)
                        false
                    }
                    if (!switched) {
                        // Do not leave the durable marker on a queue the player never loaded.
                        if (outgoing != null) {
                            activeQueuePersistence.activate(outgoing)
                        } else {
                            activeQueuePersistence.detach()
                        }
                        return false
                    }
                    player.play()
                }
            }
        }
        return true
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        suspend fun buildMqCustomCommand(action: String, queueId: Long?): SessionCommand {
            return SessionCommand(action, Bundle.EMPTY).apply {
                val plr = endedWorkaroundPlayer!!
                queueId?.let {
                    customExtras.putLong("queueId", it)
                }
                if (action != CLIENT_QB_REFRESH_CLEAR) {
                    customExtras.putBinder(
                        "activeQueue",
                        MultiQueueList(listOf(plr.getActiveQueue()))
                    )
                    val inactive = inactiveQueueSnapshots()
                        .map { it.copy(queue = ArrayList()) }
                    customExtras.putBinder(
                        "inactiveQueues",
                        MultiQueueList(inactive)
                    )
                }
            }
        }

        suspend fun notifyQueues(action: String, queueId: Long?) {
            for (connected in mediaSession!!.connectedControllers) {
                mediaSession!!.sendCustomCommand(
                    connected,
                    buildMqCustomCommand(action, queueId),
                    Bundle.EMPTY,
                )
            }
        }

        if (customCommand.customAction == SERVICE_SET_MEDIA_ITEMS_SEAMLESSLY
            || customCommand.customAction == SERVICE_SET_MEDIA_ITEMS_ATOMIC) {
            val songList = MediaItemList.getList(
                customCommand.customExtras.getBinder("items")!!)
            val position = customCommand.customExtras.getInt("position")
            val title = customCommand.customExtras.getString("title")!!
            val seamless = customCommand.customAction == SERVICE_SET_MEDIA_ITEMS_SEAMLESSLY
            val isOriginal = customCommand.customExtras.getBoolean("original", true)
            val isShuffledPlay = !isOriginal
            val source = customCommand.customExtras.getString("source_kind")?.let { kind ->
                QueueSource(
                    kind = QueueSourceKind.valueOf(kind),
                    identifier = customCommand.customExtras.getString("source_identifier"),
                    displayName = customCommand.customExtras.getString("source_display_name"),
                )
            }
            val repeatMode = if (customCommand.customExtras.containsKey("repeatMode"))
                customCommand.customExtras.getInt("repeatMode") else null
            val itemsFuture = Futures.transformAsync(
                onAddMediaItems(session, controller, songList),
                { resolvedSongs: List<MediaItem>? ->
                    scope.future {
                        queueResolutionMutex.withLock {
                            val player = endedWorkaroundPlayer!!
                            val items = resolvedSongs ?: emptyList()
                            val existingQueues = queueRepository.queues.first()
                            val replica = QueueDeduplicationRules.findUnmodifiedReplica(existingQueues, source)
                            val queue = if (replica != null) {
                                if (replica.id != activeQueuePersistence.activeQueueId()) {
                                    activeQueuePersistence.saveNowAwait(markModified = false)
                                    activeQueuePersistence.activate(replica)
                                }
                                replica
                            } else {
                                activeQueuePersistence.saveNowAwait(markModified = false)
                                createDurableQueue(title, source)
                            }

                            withContext(Dispatchers.Main) {
                                withoutMarkingUserModified {
                                    if (replica != null && isShuffledPlay) {
                                        val startIdx = if (position >= 0) position else 0
                                        player.setMediaItems(
                                            items,
                                            startIndex = startIdx,
                                            startPositionMs = C.TIME_UNSET,
                                            title = queue.title,
                                            ended = false,
                                            repeatMode = repeatMode,
                                            playbackParameters = null,
                                        )
                                        adoptPlayerQueue(queue)
                                        // In-place automated shuffle preserves unmodified state
                                        applyQueueShuffle(
                                            SessionCommand(SERVICE_QB_SHUFFLE, Bundle().apply {
                                                putBoolean("shufflePlay", true)
                                                putString("queueUuid", queue.id.toString())
                                            })
                                        )
                                    } else if (seamless) {
                                        player.setMediaItemsSeamlessly(
                                            items,
                                            position,
                                            null,
                                            queue.title,
                                            false,
                                            null,
                                            null,
                                        )
                                        adoptPlayerQueue(queue)
                                    } else {
                                        player.setMediaItems(
                                            items,
                                            startIndex = position,
                                            startPositionMs = C.TIME_UNSET,
                                            title = queue.title,
                                            ended = false,
                                            repeatMode = repeatMode,
                                            playbackParameters = null,
                                        )
                                        adoptPlayerQueue(queue)
                                    }
                                }
                                activeQueuePersistence.saveNowAwait(markModified = false)
                            }
                            SessionResult(SessionResult.RESULT_SUCCESS)
                        }
                    }
                },
                mainExecutor
            )
            // Ensure no further player commands (such as play) are executed until we're done.
            session.addToCommandQueueThenFlush(controller) { Futures.transform(itemsFuture,
                { null }, MoreExecutors.directExecutor()) }
            return itemsFuture
        }

        if (customCommand.customAction == SERVICE_QB_LOAD_QUEUE) {
            val startIndex = customCommand.customExtras.getInt("startIndex")
            val loadFuture = scope.future {
                val queue = durableQueueForCommand(customCommand)
                    ?: return@future SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
                val player = endedWorkaroundPlayer
                    ?: return@future SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)

                if (queue.id != activeQueuePersistence.activeQueueId()) {
                    // The outgoing queue is durable before the next one loads.
                    activeQueuePersistence.saveNowAwait(markModified = false)
                    activeQueuePersistence.activate(queue)

                    val items = queue.rows.mapNotNull(MediaRowCodec::decode)
                    val point = QueueRestoreRules.restorePoint(queue)
                    val startRow = if (startIndex >= 0 && items.isNotEmpty()) {
                        startIndex.coerceIn(0, items.lastIndex)
                    } else {
                        (point?.row ?: 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    }
                    val startPosition = if (startIndex >= 0) {
                        C.TIME_UNSET
                    } else {
                        point?.positionMs ?: 0L
                    }
                    withoutMarkingUserModified {
                        player.setMediaItems(
                            items,
                            startRow,
                            startPosition,
                            queue.title,
                            point?.ended ?: false,
                            queue.repeatMode.value,
                            null
                        )
                        player.currentQueueId?.let { registerQueueId(it, queue.id) }
                        activeQueuePersistence.syncMirroredRowsFromPlayer()
                    }
                }
                notifyQueues(CLIENT_QB_REFRESH_ALL, null)
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
            session.addToCommandQueueThenFlush(controller) {
                Futures.transform(loadFuture, { null }, MoreExecutors.directExecutor())
            }
            return loadFuture
        }

        if (customCommand.customAction == SERVICE_QB_PLAY_QUEUE) {
            val playFuture = scope.future {
                val status = playDurableQueue(customCommand)
                if (status) notifyQueues(CLIENT_QB_REFRESH_ALL, null)
                SessionResult(SessionResult.RESULT_SUCCESS).also { result ->
                    result.extras.putBoolean("status", status)
                }
            }
            session.addToCommandQueueThenFlush(controller) {
                Futures.transform(playFuture, { null }, MoreExecutors.directExecutor())
            }
            return playFuture
        }

        if (customCommand.customAction == SERVICE_QB_DEL) {
            val queueId = customCommand.customExtras.getLong("queueId")
            val queueUuid = customCommand.customExtras.getString("queueUuid")?.let(UUID::fromString)
            val deleteFuture = scope.future {
                if (queueUuid != null) {
                    val player = endedWorkaroundPlayer!!
                    val isActive = activeQueuePersistence.activeQueueId() == queueUuid
                    val status = queueRepository.delete(queueUuid)
                    if (status) {
                        if (isActive) {
                            activeQueuePersistence.detach()
                            player.clearMediaItems()
                        }
                        queueBoardIds.remove(queueUuid)?.let { boardId ->
                            queueIds.remove(boardId)
                        }
                        notifyQueues(
                            if (isActive) CLIENT_QB_REFRESH_CLEAR else CLIENT_QB_REFRESH_QUEUES,
                            null,
                        )
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS).also { result ->
                        result.extras.putBoolean("status", status)
                    }
                }
                val player = endedWorkaroundPlayer!!
                val status: Boolean
                val refreshLevel: String
                if (queueId == player.currentQueueId) {
                    val oldBoardId = player.currentQueueId
                    val oldDurableId = activeQueuePersistence.activeQueueId()
                    val deleted = oldDurableId?.let { queueRepository.delete(it) } ?: false
                    if (deleted) {
                        activeQueuePersistence.detach()
                        player.clearMediaItems()
                        oldBoardId?.let {
                            queueIds.remove(it)
                            oldDurableId.let { id -> queueBoardIds.remove(id) }
                        }
                    }
                    refreshLevel = if (deleted) CLIENT_QB_REFRESH_CLEAR else CLIENT_QB_REFRESH_ALL
                    status = deleted
                } else {
                    refreshLevel = CLIENT_QB_REFRESH_QUEUES
                    val durable = durableQueueForLong(queueId)
                    status = if (durable != null) {
                        queueRepository.delete(durable.id).also { deleted ->
                            if (deleted) queueBoardIds.remove(durable.id)
                        }
                    } else {
                        false
                    }
                    if (status) {
                        queueIds.remove(queueId)
                    }
                }
                if (status) {
                    notifyQueues(refreshLevel, null)
                }
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
            session.addToCommandQueueThenFlush(controller) {
                Futures.transform(deleteFuture, { null }, MoreExecutors.directExecutor())
            }
            return deleteFuture
        }

        if (customCommand.customAction == SERVICE_QB_RENAME_QUEUE) {
            val queueId = customCommand.customExtras.getLong("queueId")
            val queueUuid = customCommand.customExtras.getString("queueUuid")?.let(UUID::fromString)
            val title = customCommand.customExtras.getString("title")
            val dryRun = customCommand.customExtras.getBoolean("dryRun")
            val renameFuture = scope.future {
                if (queueUuid != null) {
                    val player = endedWorkaroundPlayer!!
                    val renamed = if (title.isNullOrBlank()) {
                        null
                    } else {
                        queueRepository.rename(queueUuid, title)
                    }
                    if (renamed != null) {
                        if (activeQueuePersistence.activeQueueId() == queueUuid) {
                            player.currentTitle = renamed.title
                        }
                        notifyQueues(CLIENT_QB_REFRESH_ALL, null)
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS).also { result ->
                        result.extras.putBoolean("status", renamed != null)
                    }
                }
                val player = endedWorkaroundPlayer!!
                val durable = durableQueueForLong(queueId)
                val status: Boolean = when {
                    title.isNullOrBlank() || durable == null -> false
                    queueRepository.queues.first().any {
                        it.id != durable.id && it.title == title
                    } -> false
                    queueId == player.currentQueueId -> {
                        if (player.currentTitle == title) {
                            false
                        } else {
                            if (!dryRun) {
                                player.currentTitle = title
                                queueRepository.rename(durable.id, title)
                            }
                            true
                        }
                    }
                    else -> {
                        if (!dryRun) {
                            queueRepository.rename(durable.id, title)
                        }
                        true
                    }
                }
                if (status) {
                    notifyQueues(CLIENT_QB_REFRESH_ITEM, queueId)
                }
                SessionResult(SessionResult.RESULT_SUCCESS).also { result ->
                    result.extras.putBoolean("status", status)
                }
            }
            session.addToCommandQueueThenFlush(controller) {
                Futures.transform(renameFuture, { null }, MoreExecutors.directExecutor())
            }
            return renameFuture
        }

        return scope.future {
            when (customCommand.customAction) {
                SERVICE_SET_TIMER -> {
                    // 0 = clear timer; 0 with pauseOnEnd true will pause on end of current song
                    val duration = customCommand.customExtras.getInt("duration")
                    val pauseOnEnd = customCommand.customExtras.getBoolean("pauseOnEnd")
                    if (duration > 0) {
                        timerPauseOnEnd = pauseOnEnd
                        timerDuration = SystemClock.elapsedRealtime() + duration
                    } else {
                        val currentPauseOnEnd =
                            endedWorkaroundPlayer!!.exoPlayer.pauseAtEndOfMediaItems
                        endedWorkaroundPlayer!!.exoPlayer.pauseAtEndOfMediaItems = pauseOnEnd
                        if (timerDuration != null) {
                            timerDuration = null
                        } else if (pauseOnEnd != currentPauseOnEnd) {
                            mediaSession!!.broadcastCustomCommand(
                                SessionCommand(SERVICE_TIMER_CHANGED, Bundle.EMPTY),
                                Bundle.EMPTY
                            )
                        }
                    }
                    if (duration > 0 || pauseOnEnd) {
                        prefs.edit {
                            putBoolean("lastTimerEos", pauseOnEnd)
                        }
                    }
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }

                SERVICE_QUERY_TIMER -> {
                    SessionResult(SessionResult.RESULT_SUCCESS).also {
                        timerDuration?.let { td ->
                            it.extras.putInt(
                                "duration",
                                (td - SystemClock.elapsedRealtime()).toInt()
                            )
                            it.extras.putBoolean("pauseOnEnd", timerPauseOnEnd)
                        } ?: it.extras.putBoolean(
                            "pauseOnEnd",
                            this@GramophonePlaybackService
                                .endedWorkaroundPlayer!!.exoPlayer.pauseAtEndOfMediaItems
                        )
                    }
                }

                SERVICE_GET_AUDIO_FORMAT -> {
                    SessionResult(SessionResult.RESULT_SUCCESS).also { res ->
                        if (downstreamFormat.isNotEmpty()) {
                            res.extras.putParcelableArrayList(
                                "file_format",
                                ArrayList(downstreamFormat.map {
                                    Bundle().apply {
                                        putInt("type", it.second.first)
                                        val bitrate = bitrate
                                        // TODO: should this be done here? this will create a new format object every query
                                        val format = if (it.second.first == C.TRACK_TYPE_AUDIO &&
                                            bitrate != null &&
                                            it.second.second.sampleMimeType == MimeTypes.AUDIO_OPUS
                                        ) {
                                            it.second.second.buildUpon().setAverageBitrate(bitrate)
                                                .build()
                                        } else it.second.second
                                        putBundle("format", format.toBundle())
                                        putBundle("rg", ReplayGainUtil.parse(format).toBundle())
                                    }
                                })
                            )
                        }
                        res.extras.putBundle("sink_format", audioSinkInputFormat?.toBundle())
                        res.extras.putParcelable("track_format", audioTrackInfo)
                        res.extras.putParcelable("hal_format", afTrackFormat?.second)
                        if (afFormatTracker.format?.routedDeviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                            res.extras.putParcelable("bt", btInfo)
                        }
                    }
                }

                SERVICE_GET_LYRICS -> {
                    SessionResult(SessionResult.RESULT_SUCCESS).also {
                        it.extras.putParcelable("lyrics", lyrics)
                    }
                }

                SERVICE_QB_GET_INACTIVE_LIST -> {
                    SessionResult(SessionResult.RESULT_SUCCESS).also { res ->
                        val queueList = inactiveQueueSnapshots()
                            .map { it.copy(queue = ArrayList()) }
                        res.extras.putBinder("allQueues", MultiQueueList(queueList))
                    }
                }

                SERVICE_QB_GET_QUEUE_FOR_UI -> {
                    try {
                        val queueId = customCommand.customExtras.getLong("queueId")
                        val current = endedWorkaroundPlayer!!.currentQueueId
                        val queue: MultiQueueObject? = if (queueId == -1L || queueId == current) {
                            endedWorkaroundPlayer!!.getActiveQueue()
                        } else {
                            durableQueueForLong(queueId)?.let(::toBoardQueue)
                        }
                        if (queue == null) {
                            SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
                        } else {
                            SessionResult(SessionResult.RESULT_SUCCESS).also { res ->
                                res.extras.putBinder(
                                    "allQueues",
                                    MultiQueueList(listOf(queue))
                                )
                            }
                        }
                    } catch (e: IllegalStateException) {
                        SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
                    }
                }

                SERVICE_QB_EDIT -> {
                    val status = applyQueueEdit(customCommand)
                    if (status) {
                        broadcastQueueSongsRefresh(
                            queueId = inactiveQueueIdForRefresh(customCommand)
                        )
                    }
                    SessionResult(SessionResult.RESULT_SUCCESS).also { res ->
                        res.extras.putBoolean("status", status)
                    }
                }

                SERVICE_QB_SHUFFLE -> {
                    val status = applyQueueShuffle(customCommand)
                    SessionResult(SessionResult.RESULT_SUCCESS).also { res ->
                        res.extras.putBoolean("status", status)
                    }
                }

                SERVICE_QB_UNDO_EDIT -> {
                    val status = applyQueueUndo(customCommand)
                    if (status) {
                        broadcastQueueSongsRefresh(
                            queueId = inactiveQueueIdForRefresh(customCommand)
                        )
                    }
                    SessionResult(SessionResult.RESULT_SUCCESS).also { res ->
                        res.extras.putBoolean("status", status)
                    }
                }

                SERVICE_QB_CLEAR_EDIT_UNDO -> {
                    lastQueueEditUndo = null
                    lastInactiveQueueEditUndo = null
                    SessionResult(SessionResult.RESULT_SUCCESS).also { res ->
                        res.extras.putBoolean("status", true)
                    }
                }

                else -> {
                    SessionResult(SessionError.ERROR_BAD_VALUE)
                }
            }
        }
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: @Player.PlayWhenReadyChangeReason Int
    ) {
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            this.endedWorkaroundPlayer?.exoPlayer?.pauseAtEndOfMediaItems = false
            mediaSession!!.broadcastCustomCommand(
                SessionCommand(SERVICE_TIMER_CHANGED, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val settable = SettableFuture.create<MediaItemsWithStartPosition>()
        if (isForPlayback) {
            scope.launch {
                lastPlaylistLoaded.await()
                Util.handlePlayButtonAction(endedWorkaroundPlayer)
                settable.setException(MediaSession.ManuallyHandlePlaybackResumption())
            }
            return settable
        }
        val job = scope.launch {
            val restored = activeQueuePersistence.readRestored()
            if (restored == null) {
                settable.setException(
                    NullPointerException(
                        "no durable last active queue, see former logs for root cause"
                    ).also { Log.e(TAG, Log.getThrowableString(it)!!) }
                )
                return@launch
            }
            if ((endedWorkaroundPlayer?.mediaItemCount ?: 0) == 0) {
                // Nothing loaded the queue yet, so this resumption owns it.
                activeQueuePersistence.adopt(restored.queue)
            }
            var theItem = restored.items[restored.startRow]
            if (theItem.mediaMetadata.durationMs != null &&
                theItem.mediaMetadata.durationMs!! > 0 &&
                restored.positionMs != C.TIME_UNSET
            ) {
                theItem = theItem.buildUpon()
                    .setMediaMetadata(
                        theItem.mediaMetadata.buildUpon()
                        .setExtras(Bundle(theItem.mediaMetadata.extras).apply {
                            if (restored.positionMs == 0L) {
                                putInt(
                                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                                )
                            } else if (restored.positionMs != theItem.mediaMetadata.durationMs!!) {
                                putInt(
                                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                                )
                                putDouble(
                                    MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE,
                                    (restored.positionMs.toDouble() /
                                            theItem.mediaMetadata.durationMs!!)
                                        .coerceIn(0.0, 1.0)
                                )
                            } else {
                                putInt(
                                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                                )
                            }
                        }).build()
                    ).build()
            }
            val items = restored.items.toMutableList().apply {
                this[restored.startRow] = theItem
            }
            settable.set(
                MediaItemsWithStartPosition(
                    items,
                    restored.startRow,
                    restored.positionMs,
                )
            )
        }
        job.invokeOnCompletion { t ->
            if (t is CancellationException && !settable.isDone) {
                settable.setException(t)
            }
        }
        return settable
    }


    override fun onTracksChanged(tracks: Tracks) {
        if (!tracks.isEmpty && !tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)) {
            Log.e(TAG, "No audio track selected: $tracks")
            controller!!.stop()
        }

        val mediaItem = controller?.currentMediaItem
        lyricsFetcher.launch {
            val trim = prefs.getBoolean("trim_lyrics", true)
            val options = LrcParserOptions(
                trim = trim, multiLine = true,
                errorText = getString(R.string.failed_to_parse_lyric)
            )
            // TODO: allow multiple lyric files/tags combining them for translations...maybe?
            val format = tracks.getFirstSelectedTrackFormatByType(C.TRACK_TYPE_AUDIO)
            var lrc: SemanticLyrics? = null
            if (format != null) {
                lrc = loadAndParseLyricsFile(
                    applicationContext,
                    mediaItem?.getFile(),
                    format.sampleMimeType, options
                )
                if (lrc == null) {
                    // note: wav files can have null metadata
                    val trackMetadata = format.metadata
                    if (trackMetadata != null) {
                        lrc = extractAndParseLyrics(
                            format.sampleRate.takeIf { it != Format.NO_VALUE } ?: 0,
                            format.sampleMimeType,
                            trackMetadata,
                            options).firstOrNull()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                mediaSession?.let {
                    lyrics = lrc
                    it.broadcastCustomCommand(
                        SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY),
                        Bundle.EMPTY
                    )
                    scheduleSendingLyrics(true)
                }
            }
        }
    }

    override fun onAudioTrackInitialized(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig
    ) {
        audioTrackInfoCounter++
        audioTrackInfo = AudioTrackInfo.fromMedia3AudioTrackConfig(audioTrackConfig)
        mediaSession?.broadcastCustomCommand(
            SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    override fun onAudioTrackReleased(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig
    ) {
        // Normally called after the replacement has been initialized, but if old track is released
        // without replacement, we want to instantly know that instead of keeping stale data.
        if (++audioTrackReleaseCounter == audioTrackInfoCounter) {
            audioTrackInfo = null
            mediaSession?.broadcastCustomCommand(
                SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
    }

    override fun onDownstreamFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        mediaLoadData: MediaLoadData
    ) {
        if (eventTime.mediaPeriodId == null) { // https://github.com/androidx/media/issues/2812
            Log.e(TAG, "mediaPeriodId is NULL in onDownstreamFormatChanged()!!")
            return
        }
        val currentPeriod = endedWorkaroundPlayer?.exoPlayer?.currentPeriodIndex?.takeIf {
            it != C.INDEX_UNSET &&
                    (endedWorkaroundPlayer?.exoPlayer?.currentTimeline?.periodCount ?: 0) > it
        }?.let { endedWorkaroundPlayer!!.exoPlayer.currentTimeline.getUidOfPeriod(it) }
        val item = eventTime.mediaPeriodId!!.periodUid to
                (mediaLoadData.trackType to mediaLoadData.trackFormat!!)
        if (currentPeriod != item.first) {
            pendingDownstreamFormat += item
        } else {
            downstreamFormat += item
            mediaSession?.broadcastCustomCommand(
                SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
    }

    private fun onAudioSinkInputFormatChanged(inputFormat: Format?) {
        audioSinkInputFormat = inputFormat
        mediaSession?.broadcastCustomCommand(
            SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_IDLE) {
            var changed = false
            if (afTrackFormat != null) {
                Log.e(TAG, "leaked track format: $afTrackFormat")
                afTrackFormat = null
                changed = true
            }
            if (pendingAfTrackFormats.isNotEmpty()) {
                Log.e(TAG, "leaked pending track formats: $pendingAfTrackFormats")
                pendingAfTrackFormats.clear()
            }
            if (downstreamFormat.isNotEmpty()) {
                Log.e(TAG, "leaked downstream formats: $downstreamFormat")
                downstreamFormat.clear()
                changed = true
            }
            if (pendingDownstreamFormat.isNotEmpty()) {
                Log.e(TAG, "leaked pending downstream formats: $pendingDownstreamFormat")
                pendingDownstreamFormat.clear()
            }
            if (changed) {
                mediaSession?.broadcastCustomCommand(
                    SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                    Bundle.EMPTY
                )
            }
        }
        if (state == Player.STATE_ENDED) {
            activeQueuePersistence.stopProgressSaving(save = false)
            activeQueuePersistence.saveNow()
        }
    }

    override fun onPlaybackParametersChanged(
        eventTime: AnalyticsListener.EventTime,
        playbackParameters: PlaybackParameters
    ) {
        scheduleSendingLyrics(false) // if speed changes
    }

    override fun onPlayerError(error: PlaybackException) {
        // TODO
    }

    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
        if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            handler.postDelayed({
                setShowNotificationForEmptyPlayer(SHOW_NOTIFICATION_FOR_EMPTY_PLAYER_NEVER)
            }, 2000) // TODO lol
        } else {
            setShowNotificationForEmptyPlayer(SHOW_NOTIFICATION_FOR_EMPTY_PLAYER_AFTER_STOP_OR_ERROR)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            bitrate = null
            bitrateFetcher.launch {
                bitrate = mediaItem?.getBitrate(this@GramophonePlaybackService) // TODO subtract cover size
                this@GramophonePlaybackService.mediaSession?.broadcastCustomCommand(
                    SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                    Bundle.EMPTY
                )
            }
            // TODO: re-enable this when https://github.com/androidx/media/issues/3248 is fixed
            //lyrics = null
            //scheduleSendingLyrics(true)
        }
        lastSentNotificationLyric = null
        lastSentHighlightedLyric = null

        activeQueuePersistence.saveProgressNow()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (prefs.getBooleanStrict("stopPlayingWhenDismissTask", false) &&
            rootIntent?.component != ComponentName(this, AudioPreviewActivity::class.java)
        ) {
            pauseAllPlayersAndStopSelf()
        } else {
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        refreshMediaButtonCustomLayout()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        scheduleSendingLyrics(false)
        if (isPlaying) {
            activeQueuePersistence.startProgressSaving()
        } else {
            activeQueuePersistence.stopProgressSaving(save = true)
        }
    }

    private fun mapMediaItemsForFavorites(mediaItems: List<MediaItem>): List<MediaItem> {
        val favorites = gramophoneApplication.favoritesStore.ids.value
        return mediaItems.map { item ->
            val isHeart = (item.mediaMetadata.userRating as? HeartRating)?.isHeart == true
            val shouldBeHeart = favorites.contains(item.mediaId)
            if (isHeart != shouldBeHeart || item.mediaMetadata.userRating !is HeartRating) {
                item.buildUpon().setMediaMetadata(
                    item.mediaMetadata.buildUpon().setUserRating(HeartRating(shouldBeHeart)).build()
                ).build()
            } else item
        }
    }
    override fun onRepeatModeChanged(repeatMode: Int) {
        refreshMediaButtonCustomLayout()
        activeQueuePersistence.saveProgressNow()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: @Player.TimelineChangeReason Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            lastPlaylistLoaded.complete(Unit)
            refreshMediaButtonCustomLayout()
            if ((endedWorkaroundPlayer?.mediaItemCount ?: 0) == 0 &&
                activeQueuePersistence.activeQueueId() != null
            ) {
                // The active queue was cleared; no ghost queue may survive in the mini player.
                activeQueuePersistence.detach()
            } else if (activeQueuePersistence.activeQueueId() == null &&
                (endedWorkaroundPlayer?.mediaItemCount ?: 0) > 0
            ) {
                // Items can reach the player directly (for example add-to-queue). Adopt them.
                scope.launch {
                    val player = endedWorkaroundPlayer ?: return@launch
                    if (activeQueuePersistence.activeQueueId() == null &&
                        player.mediaItemCount > 0
                    ) {
                        val queue = createDurableQueue(
                            player.currentTitle ?: getString(R.string.unknown_queue)
                        )
                        adoptPlayerQueue(queue)
                        activeQueuePersistence.saveNowAwait(markModified = false)
                    }
                }
            } else {
                activeQueuePersistence.saveNow(
                    markModified = suppressUserModifiedTimelineDepth == 0
                )
            }
            if (!computeRgMode(false))
                throw IllegalStateException("unreachable, mode failed with force=false")
        }
        // if it's a remotable timeline, it's a temporary masking timeline and real one will follow
        if (timeline !is Timeline.RemotableTimeline) {
            pendingDownstreamFormat.toSet().forEach {
                if (timeline.getIndexOfPeriod(it.first) == C.INDEX_UNSET) {
                    // This period is going away.
                    pendingDownstreamFormat.remove(it)
                }
            }
            pendingAfTrackFormats.toMap().forEach { (key, _) ->
                if (timeline.getIndexOfPeriod(key) == C.INDEX_UNSET) {
                    // This period is going away.
                    pendingAfTrackFormats.remove(key)
                }
            }
        }
    }

    private fun refreshMediaButtonCustomLayout() {
        val isEmpty = controller?.currentTimeline?.isEmpty != false
        mediaSession!!.connectedControllers.forEach {
            if (mediaSession!!.isMediaNotificationController(it)
                || mediaSession!!.isAutoCompanionController(it)
                || mediaSession!!.isAutomotiveController(it)
            ) {
                mediaSession!!.setMediaButtonPreferences(
                    it, if (isEmpty) emptyList() else
                        ImmutableList.of(getRepeatCommand(), getFavoriteCommand())
                )
            }
        }
    }

    override fun onLoadCanceled(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        pendingDownstreamFormat.removeAll { eventTime.mediaPeriodId?.periodUid == it.first }
    }

    var lastKnownPeriodUid: Any? = null // TODO: file upstream bug, maybe? this seems a bit weird
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (lastKnownPeriodUid != newPosition.periodUid || oldPosition.periodUid != newPosition.periodUid) {
            var changed = false
            downstreamFormat.toSet().forEach {
                if (newPosition.periodUid != it.first) {
                    downstreamFormat.remove(it)
                    changed = true
                }
            }
            pendingDownstreamFormat.toSet().forEach {
                if (newPosition.periodUid == it.first) {
                    downstreamFormat.add(it)
                    pendingDownstreamFormat.remove(it)
                    changed = true
                }
            }
            if (afTrackFormat?.first != newPosition.periodUid) {
                afTrackFormat = null
                changed = true
            }
            pendingAfTrackFormats[newPosition.periodUid]?.let { format ->
                afTrackFormat = newPosition.periodUid!! to format
                pendingAfTrackFormats.remove(newPosition.periodUid)
                changed = true
            }
            if (changed) {
                mediaSession?.broadcastCustomCommand(
                    SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                    Bundle.EMPTY
                )
            }
            lastKnownPeriodUid = newPosition.periodUid
        }
        scheduleSendingLyrics(false)
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            // A seek while paused does not otherwise reach the debounced progress saver.
            activeQueuePersistence.saveProgressNow()
        }
    }

    private fun getActiveNotificationLyric(): String? {
        val isNotificationLyricsEnabled = prefs.getBooleanStrict("notification_lyrics", false)
        if (!isNotificationLyricsEnabled) return null
        if (controller?.playbackState == Player.STATE_ENDED || controller?.playbackState == Player.STATE_IDLE) return null

        val cPos = (controller?.contentPosition ?: 0).toULong()
        val lines = syncedLyrics?.text?.filter {
            it.start <= cPos && !it.isTranslated
        }
        val currentLine = lines?.maxByOrNull { it.start } ?: return null
        if (currentLine.text.isBlank()) return null
        return currentLine.text
    }

    private fun scheduleSendingLyrics(new: Boolean) {
        handler.removeCallbacks(sendLyrics)
        sendLyricNow(new || !updatedLyricAtLeastOnce)
        updatedLyricAtLeastOnce = true
        if (new) {
            endedWorkaroundPlayer?.updateLyricNow()
        }
        val isStatusBarLyricsEnabled = prefs.getBooleanStrict("status_bar_lyrics", false)
        val isNotificationLyricsEnabled = prefs.getBooleanStrict("notification_lyrics", false)
        val hnw = !LyricWidgetProvider.hasWidget(this)
        if (controller?.isPlaying != true || (!isStatusBarLyricsEnabled && !isNotificationLyricsEnabled && hnw)) return
        val cPos = (controller?.contentPosition ?: 0).toULong()
        val nextUpdate = syncedLyrics?.text?.flatMap { line ->
            if (hnw && line.start <= cPos) listOf() else if (hnw) listOf(line.start) else
                (line.words?.map { it.timeRange.first }?.filter { it > cPos } ?: listOf())
                    .let { i -> if (line.start > cPos) i + line.start else i }
        }?.minOrNull()
        nextUpdate?.let {
            handler.postDelayed(
                sendLyrics, ((it - cPos).toLong()
                        / (controller?.playbackParameters?.speed ?: 1f)).toLong()
            )
        }
    }

    private fun sendLyricNow(new: Boolean) {
        if (new)
            LyricWidgetProvider.update(this)
        else
            LyricWidgetProvider.adapterUpdate(this)
        val isStatusBarLyricsEnabled = prefs.getBooleanStrict("status_bar_lyrics", false)
        val highlightedLyric = if (isStatusBarLyricsEnabled && controller?.playWhenReady == true)
            getCurrentLyricIndex(false)?.let {
                syncedLyrics?.text?.get(it)?.text
            }
        else null
        val notificationLyric = getActiveNotificationLyric()
        if (lastSentHighlightedLyric != highlightedLyric || lastSentNotificationLyric != notificationLyric) {
            val notifLyricChanged = lastSentNotificationLyric != notificationLyric
            lastSentHighlightedLyric = highlightedLyric
            lastSentNotificationLyric = notificationLyric
            handler.post {
                endedWorkaroundPlayer?.let {
                    if (notifLyricChanged) {
                        it.updateLyricNow()
                    }
                    // This will access the media notification controller's getters. But because
                    // controller callback ordering is undefined and in practice our service
                    // controller sometimes gets called first, this would cause us to access a stale
                    // PlaybackInfo in the media notification controller which causes wrong decision
                    // for startInForegroundRequired and that leads to crash.
                    if (Looper.myLooper() != it.applicationLooper)
                        throw UnsupportedOperationException("wrong looper for triggerNotificationUpdate")
                    isManualNotificationUpdate = true
                    triggerNotificationUpdate()
                    isManualNotificationUpdate = false
                }
            }
        }
    }

    fun getCurrentLyricIndex(withTranslation: Boolean): Int? {
        val lines = syncedLyrics?.text?.mapIndexed { i, it -> i to it }?.filter {
            it.second.start <= (controller?.currentPosition ?: 0).toULong()
                    && (!it.second.isTranslated || withTranslation)
        }
        // return first non-blank line if there are are multiple lines, else the first blank like
        val max = lines?.maxByOrNull { it.second.start }
        if (max == null) {
            return null
        }
        val maxLines =
            lines.filter { it.second.start == max.second.start && it.second.text.isNotBlank() }
        return maxLines.firstOrNull()?.first ?: max.first
    }

    override fun onForegroundServiceStartNotAllowedException() {
        Log.w(TAG, "Failed to resume playback :/")
        if (mayThrowForegroundServiceStartNotAllowed()
            || mayThrowForegroundServiceStartNotAllowedMiui()
        ) {
            if (supportsNotificationPermission() && !hasNotificationPermission()) {
                Log.e(
                    TAG, Log.getThrowableString(
                        IllegalStateException(
                            "onForegroundServiceStartNotAllowedException shouldn't be called on T+"
                        )
                    )!!
                )
                return
            }
            @SuppressLint("MissingPermission") // false positive
            nm.notify(NOTIFY_ID, NotificationCompat.Builder(this, NOTIFY_CHANNEL_ID).apply {
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setAutoCancel(true)
                setCategory(NotificationCompat.CATEGORY_ERROR)
                setSmallIcon(R.drawable.ic_error)
                setContentTitle(this@GramophonePlaybackService.getString(R.string.fgs_failed_title))
                setContentText(this@GramophonePlaybackService.getString(R.string.fgs_failed_text))
                setContentIntent(
                    PendingIntent.getActivity(
                        this@GramophonePlaybackService,
                        PENDING_INTENT_NOTIFY_ID,
                        Intent(this@GramophonePlaybackService, MainActivity::class.java)
                            .putExtra(MainActivity.PLAYBACK_AUTO_START_FOR_FGS, true),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                setVibrate(longArrayOf(0L, 200L))
                setLights(0, 0, 0)
                setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                setSound(null)
            }.build())
        } else {
            handler.post {
                throw IllegalStateException("onForegroundServiceStartNotAllowedException shouldn't be called on T+")
            }
        }
    }

    // --- MediaLibrarySession.Callback Implementation ---

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val tabCount = params?.extras?.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT,
            4) ?: 4
        return libraryTreeLoader.getLibraryRoot(tabCount)
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return libraryTreeLoader.getChildren(parentId, page, pageSize, params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return libraryTreeLoader.getItem(mediaId)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        session.notifySearchResultChanged(browser, query, 0, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return libraryTreeLoader.getSearchResult(query, page, pageSize, params)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> = lifecycleScope.future(Dispatchers.Default) {
        val expanded = libraryTreeLoader.addMediaItems(mediaItems).await()
        mapMediaItemsForFavorites(expanded.mediaItems)
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = lifecycleScope.future(Dispatchers.Default) {
        if (mediaItems.size == 1) {
            val queueId = LibraryTreeLoader.parseQueueMediaId(mediaItems.first().mediaId)
            if (queueId != null) {
                playDurableQueue(queueId, C.INDEX_UNSET)
                val player = endedWorkaroundPlayer
                val current = if (player != null) {
                    activeQueueMediaItems(player)
                } else {
                    emptyList()
                }
                val currentIndex = player?.currentMediaItemIndex?.takeIf { it >= 0 } ?: 0
                val currentPosition = player?.currentPosition ?: 0L
                return@future MediaSession.MediaItemsWithStartPosition(
                    mapMediaItemsForFavorites(current),
                    currentIndex,
                    currentPosition,
                )
            }
        }
        val expanded = libraryTreeLoader.addMediaItems(mediaItems).await()
        val mapped = mapMediaItemsForFavorites(expanded.mediaItems)
        MediaSession.MediaItemsWithStartPosition(mapped, expanded.startIndex ?: startIndex, startPositionMs)
    }
}
