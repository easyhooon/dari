import type { Metadata } from 'next';
import { DownloadPageContent } from '@/app/download/download-page';

export const metadata: Metadata = {
  title: 'Dari 데스크톱 다운로드',
  description: '컴퓨터에서 WebView 브릿지 메시지를 확인하세요. macOS용 Dari, Windows와 Linux는 미리보기로 제공합니다.',
};

export function generateStaticParams() {
  return [{ lang: 'ko' }];
}

export default function KoreanDownloadPage() {
  return <DownloadPageContent lang="ko" />;
}
