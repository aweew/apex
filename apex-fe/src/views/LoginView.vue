<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { BRAND } from '../brand/identity.js'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ phone: '', password: '' })

async function submit() {
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
  <main class="auth-page">
    <section class="auth-panel" aria-labelledby="login-title">
      <div class="brand-mark">{{ BRAND.cn }}</div>
      <p class="eyebrow">PRIVATE RESEARCH TERMINAL</p>
      <h1 id="login-title">登录 Apex</h1>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="手机号"><el-input v-model="form.phone" inputmode="tel" autocomplete="username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="current-password" @keyup.enter="submit" /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="submit-btn">登录</el-button>
      </el-form>
      <RouterLink class="invite-link" to="/register">使用邀请链接注册</RouterLink>
    </section>
  </main>
</template>

<style scoped>
.auth-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: #f4f7f8; }
.auth-panel { width: min(100%, 390px); padding: 32px; background: #fff; border: 1px solid #dce4e7; border-radius: 8px; box-shadow: 0 12px 34px rgba(22, 38, 46, .08); }
.brand-mark { font-size: 22px; font-weight: 700; color: #15181c; }
.eyebrow { margin: 16px 0 6px; color: #688087; font-size: 12px; }
h1 { margin: 0 0 22px; font-size: 25px; color: #15181c; }
.submit-btn { width: 100%; margin-top: 4px; }
.invite-link { display: block; margin-top: 18px; color: #187b83; font-size: 14px; text-align: center; }
</style>
