#!/usr/bin/env node
/**
 * Mirrors the official OpenCode web client into app/src/main/assets/web.
 *
 * The WebView keeps the server origin, so index.html, /_assets/*, icons and fonts all
 * resolve locally while /api/* still goes to the real server. Re-run this whenever the
 * server's web client changes.
 *
 * OC_SERVER_URL is required. It is intentionally not committed: a tunnel hostname is part
 * of the maintainer's private infrastructure, not part of a public Android client. In CI
 * the value comes from a repository secret.
 */
import fs from "node:fs";
import path from "node:path";

const RAW_BASE = (process.env.OC_SERVER_URL || "").trim();
if (!RAW_BASE) {
  console.error("OC_SERVER_URL is required, for example https://your-server.example");
  process.exit(1);
}

let base;
try {
  base = new URL(RAW_BASE);
} catch {
  console.error("OC_SERVER_URL must be a valid http:// or https:// URL");
  process.exit(1);
}
if (base.protocol !== "http:" && base.protocol !== "https:") {
  console.error("OC_SERVER_URL must use http:// or https://");
  process.exit(1);
}
if (base.username || base.password) {
  console.error("Do not put credentials in OC_SERVER_URL; use OC_SERVER_USERNAME/PASSWORD");
  process.exit(1);
}

const ORIGIN = base.origin;
const OUT = path.resolve("app/src/main/assets/web");
const USERNAME = process.env.OC_SERVER_USERNAME || "opencode";
const PASSWORD = process.env.OC_SERVER_PASSWORD || "";

const headers = { "User-Agent": "OCDroid-bundler/1.0" };
if (PASSWORD) {
  headers.Authorization = "Basic " + Buffer.from(`${USERNAME}:${PASSWORD}`).toString("base64");
}

// Crawl only the static shell. In particular, never mirror /api/* or arbitrary server pages:
// they can contain session data, prompts or credentials and must not end up in an APK.
const ALLOWED_PREFIXES = ["/_assets/", "/assets/", "/icons/", "/fonts/"];
const ALLOWED_FILES = new Set([
  "/site.webmanifest",
  "/social-share.png",
  "/favicon.ico",
  "/apple-touch-icon.png",
]);
const MAX_FILE_BYTES = 25 * 1024 * 1024;
const MAX_TOTAL_BYTES = 150 * 1024 * 1024;
const MAX_INDEX_BYTES = 2 * 1024 * 1024;
const MAX_REDIRECTS = 3;

const seen = new Set();
const queue = [];
let bytes = 0;

function isAllowedPath(pathname) {
  if (pathname === "/") return true;
  if (ALLOWED_FILES.has(pathname)) return true;
  return ALLOWED_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

function enqueue(url) {
  let parsed;
  try {
    parsed = url instanceof URL ? url : new URL(url, ORIGIN + "/");
  } catch {
    return;
  }
  if (parsed.origin !== ORIGIN) return;
  if (!isAllowedPath(parsed.pathname)) return;
  const key = parsed.pathname;
  if (seen.has(key)) return;
  seen.add(key);
  queue.push(parsed);
}

function localPath(url) {
  let clean;
  try {
    clean = decodeURIComponent(url.pathname).replace(/^\/+/, "");
  } catch {
    throw new Error(`unsafe path: ${url.pathname}`);
  }
  if (!clean || clean.includes("\0") || clean.includes("\\")) {
    throw new Error(`unsafe path: ${url.pathname}`);
  }
  const resolved = path.resolve(OUT, clean);
  if (resolved !== OUT && !resolved.startsWith(OUT + path.sep)) {
    throw new Error(`unsafe path: ${url.pathname}`);
  }
  return resolved;
}

function extractCssRefs(css, cssUrl) {
  const refs = [];
  for (const match of css.matchAll(/url\(\s*['"]?([^'")]+)['"]?\s*\)/g)) {
    refs.push(match[1]);
  }
  for (const match of css.matchAll(/@import\s+(?:url\()?['"]([^'"]+)['"]/g)) {
    refs.push(match[1]);
  }
  for (const ref of refs) {
    if (!ref || ref.startsWith("data:") || ref.startsWith("#")) continue;
    try {
      enqueue(new URL(ref, cssUrl));
    } catch {
      /* ignore malformed references */
    }
  }
}

function extractHtmlRefs(html, documentUrl) {
  for (const tag of html.match(/<(?:script|link)\b[^>]*>/gi) || []) {
    const attr = tag.match(/\b(?:src|href)\s*=\s*["']([^"']+)["']/i);
    if (!attr) continue;
    try {
      enqueue(new URL(attr[1], documentUrl));
    } catch {
      /* ignore malformed references */
    }
  }
}

/**
 * Follow only same-origin redirects. Node's default `redirect: "follow"` would let the
 * configured server redirect the bundler to an unrelated host while carrying the Basic
 * auth header and writing that host's bytes into the APK.
 */
async function fetchSameOrigin(startUrl) {
  let current = startUrl instanceof URL ? startUrl : new URL(startUrl);
  for (let redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
    const response = await fetch(current, { headers, redirect: "manual" });
    if (response.status < 300 || response.status >= 400) {
      return { response, url: current };
    }
    const location = response.headers.get("location");
    if (!location) throw new Error(`redirect without Location for ${current.pathname}`);
    const next = new URL(location, current);
    if (next.origin !== ORIGIN) throw new Error("cross-origin redirect blocked");
    if (!isAllowedPath(next.pathname)) throw new Error(`redirect leaves static shell: ${next.pathname}`);
    current = next;
  }
  throw new Error("too many redirects");
}

async function download(url) {
  const { response, url: finalUrl } = await fetchSameOrigin(url);
  if (!response.ok) throw new Error(`HTTP ${response.status} for ${finalUrl.pathname}`);
  if (!isAllowedPath(finalUrl.pathname)) throw new Error(`not a static asset: ${finalUrl.pathname}`);

  const buffer = Buffer.from(await response.arrayBuffer());
  if (buffer.length > MAX_FILE_BYTES) throw new Error(`file too large: ${finalUrl.pathname}`);
  if (bytes + buffer.length > MAX_TOTAL_BYTES) throw new Error("bundle size limit exceeded");

  const file = localPath(finalUrl);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, buffer);
  bytes += buffer.length;
  seen.add(finalUrl.pathname);

  const type = response.headers.get("content-type") || "";
  if (type.includes("text/css") || finalUrl.pathname.endsWith(".css")) {
    extractCssRefs(buffer.toString("utf8"), finalUrl);
  }
  if (type.includes("application/manifest+json") || finalUrl.pathname.endsWith(".webmanifest")) {
    try {
      const manifest = JSON.parse(buffer.toString("utf8"));
      for (const icon of manifest.icons || []) {
        if (icon.src) enqueue(new URL(icon.src, finalUrl));
      }
    } catch {
      /* ignore malformed manifest */
    }
  }
}

async function main() {
  fs.rmSync(OUT, { recursive: true, force: true });
  fs.mkdirSync(OUT, { recursive: true });

  const indexUrl = new URL("/", ORIGIN);
  const { response: indexResponse, url: finalIndexUrl } = await fetchSameOrigin(indexUrl);
  if (!indexResponse.ok) throw new Error(`index HTTP ${indexResponse.status}`);
  const contentType = indexResponse.headers.get("content-type") || "";
  if (!contentType.includes("text/html")) {
    throw new Error("OC_SERVER_URL did not return an HTML web client");
  }
  const indexHtml = await indexResponse.text();
  if (indexHtml.length > MAX_INDEX_BYTES) throw new Error("index.html is unexpectedly large");
  fs.writeFileSync(path.join(OUT, "index.html"), indexHtml);
  extractHtmlRefs(indexHtml, finalIndexUrl);

  const mainScript = indexHtml.match(/src="(\/_assets\/index-[^"]+\.js)"/)?.[1] ?? null;

  while (queue.length) {
    const batch = queue.splice(0, 8);
    await Promise.all(batch.map((url) => download(url).catch((error) => {
      console.warn(`skip ${url.pathname}: ${error.message}`);
    })));
  }

  fs.writeFileSync(
    path.join(OUT, "bundle.json"),
    JSON.stringify({ builtAt: new Date().toISOString(), mainScript, files: seen.size + 1 }, null, 2),
  );
  console.log(`Bundled ${seen.size + 1} files (${(bytes / 1024 / 1024).toFixed(1)} MiB) from the configured server.`);
  console.log(`Main script: ${mainScript}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
