package me.ethanhoward.countdown.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.ethanhoward.countdown.ui.events.EditEventScreen
import me.ethanhoward.countdown.ui.events.EditEventViewModel
import me.ethanhoward.countdown.ui.events.EventListScreen

private const val ROUTE_LIST = "events"
private const val ROUTE_EDIT = "edit"

@Composable
fun CountdownNavHost() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            EventListScreen(
                onAddEvent = { nav.navigate(ROUTE_EDIT) },
                onEditEvent = { id -> nav.navigate("$ROUTE_EDIT?${EditEventViewModel.ARG_EVENT_ID}=$id") },
            )
        }
        composable(
            "$ROUTE_EDIT?${EditEventViewModel.ARG_EVENT_ID}={${EditEventViewModel.ARG_EVENT_ID}}",
            arguments = listOf(navArgument(EditEventViewModel.ARG_EVENT_ID) { type = NavType.LongType; defaultValue = -1L }),
        ) {
            EditEventScreen(onDone = { nav.popBackStack(ROUTE_LIST, inclusive = false) })
        }
    }
}
