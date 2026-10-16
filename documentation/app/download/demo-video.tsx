'use client';

import { useEffect, useRef } from 'react';

// Plays on load unless the visitor asked for reduced motion; the controls still start it.
export function DemoVideo() {
  const video = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    // Browsers may still block playback; the poster and controls stay usable.
    video.current?.play().catch(() => {});
  }, []);

  return (
    <video
      ref={video}
      src="/dari/desktop-demo.mp4"
      poster="/dari/desktop-demo-poster.jpg"
      muted
      loop
      playsInline
      controls
      className="w-full rounded-xl border border-gray-200 dark:border-gray-800"
    />
  );
}
