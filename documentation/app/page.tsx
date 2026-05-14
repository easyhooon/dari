import Link from 'next/link';

export default function HomePage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 bg-white px-4 text-center dark:bg-neutral-950">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/dari/logo.png" alt="Dari logo" width={280} height={280} className="rounded-3xl" />
      <h1 className="text-7xl font-bold tracking-tight">Dari</h1>
      <p className="max-w-xl text-xl text-gray-500 dark:text-gray-400">
        A Chucker-inspired WebView bridge communication inspector for Android.
      </p>
      <div className="flex gap-4">
        <Link
          href="/docs"
          className="rounded-xl bg-black px-10 py-4 text-base font-semibold text-white transition-opacity hover:opacity-70 dark:bg-white dark:text-black"
        >
          Get Started
        </Link>
        <Link
          href="https://github.com/easyhooon/dari"
          className="rounded-xl border border-gray-300 px-10 py-4 text-base font-semibold transition-colors hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
          target="_blank"
          rel="noopener noreferrer"
        >
          GitHub
        </Link>
      </div>
    </main>
  );
}
