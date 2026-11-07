package me.ethanhoward.countdown.widget

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ethanhoward.countdown.appContainer
import me.ethanhoward.countdown.data.BackgroundType
import me.ethanhoward.countdown.data.Event
import me.ethanhoward.countdown.data.TextColorMode
import me.ethanhoward.countdown.data.WidgetConfig
import me.ethanhoward.countdown.domain.ColorMath
import me.ethanhoward.countdown.ui.events.EventDraft

data class WidgetConfigUiState(
    val loaded: Boolean = false,
    val events: List<Event> = emptyList(),
    val eventId: Long? = null,
    val backgroundType: BackgroundType = BackgroundType.COLOR,
    val color: Int = WidgetConfig.DEFAULT_COLOR,
    val opacity: Float = 1f,
    val blur: Float = 0f,
    val contrast: Float = 1f,
    val textColorMode: TextColorMode = TextColorMode.AUTO,
    /** Low-res rendering of the adjusted photo, or null when there's no photo. */
    val photoPreview: Bitmap? = null,
    val photoLuminance: Float = 0f,
    val loadingPhoto: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
) {
    val hasPhoto: Boolean get() = photoPreview != null
    val canSave: Boolean get() = !saving && eventId != null && (backgroundType == BackgroundType.COLOR || hasPhoto)
    val backgroundLuminance: Float
        get() = if (backgroundType == BackgroundType.PHOTO) photoLuminance else ColorMath.luminance(color)
}

class WidgetConfigViewModel(application: Application, private val appWidgetId: Int) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val processor = container.backgroundProcessor
    private val configs = container.widgetConfigRepository

    private val _state = MutableStateFlow(WidgetConfigUiState())
    val state: StateFlow<WidgetConfigUiState> = _state.asStateFlow()

    /** Full-resolution source photo (<= SOURCE_MAX_SIDE). */
    private val source = MutableStateFlow<Bitmap?>(null)
    /** True when [source] came from the picker and hasn't been written to disk yet. */
    private var sourceIsNew = false

    init {
        viewModelScope.launch {
            container.eventRepository.observeAll().collect { events ->
                _state.update { s ->
                    // Default to the soonest upcoming event for a brand-new widget.
                    s.copy(events = events, eventId = s.eventId?.takeIf { id -> events.any { it.id == id } } ?: events.firstOrNull()?.id)
                }
            }
        }
        viewModelScope.launch {
            configs.get(appWidgetId)?.let { c ->
                _state.update {
                    it.copy(
                        eventId = c.eventId ?: it.eventId,
                        backgroundType = c.backgroundType,
                        color = c.backgroundColor,
                        opacity = c.opacity,
                        blur = c.blur,
                        contrast = c.contrast,
                        textColorMode = c.textColorMode,
                    )
                }
                if (c.backgroundType == BackgroundType.PHOTO) {
                    source.value = processor.decode(configs.sourceImageFile(appWidgetId))
                }
            }
            _state.update { it.copy(loaded = true) }
        }
        // Re-render the preview whenever the photo or its adjustments change.
        viewModelScope.launch {
            _state.map { PhotoAdjustments(it.blur, it.contrast, it.opacity) }
                .distinctUntilChanged()
                .let { adjustments -> kotlinx.coroutines.flow.combine(source, adjustments, ::Pair) }
                .collectLatest { (bitmap, adj) ->
                    if (bitmap == null) {
                        _state.update { it.copy(photoPreview = null) }
                        return@collectLatest
                    }
                    delay(50) // debounce slider drags
                    val preview = processor.render(bitmap, adj, WidgetBackgroundProcessor.PREVIEW_MAX_SIDE)
                    val luminance = processor.averageLuminance(preview)
                    _state.update { it.copy(photoPreview = preview, photoLuminance = luminance) }
                }
        }
    }

    fun selectEvent(id: Long) = _state.update { it.copy(eventId = id) }
    fun setBackgroundType(type: BackgroundType) = _state.update { it.copy(backgroundType = type) }
    fun setColor(color: Int) = _state.update { it.copy(color = color or 0xFF000000.toInt()) }
    fun setOpacity(v: Float) = _state.update { it.copy(opacity = v) }
    fun setBlur(v: Float) = _state.update { it.copy(blur = v) }
    fun setContrast(v: Float) = _state.update { it.copy(contrast = v) }
    fun setTextColorMode(mode: TextColorMode) = _state.update { it.copy(textColorMode = mode) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(loadingPhoto = true, backgroundType = BackgroundType.PHOTO) }
            try {
                source.value = processor.decode(uri)
                sourceIsNew = true
            } catch (e: Exception) {
                Log.w(TAG, "Could not load photo", e)
                _state.update { it.copy(error = "Couldn't open that photo.") }
            } finally {
                _state.update { it.copy(loadingPhoto = false) }
            }
        }
    }

    fun createEvent(draft: EventDraft) {
        viewModelScope.launch {
            val id = container.eventRepository.save(Event(title = draft.title.trim(), targetDate = draft.date, targetTime = draft.time))
            _state.update { it.copy(eventId = id) }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val processedFile = configs.processedImageFile(appWidgetId)
                val sourceFile = configs.sourceImageFile(appWidgetId)
                var imagePath: String? = null
                var luminance = ColorMath.luminance(s.color)

                val photo = source.value
                if (s.backgroundType == BackgroundType.PHOTO && photo != null) {
                    if (sourceIsNew) processor.saveSource(photo, sourceFile)
                    val output = processor.render(photo, PhotoAdjustments(s.blur, s.contrast, s.opacity))
                    processor.saveProcessed(output, processedFile)
                    luminance = processor.averageLuminance(output)
                    imagePath = processedFile.absolutePath
                } else {
                    withContext(Dispatchers.IO) { processedFile.delete(); sourceFile.delete() }
                }

                configs.save(
                    WidgetConfig(
                        appWidgetId = appWidgetId,
                        eventId = s.eventId,
                        backgroundType = s.backgroundType,
                        backgroundColor = s.color,
                        opacity = s.opacity,
                        blur = s.blur,
                        contrast = s.contrast,
                        imagePath = imagePath,
                        backgroundLuminance = luminance,
                        textColorMode = s.textColorMode,
                    ),
                )
                WidgetRefresher.refresh(app, GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId))
                WidgetRefresher.scheduleNext(app)
                _state.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Saving widget failed", e)
                _state.update { it.copy(saving = false, error = "Couldn't save the widget. Please try again.") }
            }
        }
    }

    companion object {
        private const val TAG = "WidgetConfigVM"
    }
}
