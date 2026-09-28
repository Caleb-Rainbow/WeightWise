package com.example.weight.data.sync

import com.example.weight.BuildConfig
import com.example.weight.data.update.UpdateConfig
import java.security.MessageDigest

/** Backend identity namespaces both records and preferences in local test builds. */
object SyncStorage {
    private val suffix = if (BuildConfig.SYNC_SERVER_URL == UpdateConfig.SERVER_URL) "" else
        "-sync-" + MessageDigest.getInstance("SHA-256").digest(BuildConfig.SYNC_SERVER_URL.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
    val databaseName = "database$suffix"
    val settingsName = "settings$suffix"
    val settingsOwnerKey = "sync.settings.owner$suffix"
}
