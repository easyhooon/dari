import { ImageResponse } from 'next/og';
import { readFileSync } from 'fs';
import { join } from 'path';

export const dynamic = 'force-static';

export const alt = 'Dari — WebView Bridge Inspector for Android';
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';

export default function Image() {
  const logoData = readFileSync(join(process.cwd(), 'public/logo.png'));
  const logoSrc = `data:image/png;base64,${logoData.toString('base64')}`;

  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#0a0d1a',
          gap: 36,
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={logoSrc} width={200} height={200} alt="Dari logo" />
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 16,
          }}
        >
          <span
            style={{
              fontSize: 80,
              fontWeight: 700,
              color: '#ffffff',
              letterSpacing: '-3px',
              lineHeight: 1,
            }}
          >
            dari
          </span>
          <span
            style={{
              fontSize: 26,
              color: '#94a3b8',
              textAlign: 'center',
              maxWidth: 680,
              lineHeight: 1.4,
            }}
          >
            WebView Bridge Communication Inspector for Android
          </span>
        </div>
      </div>
    ),
    { ...size },
  );
}
