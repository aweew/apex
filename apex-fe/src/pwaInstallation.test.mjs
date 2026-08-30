import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const indexSource = await readFile(new URL('../index.html', import.meta.url), 'utf8')
const mainSource = await readFile(new URL('./main.js', import.meta.url), 'utf8')
const appSource = await readFile(new URL('./App.vue', import.meta.url), 'utf8')
const sharedStyles = await readFile(new URL('./style.css', import.meta.url), 'utf8')

test('mobile viewport fills browser safe areas and follows dynamic toolbar height', () => {
  assert.match(
    indexSource,
    /<meta name="viewport" content="width=device-width, initial-scale=1\.0, viewport-fit=cover" \/>/,
  )
  assert.match(appSource, /\.shell\s*\{[^}]*min-height:\s*100vh;[^}]*min-height:\s*100dvh;/)
  assert.match(
    sharedStyles,
    /\.page\s*\{[^}]*min-height:\s*calc\(100vh - 56px\);[^}]*min-height:\s*calc\(100dvh - 56px\);/,
  )
})

test('PWA metadata launches Apex in standalone mode with complete icons', async () => {
  assert.match(indexSource, /<link rel="manifest" href="\/manifest\.webmanifest" \/>/)
  assert.match(indexSource, /<meta name="theme-color" content="#ffffff" \/>/)
  assert.match(indexSource, /<meta name="apple-mobile-web-app-capable" content="yes" \/>/)
  assert.match(indexSource, /<link rel="apple-touch-icon" sizes="180x180" href="\/pwa\/apple-touch-icon-180\.png" \/>/)

  const manifest = JSON.parse(
    await readFile(new URL('../public/manifest.webmanifest', import.meta.url), 'utf8'),
  )
  assert.equal(manifest.name, '灵极 Apex')
  assert.equal(manifest.display, 'standalone')
  assert.equal(manifest.start_url, '/')
  assert.equal(manifest.scope, '/')

  const expectedIcons = new Map([
    ['/pwa/icon-192.png', [192, 192]],
    ['/pwa/icon-512.png', [512, 512]],
  ])
  for (const icon of manifest.icons) {
    if (!expectedIcons.has(icon.src)) continue
    const image = await readFile(new URL(`../public${icon.src}`, import.meta.url))
    assert.equal(image.toString('ascii', 1, 4), 'PNG')
    assert.deepEqual([image.readUInt32BE(16), image.readUInt32BE(20)], expectedIcons.get(icon.src))
    expectedIcons.delete(icon.src)
  }
  assert.equal(expectedIcons.size, 0)
})

test('production registers a network-only service worker without caching business data', async () => {
  const serviceWorkerSource = await readFile(new URL('../public/sw.js', import.meta.url), 'utf8')
  const nginxConfig = await readFile(new URL('../nginx.conf', import.meta.url), 'utf8')

  assert.match(mainSource, /'serviceWorker' in navigator && import\.meta\.env\.PROD/)
  assert.match(mainSource, /navigator\.serviceWorker\.register\('\/sw\.js', \{ updateViaCache: 'none' \}\)/)
  assert.doesNotMatch(serviceWorkerSource, /caches\.|CacheStorage/)
  assert.match(serviceWorkerSource, /event\.respondWith\(fetch\(event\.request\)\)/)
  assert.match(
    nginxConfig,
    /location = \/sw\.js\s*\{[^}]*add_header Cache-Control "no-store, no-cache, must-revalidate" always;/,
  )
  assert.match(
    nginxConfig,
    /location = \/manifest\.webmanifest\s*\{[^}]*add_header Cache-Control "no-store, no-cache, must-revalidate" always;/,
  )
})
