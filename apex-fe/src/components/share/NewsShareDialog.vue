<script setup>
import { nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import NewsShareCard from './NewsShareCard.vue'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  shareFilename,
} from '../../utils/shareCapture'

const SOURCE_LABELS = {
  eastmoney: '东财',
  cls: '财联社',
  ths: '同花顺',
  sina: '新浪',
  cctv: '央视',
}

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  item: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue'])

const cardHostRef = ref(null)
const downloading = ref(false)
const copying = ref(false)

function sourceLabel(s) {
  return SOURCE_LABELS[s] || s || '-'
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function close() {
  emit('update:modelValue', false)
}

async function captureCard() {
  await nextTick()
  await new Promise((r) => requestAnimationFrame(() => r()))
  const el = cardHostRef.value?.querySelector?.('.share-card') || cardHostRef.value
  if (!el) throw new Error('分享卡片未就绪')
  return captureElementBlob(el, {
    // 避免把弹层半透明背景/滤镜算进去
    filter: (node) => !(node instanceof Element && node.classList?.contains('el-overlay')),
  })
}

async function onDownload() {
  if (!props.item) return
  downloading.value = true
  try {
    const blob = await captureCard()
    downloadBlob(blob, shareFilename('apex_news', props.item.title))
    ElMessage.success('已下载分享图')
  } catch (e) {
    console.error('下载新闻分享图失败', e)
    ElMessage.error(e.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

async function onCopy() {
  if (!props.item) return
  copying.value = true
  try {
    await copyImageBlob(captureCard())
    ElMessage.success('已复制到剪贴板，可直接粘贴到微信/文档')
  } catch (e) {
    console.error('复制新闻分享图失败', e)
    ElMessage.error(e.message || '复制失败，请改用下载')
  } finally {
    copying.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      downloading.value = false
      copying.value = false
    }
  },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="分享资讯截图"
    width="740px"
    append-to-body
    destroy-on-close
    align-center
    class="news-share-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="item" class="dialog-body">
      <p class="tip">预览如下，可复制图片或下载 PNG 后发到微信/社群。</p>
      <div ref="cardHostRef" class="card-stage">
        <NewsShareCard
          :item="item"
          :source-label="sourceLabel"
          :fmt-time="fmtTime"
        />
      </div>
    </div>
    <el-empty v-else description="未选择资讯" />

    <template #footer>
      <el-button @click="close">关闭</el-button>
      <el-button type="primary" plain :loading="copying" :disabled="!item" @click="onCopy">
        复制图片
      </el-button>
      <el-button type="primary" :loading="downloading" :disabled="!item" @click="onDownload">
        下载 PNG
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-body {
  min-height: 120px;
}

.tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: #86868b;
}

.card-stage {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  max-height: min(62vh, 680px);
  overflow: auto;
  padding: 10px;
  background: #ececec;
  border-radius: 12px;
}

.card-stage :deep(.share-card) {
  flex-shrink: 0;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.12);
  border-radius: 4px;
}
</style>
