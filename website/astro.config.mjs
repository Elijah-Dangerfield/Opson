// @ts-check
import { defineConfig } from 'astro/config';
import { siteConfig } from './src/lib/site.ts';

const { site, base } = siteConfig(process.env);

// https://astro.build/config
export default defineConfig({
  site,
  base,
  vite: {
    server: {
      // The privacy, terms, health data and credits pages render
      // ../legal/*.md, outside this folder.
      fs: { allow: ['..'] },
    },
  },
});
