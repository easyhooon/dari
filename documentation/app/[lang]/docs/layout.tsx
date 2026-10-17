import { notFound } from 'next/navigation';
import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import { baseOptions } from '@/app/layout.config';
import { source } from '@/lib/source';
import { SidebarFooter } from '@/components/sidebar-footer';

export default async function Layout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ lang: string }>;
}) {
  const { lang } = await params;
  const tree = source.getPageTree(lang);
  if (!tree) notFound();

  return (
    <DocsLayout
      tree={tree}
      {...baseOptions(lang)}
      sidebar={{ footer: <SidebarFooter /> }}
    >
      {children}
    </DocsLayout>
  );
}
