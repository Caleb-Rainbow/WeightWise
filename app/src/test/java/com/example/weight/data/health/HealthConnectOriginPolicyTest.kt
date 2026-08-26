package com.example.weight.data.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectOriginPolicyTest {

    @Test
    fun `release rejects records written by its debug variant`() {
        assertFalse(
            HealthConnectOriginPolicy.shouldImport(
                originPackage = "com.example.weight.debug",
                ownPackage = "com.example.weight",
            )
        )
    }

    @Test
    fun `current package is never imported back`() {
        assertFalse(
            HealthConnectOriginPolicy.shouldImport(
                originPackage = "com.example.weight",
                ownPackage = "com.example.weight",
            )
        )
    }

    @Test
    fun `unrelated health data source remains importable`() {
        assertTrue(
            HealthConnectOriginPolicy.shouldImport(
                originPackage = "com.vendor.scale",
                ownPackage = "com.example.weight",
            )
        )
    }
}
