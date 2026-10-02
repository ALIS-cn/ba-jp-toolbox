package com.bluearchive.toolbox.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "ba_toolbox_prefs")

/**
 * 本地持久化：免责声明接受版本、最新汉化资源缓存（含 GitHub ETag）。
 */
class AppPreferences(context: Context) {

    private val store = context.applicationContext.appDataStore

    // ---------- 用户守则 / 免责声明 ----------

    suspend fun disclaimerAcceptedVersion(): Int =
        store.data.first()[KEY_DISCLAIMER_VERSION] ?: 0

    suspend fun setDisclaimerAccepted(version: Int) {
        store.edit { it[KEY_DISCLAIMER_VERSION] = version }
    }

    // ---------- 汉化资源 Release 缓存 ----------

    suspend fun releaseEtag(): String? = store.data.first()[KEY_RELEASE_ETAG]

    suspend fun releaseJson(): String? = store.data.first()[KEY_RELEASE_JSON]

    suspend fun releaseFetchedAt(): Long = store.data.first()[KEY_RELEASE_FETCHED_AT] ?: 0L

    suspend fun saveReleaseCache(etag: String?, json: String, fetchedAt: Long) {
        store.edit {
            it[KEY_RELEASE_ETAG] = etag.orEmpty()
            it[KEY_RELEASE_JSON] = json
            it[KEY_RELEASE_FETCHED_AT] = fetchedAt
        }
    }

    // ---------- SAF 目录授权 ----------

    suspend fun safTreeUri(): String? = store.data.first()[KEY_SAF_TREE_URI]

    suspend fun saveSafTreeUri(uri: String) {
        store.edit { it[KEY_SAF_TREE_URI] = uri }
    }

    suspend fun clearSafTreeUri() {
        store.edit { it.remove(KEY_SAF_TREE_URI) }
    }

    // ---------- 新手引导 ----------

    /** 首次使用引导是否已完成（仅首次同意协议后展示一次） */
    suspend fun onboardingCompleted(): Boolean =
        store.data.first()[KEY_ONBOARDING_DONE] ?: false

    suspend fun setOnboardingCompleted() {
        store.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    companion object {
        /** 守则内容更新后 +1，已同意过的老用户会被重新要求确认 */
        const val DISCLAIMER_VERSION = 2

        private val KEY_DISCLAIMER_VERSION = intPreferencesKey("disclaimer_accepted_version")
        private val KEY_RELEASE_ETAG = stringPreferencesKey("release_etag")
        private val KEY_RELEASE_JSON = stringPreferencesKey("release_json")
        private val KEY_RELEASE_FETCHED_AT = longPreferencesKey("release_fetched_at")
        private val KEY_SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
