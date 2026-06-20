package com.alfredang.sgevcharging.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper over FusedLocationProviderClient that returns the device's
 * current coordinate. Mirrors the iOS UserLocationProvider role. Permission
 * handling lives in the UI layer (see MainActivity).
 */
class UserLocationProvider(context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun currentCoordinate(): Coordinate? = suspendCancellableCoroutine { cont ->
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    cont.resume(Coordinate(location.latitude, location.longitude))
                } else {
                    // Fall back to last known location if a fresh fix is unavailable.
                    fused.lastLocation
                        .addOnSuccessListener { last ->
                            cont.resume(last?.let { Coordinate(it.latitude, it.longitude) })
                        }
                        .addOnFailureListener { cont.resume(null) }
                }
            }
            .addOnFailureListener { cont.resume(null) }
    }

    data class Coordinate(val latitude: Double, val longitude: Double)
}
