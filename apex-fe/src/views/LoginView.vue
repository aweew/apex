<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
  <AuthShell eyebrow="PRIVATE ACCESS" title="登录 Apex" description="登录后查看你的市场研究与组合数据。">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" inputmode="tel" autocomplete="username" placeholder="请输入手机号" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" @keyup.enter="submit" /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="submit-btn">登录</el-button>
      </el-form>
      <RouterLink class="invite-link" to="/register">使用邀请链接注册</RouterLink>
  </AuthShell>
</template>

<style scoped>
.submit-btn { width: 100%; height: 42px; margin-top: 4px; border: 0; border-radius: 6px; background: #1478d4; font-weight: 700; }
.invite-link { display: block; margin-top: 20px; color: #72dbe0; font-size: 14px; text-align: center; }
.invite-link:hover { color: #f2b84b; }
</style>
