# Food data sources: final recommendation

Researched 2026-10-26. Licences were read on the official pages linked below,
except where a row says otherwise.

## The recommendation

1. **Import Open Food Facts in full, kept in its own table.** It is the only
   large non-US source of packaged, barcoded products, and it covers almost
   every market. Keep it separate from the USDA rows so its share-alike
   licence can't reach the rest of the catalog.
2. **Add the open national tables for generic foods.** They cover Canada, the
   UK, France, Germany, Sweden, Denmark and Japan, and each allows commercial
   use and transformation with attribution.
3. **Hold the sources whose terms forbid changing the data, or force
   share-alike on what we build.**
4. **Skip the paid APIs as data sources.** Their caching limits don't fit a
   catalog we own. Keep Nutritionix as the optional paid barcode fallback
   that's already wired up.
5. **Add a Credits screen** (More → Support) listing every source with its
   required attribution line.

## Open Food Facts, measured

Downloaded the full CSV export on 2026-10-26 (1.5 GB compressed).

- **4,837,151 products in total. 3,303,905 usable:** energy, protein, carbs
  and fat per 100 g, values within physical limits (macros 0 to 100 g, energy
  at most 900 kcal, macros summing to at most 101 g), a valid 8 to 14 digit
  barcode, and a name.
- **Usable products by country:**

| Country | Products | Country | Products |
|---|---:|---|---:|
| France | 882,557 | Belgium | 80,633 |
| United States | 774,242 | Australia | 65,666 |
| Spain | 245,938 | Netherlands | 59,199 |
| Germany | 235,064 | Ireland | 59,185 |
| Italy | 216,192 | Japan | 40,771 |
| United Kingdom | 150,101 | Brazil | 25,785 |
| Canada | 111,044 | Sweden | 24,755 |
| Switzerland | 86,271 | Mexico | 12,446 |

  A product can be tagged with several countries, so the column doesn't sum
  to the total.
- **US overlap:** keep USDA Branded (manufacturer labels, tier 2) as the
  answer for a barcode both sources have. Open Food Facts fills in the rest
  as tier 4.
- **Size:** an estimate of roughly 1 to 1.5 GB in Postgres with indexes. Pro
  includes 8 GB per project. Measure on dev before loading prod.
- **Refresh:** a full monthly reload, because the daily deltas don't record
  deletions. Keep the live barcode lookup as a fallback for products newer
  than the last dump.

### Licence: ODbL, kept contained

- **Required in the app:** "Contains data from Open Food Facts, available
  under the Open Database License", with a link, on a credits screen and
  wherever its foods appear.
- **Keep it in its own table.** A database built from Open Food Facts is a
  Derivative Database. If it is "publicly used", it has to be offered under
  ODbL. Mixing Open Food Facts rows into the USDA table risks that applying
  to the whole merged catalog.
- **Don't ship a bulk copy inside the app.** Serving search results from our
  server is the lower-risk reading. A bundled database is likelier to count
  as public conveyance.
- **Unverified:** Open Food Facts' own reuse FAQ wouldn't load. The boundary
  above comes from the ODbL text
  (https://opendatacommons.org/licenses/odbl/1-0/), not a ruling from them.
  Have counsel or Open Food Facts confirm before public launch.

## Generic national tables: import

| Source | Region | Foods | Licence | Attribution |
|---|---|---:|---|---|
| Canadian Nutrient File 2026 | Canada | 5,993 | Open Government Licence – Canada 2.0 ([dataset](https://open.canada.ca/data/en/dataset/1b6139bd-ed7e-4043-bc28-ff00e10f3109)) | "Contains information licensed under the Open Government Licence – Canada." |
| BLS 4.0 | Germany | not counted | CC BY 4.0 ([download](https://blsdb.de/download)) | Max Rubner-Institut (2025), BLS 4.0, DOI 10.25826/Data20251217-134202-0 |
| Livsmedelsverket | Sweden | ~2,500 | CC BY 4.0 ([open data](https://www.livsmedelsverket.se/en/about-us/open-data/food-composition-data/)) | "Livsmedelsverkets Livsmedelsdatabas version YYYY-MM-DD" |
| Ciqual 2025 | France | 3,484 | Etalab Open Licence 2.0 ([Zenodo](https://zenodo.org/records/17550133)) | Cite source and version date |
| CoFID | UK | not stated | Open Government Licence v3.0 ([gov.uk](https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid)) | OGL attribution statement |
| Frida 5.5 | Denmark | ~1,370 | CC BY 4.0 (from search summaries; the Frida site didn't load) | DTU Food, Frida 5.5 |
| MEXT 8th edition | Japan | ~2,500 | No standard licence; apps may use it with a citation (from a third-party summary of MEXT's Q&A) | Cite MEXT |

| Fineli | Finland | 4,232 | CC BY 4.0, verified on [fineli.fi Open Data](https://fineli.fi/fineli/en/avoin-data) | "Finnish Institute for Health and Welfare, Fineli" |
| Matvaretabellen | Norway | ~2,000 | Open API; the [API page](https://www.matvaretabellen.no/api/) invites caching and asks only that Matvaretabellen be cited as the source | "Matvaretabellen" |
| Korea integrated food nutrient data | South Korea | large (ingredients, dishes, processed foods, chain-restaurant menus) | Government standard dataset on [data.go.kr](https://www.data.go.kr/data/15100064/standard.do); the page features a calorie app built on it, but shows no licence field. **Confirm the licence before importing.** | Ministry of Food and Drug Safety, RDA, NIFS |

All of these go in as tier 1 (reference), each with its source and version on
every row. They cover about 20,000 foods in total. This is the same shape of
importer as the USDA one.

## Hold

| Source | Why |
|---|---|
| Australia AFCD | Its licence is based on CC BY-SA 3.0 AU, so share-alike would apply to what we build from it. Same containment question as Open Food Facts; decide alongside it. |
| New Zealand FOODfiles | Its terms say "must not modify any part", and our normalisation is a modification. |
| Dutch NEVO | Unchanged data only, and no charging users for NEVO data. |
| Spain BEDCA, FAO regional tables, Italy CREA, Mexico, Korea, China, Open Food Repo | No open licence found or not checked. FAO items are often CC BY-NC-SA, which rules out commercial use. |
| India IFCT, Philippines PhilFCT | Copyrighted with no open licence. PhilFCT isn't downloadable, and FNRI declined a raw-data request. |

## Added in the final review (2026-10-26)

- **Korea** is the strongest addition. Besides generic foods it carries menu
  items from Korean chains (Domino's, Pizza Hut, Papa John's and others),
  which nothing else we have covers.
- **Restaurant menus are the biggest remaining gap** for the US and Europe.
  NYC MenuStat (96 US chains, about 65,000 items) is open but stopped
  updating in 2019. The better answer is hand-curating the top chains from
  their published nutrition pages, later.
- **Requested permission**, waiting on replies: NZ FOODfiles, India IFCT,
  Philippines PhilFCT, Dutch NEVO, and the Open Food Facts share-alike
  question.
- **Ask next:** Brazil TBCA (commercial use needs the coordinators' OK),
  for Latin America.
- **Dropped:** Switzerland's Open Food Repo has shut down and redirects to
  Open Food Facts, which the dump already covers.

## Skip

- **Edamam, FatSecret, Spoonacular:** API-only, with caching limits below
  custom tiers. Pricing came from third-party blogs, not the vendors' pages.
- **Nutritionix:** the free tier is non-commercial. Paid tiers are expensive,
  so keep it as the optional barcode fallback it already is.
- **Recipe datasets (Recipe1M, RecipeNLG, Food.com scrapes):** scraped, non-commercial or unclear licences, and the nutrition is estimated. Mixed dishes come from FNDDS, the Korean dataset and our own curation.
- **GS1 product data:** not open, and its terms forbid redistribution.
- **Mexico SMAE:** sold, not open.
- **Kaggle "Dietly food sample":** about 5,000 foods with macros only, from
  an unknown source under an unknown licence. Nothing USDA plus Open Food
  Facts doesn't already cover.

## Order of work

1. Open Food Facts importer into its own table, a monthly full refresh, and
   barcode lookups preferring USDA Branded. Load dev, measure, then prod.
2. The Credits screen.
3. CNF, BLS, Livsmedelsverket, Ciqual, CoFID, Frida, MEXT, Fineli, Matvaretabellen and AFCD importers; Korea once its licence is confirmed.
4. Before public launch: counsel or Open Food Facts confirms the ODbL
   containment, and you decide on AFCD.
