import type { Metadata } from 'next';
import { DownloadPageContent } from './download-page';

export const metadata: Metadata = {
  title: 'Download Dari for desktop',
  description: 'Inspect WebView bridge messages from your computer. Dari for macOS and Windows, with Linux in preview.',
};

export default function DownloadPage() {
  return <DownloadPageContent lang="en" />;
}
