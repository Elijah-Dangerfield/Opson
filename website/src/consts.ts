import { joinBase } from './lib/paths';

export const CONTACT_EMAIL = 'contact@nightjarlabs.llc';

/** The server the waitlist form posts to. Set PUBLIC_API_URL at build to change it. */
export const API_URL: string = import.meta.env.PUBLIC_API_URL || 'https://opson-server-prod.fly.dev';

/** A link or asset path inside this site, with the base in front. */
export const href = (path: string) => joinBase(import.meta.env.BASE_URL, path);
