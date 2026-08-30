import { createApp, defineAsyncComponent } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import TermTip from './components/TermTip.vue'
import ScoreBar from './components/ScoreBar.vue'
import StockIdentity from './components/StockIdentity.vue'
import './style.css'

const app = createApp(App)
const StockDetailCard = defineAsyncComponent(() => import('./components/StockDetailCard.vue'))
app.component('TermTip', TermTip)
app.component('ScoreBar', ScoreBar)
app.component('StockIdentity', StockIdentity)
app.component('StockDetailCard', StockDetailCard)
app.use(createPinia()).use(router).use(ElementPlus).mount('#app')

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' }).catch(() => {})
  })
}
