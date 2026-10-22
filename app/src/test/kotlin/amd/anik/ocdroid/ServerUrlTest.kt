package amd.anik.ocdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun publicHostsDefaultToHttps() {
        assertEquals("https://example.com", normalizeServerUrl("example.com"))
        assertEquals("https://example.com", normalizeServerUrl("https://example.com/"))
        assertEquals("https://example.com:8443", normalizeServerUrl("example.com:8443"))
    }

    @Test
    fun privateHostsDefaultToHttp() {
        assertEquals("http://192.168.1.5:4096", normalizeServerUrl("192.168.1.5:4096"))
        assertEquals("http://10.0.0.7", normalizeServerUrl("10.0.0.7"))
        assertEquals("http://172.20.1.2:4096", normalizeServerUrl("172.20.1.2:4096"))
        assertEquals("http://opencode.local:4096", normalizeServerUrl("opencode.local:4096"))
        assertEquals("http://localhost:4096", normalizeServerUrl("localhost:4096"))
    }

    @Test
    fun explicitSchemesArePreserved() {
        assertEquals("http://example.com", normalizeServerUrl("http://example.com"))
        assertEquals("https://192.168.1.5:4096", normalizeServerUrl("https://192.168.1.5:4096"))
    }

    @Test
    fun pathsQueriesAndFragmentsAreDropped() {
        assertEquals(
            "https://example.com:8443",
            normalizeServerUrl("https://example.com:8443/some/path?q=1#frag"),
        )
    }

    @Test
    fun invalidValuesAreRejected() {
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("   "))
        assertNull(normalizeServerUrl("ftp://example.com"))
        assertNull(normalizeServerUrl("https://user:pass@example.com"))
        assertNull(normalizeServerUrl("https://"))
        assertNull(normalizeServerUrl("https://exa mple.com"))
    }
}
