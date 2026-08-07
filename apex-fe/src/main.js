import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import TermTip from './components/TermTip.vue'
import ScoreBar from './components/ScoreBar.vue'
import StockBoardTag from './components/StockBoardTag.vue'
import './style.css'

const app = createApp(App)
app.component('TermTip', TermTip)
app.component('ScoreBar', ScoreBar)
app.component('StockBoardTag', StockBoardTag)
app.use(createPinia()).use(router).use(ElementPlus).mount('#app')
