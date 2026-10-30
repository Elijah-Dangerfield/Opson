// A shared recipe's page (/r/<code>, rewritten to /recipe/ by public/_redirects). The page reads
// the code from its address, asks the server for the recipe and draws it. The contract is the
// server's GET /v1/recipes/public/{code} (apps/server/.../routes/RecipeDto.kt):
//
//   200 the recipe
//   404 no recipe has that code
//   410 it isn't shared now (stopped, taken down, or hidden while reports wait)

export interface RecipeNutrients {
  kcal?: number | null;
  protein?: number | null;
  carbs?: number | null;
  fat?: number | null;
  fiber?: number | null;
}

export interface RecipeIngredient {
  name: string;
  amountText?: string | null;
  grams?: number | null;
  nutrients: RecipeNutrients;
  checked?: boolean;
}

export interface PublicRecipe {
  code: string;
  name: string;
  servings: number;
  notes?: string | null;
  by?: string | null;
  ingredients: RecipeIngredient[];
  perServing: RecipeNutrients;
  checkedCount: number;
}

export type RecipeLookup =
  | { kind: 'found'; recipe: PublicRecipe }
  | { kind: 'not_shared' }
  | { kind: 'not_found' }
  | { kind: 'error' };

/** No 0, 1, i, l or o, as the server makes them. */
const CODE = /^[2-9a-hj-km-np-z]{8}$/;

/**
 * The share code from the page's address: /r/<code> (what people get), /recipe/<code>, or
 * ?c=<code>, which works on any static host. Null when there is none.
 */
export function codeFrom(pathname: string, search: string): string | null {
  const fromQuery = new URLSearchParams(search).get('c')?.trim().toLowerCase() ?? '';
  if (CODE.test(fromQuery)) return fromQuery;
  const parts = pathname.split('/').filter(Boolean);
  const last = (parts.at(-1) ?? '').toLowerCase();
  return CODE.test(last) ? last : null;
}

export function recipeEndpoint(apiUrl: string, code: string): string {
  return `${apiUrl.replace(/\/+$/, '')}/v1/recipes/public/${encodeURIComponent(code)}`;
}

/** The app's own link for the recipe, for Open in Opson. */
export function appLink(code: string): string {
  return `opson://recipe/${code}`;
}

export async function fetchRecipe(
  apiUrl: string,
  code: string,
  fetchFn: typeof fetch = fetch,
  timeoutMs = 15_000,
): Promise<RecipeLookup> {
  let response: Response;
  try {
    response = await fetchFn(recipeEndpoint(apiUrl, code), { signal: AbortSignal.timeout(timeoutMs) });
  } catch {
    return { kind: 'error' };
  }
  if (response.status === 404) return { kind: 'not_found' };
  if (response.status === 410) return { kind: 'not_shared' };
  if (!response.ok) return { kind: 'error' };
  try {
    return { kind: 'found', recipe: (await response.json()) as PublicRecipe };
  } catch {
    return { kind: 'error' };
  }
}

/** "1,234 kcal", or null when unknown. Whole numbers, as the app shows them. */
export function kcalText(kcal: number | null | undefined): string | null {
  if (kcal == null || !Number.isFinite(kcal)) return null;
  return `${Math.round(kcal).toLocaleString('en-US')} kcal`;
}

/** "13 g protein · 40 g carbs · 9 g fat", leaving out what isn't known. */
export function macrosText(n: RecipeNutrients): string {
  const parts: string[] = [];
  const add = (value: number | null | undefined, label: string) => {
    if (value != null && Number.isFinite(value)) parts.push(`${Math.round(value)} g ${label}`);
  };
  add(n.protein, 'protein');
  add(n.carbs, 'carbs');
  add(n.fat, 'fat');
  return parts.join(' · ');
}

/** "6 servings", "1 serving", "1.5 servings". */
export function servingsText(servings: number): string {
  const n = Number.isInteger(servings) ? String(servings) : String(Math.round(servings * 100) / 100);
  return `${n} ${servings === 1 ? 'serving' : 'servings'}`;
}

/** "2 of 3 ingredients come from foods Opson checked." */
export function checkedText(checked: number, total: number): string {
  if (total === 1) return `${checked} of 1 ingredient comes from a food Opson checked.`;
  return `${checked} of ${total} ingredients come from foods Opson checked.`;
}
