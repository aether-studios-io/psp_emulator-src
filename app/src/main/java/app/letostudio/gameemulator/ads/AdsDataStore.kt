package app.letostudio.gameemulator.ads

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * One DataStore file for all ad frequency state (interstitial + app-open cooldowns).
 * A preferences file may only have ONE delegate — every ads class shares this one.
 */
internal val Context.adsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ads")
