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
     * Adds the command-palette entry point to the official titlebar.
     *
     * The official client already binds `Ctrl+K` / `Ctrl+Shift+P` to its palette, but a phone
     * keyboard has no Ctrl key. This injects a normal icon button into the titlebar's own
     * right-hand slot and dispatches the same keyboard event the official command handler
     * listens for. Nothing is re-skinned: the button uses the official icon-button component
     * and its theme variables, so light/dark and future theme changes carry over.
     */
    val COMMAND_PALETTE = """
        (function () {
          if (window.__ocdroidCommandPalette) return;
          window.__ocdroidCommandPalette = true;

          function dialogOpen() {
            // The mobile layout keeps its closed bottom drawer in the DOM with
            // role="dialog". Treating every element with that role as an open dialog
            // hid the button forever on phones, so only count dialogs that are
            // actually expanded and rendered.
            var nodes = document.querySelectorAll('[role="dialog"], [data-component="dialog"]');
            for (var i = 0; i < nodes.length; i++) {
              var node = nodes[i];
              if (node.hasAttribute('data-closed') || node.closest('[data-closed]')) continue;
              var rect = node.getBoundingClientRect();
              if (rect.width <= 1 || rect.height <= 1) continue;
              var style = window.getComputedStyle(node);
              if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') continue;
              return true;
            }
            return false;
          }

          function press(key, code, ctrlKey, shiftKey) {
            var event = new KeyboardEvent('keydown', {
              key: key,
              code: code,
              ctrlKey: ctrlKey,
              shiftKey: shiftKey,
              bubbles: true,
              cancelable: true,
              composed: true
            });
            document.dispatchEvent(event);
          }

          function openPalette() {
            if (dialogOpen()) return;
            press('k', 'KeyK', true, false);
            window.setTimeout(function () {
              if (!dialogOpen()) press('p', 'KeyP', true, true);
            }, 250);
          }

          function ensureButton() {
            var host = document.getElementById('opencode-titlebar-right');
            if (!host) return;
            var button = document.getElementById('ocdroid-command-palette');
            if (!button) {
              button = document.createElement('button');
              button.id = 'ocdroid-command-palette';
              button.type = 'button';
              button.setAttribute('data-component', 'icon-button-v2');
              button.setAttribute('data-size', 'normal');
              button.setAttribute('data-variant', 'ghost-muted');
              button.setAttribute('aria-label', 'Open command palette');
              button.title = 'Command palette';
              button.style.marginInlineStart = '2px';
              button.style.marginLeft = '2px';
              button.innerHTML = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" data-slot="icon-svg"><path d="M6 6H4.75a1.75 1.75 0 1 1 1.75-1.75V6Zm0 0v4m0-4h4m-4 4H4.75A1.75 1.75 0 1 0 6.5 11.75V10Zm4-4V4.25A1.75 1.75 0 1 1 11.75 6H10Zm0 0v4m0-4h1.75A1.75 1.75 0 1 1 10 11.75V10Z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
              button.addEventListener('click', function (event) {
                event.preventDefault();
                event.stopPropagation();
                openPalette();
              });
            }
            if (button.parentNode !== host) host.appendChild(button);
            button.style.display = dialogOpen() ? 'none' : '';
          }

          window.setTimeout(ensureButton, 0);
          window.setTimeout(ensureButton, 500);
          window.setInterval(ensureButton, 1000);
          window.addEventListener('popstate', ensureButton);
          window.addEventListener('hashchange', ensureButton);
        })();
    """.trimIndent()

    /**
     * Bridges the official web client back to Android:
     *
     *  - It follows the official route so the activity knows which conversation is open.
     *  - It reads the token the official login flow stored, so the native monitor uses the
     *    same credentials as the web client.
     *
     * The login itself stays entirely inside the official UI, on the server site or on the
     * bundled login page (file://) when no server is configured yet.
     */
    val nativeBridge: String = """
        (function () {
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
        })();
    """.trimIndent()
}
