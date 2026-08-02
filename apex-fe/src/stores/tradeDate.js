import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useTradeDateStore = defineStore('tradeDate', () => {
  const tradeDate = ref('')

  function setTradeDate(date) {
    tradeDate.value = date ? String(date).slice(0, 10) : ''
  }

  return { tradeDate, setTradeDate }
})
