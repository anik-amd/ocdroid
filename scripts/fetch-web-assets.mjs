#!/usr/bin/env node
/**
 * Mirrors the official OpenCode web client into app/src/main/assets/web.
 *
 * The WebView keeps the server origin, so index.html, /_assets/*, icons and fonts all
 * resolve locally while /api/* still goes to the real server. Re-run this whenever the
 * server's web client changes (the CI workflow does it on every build).
 */
import fs from "node:fs";
import path from "node:path";

const BASE = (process.env.OC_SERVER_URL || "https://10e54ea9992ef31e.zgpcurbw6kr6.opentunnel.xyz").replace(/\/+$/, "");
const OUT = path.resolve("app/src/main/assets/web");
const USERNAME = process.env.OC_SERVER_USERNAME || "opencode";
const PASSWORD = process.env.OC_SERVER_PASSWORD || "";

const headers = { "User-Agent": "OCDroid-bundler/1.0" };
if (PASSWORD) {
  headers.Authorization = "Basic " + Buffer.from(`${USERNAME}:${PASSWORD}`).toString("base64");
}

const seen = new Set();
const queue = [];
let bytes = 0;

function enqueue(url, force = false) {
  let parsed;
  try {
    parsed = new URL(url, BASE + "/");
  } catch {
    return;
  }
  if (parsed.origin !== new URL(BASE).origin) return;
  if (parsed.pathname === "/" || parsed.pathname === "") return;
  if (parsed.pathname.startsWith("/api/")) return;
  const key = parsed.pathname;
  if (seen.has(key) && !force) return;
  seen.add(key);
  queue.push(parsed);
}

function localPath(url) {
  const clean = decodeURIComponent(url.pathname).replace(/^\/+/, "");
  if (clean.includes("..")) throw new Error(`unsafe path: ${clean}`);
  return path.join(OUT, clean);
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
    enqueue(new URL(ref, cssUrl).toString());
  }
}

function extractHtmlRefs(html) {
  for (const tag of html.match(/<(?:script|link)\b[^>]*>/gi) || []) {
    const attr = tag.match(/\b(?:src|href)\s*=\s*"([^"]+)"/i);
    if (attr) enqueue(new URL(attr[1], BASE + "/").toString());
  }
}

async function download(url) {
  const response = await fetch(url, { headers, redirect: "follow" });
  if (!response.ok) throw new Error(`HTTP ${response.status} for ${url.pathname}`);
  const buffer = Buffer.from(await response.arrayBuffer());
  const file = localPath(url);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, buffer);
  bytes += buffer.length;

  const type = response.headers.get("content-type") || "";
  if (type.includes("text/css") || url.pathname.endsWith(".css")) {
    extractCssRefs(buffer.toString("utf8"), url.toString());
  }
  if (type.includes("application/manifest+json") || url.pathname.endsWith(".webmanifest")) {
    try {
      const manifest = JSON.parse(buffer.toString("utf8"));
      for (const icon of manifest.icons || []) {
        if (icon.src) enqueue(new URL(icon.src, url).toString());
      }
    } catch {
      /* ignore malformed manifest */
    }
  }
}

async function main() {
  fs.rmSync(OUT, { recursive: true, force: true });
  fs.mkdirSync(OUT, { recursive: true });

  const indexUrl = new URL(BASE + "/");
  const indexResponse = await fetch(indexUrl, { headers });
  if (!indexResponse.ok) throw new Error(`index HTTP ${indexResponse.status}`);
  const indexHtml = await indexResponse.text();
  fs.writeFileSync(path.join(OUT, "index.html"), indexHtml);
  extractHtmlRefs(indexHtml);

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
  console.log(`Bundled ${seen.size + 1} files (${(bytes / 1024 / 1024).toFixed(1)} MiB) from ${BASE}`);
  console.log(`Main script: ${mainScript}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
