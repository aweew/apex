<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { registerByInvite } from '../api/auth'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ token: String(route.query.token || ''), phone: '', nickName: '', password: '' })
const tokenReady = computed(() => form.token.trim().length > 0)

async function submit() {
  loading.value = true
  try {
    await registerByInvite(form)
    ElMessage.success('注册完成，请登录')
    await router.replace('/login')
  } catch (error) {
    ElMessage.error(error.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel" aria-labelledby="register-title">
      <p class="eyebrow">INVITED ACCESS</p>
      <h1 id="register-title">创建你的账户</h1>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="邀请令牌"><el-input v-model="form.token" autocomplete="off" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" inputmode="tel" autocomplete="username" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickName" maxlength="32" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-button type="primary" native-type="submit" :disabled="!tokenReady" :loading="loading" class="submit-btn">完成注册</el-button>
      </el-form>
      <RouterLink class="invite-link" to="/login">返回登录</RouterLink>
    </section>
  </main>
</template>

<style scoped>
.auth-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: #f4f7f8; }
.auth-panel { width: min(100%, 390px); padding: 32px; background: #fff; border: 1px solid #dce4e7; border-radius: 8px; box-shadow: 0 12px 34px rgba(22, 38, 46, .08); }
.eyebrow { margin: 0 0 6px; color: #688087; font-size: 12px; }
h1 { margin: 0 0 22px; font-size: 25px; color: #15181c; }
.submit-btn { width: 100%; margin-top: 4px; }
.invite-link { display: block; margin-top: 18px; color: #187b83; font-size: 14px; text-align: center; }
</style>
