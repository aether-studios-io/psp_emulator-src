package app.letostudio.gameemulator.billing

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the purchase screens' product lists from Remote Config key
 * [StoreConfigParser.KEY]. Fetch and activation are owned by
 * [app.letostudio.gameemulator.ads.AdsRemoteConfig] (one fetch per launch), which also
 * registers [DEFAULTS]: `setDefaultsAsync` replaces the whole defaults map, so every
 * default lives in that single call.
 *
 * Reads hit the last ACTIVATED config: a console change applies from the next launch
 * after a successful fetch; a fresh install runs on the in-code catalogue until then.
 */
@Singleton
class StoreRemoteConfig @Inject constructor() {

    private val remoteConfig = Firebase.remoteConfig

    fun storeConfiguration(): StoreConfiguration =
        StoreConfigParser.resolve(remoteConfig.getString(StoreConfigParser.KEY))

    companion object {
        /** Remote Config in-code default: the [BillingConfig] catalogue as store JSON. */
        val DEFAULTS: Map<String, Any> by lazy { mapOf(StoreConfigParser.KEY to StoreConfigParser.defaultJson()) }
    }
}
