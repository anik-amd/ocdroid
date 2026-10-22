package amd.anik.ocdroid

/**
 * Tiny compatibility patches for the official web client.
 *
 * The official composer sends a prompt on plain Enter. On a phone keyboard that makes
 * Enter nearly useless for writing multi-line prompts, so keep the browser's normal
 * contenteditable line break and stop the event before the composer's submit handler.
 */
object WebViewPatches {

    const val ENTER_INSERTS_NEWLINE = """
        (function () {
          if (window.__ocdroidEnterNewline) return;
          window.__ocdroidEnterNewline = true;
          window.addEventListener('keydown', function (event) {
            if (event.key !== 'Enter' && event.keyCode !== 13) return;
            if (event.shiftKey || event.ctrlKey || event.metaKey || event.altKey || event.isComposing) return;
            var target = event.target;
            if (!target || !target.closest) return;
            if (!target.closest('[data-component="composer-editor"]')) return;
            // No preventDefault: the browser inserts a real line break and fires input,
            // so the official composer keeps its draft state, paste handling and undo.
            event.stopImmediatePropagation();
          }, true);
        })();
    """.trimIndent()
}
