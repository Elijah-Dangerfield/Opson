// @ts-check
import { defineConfig } from 'astro/config';

// Where the site is served from. Today that's the GitHub Pages project URL,
// https://elijah-dangerfield.github.io/Opson/. With a custom domain, set
// SITE_URL=https://<domain> and SITE_BASE=/ in the repo variables; no code
// changes, because every link and asset goes through import.meta.env.BASE_URL.
const site = process.env.SITE_URL || 'https://elijah-dangerfield.github.io';
const base = process.env.SITE_BASE || '/Opson';

// https://astro.build/config
export default defineConfig({
  site,
  base,
  vite: {
    server: {
      // The privacy and terms pages render ../legal/*.md and the home page
      // uses ../docs/app-store/screenshots, both outside this folder.
      fs: { allow: ['..'] },
    },
  },
});
