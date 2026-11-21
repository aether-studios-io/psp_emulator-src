package app.letostudio.gameemulator

import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.analytics.AppUserProps
import app.letostudio.gameemulator.analytics.GameHash
import app.letostudio.gameemulator.billing.BillingConfig
import app.letostudio.gameemulator.core.DeviceSignals
import app.letostudio.gameemulator.core.DeviceTier
import app.letostudio.gameemulator.core.FixLagStep
import app.letostudio.gameemulator.core.SmartSetup
import app.letostudio.gameemulator.data.db.GameEntity
import app.letostudio.gameemulator.data.prefs.EmulatorDefaults
import app.letostudio.gameemulator.domain.model.PremiumSource
import app.letostudio.gameemulator.domain.model.PremiumStatus
import app.letostudio.gameemulator.input.KeyMapper
import app.letostudio.gameemulator.input.PadButton
import app.letostudio.gameemulator.monetization.InterstitialPacer
import app.letostudio.gameemulator.monetization.PlanKind
import app.letostudio.gameemulator.monetization.ProGate
import app.letostudio.gameemulator.monetization.ProPlan
import app.letostudio.gameemulator.ui.library.LibraryFilter
import app.letostudio.gameemulator.ui.library.LibraryViewModel
import app.letostudio.gameemulator.ui.paywall.RnPaywallViewModel
import app.letostudio.gameemulator.ui.player.PadGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPacerTest {
    @Test fun `never for Pro`() = assertEquals(InterstitialPacer.Skip.PRO, InterstitialPacer.decide(true, 1_000_000, 0, true))
    @Test fun `no first-session delay`() = assertNull(InterstitialPacer.decide(false, 5_000, 0, true))
    @Test fun `60 s gap enforced`() = assertEquals(InterstitialPacer.Skip.COOLDOWN, InterstitialPacer.decide(false, 159_999, 100_000, true))
    @Test fun `exactly 60 s is allowed`() = assertNull(InterstitialPacer.decide(false, 160_000, 100_000, true))
    @Test fun `skip when not loaded`() = assertEquals(InterstitialPacer.Skip.NOT_LOADED, InterstitialPacer.decide(false, 500_000, 100_000, false))
    @Test fun `skip reasons match analytics enum`() =
        assertEquals(setOf("not_loaded", "cooldown", "pro"), InterstitialPacer.Skip.entries.map { it.reason }.toSet())
}

class SmartSetupTest {
    private val flagship = DeviceSignals(8, 12_000, "Adreno (TM) 740", 150_000_000)
    private val mid = DeviceSignals(8, 6_000, "Mali-G68", 70_000_000)
    private val low = DeviceSignals(4, 2_000, "Adreno (TM) 505", 20_000_000)

    @Test fun `tier mapping`() {
        assertEquals(DeviceTier.HIGH, SmartSetup.tierOf(flagship))
        assertEquals(DeviceTier.MID, SmartSetup.tierOf(mid))
        assertEquals(DeviceTier.LOW, SmartSetup.tierOf(low))
    }

    @Test fun `mali g7x is mid and gets 1x`() {
        val pixel6a = DeviceSignals(8, 5_700, "Mali-G78", 150_000_000)
        assertEquals(DeviceTier.MID, SmartSetup.tierOf(pixel6a))
        assertEquals(1, SmartSetup.defaultsFor(DeviceTier.MID).resolution)
        assertEquals(DeviceTier.HIGH, SmartSetup.tierOf(DeviceSignals(8, 12_000, "Mali-G715-Immortalis MC11", 150_000_000)))
    }

    @Test fun `fix lag lowers one step per tap then stops`() {
        var s = EmulatorDefaults(resolution = 2, frameSkip = 0, effectsOn = true)
        val steps = mutableListOf<FixLagStep>()
        while (true) {
            val step = SmartSetup.nextFixLag(s) ?: break
            steps += step
            s = SmartSetup.applyFixLag(s, step)
        }
        assertEquals(listOf(FixLagStep.RESOLUTION, FixLagStep.FRAME_SKIP, FixLagStep.EFFECTS), steps)
        assertEquals(3, s.fixLagSteps)
        assertEquals(1, s.resolution)
        assertNull(SmartSetup.nextFixLag(s))
    }

    @Test fun `per-game overrides win over defaults`() {
        val game = GameEntity(uri = "u", fileName = "f", format = "iso", sizeBytes = 1, title = "t", gameHash = "h", resolution = 1)
        assertEquals(1, SmartSetup.effectiveFor(game, EmulatorDefaults(resolution = 2)).resolution)
        assertEquals(2, SmartSetup.effectiveFor(null, EmulatorDefaults(resolution = 2)).resolution)
    }

    @Test fun `core variables map resolution and frame skip`() {
        val vars = SmartSetup.coreVariables(EmulatorDefaults(resolution = 1, frameSkip = 1, effectsOn = false)).toMap()
        assertEquals("480x272", vars["ppsspp_internal_resolution"])
        assertEquals("enabled", vars["ppsspp_auto_frameskip"])
        assertEquals("enabled", vars["ppsspp_skip_buffer_effects"])
        assertEquals("opengl", vars["ppsspp_backend"])
    }
}

class ProGateTest {
    @Test fun `free`() = assertEquals(ProPlan.NONE, ProGate.derive(PremiumStatus.FREE).plan)

    @Test fun `lifetime`() = assertEquals(
        ProPlan.LIFETIME,
        ProGate.derive(PremiumStatus(true, PremiumSource.LIFETIME, BillingConfig.PRO_LIFETIME)).plan,
    )

    @Test fun `plan comes from the product id`() {
        fun sub(id: String) = PremiumStatus(true, PremiumSource.SUBSCRIPTION, id)
        assertEquals(ProPlan.MONTHLY, ProGate.derive(sub(BillingConfig.PRO_MONTHLY)).plan)
        assertEquals(ProPlan.WEEKLY, ProGate.derive(sub(BillingConfig.PRO_WEEKLY)).plan)
        // Retired plan: existing buyers keep Pro and their plan name.
        assertEquals(ProPlan.YEARLY, ProGate.derive(sub(BillingConfig.PRO_YEARLY)).plan)
        assertTrue(BillingConfig.isSubscription(BillingConfig.PRO_YEARLY))
        assertEquals(ProPlan.SUBSCRIPTION, ProGate.derive(sub("other_sub")).plan)
        assertTrue(ProGate.derive(sub("other_sub")).isPro)
        assertEquals(BillingConfig.PRO_MONTHLY, ProGate.productOf(ProPlan.MONTHLY))
        assertEquals(null, ProGate.productOf(ProPlan.LIFETIME))
    }

    @Test fun `plan catalog orders weekly monthly lifetime and drops unknown products`() {
        fun p(id: String) = app.letostudio.gameemulator.domain.model.SubscriptionPlan(
            productId = id, basePlanId = null, offerToken = null, name = id,
            billingPeriod = app.letostudio.gameemulator.billing.model.BillingPeriod.MONTHLY,
            formattedPrice = "$1", priceMicros = 1, currencyCode = "USD",
        )
        val kinds = app.letostudio.gameemulator.monetization.PlanCatalog.options(
            listOf(p(BillingConfig.PRO_LIFETIME), p("x"), p(BillingConfig.PRO_MONTHLY), p(BillingConfig.PRO_YEARLY), p(BillingConfig.PRO_WEEKLY)),
        ).map { it.kind }
        assertEquals(listOf(PlanKind.WEEKLY, PlanKind.MONTHLY, PlanKind.LIFETIME), kinds)
    }

    @Test fun `cancel anytime url`() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=gameemulator_pro_yearly&package=app.letostudio.gameemulator",
            RnPaywallViewModel.playSubscriptionsUrl("gameemulator_pro_yearly"),
        )
        assertFalse(RnPaywallViewModel.playSubscriptionsUrl(null).contains("sku="))
    }
}

class AppEventsTest {
    private val reserved = listOf("firebase_", "google_", "ga_")

    @Test fun `event names are snake_case, at most 40 chars, no reserved prefix, unique`() {
        AppEvents.ALL.forEach { e ->
            assertTrue(e, e.matches(Regex("[a-z][a-z0-9_]*")) && e.length <= 40)
            assertTrue(e, reserved.none { e.startsWith(it) })
        }
        assertEquals(AppEvents.ALL.size, AppEvents.ALL.toSet().size)
    }

    @Test fun `param names follow the same rules`() {
        AppParams::class.java.declaredFields.filter { it.type == String::class.java }.forEach { f ->
            f.isAccessible = true
            val v = f.get(null) as String
            assertTrue(v, v.matches(Regex("[a-z][a-z0-9_]*")) && v.length <= 40)
        }
    }

    @Test fun `game hash is 12 hex chars and never the title`() {
        val h = GameHash.ofDiscId("ULUS10000")
        assertTrue(h.matches(Regex("[0-9a-f]{12}")))
        assertEquals(h, GameHash.ofDiscId(" ulus10000 "))
        assertEquals(12, GameHash.ofContent(123, ByteArray(10)).length)
    }

    @Test fun `buckets`() {
        assertEquals("0", AppUserProps.gamesBucket(0))
        assertEquals("1-3", AppUserProps.gamesBucket(3))
        assertEquals("4-10", AppUserProps.gamesBucket(4))
        assertEquals("31+", AppUserProps.gamesBucket(99))
        assertEquals("6-20", AppUserProps.savesBucket(6))
        assertEquals("21+", AppUserProps.savesBucket(21))
    }
}

class KeyMapperTest {
    @Test fun `encode decode round trip and translate`() {
        val m = mapOf(96 to PadButton.B.keyCode, 97 to PadButton.A.keyCode)
        assertEquals(m, KeyMapper.decode(KeyMapper.encode(m)))
        assertEquals(PadButton.B.keyCode, KeyMapper.translate(m, 96))
        assertEquals(100, KeyMapper.translate(m, 100))
        assertEquals(96, KeyMapper.physicalFor(m, PadButton.B))
        assertEquals(emptyMap<Int, Int>(), KeyMapper.decode("garbage"))
    }

    @Test fun `face buttons swap to RetroPad positions`() {
        assertEquals(PadButton.B.keyCode, KeyMapper.toRetroPad(PadButton.A.keyCode))
        assertEquals(PadButton.A.keyCode, KeyMapper.toRetroPad(PadButton.B.keyCode))
        assertEquals(PadButton.Y.keyCode, KeyMapper.toRetroPad(PadButton.X.keyCode))
        assertEquals(PadButton.X.keyCode, KeyMapper.toRetroPad(PadButton.Y.keyCode))
        assertEquals(PadButton.START.keyCode, KeyMapper.toRetroPad(PadButton.START.keyCode))
    }
}

class PadGeometryTest {
    private val up = android.view.KeyEvent.KEYCODE_DPAD_UP
    private val down = android.view.KeyEvent.KEYCODE_DPAD_DOWN
    private val left = android.view.KeyEvent.KEYCODE_DPAD_LEFT
    private val right = android.view.KeyEvent.KEYCODE_DPAD_RIGHT

    @Test fun `dpad is 8-way with a centre dead zone`() {
        assertEquals(emptySet<Int>(), PadGeometry.dpad(0.5f, 0.5f))
        assertEquals(setOf(up), PadGeometry.dpad(0.5f, 0.05f))
        assertEquals(setOf(right), PadGeometry.dpad(0.95f, 0.55f))
        assertEquals(setOf(up, right), PadGeometry.dpad(0.9f, 0.1f))
        assertEquals(setOf(down, left), PadGeometry.dpad(0.1f, 0.9f))
    }

    @Test fun `face cluster picks the nearest button, gaps included`() {
        assertEquals(PadButton.Y.keyCode, PadGeometry.face(0.5f, 0.1f))
        assertEquals(PadButton.A.keyCode, PadGeometry.face(0.5f, 0.95f))
        assertEquals(PadButton.X.keyCode, PadGeometry.face(0.05f, 0.5f))
        assertEquals(PadButton.B.keyCode, PadGeometry.face(0.9f, 0.5f))
        // gap between A (bottom) and B (right), nearer to B
        assertEquals(PadButton.B.keyCode, PadGeometry.face(0.85f, 0.75f))
    }
}

class LibraryFilterTest {
    private fun g(id: Long, title: String, played: Long? = null, fav: Boolean = false, hb: Boolean = false) =
        GameEntity(id = id, uri = "u$id", fileName = "f", format = "iso", sizeBytes = 1, title = title, gameHash = "h", lastPlayedAt = played, favorite = fav, isHomebrew = hb)

    private val games = listOf(g(1, "Alpha", played = 10), g(2, "Beta", fav = true), g(3, "Gamma", played = 20, hb = true))

    @Test fun `filters and search`() {
        assertEquals(listOf(3L, 1L), LibraryViewModel.filterGames(games, LibraryFilter.RECENT, "").map { it.id })
        assertEquals(listOf(2L), LibraryViewModel.filterGames(games, LibraryFilter.FAVORITES, "").map { it.id })
        assertEquals(listOf(3L), LibraryViewModel.filterGames(games, LibraryFilter.HOMEBREW, "").map { it.id })
        assertEquals(listOf(1L), LibraryViewModel.filterGames(games, LibraryFilter.ALL, "alp").map { it.id })
        assertTrue(LibraryViewModel.filterGames(games, LibraryFilter.ALL, "zzz").isEmpty())
    }
}
