package nirmal.auric.music.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtMusicSessionProbeTest {
    @Test
    fun parsesWebViewEncodedProbe() {
        val raw = "\"{\\\"loggedIn\\\":true,\\\"visitorData\\\":\\\"visitor\\\"," +
            "\\\"dataSyncId\\\":\\\"sync||extra\\\",\\\"delegatedSessionId\\\":\\\"channel\\\"}\""

        val probe = parseYtMusicSessionProbe(raw)

        assertTrue(probe?.loggedIn == true)
        assertEquals("visitor", probe?.visitorData)
        assertEquals("channel", probe?.delegatedSessionId)
    }

    @Test
    fun rejectsMissingProbe() {
        assertNull(parseYtMusicSessionProbe(null))
        assertNull(parseYtMusicSessionProbe("null"))
    }
}
