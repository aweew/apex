import assert from 'node:assert/strict'
import test from 'node:test'

import { copyImageBlob } from './shareCapture.js'

test('clipboard write starts before an asynchronous screenshot resolves', async () => {
  let resolveScreenshot
  const screenshotPromise = new Promise((resolve) => {
    resolveScreenshot = resolve
  })
  const clipboardItems = []

  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: {
      clipboard: {
        write(items) {
          clipboardItems.push(...items)
          return Promise.resolve()
        },
      },
    },
  })
  globalThis.ClipboardItem = class ClipboardItem {
    constructor(items) {
      this.items = items
    }
  }

  const copyPromise = copyImageBlob(screenshotPromise)
  assert.equal(clipboardItems.length, 1)

  const pngPromise = clipboardItems[0].items['image/png']
  resolveScreenshot(new Blob(['png'], { type: 'image/png' }))

  assert.equal((await pngPromise).type, 'image/png')
  await copyPromise
})
