import { defineI18n } from 'fumadocs-core/i18n';

export const i18n = defineI18n({
  defaultLanguage: 'en',
  languages: ['en', 'ko'],
  hideLocale: 'default-locale',
  parser: 'dir',
});

export const languageNames: Record<string, string> = {
  en: 'English',
  ko: '한국어',
};
