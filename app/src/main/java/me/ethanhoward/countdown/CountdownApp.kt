package me.ethanhoward.countdown

import android.app.Application
import kotlinx.coroutines.launch
import me.ethanhoward.countdown.widget.WidgetRefresher

class CountdownApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            // Make sure the refresh chain exists (e.g. after an app update or if work was cleared).
            WidgetRefresher.scheduleNext(this@CountdownApp)
            container.eventRepository.pruneImages()
        }
    }
}
