import { loader } from 'fumadocs-core/source';
import { docs } from '@/.source/server';
import { i18n } from './i18n';

export const source = loader({
  baseUrl: '/docs',
  i18n,
  source: docs.toFumadocsSource(),
});
