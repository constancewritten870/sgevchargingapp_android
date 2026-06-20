package com.alfredang.sgevcharging.data

import com.alfredang.sgevcharging.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LTADataMallException(message: String) : IOException(message)

/**
 * Client for the LTA DataMall EV charging endpoints, mirroring the iOS
 * LTADataMallClient:
 *   - EVChargingPoints (postal-code scoped)
 *   - EVCBatch (island-wide; returns a download link to the full dataset)
 */
class LTADataMallClient(
    private val accountKey: String = BuildConfig.LTA_DATAMALL_ACCOUNT_KEY,
    private val client: OkHttpClient = defaultClient,
) {
    suspend fun chargingPoints(nearPostalCode: String): List<EVChargingLocation> =
        withContext(Dispatchers.IO) {
            val url = "https://datamall2.mytransport.sg/ltaodataservice/EVChargingPoints"
                .toHttpUrl().newBuilder()
                .addQueryParameter("PostalCode", nearPostalCode)
                .build()
            val root = getJson(url.toString(), includeAccountKey = true)
            // EVPostalEnvelope { value: { evLocationsData: [...] } }
            val data = root.optJSONObject("value")?.optJSONArray("evLocationsData")
            (0 until (data?.length() ?: 0)).mapNotNull { i ->
                data?.optJSONObject(i)?.let { EVChargingLocation.from(it) }
            }
        }

    suspend fun allChargingPoints(): EVBatchEnvelope = withContext(Dispatchers.IO) {
        val linkRoot = getJson(
            "https://datamall2.mytransport.sg/ltaodataservice/EVCBatch",
            includeAccountKey = true,
        )
        val batchUrl = linkRoot.optJSONArray("value")
            ?.optJSONObject(0)
            ?.let { it.optString("Link").ifEmpty { it.optString("link") } }
            ?.takeIf { it.isNotEmpty() }
            ?: throw LTADataMallException("LTA did not return a batch download link.")

        val batchRoot = getJson(batchUrl, includeAccountKey = false)
        EVBatchEnvelope.from(batchRoot)
    }

    private fun getJson(url: String, includeAccountKey: Boolean): JSONObject {
        val builder = Request.Builder()
            .url(url)
            .header("accept", "application/json")
            .get()

        if (includeAccountKey) {
            if (accountKey.isBlank()) {
                throw LTADataMallException(
                    "Missing LTA DataMall account key. Add LTA_DATAMALL_ACCOUNT_KEY to local.properties.",
                )
            }
            builder.header("AccountKey", accountKey)
        }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw LTADataMallException("LTA returned an unexpected response (${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw LTADataMallException("LTA returned an empty response.")
            return JSONObject(body)
        }
    }

    companion object {
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
