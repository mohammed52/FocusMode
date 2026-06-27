package com.example.focusmode

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Banner ad visibility is driven by a Remote Config flag fetched on every launch.
// The StateFlow starts false so no ad shows before the fetch completes — this prevents
// a flash of an ad when Firebase has it disabled. Once fetchAndActivate() resolves,
// the flow updates and the UI reacts immediately. Ad unit ID defaults to Google's public
// test ID (always shows "Test Ad", no policy risk); swap via Remote Config without a rebuild.
private const val ADS_ENABLED_KEY = "ads_enabled"
private const val BANNER_UNIT_KEY = "banner_ad_unit_id"
private const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

object Ads {
    private val _enabled = MutableStateFlow(false)
    val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

    fun init(context: Context) {
        val rc = Firebase.remoteConfig
        // Chain: apply interval setting first, then fetch — so the interval is in effect
        // before fetchAndActivate runs (firing both in parallel ignores the new interval).
        rc.setConfigSettingsAsync(remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0L else 3600L
        }).addOnSuccessListener {
            rc.setDefaultsAsync(mapOf(ADS_ENABLED_KEY to true, BANNER_UNIT_KEY to TEST_BANNER_UNIT_ID))
            rc.fetchAndActivate().addOnCompleteListener {
                _enabled.value = rc.getBoolean(ADS_ENABLED_KEY)
            }
        }
        MobileAds.initialize(context)
    }

    fun bannerUnitId(): String =
        Firebase.remoteConfig.getString(BANNER_UNIT_KEY).ifBlank { TEST_BANNER_UNIT_ID }

    fun buildBannerRequest(): AdRequest = AdRequest.Builder().build()
}
