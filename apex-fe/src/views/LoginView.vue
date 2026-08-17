<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Iphone, Lock } from '@element-plus/icons-vue'
import { login } from '../api/auth'
import { BRAND } from '../brand/identity.js'
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

function showPasswordResetHelp() {
  ElMessage.info('请联系管理员重置账户密码')
}
</script>

<template>
  <AuthShell title="登录你的研究账户" :description="`进入${BRAND.nameZh}量化研究平台`">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" inputmode="tel" autocomplete="username" placeholder="请输入手机号">
            <template #prefix><el-icon><Iphone /></el-icon></template>
          </el-input>
        </el-form-item>
        <div class="password-field">
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" @keyup.enter="submit">
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <button type="button" class="forgot-password" @click="showPasswordResetHelp">忘记密码？</button>
        </div>
        <el-button type="primary" native-type="submit" :loading="loading" class="submit-btn">进入{{ BRAND.nameZh }}</el-button>
      </el-form>
      <RouterLink class="invite-link" to="/register">
        <span>持有邀请？</span>
        <strong>注册账户</strong>
      </RouterLink>
  </AuthShell>
</template>

<style scoped>
.submit-btn { width: 100%; height: 50px; margin-top: 2px; border: 0; border-radius: 6px; background: #1669c9; font-weight: 700; box-shadow: none; transition: background .18s ease; }
.submit-btn:hover, .submit-btn:focus-visible { background: #1058ad; }
.submit-btn:active { background: #0d4d92; }
.submit-btn.is-disabled, .submit-btn.is-disabled:hover { background: #90b3d6; color: rgba(255, 255, 255, .78); }
.password-field { position: relative; }
.forgot-password { position: absolute; top: 0; right: 6px; padding: 0; border: 0; background: transparent; color: #0d58af; font: inherit; font-size: 12px; font-weight: 600; line-height: 1.3; cursor: pointer; }
.forgot-password:hover, .forgot-password:focus-visible { color: #08488f; text-decoration: underline; outline: none; }
.invite-link { display: flex; align-items: center; justify-content: center; gap: 6px; width: fit-content; margin: 22px auto 0; color: rgba(28, 57, 75, .58); font-size: 13px; text-decoration: none; }
.invite-link strong { color: #1478d4; font-size: 13px; }
.invite-link strong::after { content: ' →'; }
.invite-link:hover { color: rgba(28, 57, 75, .78); }

@media (prefers-color-scheme: dark) {
  .invite-link { color: rgba(231, 241, 246, .58); }
  .invite-link strong { color: #61d1d7; }
  .forgot-password { color: #61d1d7; }
  .forgot-password:hover, .forgot-password:focus-visible { color: #a0f0f0; }
  .invite-link:hover { color: rgba(231, 241, 246, .82); }
}
</style>
