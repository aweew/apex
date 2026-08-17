import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const loginSource = await readFile(new URL('./LoginView.vue', import.meta.url), 'utf8')
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

test('auth validation errors stay in document flow', () => {
  assert.match(
    authShellSource,
    /:deep\(\.el-form-item__error\)\s*\{[\s\S]*?position:\s*static;[\s\S]*?width:\s*100%;/,
  )
})
