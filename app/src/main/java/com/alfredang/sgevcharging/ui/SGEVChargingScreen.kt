package com.alfredang.sgevcharging.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfredang.sgevcharging.ChargingSearchViewModel
import com.alfredang.sgevcharging.data.ChargingSearchResult
import com.alfredang.sgevcharging.ui.theme.AvailableGreen
import com.alfredang.sgevcharging.ui.theme.BrandPrimary
import com.alfredang.sgevcharging.ui.theme.BrandSecondary
import com.alfredang.sgevcharging.ui.theme.OccupiedOrange
import com.alfredang.sgevcharging.ui.theme.UnavailableGray
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.roundToInt

private fun statusColor(name: String): Color = when (name) {
    "green" -> AvailableGreen
    "orange" -> OccupiedOrange
    else -> UnavailableGray
}

fun distanceText(meters: Double): String =
    if (meters >= 1000) String.format("%.1f km", meters / 1000)
    else "${meters.roundToInt()} m"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SGEVChargingScreen(
    viewModel: ChargingSearchViewModel,
    onRequestCurrentLocation: () -> Unit,
    onDirections: (ChargingSearchResult) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(1.3521, 103.8198), 11f)
    }

    val hasLocationPermission = remember { mutableStateOf(false) }

    // Animate the map whenever the view model publishes a new camera request.
    LaunchedEffect(viewModel.cameraRequest) {
        val req = viewModel.cameraRequest
        val bounds = LatLngBounds(
            LatLng(req.center.latitude - req.latSpan / 2, req.center.longitude - req.lngSpan / 2),
            LatLng(req.center.latitude + req.latSpan / 2, req.center.longitude + req.lngSpan / 2),
        )
        runCatching {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SG EV Charging", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandPrimary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                actions = {
                    IconButton(onClick = onRequestCurrentLocation) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Use current location")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission.value),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                    mapToolbarEnabled = false,
                ),
            ) {
                viewModel.resolvedLocation?.let { resolved ->
                    Marker(
                        state = MarkerState(LatLng(resolved.latitude, resolved.longitude)),
                        title = resolved.title,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    )
                }
                viewModel.results.take(25).forEach { result ->
                    val available = result.station.availableConnectors > 0
                    Marker(
                        state = MarkerState(LatLng(result.station.latitude, result.station.longitude)),
                        title = result.station.name,
                        snippet = result.station.availabilityText,
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (available) BitmapDescriptorFactory.HUE_GREEN
                            else BitmapDescriptorFactory.HUE_ORANGE,
                        ),
                        onClick = {
                            viewModel.select(result)
                            false
                        },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SearchPanel(
                    query = viewModel.query,
                    onQueryChange = { viewModel.query = it },
                    isLoading = viewModel.isLoading,
                    statusMessage = viewModel.statusMessage,
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.search()
                    },
                    onClear = { viewModel.query = "" },
                )

                if (viewModel.results.isNotEmpty()) {
                    ResultSummary(
                        lastUpdated = viewModel.lastUpdatedTime,
                        nearest = viewModel.results.firstOrNull(),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                viewModel.selectedResult?.let { selected ->
                    ChargingResultCard(
                        result = selected,
                        onDirections = { onDirections(selected) },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        viewModel.results.take(8).forEach { result ->
                            MiniResultChip(
                                result = result,
                                selected = result.id == selected.id,
                                onClick = { viewModel.select(result) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Surface permission state into the map (best-effort; updated on recomposition).
    LaunchedEffect(viewModel.resolvedLocation) {
        if (viewModel.resolvedLocation?.title == "Current location") {
            hasLocationPermission.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    isLoading: Boolean,
    statusMessage: String,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Postal code or place") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = onClear) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp).padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = onSearch) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = "Search",
                                    tint = BrandPrimary,
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusMessage,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ResultSummary(lastUpdated: String?, nearest: ChargingSearchResult?) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lastUpdated != null) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(lastUpdated, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (nearest != null) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(distanceText(nearest.distanceMeters), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChargingResultCard(
    result: ChargingSearchResult,
    onDirections: () -> Unit,
) {
    val available = result.station.availableConnectors > 0
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            statusColor(result.station.availabilityColorName).copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ElectricCar,
                        contentDescription = null,
                        tint = statusColor(result.station.availabilityColorName),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Nearest charging point",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        result.station.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (result.station.address.isNotBlank()) {
                        Text(
                            result.station.address,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(Icons.Filled.Bolt, result.station.availabilityText)
                InfoPill(Icons.Filled.LocationOn, distanceText(result.distanceMeters))
                if (result.station.totalConnectors > 0) {
                    InfoPill(Icons.Filled.Power, "${result.station.totalConnectors} plugs")
                }
            }

            if (result.station.operators.isNotBlank()) {
                Text(result.station.operators, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (result.station.plugSummary.isNotBlank()) {
                Text(
                    result.station.plugSummary,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onDirections,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.Directions, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Directions in Maps", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MiniResultChip(
    result: ChargingSearchResult,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.width(160.dp).height(78.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) BrandSecondary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                result.station.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(statusColor(result.station.availabilityColorName), CircleShape),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    result.station.availabilityText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InfoPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = BrandPrimary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
