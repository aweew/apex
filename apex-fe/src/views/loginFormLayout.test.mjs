import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const loginSource = await readFile(new URL('./LoginView.vue', import.meta.url), 'utf8')
const registerSource = await readFile(new URL('./RegisterView.vue', import.meta.url), 'utf8')
const authShellSource = await readFile(new URL('../components/auth/AuthShell.vue', import.meta.url), 'utf8')

test('login fields keep the expected keyboard focus order', () => {
  const phoneInputIndex = loginSource.indexOf('<el-input v-model="form.phone"')
  const passwordInputIndex = loginSource.indexOf('<el-input v-model="form.password"')
  const passwordHelpIndex = loginSource.indexOf('class="forgot-password"')

  assert.notEqual(phoneInputIndex, -1)
  assert.notEqual(passwordInputIndex, -1)
  assert.notEqual(passwordHelpIndex, -1)
  assert.ok(phoneInputIndex < passwordInputIndex)
  assert.ok(passwordInputIndex < passwordHelpIndex)
})

test('login phone input removes pasted whitespace before validation', () => {
  assert.match(loginSource, /function normalizePhone\(\)\s*\{\s*form\.phone = form\.phone\.replace\(\/\\s\/g, ''\)/)
  assert.match(loginSource, /<el-input v-model="form\.phone"[^>]*@input="normalizePhone"/)
})

test('login submission uses one keyboard path and blocks concurrent requests', () => {
  assert.match(loginSource, /<el-form[^>]*@submit\.prevent="submit"/)
  assert.doesNotMatch(loginSource, /@keyup\.enter="submit"/)

  const guardIndex = loginSource.indexOf('if (loading.value) return')
  const lockIndex = loginSource.indexOf('loading.value = true')
  const validateIndex = loginSource.indexOf('formRef.value.validate()')

  assert.notEqual(guardIndex, -1)
  assert.notEqual(lockIndex, -1)
  assert.notEqual(validateIndex, -1)
  assert.ok(guardIndex < lockIndex)
  assert.ok(lockIndex < validateIndex)
})

test('auth validation errors use a reserved slot without changing field height', () => {
  assert.match(
    authShellSource,
    /:deep\(\.el-form-item\)\s*\{[\s\S]*?position:\s*relative;[\s\S]*?padding-bottom:\s*20px;/,
  )
  assert.match(
    authShellSource,
    /:deep\(\.el-form-item__error\)\s*\{[\s\S]*?position:\s*absolute;[\s\S]*?bottom:\s*-20px;[\s\S]*?width:\s*100%;/,
  )
})

test('auth shell keeps the restrained premium surface contract', () => {
  assert.match(authShellSource, /--auth-font:[^;]*-apple-system/)
  assert.match(authShellSource, /background:\s*#f5f5f7;/)
  assert.match(authShellSource, /--field-bg:\s*#ffffff;/)
  assert.match(authShellSource, /\.el-input__wrapper\)\s*\{[\s\S]*?background:\s*var\(--field-bg\);/)
  assert.match(authShellSource, /\.el-input\.is-disabled \.el-input__wrapper\)\s*\{[\s\S]*?background:\s*#eeeeef;/)
  assert.match(authShellSource, /\.auth-page::before\s*\{[\s\S]*?repeating-linear-gradient\(/)
  assert.match(authShellSource, /\.apex-geometry\s*\{[^}]*right:\s*clamp\([^;]+;[^}]*bottom:\s*clamp\([^;]+;[^}]*display:\s*grid;/)
  assert.match(authShellSource, /@media \(max-width:\s*820px\)[\s\S]*?\.apex-geometry\s*\{\s*display:\s*none;/)
  assert.match(authShellSource, /\.auth-panel\s*\{[\s\S]*?border-radius:\s*8px;/)
  assert.doesNotMatch(authShellSource, /backdrop-filter:|prefers-color-scheme:\s*dark/)
  assert.doesNotMatch(`${loginSource}\n${registerSource}`, /prefers-color-scheme:\s*dark/)
})
