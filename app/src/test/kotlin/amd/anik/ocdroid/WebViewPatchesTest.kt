package amd.anik.ocdroid

import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewPatchesTest {

    @Test
    fun commandPaletteUsesTheOfficialTitlebarSlot() {
        val script = WebViewPatches.COMMAND_PALETTE
        assertTrue(script.contains("opencode-titlebar-right"))
        assertTrue(script.contains("ocdroid-command-palette"))
        assertTrue(script.contains("icon-button-v2"))
        assertTrue(script.contains("ghost-muted"))
        assertTrue(script.contains("aria-label"))
    }

    @Test
    fun commandPaletteDispatchesTheOfficialShortcut() {
        val script = WebViewPatches.COMMAND_PALETTE
        assertTrue(script.contains("KeyboardEvent"))
        assertTrue(script.contains("ctrlKey:"))
        assertTrue(script.contains("shiftKey:"))
        assertTrue(script.contains("'k', 'KeyK', true, false"))
        assertTrue(script.contains("'p', 'KeyP', true, true"))
    }

    @Test
    fun commandPaletteDoesNotStackOnTopOfAnOpenDialog() {
        val script = WebViewPatches.COMMAND_PALETTE
        assertTrue(script.contains("[role=\"dialog\"]"))
        // The mobile layout keeps its closed bottom drawer in the DOM with role="dialog";
        // the patch must ignore elements that are not actually rendered and expanded,
        // otherwise the button is hidden forever on phones.
        assertTrue(script.contains("data-closed"))
        assertTrue(script.contains("getBoundingClientRect"))
        assertTrue(script.contains("getComputedStyle"))
    }
}
