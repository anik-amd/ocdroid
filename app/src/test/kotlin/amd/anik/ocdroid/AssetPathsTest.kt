package amd.anik.ocdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssetPathsTest {

    @Test
    fun mapsStaticFilesBelowWebAssets() {
        assertEquals("web/index.html", AssetPaths.underWeb("index.html"))
        assertEquals("web/_assets/app.js", AssetPaths.underWeb("/_assets/app.js"))
        assertEquals("web/icons/icon.png", AssetPaths.underWeb("icons/icon.png"))
    }

    @Test
    fun rejectsDotSegmentsAndEmptySegments() {
        assertNull(AssetPaths.underWeb("../secret"))
        assertNull(AssetPaths.underWeb("/../secret"))
        assertNull(AssetPaths.underWeb("a/../b"))
        assertNull(AssetPaths.underWeb("a/./b"))
        assertNull(AssetPaths.underWeb("a//b"))
        assertNull(AssetPaths.underWeb("./index.html"))
    }

    @Test
    fun rejectsBackslashesAndEncodedTraversal() {
        assertNull(AssetPaths.underWeb("a\\b"))
        assertNull(AssetPaths.underWeb("%2e%2e/secret"))
        assertNull(AssetPaths.underWeb("a/%2E%2E/secret"))
        assertNull(AssetPaths.underWeb("a/%2fsecret"))
    }

    @Test
    fun rejectsEmptyInput() {
        assertNull(AssetPaths.underWeb(null))
        assertNull(AssetPaths.underWeb(""))
        assertNull(AssetPaths.underWeb("/"))
    }
}
