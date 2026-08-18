import test from 'node:test'
import assert from 'node:assert/strict'
import { readdir, readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { extname, join } from 'node:path'

const sourceRoot = fileURLToPath(new URL('.', import.meta.url))

async function collectSourceFiles(directory, result) {
  const entries = await readdir(directory, { withFileTypes: true })
  for (const entry of entries) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) {
      await collectSourceFiles(path, result)
      continue
    }
    if (['.js', '.mjs', '.ts', '.vue'].includes(extname(entry.name))) result.push(path)
  }
}

test('frontend console logs include Chinese context', async () => {
  const files = []
  await collectSourceFiles(sourceRoot, files)
  const violations = []
  const consoleCallPattern = /console\.(?:log|info|warn|error|debug)\s*\(/g
  const chineseLiteralPattern = /console\.(?:log|info|warn|error|debug)\s*\(\s*(['"`])[^'"`]*[\u4e00-\u9fff][^'"`]*\1/
  for (const file of files) {
    const source = await readFile(file, 'utf8')
    const lines = source.split(/\r?\n/)
    lines.forEach((line, index) => {
      if (consoleCallPattern.test(line) && !chineseLiteralPattern.test(line)) {
        violations.push(`${file}:${index + 1} ${line.trim()}`)
      }
      consoleCallPattern.lastIndex = 0
    })
  }
  assert.deepEqual(violations, [])
})
