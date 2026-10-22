package amd.anik.ocdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerOriginTest {

    @Test
    fun defaultPortsAreEquivalent() {
        assertTrue(ServerOrigin.same("https://example.com", "https://example.com:443"))
        assertTrue(ServerOrigin.same("http://example.com", "http://example.com:80"))
    }

    @Test
    fun schemeHostAndPortAreAllPartOfTheOrigin() {
        assertFalse(ServerOrigin.same("https://example.com", "http://example.com"))
        assertFalse(ServerOrigin.same("https://example.com", "https://example.com:8443"))
        assertFalse(ServerOrigin.same("https://example.com", "https://example.com.evil"))
        assertFalse(ServerOrigin.same("https://example.com", "https://other.example.com"))
    }

    @Test
    fun pathsQueriesAndFragmentsAreIgnored() {
        assertTrue(
            ServerOrigin.same(
                "https://example.com",
                "https://example.com:443/server/abc/session/ses_1?tab=1#top",
            ),
        )
    }

    @Test
    fun hostsAreCaseInsensitiveAndTrailingDotsAreIgnored() {
        assertTrue(ServerOrigin.same("https://EXAMPLE.com.", "https://example.com"))
    }

    @Test
    fun ipv6HostsAreNormalized() {
        assertTrue(ServerOrigin.same("http://[::1]:4096", "http://[::1]:4096/"))
        assertEquals("::1", ServerOrigin.host("http://[::1]:4096"))
    }

    @Test
    fun nonHttpSchemesAreRejected() {
        assertNull(ServerOrigin.normalize("mailto:dev@example.com"))
        assertFalse(ServerOrigin.same("mailto:dev@example.com", "mailto:dev@example.com"))
    }

    @Test
    fun invalidValuesNeverMatch() {
        assertFalse(ServerOrigin.same(null, "https://example.com"))
        assertFalse(ServerOrigin.same("", "https://example.com"))
        assertFalse(ServerOrigin.same("not a url", "https://example.com"))
    }
}
