import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './en.json'

export const defaultNS = 'translation'
export const resources = { en: { translation: en } } as const

// Every user-facing string lives in a resource file (NFR-12); the lint rule
// i18next/no-literal-string rejects text written directly in JSX.
void i18n.use(initReactI18next).init({
  resources,
  // British English: the club is in the UK (OQ-01). Text comes from the 'en' resources; dates and
  // numbers are formatted for en-GB.
  lng: 'en-GB',
  fallbackLng: 'en',
  defaultNS,
  interpolation: { escapeValue: false }, // React already escapes
})

export { i18n }
