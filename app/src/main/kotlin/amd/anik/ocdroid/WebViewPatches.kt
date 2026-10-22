package amd.anik.ocdroid

/**
 * Tiny compatibility patches for the official web client.
 *
 * The official composer sends a prompt on plain Enter. On a phone keyboard that makes
 * Enter nearly useless for writing multi-line prompts, so keep the browser's normal
 * contenteditable line break and stop the event before the composer's submit handler.
 */
object WebViewPatches {

    val ENTER_INSERTS_NEWLINE = """
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

    /**
     * Bridges the official web client back to Android:
     *
     *  - It follows the official route so the activity knows which conversation is open.
     *  - It reads the token the official login flow stored, so the native monitor uses the
     *    same credentials as the web client.
     *  - When the user pasted a pairing link into the native field, it fills the official
     *    login form and submits it. The login itself stays entirely inside the official UI.
     *
     * @param pendingLink JSON-quoted raw pairing link or QR payload, or "" for none.
     */
    fun nativeBridge(pendingLink: String): String = """
        (function () {
          var pending = $pendingLink;
          var bridge = window.OCDroidNative;

          if (!window.__ocdroidBridgeInstalled) {
            window.__ocdroidBridgeInstalled = true;
            var lastCredential = '';
            var lastSession = null;

            function reportCredentials() {
              if (!bridge || !window.localStorage) return;
              try {
                var raw = localStorage.getItem('opencode.global.dat:server');
                if (!raw) return;
                var state = JSON.parse(raw);
                var list = state && state.list;
                if (!Array.isArray(list)) return;
                var candidate = null;
                for (var i = 0; i < list.length; i++) {
                  var entry = list[i];
                  var http = entry && entry.http ? entry.http : entry;
                  if (!http || typeof http.url !== 'string') continue;
                  if (typeof http.password !== 'string' || !http.password) continue;
                  var host = '';
                  try { host = new URL(http.url).host; } catch (ignore) {}
                  if (host && host === location.host) {
                    candidate = http;
                    break;
                  }
                  if (!candidate) candidate = http;
                }
                if (!candidate) return;
                var key = candidate.url + '|' + candidate.password;
                if (key === lastCredential) return;
                lastCredential = key;
                bridge.onServerConnected(candidate.url, candidate.password);
              } catch (ignore) {}
            }

            function reportSession() {
              if (!bridge) return;
              var match = /\/server\/[^\/]+\/session\/([^\/?#]+)/.exec(location.pathname);
              if (match) {
                var id = match[1];
                try { id = decodeURIComponent(id); } catch (ignore) {}
                if (id !== lastSession) {
                  lastSession = id;
                  bridge.onSessionOpened(id);
                }
              } else if (lastSession !== null) {
                lastSession = null;
                bridge.onSessionClosed();
              }
            }

            setInterval(reportCredentials, 1000);
            setInterval(reportSession, 500);
            window.addEventListener('popstate', reportSession);
            window.addEventListener('hashchange', reportSession);
            setTimeout(reportCredentials, 0);
            setTimeout(reportSession, 0);
          }

          if (pending) {
            var attempts = 0;
            var timer = setInterval(function () {
              var input = document.querySelector('#server-connect-link');
              if (!input) {
                if (++attempts > 60) clearInterval(timer);
                return;
              }
              clearInterval(timer);
              try {
                var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                setter.call(input, pending);
                input.dispatchEvent(new Event('input', { bubbles: true }));
              } catch (ignore) {
                input.value = pending;
                input.dispatchEvent(new Event('input', { bubbles: true }));
              }
              // Solid has to re-render the submit button after the input event.
              setTimeout(function () {
                var form = input.closest('form');
                var button = form && form.querySelector('button[type="submit"]');
                if (button) button.click();
              }, 500);
            }, 250);
            setTimeout(function () { clearInterval(timer); }, 15000);
          }
        })();
    """.trimIndent()
}
