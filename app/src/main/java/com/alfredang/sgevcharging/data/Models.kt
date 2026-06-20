package com.alfredang.sgevcharging.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Data models mirroring the iOS SG EV Charging app (Models.swift).
 *
 * The LTA DataMall payloads are inconsistent: numeric fields arrive as either
 * numbers or strings, longitude is sometimes misspelled "longtitude", etc. The
 * helpers below replicate the iOS "flexible" decoders so parsing is forgiving.
 */

/** A charging location ranked by distance from the search origin (metres). */
data class ChargingSearchResult(
    val station: EVChargingLocation,
    val distanceMeters: Double,
) {
    val id: String get() = station.id
}

data class EVConnector(
    val id: String?,
    val evCpId: String,
    val status: String,
) {
    companion object {
        fun from(obj: JSONObject): EVConnector = EVConnector(
            id = obj.flexibleStringOrNull("id"),
            evCpId = obj.flexibleStringOrNull("evCpId") ?: "",
            status = obj.flexibleStringOrNull("status") ?: "",
        )
    }
}

data class PlugType(
    val plugType: String,
    val current: String?,
    val speedInKW: Double?,
    val price: String?,
    val priceType: String?,
    val evIds: List<EVConnector>,
) {
    companion object {
        fun from(obj: JSONObject): PlugType {
            val current = obj.flexibleStringOrNull("current")
                ?: obj.nonNumericStringOrNull("powerRating")
            val speed = obj.flexibleDoubleOrNull("chargingSpeed")
                ?: obj.flexibleDoubleOrNull("powerRating")
            return PlugType(
                plugType = obj.flexibleStringOrNull("plugType") ?: "Plug",
                current = current,
                speedInKW = speed,
                price = obj.flexibleStringOrNull("price"),
                priceType = obj.flexibleStringOrNull("priceType"),
                evIds = obj.optJSONArray("evIds").objects().map { EVConnector.from(it) },
            )
        }
    }
}

data class ChargingPoint(
    val status: String,
    val operatingHours: String,
    val operatorName: String,
    val position: String,
    val name: String,
    val plugTypes: List<PlugType>,
) {
    companion object {
        fun from(obj: JSONObject): ChargingPoint = ChargingPoint(
            status = obj.flexibleStringOrNull("status") ?: "",
            operatingHours = obj.flexibleStringOrNull("operatingHours") ?: "",
            operatorName = obj.flexibleStringOrNull("operator") ?: "",
            position = obj.flexibleStringOrNull("position") ?: "",
            name = obj.flexibleStringOrNull("name") ?: "",
            plugTypes = obj.optJSONArray("plugTypes").objects().map { PlugType.from(it) },
        )
    }
}

data class EVChargingLocation(
    val address: String,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val postalCode: String?,
    val locationId: String?,
    val status: String?,
    val chargingPoints: List<ChargingPoint>,
) {
    val id: String
        get() = locationId ?: "$name-$latitude-$longitude"

    private val allConnectors: List<EVConnector>
        get() = chargingPoints.flatMap { it.plugTypes }.flatMap { it.evIds }

    val availableConnectors: Int get() = allConnectors.count { it.status == "1" }
    val occupiedConnectors: Int get() = allConnectors.count { it.status == "0" }
    val totalConnectors: Int get() = allConnectors.size

    val availabilityText: String
        get() = when {
            availableConnectors > 0 -> "$availableConnectors available"
            occupiedConnectors > 0 -> "Occupied"
            else -> "Unavailable"
        }

    /** "green" | "orange" | "gray" — mirrors iOS availabilityColorName. */
    val availabilityColorName: String
        get() = when {
            availableConnectors > 0 -> "green"
            occupiedConnectors > 0 -> "orange"
            else -> "gray"
        }

    val operators: String
        get() = chargingPoints.map { it.operatorName }.filter { it.isNotEmpty() }
            .toSortedSet().joinToString(", ")

    val plugSummary: String
        get() = chargingPoints.flatMap { it.plugTypes }
            .map { plug ->
                val speed = plug.speedInKW
                if (speed != null) "${plug.plugType} ${speed.cleanKW()}kW" else plug.plugType
            }
            .toSortedSet().joinToString(", ")

    companion object {
        fun from(obj: JSONObject): EVChargingLocation = EVChargingLocation(
            address = obj.flexibleStringOrNull("address") ?: "",
            name = obj.flexibleStringOrNull("name") ?: "EV charging point",
            longitude = obj.flexibleDoubleOrNull("longitude")
                ?: obj.flexibleDoubleOrNull("longtitude") ?: 0.0,
            latitude = obj.flexibleDoubleOrNull("latitude") ?: 0.0,
            postalCode = obj.flexibleStringOrNull("postalCode"),
            locationId = obj.flexibleStringOrNull("locationId"),
            status = obj.flexibleStringOrNull("status"),
            chargingPoints = obj.optJSONArray("chargingPoints").objects().map { ChargingPoint.from(it) },
        )
    }
}

/** Result of resolving a postcode / place query to a coordinate. */
data class ResolvedSearchLocation(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val postalCode: String?,
)

/** Batch payload: { LastUpdatedTime, evLocationsData: [...] }. */
data class EVBatchEnvelope(
    val lastUpdatedTime: String?,
    val evLocationsData: List<EVChargingLocation>,
) {
    companion object {
        fun from(obj: JSONObject): EVBatchEnvelope = EVBatchEnvelope(
            lastUpdatedTime = obj.flexibleStringOrNull("LastUpdatedTime"),
            evLocationsData = obj.optJSONArray("evLocationsData").objects()
                .map { EVChargingLocation.from(it) },
        )
    }
}

// region Flexible JSON helpers (mirror iOS KeyedDecodingContainer extensions)

private fun JSONObject.flexibleStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is String -> v
        is Int -> v.toString()
        is Long -> v.toString()
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        is Boolean -> v.toString()
        else -> v?.toString()
    }
}

private fun JSONObject.flexibleDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Double -> v
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
}

private fun JSONObject.nonNumericStringOrNull(key: String): String? {
    val s = flexibleStringOrNull(key) ?: return null
    return if (s.toDoubleOrNull() == null) s else null
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(it) }
    return out
}

private fun Double.cleanKW(): String =
    if (this % 1.0 == 0.0) this.roundToInt().toString()
    else String.format(Locale.US, "%.1f", this)

// endregion
