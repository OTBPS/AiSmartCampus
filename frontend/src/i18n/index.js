import { createI18n } from 'vue-i18n'
import { messages } from './messages'

export const LOCALE_KEY = 'scn-locale'
export const supportedLocales = ['zh-CN', 'en-US']

export function initialLocale() {
  const saved = localStorage.getItem(LOCALE_KEY)
  return supportedLocales.includes(saved) ? saved : 'zh-CN'
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: initialLocale(),
  fallbackLocale: 'zh-CN',
  messages
})

export function setLocale(locale) {
  if (!supportedLocales.includes(locale)) return
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_KEY, locale)
}
