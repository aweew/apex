<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Iphone, Lock } from '@element-plus/icons-vue'
import { login } from '../api/auth'
import AuthShell from '../components/auth/AuthShell.vue'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const formRef = ref()
const form = reactive({ phone: '', password: '' })
const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的中国大陆手机号', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await login(form)
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard')
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell eyebrow="PRIVATE ACCESS" title="PRIVATE ACCESS" accessible-title="登录" title-tone="access" description="使用你的私人研究账户继续。">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" inputmode="tel" autocomplete="username" placeholder="请输入手机号">
            <template #prefix><el-icon><Iphone /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" @keyup.enter="submit">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="submit-btn">进入工作台</el-button>
      </el-form>
      <RouterLink class="invite-link" to="/register">
        <span>持有邀请链接？</span>
        <strong>注册账户</strong>
      </RouterLink>
  </AuthShell>
</template>

<style scoped>
.submit-btn { width: 100%; height: 44px; margin-top: 4px; border: 0; border-radius: 6px; background: #1478d4; font-weight: 700; box-shadow: 0 8px 18px rgba(20, 120, 212, .2); }
.invite-link { display: flex; align-items: center; justify-content: center; gap: 6px; width: fit-content; margin: 22px auto 0; color: rgba(28, 57, 75, .58); font-size: 13px; text-decoration: none; }
.invite-link strong { color: #1478d4; font-size: 13px; }
.invite-link strong::after { content: ' →'; }
.invite-link:hover { color: rgba(28, 57, 75, .78); }

@media (prefers-color-scheme: dark) {
  .invite-link { color: rgba(231, 241, 246, .58); }
  .invite-link strong { color: #61d1d7; }
  .invite-link:hover { color: rgba(231, 241, 246, .82); }
}
</style>
