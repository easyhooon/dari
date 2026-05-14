import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import { RootProvider } from 'fumadocs-ui/provider';
import './global.css';

const geist = Geist({ variable: '--font-geist-sans', subsets: ['latin'] });
const geistMono = Geist_Mono({ variable: '--font-geist-mono', subsets: ['latin'] });

export const metadata: Metadata = {
  metadataBase: new URL('https://easyhooon.github.io/dari'),
  title: 'Dari — WebView Bridge Inspector for Android',
  description: 'A Chucker-inspired debug inspector for WebView bridge communication on Android.',
  openGraph: {
    siteName: 'Dari',
    images: [{ url: '/opengraph-image', width: 1200, height: 630, alt: 'Dari — WebView Bridge Inspector for Android' }],
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    images: ['/opengraph-image'],
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${geist.variable} ${geistMono.variable} antialiased`}>
        <RootProvider>{children}</RootProvider>
      </body>
    </html>
  );
}
