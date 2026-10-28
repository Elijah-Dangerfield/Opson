// Every internal link and asset goes through here, so the site works at any
// base: / on www.opsontracker.com, or a sub-path such as /Opson/ if it is ever
// served from one.

/** Joins the site base ('/', '/Opson' or '/Opson/') and a path inside the site. */
export function joinBase(base: string, path: string): string {
  const root = base.endsWith('/') ? base : `${base}/`;
  return root + path.replace(/^\/+/, '');
}

/** The path of a page relative to the base: '/Opson/privacy/' → '/privacy/'. */
export function stripBase(base: string, pathname: string): string {
  const root = base.replace(/\/+$/, '');
  const rest = root && pathname.startsWith(root) ? pathname.slice(root.length) : pathname;
  return rest.startsWith('/') ? rest : `/${rest}`;
}
