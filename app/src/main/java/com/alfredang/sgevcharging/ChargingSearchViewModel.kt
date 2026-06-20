package com.alfredang.sgevcharging

import android.app.Application
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alfredang.sgevcharging.data.ChargingSearchResult
import com.alfredang.sgevcharging.data.EVChargingLocation
import com.alfredang.sgevcharging.data.LTADataMallClient
import com.alfredang.sgevcharging.data.ResolvedSearchLocation
import com.alfredang.sgevcharging.location.LocationSearchService
import com.alfredang.sgevcharging.location.UserLocationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.google.android.gms.maps.model.LatLng

/**
 * Holds search + map state. Port of the iOS ChargingSearchViewModel.
 *
 * A new [CameraRequest] is published whenever the map should re-centre; the UI
 * observes [cameraRequest] and animates the Google Map camera to it.
 */
class ChargingSearchViewModel(app: Application) : AndroidViewModel(app) {

    private val ltaClient = LTADataMallClient()
    private val locationSearch = LocationSearchService(app)
    private val locationProvider = UserLocationProvider(app)

    var query by mutableStateOf("")
    var resolvedLocation by mutableStateOf<ResolvedSearchLocation?>(null)
        private set
    var results by mutableStateOf<List<ChargingSearchResult>>(emptyList())
        private set
    var selectedResult by mutableStateOf<ChargingSearchResult?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf("Search a postal code or place in Singapore.")
        private set
    var lastUpdatedTime by mutableStateOf<String?>(null)
        private set

    /** Latest camera move request; the UI animates to it once per new value. */
    var cameraRequest by mutableStateOf(
        CameraRequest(LatLng(1.3521, 103.8198), latSpan = 0.16, lngSpan = 0.16),
    )
        private set

    data class CameraRequest(val center: LatLng, val latSpan: Double, val lngSpan: Double)

    fun search() {
        viewModelScope.launch { runSearch(query) }
    }

    fun showLocationMessage(message: String) {
        statusMessage = message
    }

    fun startAutoLocationDetection() {
        statusMessage = "Detecting your location..."
    }

    /** Called by the UI once location permission has been granted. */
    fun useCurrentLocation() {
        viewModelScope.launch {
            statusMessage = "Detecting your location..."
            val coord = locationProvider.currentCoordinate()
            if (coord == null) {
                statusMessage = "Unable to detect your location. Try again or search by postal code."
                return@launch
            }
            val resolved = ResolvedSearchLocation(
                latitude = coord.latitude,
                longitude = coord.longitude,
                title = "Current location",
                postalCode = null,
            )
            loadChargingPoints(resolved)
        }
    }

    fun select(result: ChargingSearchResult) {
        selectedResult = result
        cameraRequest = CameraRequest(
            LatLng(result.station.latitude, result.station.longitude),
            latSpan = 0.018,
            lngSpan = 0.018,
        )
    }

    private suspend fun runSearch(rawQuery: String) {
        isLoading = true
        statusMessage = "Finding location..."
        try {
            val resolved = locationSearch.resolve(rawQuery)
            loadChargingPoints(resolved)
        } catch (e: Exception) {
            results = emptyList()
            selectedResult = null
            resolvedLocation = null
            statusMessage = e.message ?: "Search failed."
        } finally {
            isLoading = false
        }
    }

    private suspend fun loadChargingPoints(resolved: ResolvedSearchLocation) {
        isLoading = true
        statusMessage = "Loading LTA charging data..."
        resolvedLocation = resolved
        try {
            val batchDeferred = viewModelScope.async { ltaClient.allChargingPoints() }
            val postalMatches = resolved.postalCode?.let {
                runCatching { ltaClient.chargingPoints(nearPostalCode = it) }.getOrNull()
            }

            val batch = batchDeferred.await()
            var locations = batch.evLocationsData
            if (postalMatches != null) {
                locations = merge(locations, postalMatches)
            }

            lastUpdatedTime = batch.lastUpdatedTime
            val origin = floatArrayOf(0f)
            results = locations
                .map { station ->
                    Location.distanceBetween(
                        resolved.latitude, resolved.longitude,
                        station.latitude, station.longitude, origin,
                    )
                    ChargingSearchResult(station = station, distanceMeters = origin[0].toDouble())
                }
                .sortedBy { it.distanceMeters }

            selectedResult = results.firstOrNull()
            updateCamera(LatLng(resolved.latitude, resolved.longitude), results.take(5))
            statusMessage = if (results.isEmpty()) {
                "No EV charging points found."
            } else {
                "${results.size} charging locations found."
            }
        } catch (e: Exception) {
            results = emptyList()
            selectedResult = null
            statusMessage = e.message ?: "Could not load charging data."
        } finally {
            isLoading = false
        }
    }

    private fun merge(
        primary: List<EVChargingLocation>,
        secondary: List<EVChargingLocation>,
    ): List<EVChargingLocation> {
        val byId = LinkedHashMap<String, EVChargingLocation>()
        primary.forEach { byId[it.id] = it }
        secondary.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    private fun updateCamera(origin: LatLng, stations: List<ChargingSearchResult>) {
        val coords = listOf(origin) + stations.map { LatLng(it.station.latitude, it.station.longitude) }
        val minLat = coords.minOf { it.latitude }
        val maxLat = coords.maxOf { it.latitude }
        val minLng = coords.minOf { it.longitude }
        val maxLng = coords.maxOf { it.longitude }
        val center = LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2)
        cameraRequest = CameraRequest(
            center = center,
            latSpan = maxOf(0.015, (maxLat - minLat) * 1.6),
            lngSpan = maxOf(0.015, (maxLng - minLng) * 1.6),
        )
    }
}
