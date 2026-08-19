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
  if (loading.value) return
  loading.value = true
  try {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return
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

function normalizePhone() {
  form.phone = form.phone.replace(/\s/g, '')
}
</script>

<template>
  <AuthShell title="登录你的研究账户" :description="`进入${BRAND.nameZh}量化研究平台`">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" inputmode="tel" autocomplete="username" placeholder="请输入手机号" @input="normalizePhone">
            <template #prefix><el-icon><Iphone /></el-icon></template>
          </el-input>
        </el-form-item>
        <div class="password-field">
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码">
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
.submit-btn { width: 100%; height: 50px; margin-top: 2px; border: 0; border-radius: 8px; background: #0071e3; font-weight: 600; box-shadow: none; transition: background .18s ease, box-shadow .18s ease; }
.submit-btn:hover { background: #0077ed; }
.submit-btn:focus-visible { background: #0071e3; box-shadow: 0 0 0 4px rgba(0, 113, 227, .18); }
.submit-btn:active { background: #0068d1; }
.submit-btn.is-disabled, .submit-btn.is-disabled:hover { background: #a6c8ea; color: rgba(255, 255, 255, .82); }
.password-field { position: relative; }
.forgot-password { position: absolute; top: -7px; right: 0; display: inline-flex; align-items: center; min-height: 32px; padding: 0; border: 0; background: transparent; color: #0066cc; font: inherit; font-size: 12px; font-weight: 600; line-height: 1.3; cursor: pointer; }
.forgot-password:hover { color: #004f9e; text-decoration: underline; }
.forgot-password:focus-visible { color: #004f9e; border-radius: 4px; outline: 3px solid rgba(0, 113, 227, .18); outline-offset: 2px; }
.invite-link { display: flex; align-items: center; justify-content: center; gap: 6px; width: fit-content; min-height: 32px; margin: 20px auto 0; color: #6e6e73; font-size: 13px; text-decoration: none; }
.invite-link strong { color: #0066cc; font-size: 13px; font-weight: 600; }
.invite-link strong::after { content: ' →'; }
.invite-link:hover { color: #1d1d1f; }
.invite-link:focus-visible { border-radius: 4px; outline: 3px solid rgba(0, 113, 227, .18); outline-offset: 2px; }

@media (prefers-color-scheme: dark) {
  .invite-link { color: #a1a1a6; }
  .invite-link strong,
  .forgot-password { color: #0a84ff; }
  .forgot-password:hover, .forgot-password:focus-visible { color: #64b5ff; }
  .invite-link:hover { color: #f5f5f7; }
}

@media (prefers-reduced-motion: reduce) {
  .submit-btn { transition: none; }
}
</style>
