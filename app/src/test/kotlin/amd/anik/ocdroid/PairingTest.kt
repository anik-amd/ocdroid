package amd.anik.ocdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {

    @Test
    fun parsesRemotePairingLink() {
        val payload = Pairing.parse(
            "https://abc123.opentunnel.xyz/auth/connect/CODE_abc-123",
        )
        assertNotNull(payload)
        assertEquals("CODE_abc-123", payload!!.code)
        assertEquals(listOf("https://abc123.opentunnel.xyz"), payload.urls)
    }

    @Test
    fun parsesQrPayloadWithSeveralAddresses() {
        val payload = Pairing.parse(
            """{"code":"abc_123","urls":["http://192.168.1.5:4096","https://abc123.opentunnel.xyz"]}""",
        )
        assertNotNull(payload)
        assertEquals("abc_123", payload!!.code)
        assertEquals(
            listOf("http://192.168.1.5:4096", "https://abc123.opentunnel.xyz"),
            payload.urls,
        )
    }

    @Test
    fun removesDuplicateAddresses() {
        val payload = Pairing.parse(
            """{"code":"abc","urls":["http://192.168.1.5:4096/","http://192.168.1.5:4096"]}""",
        )
        assertEquals(listOf("http://192.168.1.5:4096"), payload!!.urls)
    }

    @Test
    fun rejectsMalformedInput() {
        assertNull(Pairing.parse("not a link"))
        assertNull(Pairing.parse("ftp://example.com/auth/connect/abc"))
        assertNull(Pairing.parse("https://example.com/auth/connect/"))
        assertNull(Pairing.parse("https://example.com/auth/connect/bad/code"))
        assertNull(Pairing.parse("""{"code":"abc"}"""))
        assertNull(Pairing.parse("""{"code":"","urls":["https://example.com"]}"""))
        assertNull(Pairing.parse("""{"code":"abc","urls":[]}"""))
    }

    @Test
    fun leavesOrdinaryServerUrlsAlone() {
        assertNull(Pairing.parse("https://example.com"))
        assertNull(Pairing.parse("http://192.168.1.5:4096"))
    }
}
