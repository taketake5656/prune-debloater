package io.github.cyb3rl00m.prune

import io.github.cyb3rl00m.prune.core.db.DynamicProtections
import io.github.cyb3rl00m.prune.core.model.RecommendedAction
import io.github.cyb3rl00m.prune.core.model.Risk
import io.github.cyb3rl00m.prune.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {
    init { TestStrings.install() }
    private val c = TestData.classifier

    @Test fun databaseLoads() {
        assertTrue(TestData.db.uad.size > 5000)
        assertTrue(TestData.db.curatedCount > 300)
        assertEquals("core.json", TestData.ruleFiles.first())
        assertEquals(TestData.ruleFiles.size, TestData.db.files.size)
    }

    @Test fun criticalPackagesAreLocked() {
        val critical = listOf(
            "android", "com.android.systemui", "com.android.phone", "com.android.server.telecom",
            "com.samsung.android.incallui", "com.sec.imsservice", "com.android.settings", "com.android.shell",
            "com.samsung.android.biometrics.app.setting", "com.samsung.android.bio.face.service",
            "com.google.android.gms", "com.google.android.gsf", "com.sec.android.app.launcher",
            "com.android.providers.telephony", "com.android.providers.contacts", "com.android.cellbroadcastreceiver",
            "com.google.android.cellbroadcastreceiver", "com.samsung.android.kgclient", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.samsung.android.emergency", "com.android.emergency",
            "com.android.se", "com.android.nfc", "com.samsung.android.knox.attestation", "com.samsung.android.dialer",
            "com.android.networkstack.tethering", "moe.shizuku.privileged.api",
        )
        for (p in critical) {
            val r = c.classify(TestData.facts(p))
            assertEquals("$p must be locked", Risk.LOCKED, r.risk)
            assertNull(r.level)
            assertNotNull(r.lockedReason)
            assertEquals(RecommendedAction.KEEP, r.recommendedAction)
        }
    }

    @Test fun persistentSystemAppsAreLocked() {
        val r = c.classify(TestData.facts("com.example.unknown.persistent", persistent = true))
        assertEquals(Risk.LOCKED, r.risk)
    }

    @Test fun dynamicProtectionLocks() {
        val dyn = DynamicProtections(locked = mapOf("com.samsung.android.honeyboard" to "Your active keyboard."))
        assertEquals(Risk.LOCKED, c.classify(TestData.facts("com.samsung.android.honeyboard"), dyn).risk)
        assertEquals(Risk.HIGH, c.classify(TestData.facts("com.samsung.android.honeyboard")).risk)
    }

    @Test fun minimalLevelPackages() {
        for (p in listOf("com.samsung.android.app.tips", "com.facebook.appmanager", "com.samsung.android.arzone")) {
            val r = c.classify(TestData.facts(p))
            assertEquals(p, 1, r.level)
            assertTrue(r.risk == Risk.SAFE || r.risk == Risk.LOW)
        }
    }

    @Test fun ecosystemPackagesCarryWarnings() {
        val pay = c.classify(TestData.facts("com.samsung.android.spay"))
        assertTrue("WALLET" in pay.groups)
        assertTrue(pay.warnings.any { it.groupKey == "WALLET" })
        val wear = c.classify(TestData.facts("com.samsung.accessory.neobeanmgr"))
        assertTrue("WEARABLE" in wear.groups)
        assertTrue(wear.risk.ordinal >= Risk.MODERATE.ordinal)
    }

    @Test fun communityRecommendedSamsungIsNotMinimal() {
        // UAD-ng は Find My Mobile と Auto Blocker を "Recommended" としているが、Prune はより慎重でなければならない。
        assertEquals(3, c.classify(TestData.facts("com.samsung.android.fmm")).level)
        val rampart = c.classify(TestData.facts("com.samsung.android.rampart"))
        assertEquals(Risk.HIGH, rampart.risk)
        assertEquals(4, rampart.level)
    }

    @Test fun highRiskIsCustomOnly() {
        for ((pkg, e) in TestData.curated) {
            if (e.risk == "HIGH") assertEquals(pkg, 4, c.classify(TestData.facts(pkg)).level)
        }
    }

    @Test fun unknownSystemPackageIsUnknownCustomOnly() {
        val r = c.classify(TestData.facts("com.samsung.android.totally.unknown.thing"))
        assertEquals(Risk.UNKNOWN, r.risk)
        assertEquals(4, r.level)
        assertEquals(Source.OEM, r.source)
        assertEquals("Samsung", r.manufacturer)
        assertTrue(r.needsCare)
    }

    @Test fun userAppsAreNotPartOfProfiles() {
        val r = c.classify(TestData.facts("org.example.myapp", system = false))
        assertEquals(Source.USER, r.source)
        assertNull(r.level)
        assertEquals(RecommendedAction.KEEP, r.recommendedAction)
    }

    @Test fun privilegedSharedUidRaisesRisk() {
        val r = c.classify(TestData.facts("com.samsung.android.some.uncurated.service", sharedUid = "android.uid.system"))
        assertEquals(Risk.HIGH, r.risk)
        assertEquals(4, r.level)
    }

    @Test fun googleSource() {
        assertEquals(Source.GOOGLE, c.classify(TestData.facts("com.google.android.youtube")).source)
        assertEquals(Source.CARRIER, c.classify(TestData.facts("com.vzw.hss.myverizon")).source)
    }
}
