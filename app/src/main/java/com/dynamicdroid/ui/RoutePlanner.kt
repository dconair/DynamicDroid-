package com.dynamicdroid.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dynamicdroid.DynamicDroidApp
import com.dynamicdroid.R
import com.dynamicdroid.SectionCard
import com.dynamicdroid.liveupdate.Formatters
import com.dynamicdroid.routing.CurrentLocation
import com.dynamicdroid.routing.online.NominatimGeocoder
import com.dynamicdroid.routing.online.OsrmRouteProvider
import com.dynamicdroid.routing.online.Place
import com.dynamicdroid.routing.online.RoutingException
import com.dynamicdroid.service.NavigationService
import kotlinx.coroutines.launch

/**
 * Ziel suchen -> Route ab aktuellem Standort berechnen -> navigieren (GPS) oder Fahrt simulieren.
 * Suche und Routing laufen nur auf ausdrücklichen Tastendruck (Nominatim erlaubt keine Autovervollständigung).
 */
@Composable
fun RoutePlanner(
    app: DynamicDroidApp,
    context: Context,
    locationGranted: Boolean,
    speedMultiplier: Double,
    running: Boolean,
) {
    val scope = rememberCoroutineScope()
    val geocoder = remember { NominatimGeocoder() }
    val router = remember { OsrmRouteProvider() }
    val formatters = remember { Formatters() }
    val planned by app.plannedRoute.collectAsStateWithLifecycleCompat()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun search() {
        scope.launch {
            busy = true; error = null
            try {
                results = geocoder.search(query)
                if (results.isEmpty()) error = context.getString(R.string.planner_no_results)
            } catch (e: RoutingException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    fun routeTo(place: Place) {
        scope.launch {
            busy = true; error = null
            try {
                val from = CurrentLocation.get(context)
                if (from == null) {
                    error = context.getString(R.string.planner_no_location)
                } else {
                    app.plannedRoute.value = router.route(from, place.location, place.name.substringBefore(','))
                    results = emptyList()
                }
            } catch (e: RoutingException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    SectionCard {
        Text(stringResource(R.string.planner_title), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !running,
            label = { Text(stringResource(R.string.planner_destination)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        )
        OutlinedButton(
            onClick = ::search,
            enabled = !running && !busy && query.trim().length >= 3,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = CircleShape,
        ) { Text(stringResource(R.string.planner_search)) }

        AnimatedVisibility(visible = busy) { CircularProgressIndicator(Modifier.padding(4.dp)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        results.forEach { place ->
            Text(
                place.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable(enabled = locationGranted && !busy) { routeTo(place) }.padding(vertical = 8.dp),
            )
        }
        if (results.isNotEmpty() && !locationGranted) {
            Text(stringResource(R.string.start_needs_location), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        planned?.let { route ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(route.destinationName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatters.distance(route.totalDistanceMeters)} · ${formatters.duration(route.totalDurationSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                RouteMap(route)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { NavigationService.start(context, NavigationService.SOURCE_ROUTE_GPS) },
                        enabled = locationGranted && !running,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = CircleShape,
                    ) { Text(stringResource(R.string.planner_start_gps)) }
                    OutlinedButton(
                        onClick = { NavigationService.start(context, NavigationService.SOURCE_ROUTE_SIM, speedMultiplier) },
                        enabled = locationGranted && !running,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = CircleShape,
                    ) { Text(stringResource(R.string.planner_simulate)) }
                }
            }
        }
        Text(stringResource(R.string.planner_attribution), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
