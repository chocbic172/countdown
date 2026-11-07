package me.ethanhoward.countdown.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.ethanhoward.countdown.MainActivity
import me.ethanhoward.countdown.appContainer
import me.ethanhoward.countdown.data.BackgroundType
import me.ethanhoward.countdown.data.Event
import me.ethanhoward.countdown.data.WidgetConfig
import me.ethanhoward.countdown.domain.ColorMath
import me.ethanhoward.countdown.domain.CountdownCalculator

/** Everything a widget needs to render. */
sealed interface WidgetUiState {
    /** Placed but not yet configured (e.g. the configure activity was skipped). */
    data object NotConfigured : WidgetUiState

    data class Ready(val config: WidgetConfig, val event: Event?, val background: Bitmap?) : WidgetUiState
}

class CountdownWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_SQUARE, SIZE_WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val states = observeState(context, appWidgetId)
        val initial = states.first()
        provideContent {
            // Reading the refresh stamp makes the composition re-run (with a fresh "now") on refresh.
            currentState(WidgetRefresher.REFRESHED_AT)
            val state by states.collectAsState(initial)
            WidgetContent(context, appWidgetId, state, Instant.now(), ZoneId.systemDefault())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeState(context: Context, appWidgetId: Int): Flow<WidgetUiState> {
        val container = context.appContainer
        return container.widgetConfigRepository.observe(appWidgetId).flatMapLatest { config ->
            if (config == null) return@flatMapLatest flowOf(WidgetUiState.NotConfigured)
            val background = loadBackground(config)
            val events = config.eventId?.let { container.eventRepository.observe(it) } ?: flowOf(null)
            events.map { WidgetUiState.Ready(config, it, background) }
        }.flowOn(Dispatchers.IO)
    }

    private fun loadBackground(config: WidgetConfig): Bitmap? =
        if (config.backgroundType == BackgroundType.PHOTO) config.imagePath?.let(BitmapFactory::decodeFile) else null

    companion object {
        val SIZE_SMALL = DpSize(110.dp, 50.dp)
        val SIZE_SQUARE = DpSize(110.dp, 110.dp)
        val SIZE_WIDE = DpSize(250.dp, 110.dp)
    }
}

private val LightText = Color.White
private val DarkText = Color(0xFF1B1B1F)

@Composable
private fun WidgetContent(context: Context, appWidgetId: Int, state: WidgetUiState, now: Instant, zone: ZoneId) {
    val configureAction = actionStartActivity(
        Intent(context, WidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    val root = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)

    when (state) {
        WidgetUiState.NotConfigured -> Box(
            modifier = root.background(Color(0xFF3F51B5)).clickable(configureAction).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) { Message("Tap to set up", LightText) }

        is WidgetUiState.Ready -> {
            val config = state.config
            val luminance = if (config.backgroundType == BackgroundType.PHOTO) {
                config.backgroundLuminance
            } else {
                ColorMath.luminance(config.backgroundColor)
            }
            val textColor = if (ColorMath.useDarkText(config.textColorMode, luminance)) DarkText else LightText
            val event = state.event

            Box(modifier = root.clickable(configureAction), contentAlignment = Alignment.Center) {
                if (state.background != null) {
                    Image(
                        provider = ImageProvider(state.background),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                } else {
                    Box(GlanceModifier.fillMaxSize().background(Color(config.backgroundColor).copy(alpha = config.opacity))) {}
                }
                Box(GlanceModifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    if (event == null) {
                        Message("Event removed\nTap to choose", textColor)
                    } else {
                        val text = CountdownCalculator.format(
                            CountdownCalculator.calculate(event.targetDate, event.targetTime, now, zone),
                        )
                        Countdown(text.number, text.unit, event.title, textColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, color: Color) {
    Text(text, style = TextStyle(color = ColorProvider(color), fontSize = 14.sp, fontWeight = FontWeight.Medium))
}

@Composable
private fun Countdown(number: String, unit: String, title: String, color: Color) {
    val size = LocalSize.current
    val provider = ColorProvider(color)
    val compact = size.height < 100.dp
    val numberSize: TextUnit = when {
        compact -> 28.sp
        number.length > 4 -> 32.sp
        size.width >= CountdownWidget.SIZE_WIDE.width -> 52.sp
        else -> 40.sp
    }
    val numberStyle = TextStyle(color = provider, fontSize = numberSize * 1.5, fontWeight = FontWeight.Bold)
    val unitStyle = TextStyle(color = provider, fontSize = 21.sp, fontWeight = FontWeight.Medium)
    val titleStyle = TextStyle(color = provider, fontSize = 18.sp)

    if (compact || size.width >= CountdownWidget.SIZE_WIDE.width) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number, style = numberStyle, maxLines = 1)
            Spacer(GlanceModifier.width(10.dp))
            Column {
                if (unit.isNotEmpty()) Text(unit, style = unitStyle, maxLines = 1)
                Text(title, style = titleStyle, maxLines = if (compact) 1 else 2)
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number, style = numberStyle, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, style = unitStyle, maxLines = 1)
            Text(title, style = titleStyle, maxLines = 2)
        }
    }
}
