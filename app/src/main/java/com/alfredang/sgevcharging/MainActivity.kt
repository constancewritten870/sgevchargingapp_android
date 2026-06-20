package com.alfredang.sgevcharging

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.alfredang.sgevcharging.data.ChargingSearchResult
import com.alfredang.sgevcharging.ui.SGEVChargingScreen
import com.alfredang.sgevcharging.ui.theme.SGEVChargingTheme
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private val locationGranted = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted.value = granted
        if (granted) pendingOnGranted?.invoke()
        else pendingOnDenied?.invoke()
        pendingOnGranted = null
        pendingOnDenied = null
    }

    private var pendingOnGranted: (() -> Unit)? = null
    private var pendingOnDenied: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationGranted.value = hasLocationPermission()

        setContent {
            val vm: ChargingSearchViewModel = viewModel()

            SGEVChargingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SGEVChargingScreen(
                        viewModel = vm,
                        onRequestCurrentLocation = {
                            requestLocation(
                                onGranted = { vm.useCurrentLocation() },
                                onDenied = {
                                    vm.showLocationMessage(
                                        "Location access is off. Search by postal code or enable location in Settings.",
                                    )
                                },
                            )
                        },
                        onDirections = { openDirections(it) },
                    )
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestLocation(onGranted: () -> Unit, onDenied: () -> Unit) {
        if (hasLocationPermission()) {
            onGranted()
            return
        }
        pendingOnGranted = onGranted
        pendingOnDenied = onDenied
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    /** Open turn-by-turn driving directions in Google Maps (or any maps app). */
    private fun openDirections(result: ChargingSearchResult) {
        val lat = result.station.latitude
        val lng = result.station.longitude
        val label = Uri.encode(result.station.name)
        val gmm = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, gmm).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback: generic geo URI handled by any installed maps app.
            val geo = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
            startActivity(Intent(Intent.ACTION_VIEW, geo))
        }
    }
}
