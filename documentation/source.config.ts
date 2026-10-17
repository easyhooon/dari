import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { defineDocs, defineConfig } from 'fumadocs-mdx/config';

export const docs = defineDocs({
  dir: 'content/docs',
});

// The published version comes from the Gradle catalog, so a release bump updates the docs too.
// Write {{DARI_VERSION}} in code and inline code; it is replaced at build time.
const DARI_VERSION = (() => {
  const catalog = readFileSync(join(process.cwd(), '..', 'gradle', 'libs.versions.toml'), 'utf8');
  const match = catalog.match(/^dari\s*=\s*"([^"]+)"/m);
  if (!match) throw new Error('dari version not found in gradle/libs.versions.toml');
  return match[1];
})();

type MdNode = { type: string; value?: string; children?: MdNode[] };

function remarkDariVersion() {
  const replace = (node: MdNode) => {
    if ((node.type === 'code' || node.type === 'inlineCode' || node.type === 'text') && node.value) {
      node.value = node.value.replaceAll('{{DARI_VERSION}}', DARI_VERSION);
    }
    node.children?.forEach(replace);
  };
  return (tree: MdNode) => replace(tree);
}

export default defineConfig({
  mdxOptions: {
    remarkPlugins: [remarkDariVersion],
  },
});
