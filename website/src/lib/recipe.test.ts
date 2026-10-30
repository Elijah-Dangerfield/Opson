import { test } from 'node:test';
import assert from 'node:assert/strict';
import { appLink, checkedText, codeFrom, fetchRecipe, kcalText, macrosText, recipeEndpoint, servingsText } from './recipe.ts';

test('the code comes from /r/<code>, /recipe/<code> or ?c=', () => {
  assert.equal(codeFrom('/r/abcd2345', ''), 'abcd2345');
  assert.equal(codeFrom('/r/ABCD2345/', ''), 'abcd2345');
  assert.equal(codeFrom('/recipe/', '?c=k7m2p9qx'), 'k7m2p9qx');
  assert.equal(codeFrom('/recipe/', ''), null);
  assert.equal(codeFrom('/r/abcd0345', ''), null, 'no 0, 1, i, l or o');
  assert.equal(codeFrom('/r/abcd234', ''), null);
});

test('the endpoint and the app link', () => {
  assert.equal(recipeEndpoint('https://api.example/', 'abcd2345'), 'https://api.example/v1/recipes/public/abcd2345');
  assert.equal(appLink('abcd2345'), 'opson://recipe/abcd2345');
});

test('each answer from the server means one thing', async () => {
  const answer = (status: number, body = '{}') => async () => new Response(body, { status });
  const recipe = JSON.stringify({ code: 'abcd2345', name: 'Chili', servings: 6, ingredients: [], perServing: {}, checkedCount: 0 });
  assert.deepEqual(await fetchRecipe('https://api', 'abcd2345', answer(200, recipe)), {
    kind: 'found',
    recipe: JSON.parse(recipe),
  });
  assert.deepEqual(await fetchRecipe('https://api', 'abcd2345', answer(404)), { kind: 'not_found' });
  assert.deepEqual(await fetchRecipe('https://api', 'abcd2345', answer(410)), { kind: 'not_shared' });
  assert.deepEqual(await fetchRecipe('https://api', 'abcd2345', answer(503)), { kind: 'error' });
  assert.deepEqual(
    await fetchRecipe('https://api', 'abcd2345', async () => {
      throw new TypeError('offline');
    }),
    { kind: 'error' },
  );
});

test('numbers read as the app writes them', () => {
  assert.equal(kcalText(1234.4), '1,234 kcal');
  assert.equal(kcalText(null), null);
  assert.equal(macrosText({ protein: 13.4, carbs: 40, fat: null }), '13 g protein · 40 g carbs');
  assert.equal(servingsText(6), '6 servings');
  assert.equal(servingsText(1), '1 serving');
  assert.equal(servingsText(1.5), '1.5 servings');
  assert.equal(checkedText(2, 3), '2 of 3 ingredients come from foods Opson checked.');
});
