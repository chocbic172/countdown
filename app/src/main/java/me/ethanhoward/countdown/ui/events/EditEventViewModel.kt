package me.ethanhoward.countdown.ui.events

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ethanhoward.countdown.appContainer
import me.ethanhoward.countdown.data.BackgroundType
import me.ethanhoward.countdown.data.Event
import me.ethanhoward.countdown.data.EventAppearance
import me.ethanhoward.countdown.data.TextColorMode
import me.ethanhoward.countdown.domain.ColorMath
import me.ethanhoward.countdown.widget.PhotoAdjustments
import me.ethanhoward.countdown.widget.WidgetBackgroundProcessor

data class EditEventUiState(
    val isNew: Boolean,
    val loaded: Boolean,
    val draft: EventDraft = EventDraft(),
    /** Appearance being edited. Image paths/luminance are only filled in on save. */
    val look: EventAppearance = EventAppearance(),
    /** Low-res rendering of the adjusted photo, or null when there's no photo. */
    val photoPreview: Bitmap? = null,
    val photoLuminance: Float = 0f,
    val loadingPhoto: Boolean = false,
    val saving: Boolean = false,
    /** Set once the screen should close: the saved event's id, or null after a delete. */
    val result: EditResult? = null,
    val error: String? = null,
) {
    val hasPhoto: Boolean get() = photoPreview != null
    val canSave: Boolean
        get() = !saving && !loadingPhoto && draft.isValid && (look.backgroundType == BackgroundType.COLOR || hasPhoto)
}

sealed interface EditResult {
    data class Saved(val eventId: Long) : EditResult
    data object Closed : EditResult
}

class EditEventViewModel(application: Application, private val eventId: Long?) :
    AndroidViewModel(application), AppearanceActions {

    private val container = application.appContainer
    private val repository = container.eventRepository
    private val processor = container.backgroundProcessor

    private var original: Event? = null
    private val _state = MutableStateFlow(EditEventUiState(isNew = eventId == null, loaded = eventId == null))
    val state: StateFlow<EditEventUiState> = _state.asStateFlow()

    /** Full-resolution source photo (<= SOURCE_MAX_SIDE). */
    private val source = MutableStateFlow<Bitmap?>(null)
    /** True when [source] came from the picker and hasn't been written to disk yet. */
    private var sourceIsNew = false

    init {
        if (eventId != null) viewModelScope.launch {
            val event = repository.get(eventId)
            original = event
            if (event == null) {
                _state.update { it.copy(result = EditResult.Closed) }
                return@launch
            }
            _state.update {
                it.copy(
                    loaded = true,
                    draft = EventDraft(event.title, event.targetDate, event.targetTime),
                    look = event.appearance,
                )
            }
            event.appearance.sourceImagePath?.let { path ->
                if (event.appearance.backgroundType == BackgroundType.PHOTO) source.value = processor.decode(File(path))
            }
        }
        // Re-render the photo preview whenever the photo or its adjustments change.
        viewModelScope.launch {
            combine(source, _state.map { it.look.adjustments() }.distinctUntilChanged(), ::Pair)
                .collectLatest { (bitmap, adjustments) ->
                    if (bitmap == null) {
                        _state.update { it.copy(photoPreview = null) }
                        return@collectLatest
                    }
                    delay(50) // debounce slider drags
                    val preview = processor.render(bitmap, adjustments, WidgetBackgroundProcessor.PREVIEW_MAX_SIDE)
                    val luminance = processor.averageLuminance(preview)
                    _state.update { it.copy(photoPreview = preview, photoLuminance = luminance) }
                }
        }
    }

    fun onChange(draft: EventDraft) = _state.update { it.copy(draft = draft) }

    private fun updateLook(block: (EventAppearance) -> EventAppearance) = _state.update { it.copy(look = block(it.look)) }
    override fun setBackgroundType(type: BackgroundType) = updateLook { it.copy(backgroundType = type) }
    override fun setColor(color: Int) = updateLook { it.copy(backgroundColor = color or 0xFF000000.toInt()) }
    override fun setOpacity(value: Float) = updateLook { it.copy(opacity = value) }
    override fun setBlur(value: Float) = updateLook { it.copy(blur = value) }
    override fun setContrast(value: Float) = updateLook { it.copy(contrast = value) }
    override fun setTextColorMode(mode: TextColorMode) = updateLook { it.copy(textColorMode = mode) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(loadingPhoto = true) }
            updateLook { it.copy(backgroundType = BackgroundType.PHOTO) }
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

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val old = original?.appearance
                val look = buildAppearance(s.look, old)
                val base = original ?: Event(title = "", targetDate = s.draft.date)
                val id = repository.save(
                    base.copy(title = s.draft.title.trim(), targetDate = s.draft.date, targetTime = s.draft.time, appearance = look),
                )
                // Remove images this save replaced.
                val kept = setOf(look.imagePath, look.sourceImagePath)
                repository.images.delete(*listOf(old?.imagePath, old?.sourceImagePath).filter { it !in kept }.toTypedArray())
                _state.update { it.copy(saving = false, result = EditResult.Saved(id)) }
            } catch (e: Exception) {
                Log.e(TAG, "Saving event failed", e)
                _state.update { it.copy(saving = false, error = "Couldn't save the event. Please try again.") }
            }
        }
    }

    /** Writes image files as needed and returns the appearance to store. */
    private suspend fun buildAppearance(edited: EventAppearance, old: EventAppearance?): EventAppearance {
        val now = System.currentTimeMillis()
        val photo = source.value
        if (edited.backgroundType != BackgroundType.PHOTO || photo == null) {
            return edited.copy(
                backgroundType = BackgroundType.COLOR,
                imagePath = null,
                sourceImagePath = null,
                backgroundLuminance = ColorMath.luminance(edited.backgroundColor),
                appearanceUpdatedAt = now,
            )
        }
        val unchanged = !sourceIsNew && old?.backgroundType == BackgroundType.PHOTO &&
            old.imagePath != null && old.adjustments() == edited.adjustments()
        if (unchanged) return edited.copy(imagePath = old.imagePath, sourceImagePath = old.sourceImagePath)

        val images = repository.images
        val sourcePath = old?.sourceImagePath.takeIf { !sourceIsNew && it != null }
            ?: images.newSourceFile().also { processor.saveSource(photo, it) }.absolutePath
        val output = processor.render(photo, edited.adjustments())
        val processed = images.newProcessedFile().also { processor.saveProcessed(output, it) }
        return edited.copy(
            imagePath = processed.absolutePath,
            sourceImagePath = sourcePath,
            backgroundLuminance = processor.averageLuminance(output),
            appearanceUpdatedAt = now,
        )
    }

    fun delete() {
        val event = original ?: return
        viewModelScope.launch {
            repository.delete(event)
            _state.update { it.copy(result = EditResult.Closed) }
        }
    }

    companion object {
        private const val TAG = "EditEventVM"
        const val ARG_EVENT_ID = "eventId"

        val Factory = viewModelFactory {
            initializer {
                val id = createSavedStateHandle().get<Long>(ARG_EVENT_ID)?.takeIf { it > 0 }
                EditEventViewModel(this[APPLICATION_KEY]!!, id)
            }
        }

        /** For callers outside the nav graph, where there is no [ARG_EVENT_ID] argument to read. */
        fun factory(eventId: Long) = viewModelFactory {
            initializer { EditEventViewModel(this[APPLICATION_KEY]!!, eventId) }
        }
    }
}

private fun EventAppearance.adjustments() = PhotoAdjustments(blur, contrast, opacity)
