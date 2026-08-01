import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import TermTip from './components/TermTip.vue'
import './style.css'

const app = createApp(App)
app.component('TermTip', TermTip)
app.use(createPinia()).use(router).use(ElementPlus).mount('#app')
