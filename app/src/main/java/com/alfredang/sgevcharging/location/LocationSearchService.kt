package com.alfredang.sgevcharging.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.alfredang.sgevcharging.data.ResolvedSearchLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class LocationSearchException(message: String) : IOException(message)

/**
 * Resolves a Singapore postal code or place name to a coordinate using the
 * platform Geocoder. Mirrors the iOS LocationSearchService / CLGeocoder usage.
 */
class LocationSearchService(private val context: Context) {

    suspend fun resolve(query: String): ResolvedSearchLocation {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            throw LocationSearchException("Enter a Singapore postal code or place.")
        }

        val lookup = if (trimmed.isSingaporePostalCode()) "Singapore $trimmed" else "$trimmed, Singapore"
        val addresses = geocode(lookup)
        val address = addresses.firstOrNull()
            ?: throw LocationSearchException("I could not find that place in Singapore.")

        val titleParts = listOfNotNull(
            address.featureName ?: address.thoroughfare,
            address.locality,
        ).distinct().filter { it.isNotBlank() }
        val title = titleParts.joinToString(", ").ifBlank { trimmed }

        return ResolvedSearchLocation(
            latitude = address.latitude,
            longitude = address.longitude,
            title = title,
            postalCode = address.postalCode ?: trimmed.takeIf { it.isSingaporePostalCode() },
        )
    }

    private suspend fun geocode(lookup: String): List<android.location.Address> =
        withContext(Dispatchers.IO) {
            val geocoder = Geocoder(context, Locale.US)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(lookup, 1, SG_LOWER_LAT, SG_LOWER_LON, SG_UPPER_LAT, SG_UPPER_LON) { results ->
                        cont.resume(results)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(lookup, 1, SG_LOWER_LAT, SG_LOWER_LON, SG_UPPER_LAT, SG_UPPER_LON)
                    ?: emptyList()
            }
        }

    companion object {
        // Bounding box around Singapore to bias geocoding results.
        private const val SG_LOWER_LAT = 1.16
        private const val SG_LOWER_LON = 103.59
        private const val SG_UPPER_LAT = 1.48
        private const val SG_UPPER_LON = 104.05
    }
}

private val SG_POSTAL = Regex("^\\d{6}$")
fun String.isSingaporePostalCode(): Boolean = SG_POSTAL.matches(this)
