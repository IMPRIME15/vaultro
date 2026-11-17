package com.example

import com.example.data.VaultRepository
import com.google.firebase.firestore.PersistentCacheSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestorePersistenceConfigTest {

    @Test
    fun buildOfflinePersistentSettings_enablesPersistentDiskCache() {
        val settings = VaultRepository.buildOfflinePersistentSettings()
        val cacheSettings = settings.cacheSettings
        assertTrue(cacheSettings is PersistentCacheSettings)
        val persistent = cacheSettings as PersistentCacheSettings
        assertEquals(100L * 1024L * 1024L, persistent.sizeBytes)
    }
}
