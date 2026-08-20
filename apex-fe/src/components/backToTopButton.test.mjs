import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const appSource = await readFile(new URL('../App.vue', import.meta.url), 'utf8')
const buttonSource = await readFile(new URL('./BackToTopButton.vue', import.meta.url), 'utf8')

test('application shell mounts one shared back-to-top control', () => {
  assert.match(appSource, /import BackToTopButton from '.\/components\/BackToTopButton\.vue'/)
  assert.match(appSource, /<main[\s\S]*?<RouterView \/>[\s\S]*?<BackToTopButton \/>[\s\S]*?<\/main>/)
})

test('back-to-top control appears after one viewport and scrolls to the page start', () => {
  assert.match(buttonSource, /scrollTop > window\.innerHeight/)
  assert.match(buttonSource, /window\.addEventListener\('scroll', syncVisibility, \{ passive: true \}\)/)
  assert.match(buttonSource, /window\.removeEventListener\('scroll', syncVisibility\)/)
  assert.match(buttonSource, /window\.scrollTo\(\{ top: 0, behavior \}\)/)
  assert.match(buttonSource, /aria-label="回到顶部"/)
})

test('back-to-top control stays restrained and clears the lower floating action', () => {
  assert.match(buttonSource, /\.back-to-top-button\s*\{[\s\S]*?bottom:\s*calc\(68px \+ env\(safe-area-inset-bottom\)\);/)
  assert.match(buttonSource, /\.back-to-top-button\s*\{[\s\S]*?width:\s*40px;[\s\S]*?height:\s*40px;/)
  assert.match(buttonSource, /\.back-to-top-button\s*\{[\s\S]*?opacity:\s*0\.78;/)
  assert.match(buttonSource, /background:\s*rgba\(255, 255, 255, 0\.82\);/)
})
