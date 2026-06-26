package com.example.focusmode

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig

// Whether ads show at all is a single Remote Config flag (default false), so it can be flipped
// on/off from the Firebase Console without a new release — e.g. to try the real on-device
// experience before turning it on for everyone. Ad unit IDs are Remote Config values too,
// defaulting to Google's public test IDs below: those always serve safely-labeled "Test Ad"
// content, so there's no AdMob policy risk before a real AdMob account/ad units exist. Swapping
// in the real IDs later is a Firebase Console edit, not a rebuild — only the AdMob *App ID* in
// AndroidManifest.xml needs a rebuild, since that one has to be static manifest data.
private const val ADS_ENABLED_KEY = "ads_enabled"
private const val BANNER_UNIT_KEY = "banner_ad_unit_id"
private const val INTERSTITIAL_UNIT_KEY = "interstitial_ad_unit_id"

private const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
private const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

object Ads {
    private var interstitial: InterstitialAd? = null

    fun init(context: Context) {
        Firebase.remoteConfig.setDefaultsAsync(
            mapOf(
                ADS_ENABLED_KEY to false,
                BANNER_UNIT_KEY to TEST_BANNER_UNIT_ID,
                INTERSTITIAL_UNIT_KEY to TEST_INTERSTITIAL_UNIT_ID
            )
        )
        MobileAds.initialize(context)
    }

    fun isEnabled(): Boolean = Firebase.remoteConfig.getBoolean(ADS_ENABLED_KEY)

    fun bannerUnitId(): String =
        Firebase.remoteConfig.getString(BANNER_UNIT_KEY).ifBlank { TEST_BANNER_UNIT_ID }

    // Loads ahead of time so it's ready the moment "Clear All" is tapped instead of the user
    // waiting on a network round-trip right when they're trying to clear their log.
    fun preloadInterstitial(context: Context) {
        if (!isEnabled() || interstitial != null) return
        val unitId = Firebase.remoteConfig.getString(INTERSTITIAL_UNIT_KEY)
            .ifBlank { TEST_INTERSTITIAL_UNIT_ID }
        InterstitialAd.load(
            context, unitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            }
        )
    }

    // Shows the preloaded interstitial if one's ready; either way, onDone always runs — clearing
    // the log never waits on, or depends on, ad-network availability.
    fun showInterstitialThen(activity: Activity, onDone: () -> Unit) {
        val ad = interstitial
        if (!isEnabled() || ad == null) {
            onDone()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onDone()
                preloadInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onDone()
            }
        }
        ad.show(activity)
    }
}
