<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Iphone, Lock } from '@element-plus/icons-vue'
import { registerByInvite } from '../api/auth'
import AuthShell from '../components/auth/AuthShell.vue'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const formRef = ref()
const form = reactive({ token: String(route.query.token || ''), phone: '', nickName: '', password: '' })
const tokenReady = computed(() => form.token.trim().length > 0)
const rules = {
  token: [{ required: true, message: '请输入邀请令牌', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的中国大陆手机号', trigger: 'blur' },
  ],
  nickName: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度须为8到64位', trigger: 'blur' },
  ],
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
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
  <AuthShell eyebrow="INVITED ACCESS" title="创建你的账户" description="填写邀请令牌后完成私人研究账户的开通。">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="邀请令牌" prop="token"><el-input v-model="form.token" autocomplete="off" placeholder="粘贴邀请令牌" /></el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" inputmode="tel" autocomplete="username" placeholder="请输入手机号">
            <template #prefix><el-icon><Iphone /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="昵称" prop="nickName"><el-input v-model="form.nickName" maxlength="32" placeholder="显示名称" /></el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="设置登录密码">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" native-type="submit" :disabled="!tokenReady" :loading="loading" class="submit-btn">完成注册</el-button>
      </el-form>
      <RouterLink class="invite-link" to="/login">
        <span>已有私人研究账户？</span>
        <strong>返回登录</strong>
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
