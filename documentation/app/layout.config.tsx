import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';

export const baseOptions: BaseLayoutProps = {
  nav: {
    title: <span className="font-semibold">Dari</span>,
  },
  links: [
    {
      text: 'Download',
      url: '/download',
    },
    {
      text: 'GitHub',
      url: 'https://github.com/easyhooon/dari',
      external: true,
    },
  ],
};
