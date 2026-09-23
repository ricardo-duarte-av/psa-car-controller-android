package pt.aguiarvieira.psacc.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.psacc.domain.model.ServerCapabilities
import pt.aguiarvieira.psacc.ui.feature.home.UpdateBanner
import pt.aguiarvieira.psacc.util.VersionMatch
import pt.aguiarvieira.psacc.util.Versions

class UpdatesTest {

    private val available = AppUpdateState.Available(versionCode = 19, immediateAllowed = true, flexibleAllowed = true)

    @Test
    fun `release numbers parse and compare`() {
        assertEquals(listOf(0, 1, 24), Versions.parse("v0.1.24"))
        assertEquals(listOf(0, 1, 24), Versions.parse("0.1.24-debug"))
        assertNull(Versions.parse("unknown"))
        assertNull(Versions.parse("0.1"))
        assertEquals(VersionMatch.SAME, Versions.compare("0.1.24", "0.1.24"))
        assertEquals(VersionMatch.APP_BEHIND, Versions.compare("0.1.24", "0.1.25"))
        assertEquals(VersionMatch.SERVER_BEHIND, Versions.compare("0.1.24", "0.1.9"))
        assertEquals(VersionMatch.APP_BEHIND, Versions.compare("0.9.0", "1.0.0"))
        // a daemon run from its sources
        assertEquals(VersionMatch.UNKNOWN, Versions.compare("0.1.24", "0.0.0"))
        assertEquals(VersionMatch.UNKNOWN, Versions.compare("0.1.24", null))
    }

    @Test
    fun `the server's release is compared once asked, and a fork without it is older`() {
        assertEquals(VersionMatch.UNKNOWN, ServerCapabilities(serverVersion = "0.1.30").versionMatch("0.1.24"))
        val fork = ServerCapabilities(commandResults = true, events = true, probed = true)
        assertEquals(VersionMatch.APP_BEHIND, fork.copy(serverVersion = "0.1.30").versionMatch("0.1.24"))
        assertEquals(VersionMatch.SERVER_BEHIND, fork.versionMatch("0.1.24"))
        // stock upstream psacc
        assertEquals(VersionMatch.UNKNOWN, ServerCapabilities(probed = true).versionMatch("0.1.24"))
    }

    @Test
    fun `play's full screen update when the server is ahead, once per launch`() {
        assertEquals(UpdatePrompt.IMMEDIATE, UpdatePolicy.prompt(available, VersionMatch.APP_BEHIND, 19, false))
        assertNull(UpdatePolicy.prompt(available, VersionMatch.APP_BEHIND, null, promptedImmediate = true))
    }

    @Test
    fun `otherwise the flexible one, once per version`() {
        assertEquals(UpdatePrompt.FLEXIBLE, UpdatePolicy.prompt(available, VersionMatch.SAME, 18, false))
        assertNull(UpdatePolicy.prompt(available, VersionMatch.SAME, 19, false))
        assertEquals(
            UpdatePrompt.FLEXIBLE,
            UpdatePolicy.prompt(available.copy(immediateAllowed = false), VersionMatch.APP_BEHIND, null, false),
        )
        assertNull(UpdatePolicy.prompt(AppUpdateState.None, VersionMatch.APP_BEHIND, null, false))
        assertNull(UpdatePolicy.prompt(AppUpdateState.Downloaded, VersionMatch.SAME, null, false))
    }

    @Test
    fun `the banner says the most pressing`() {
        assertEquals(UpdateBanner.Downloaded, UpdateBanner.of(AppUpdateState.Downloaded, VersionMatch.APP_BEHIND, "0.1.30", null))
        assertEquals(
            UpdateBanner.AppBehind("0.1.30", canUpdate = true),
            UpdateBanner.of(available, VersionMatch.APP_BEHIND, "0.1.30", null),
        )
        assertEquals(
            UpdateBanner.AppBehind("0.1.30", canUpdate = false),
            UpdateBanner.of(AppUpdateState.None, VersionMatch.APP_BEHIND, "0.1.30", null),
        )
        assertEquals(UpdateBanner.ServerBehind("0.1.23"), UpdateBanner.of(AppUpdateState.None, VersionMatch.SERVER_BEHIND, "0.1.23", null))
        val dismissed = UpdateBanner.noticeKey("0.1.23")
        assertNull(UpdateBanner.of(AppUpdateState.None, VersionMatch.SERVER_BEHIND, "0.1.23", dismissed))
        // a new server release brings the notice back
        assertEquals(UpdateBanner.ServerBehind("0.1.22"), UpdateBanner.of(AppUpdateState.None, VersionMatch.SERVER_BEHIND, "0.1.22", dismissed))
        assertNull(UpdateBanner.of(available, VersionMatch.SAME, "0.1.24", null))
    }
}
