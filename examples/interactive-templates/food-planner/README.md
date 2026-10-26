# Provisional source-based food planner

A user-owned Skill/template pack with deterministic menu, recipe and shopping views.
AI selects source recipes and suggests reviewed options; it does not write recipe
bodies, cooking times or temperatures. The native addition is generic, bounded,
conversation-owned Room JSON persistence through Agora's existing sandbox bridge.

## User flow

1. Open `/food` in a chat. Set days, people and dinner-only or three meals, then press **Go ahead**.
   No separate setup-save, recipe-browser or Lidl-selection step is required. The button validates
   the basics, reads the connected local library, prepares at most six source-recipe candidates,
   saves the request setup locally and submits one ordinary Skill request. It does not generate
   recipe instructions or make a Lidl call. Repeat taps during preparation are blocked.
2. Suggestions return in a fresh card as readable meal titles. **Use this suggestion** applies
   the reviewed proposal locally; **Keep my current menu** dismisses it. No suggestion is saved
   without Apply. Longer menus advance a day at a time, retaining the other days.
3. Your menu contains Meals and recipes / Shopping list and appears after meals are saved.
   Product deep dives and changes remain contextual to a selected meal/item.
4. **Optional choices** follows the main action and starts closed. It contains diet, vegetarian
   days, budget/calorie targets and the Ninja preference (on by default), an optional free-text
   request, manual source-recipe choices, Lidl products/prices, a one-recipe ingredient check,
   and saved-plan/example tools. Nothing in it is required for basic recipe planning.
5. Automatic candidates use explicit source diet/category/equipment labels. Dinner planning skips
   source-labelled dessert/breakfast/snack/drink/cake/pudding categories; unknown categories remain
   eligible. Unknown diet labels remain eligible for suitability review. Three-meal planning
   includes source-labelled meal types where available. A manual shortlist takes priority over
   automatic candidates. Bodies stay local; only selected IDs/titles/diet flags reach AI.
6. Missing library access gives a Reference folder recovery message and sends no AI request.
   An inactive or preview card explains that it is read-only and asks the user to close it and
   reopen `/food`. Library edits do not update immutable historical cards. Preview never submits.

The template uses black and white text, fills, borders and controls in both app display modes.
Disabled controls use dashed black borders and explicit state text, never reduced opacity or grey.

Longer plans are selected a day at a time to fit Agora's 3,000-character Skill result
limit. Other saved days and shopping progress are retained. Local controls do not
call a provider. Missing source recipes block planning before an AI request.

## Build/import

```sh
python3 build_pack.py /path/to/food-planner.agora --update-template
```

Import Memories/Skills and Templates using Agora's native importer. Merge preserves
conflicts; inspect conflicts instead of replacing unrelated library resources.
The new persistence bridge requires the matching provisional Agora build; older
installed builds cannot run this pack's saved-state features. Use `/food` in a
conversation. The example menu is explicitly fixture data, not a fetched recipe
or live Lidl data. Real AI selection requires imported source recipes.

Choose a shared prepared-data directory in Settings → Templates → Food planner →
Reference folder, then press Save in the editor. Leave recursive discovery disabled. It contains both catalogue
and source-recipe indexes. The existing sandbox runner needs the same directory's
actual `/mnt/shared/...` path and the helper script paths configured in the Skill.
The pack does not guess or install those paths or execute scripts on import.

The same template ID is Before/After. Each later action requests a fresh root Skill
invocation; it does not configure a cyclic nextSkill or reuse a completed token.
The existing native one-handoff/current-card guards still apply. Reopen `/food`
when conversation history has moved beyond a historical card.

## Source recipes and Ninja focus

```sh
# Offline import of an existing saved source page:
python3 import_recipes.py /path/to/recipe.html /path/to/FoodData \
  --url https://ACTUAL-SOURCE/recipe
# Explicit fetch through the existing script runner (not through the template):
python3 fetch_recipe.py https://ACTUAL-SOURCE/recipe /path/to/FoodData
```

Supports Recipe JSON-LD, including @graph, HowToStep and HowToSection. A missing
structured recipe or serving count fails visibly; nothing is filled from model
memory. Original ingredients, instructions, source URL/hash, retrieval time and
source nutrition remain available locally. Unsupported quantities remain unknown;
no gram/ml conversion or price is invented. fetch_recipe.py reuses an already
imported source; --refresh explicitly fetches a new source version. The offline collection is downloaded explicitly with download_collection.py;
normal browsing, filtering and saving never fetch a source.

The index is `recipes-index.json`; immutable records live under `recipes/`.
Browse then add a recipe to the current menu shortlist. Save recipe is separate. Ninja-tagged source
recipes are prioritised when the default focus is enabled. General Ninja recipes
are labelled separately from sources explicitly mentioning 11-in-1; exact appliance
compatibility is not inferred and cooking modes are not translated. Turning focus
off shows sources without Ninja prioritisation. The source kit contains code only; the separately prepared library contains
source recipes and provenance, not a generated cookbook.

Budget/calorie controls are targets, not verified guarantees. The template reports
known costs and unpriced items. Source nutrition is displayed with its provenance;
unverified product matches are not used to claim verified calorie totals.

## Lidl categories, dated files and guarded refresh

Reuse the user's EXISTING Lidl fetcher. The yuka-lidl investigation references
lidl_catalogue.py, but that source is not in this checkout; it is not reimplemented
or guessed here. For a saved snapshot:

```sh
python3 prepare_catalogue.py /path/to/tallaght.json /path/to/FoodData \
  --category-map /path/to/categories.json --off-pilot /path/to/pilot-staging.json
```

--off-pilot is optional. Snapshot fields: products[] (id/title/brand/price), storeId,
fetchedAtUtc and totalProducts for completeness. Category labels come only from
explicit source fields or exact product-ID mappings. The saved pilot has no category
labels, so export the existing fetcher's taxonomy as a mapping. The example mapping
shows its shape, not real product IDs. Missing labels remain Uncategorised.

```
catalogue-index.json
catalogues/2026-10-25T120000Z-STORE-HASH/index.json
catalogues/2026-10-25T120000Z-STORE-HASH/category-000-pantry-pasta-000.json
catalogues/2026-10-25T120000Z-STORE-HASH/category-001-fresh-fish-000.json
```

Each category page holds at most 25 full product records. There are no duplicate
names-only files. The template projects names at the handoff and does not attach
raw catalogue files. Product prices, weights, IDs, stock and OFF data are excluded
from AI requests. Recipe-selection IDs and user constraints are separate control
inputs, not product metadata. Only chosen categories are read; unrelated fish
records do not need to be loaded for a pasta request.

Use the guarded wrapper instead of invoking the fetcher directly:

```sh
python3 refresh_catalogue.py /path/to/tallaght.json /path/to/FoodData \
  --store IE0206 --max-age-hours 72 --category-map /path/to/categories.json \
  --fetch-command-json '["python3","/ACTUAL/lidl_catalogue.py","ACTUAL_OUTPUT_FLAG","{output}"]'
```

Configure the actual existing fetcher's argv; the shown output flag is a placeholder.
Fresh, complete, matching-store data skips the fetch command ENTIRELY. Completeness
requires a known count matching distinct valid ID/title records. Unknown completeness
is not complete. A process lock and recheck avoid duplicate concurrent refreshes.
Failed, timed-out or incomplete candidates preserve the prior snapshot. Refresh
emits a small receipt only, never full metadata into model context. Default age is
72 hours, configurable. The UI shows age and completeness. Old dated snapshots
remain when the latest pointer changes. Data/enrichment are prepared locally on a
cache hit, without a Lidl call.

## Open Food Facts

Saved yuka-lidl pilot results join by Lidl ID into the SAME category records.
Product details show candidate ingredients/allergens, nutrients, Nutri-Score/NOVA,
conflicts, source/time and attribution. All pilot candidates remain unverified,
with staging/production provenance visible. There are no Yuka ratings. These fields
are not sent to AI. Explicit refresh_off.py uses cached OFF v3 barcode lookups and re-prepares the
local category files; it never fetches Lidl. Enrichment updates do not force a Lidl refresh.

## Persistence and provisional limits

`Websandbox.connection.remote.storageGet({key})` returns `{revision,value}`;
`storageSet({key,expectedRevision,value})` requires a user gesture and exact revision.
Native card identity selects the conversation. Browser storage and same-origin iframe
access stay disabled. Stale views cannot overwrite a newer save. Limits: 64,000
UTF-8 value bytes, 32 keys, 1 MiB per conversation. Previews/Remote have no handler.

State survives view disposal/restart and travels in native conversation backups,
imports and forks. Deleting a conversation removes its state with the Room row.
Reference-folder recipe/catalogue files stay separate; keep their library ZIP. Local Undo is one-view
only. User data is not automatically shared across chats. Interface requests are
limited to 4,000 characters and completion receipts to 3,000; oversize selections
fail visibly. The generated template stays within existing native format limits.

Physical UI, e-ink panel behavior and live model/tool compliance remain owner checks.
No emulator, device interaction, live Lidl call or paid AI request was used here.
A single OFF staging barcode lookup succeeded, followed by a no-network cache hit.

## Source and checks

Canonical interface sources are engine.js, planner.js, planner.html, planner.css and
example.json. build_pack.py --update-template regenerates the checked-in template;
tests reject stale generated JSON. The archive contains Skills/Templates, not helper
installation or credentials. Copy these Python helpers separately for the runner.

```sh
node --test tests/engine.test.cjs
# Install jsdom separately, or set JSDOM_MODULE to its installed module directory:
node --test tests/ui.test.cjs
python3 -m unittest discover -s tests -v
```

Native checks use the real InteractiveTemplate/SkillDocument validators, storage CAS,
artifact ownership and existing Skill workflow/submission tests. All network work in
refresh tests is replaced with synthetic runners; a fresh cache test rejects any
attempt to invoke the fetcher.

## Offline library and saved recipes

`download_collection.py DESTINATION --cache CACHE` imports public publisher Recipe
JSON-LD into local full-recipe files, retaining author, original text, URL, retrieval
time and source hash. It uses public sitemaps, checks robots, paces one request per
second per publisher, caches source HTML and stops on 401/403/429. No Lidl or AI call
is involved. Download failures and unsupported recipes are recorded per publisher.
The native app does not crawl websites; copy the prepared folder to the device.

`recipe_index.py DESTINATION` converts an existing flat index to a v2 manifest with
content-addressed pages of at most 60 summaries and 100 KB. Imports maintain this
format. The template loads summaries only on Browse; inspecting/shortlisting reads
one full recipe. Search is local, paginated (20 rows), with title/category, diet,
optional maximum calories and saved/shortlisted views. Missing filter information
is included by default, clearly labelled; the user may exclude unknowns. Ninja
focus ranks exact 11-in-1 mentions first, then multi-cooker recipes, then other
Ninja recipes. A Ninja recipe is not automatically compatible with an 11-in-1.

Save recipe stores only its title separately from the current menu shortlist. It survives
reopening the same chat without an AI request; saving does not add its full body
to the 64 KB plan. The local library folder must remain available. Saved recipes
are currently chat-scoped, not a global account library, and travel with conversation backups/forks.

Source metadata is conservative: explicit source diet labels only, no inferred
vegan status; zero/blank calories are unknown. Diabetes UK diet icons are read from
the actual recipe's Special diets region, excluding the site's symbols legend.
Its calorie panel is per source serving and can include serving suggestions. Ninja
servings come from its recipe serving table when omitted from JSON-LD; metric
ingredient variants are preserved. Equipment classification uses recipe content,
never a site's footer list of every appliance. Full instructions stay local.

Favourites contain titles only (no recipe ID, body, ingredients or metadata). The saved view displays those titles immediately; Find in local library resolves candidate source recipes locally. Duplicate titles can match multiple sources, which the user chooses when shortlisting. Legacy prototype ID bookmarks are resolved locally when their library becomes available.

## Explicit OFF refresh

`refresh_off.py SNAPSHOT PILOT DESTINATION --product-name EXACT_NAME --cache CACHE`
resolves an existing unique candidate barcode locally and refreshes its OFF record.
The product deep-dive button sends only the product name to the Skill. Configure
actual runner paths in the Skill as for Lidl/import helpers. A missing/ambiguous
candidate needs explicit local setup using `--barcode CODE --product-id LOCAL_ID`;
no identity or barcode is guessed. Staging is default; production is explicit and
uses a separate enrichment/cache identity. Cache age defaults to 30 days, lookup
rate is fenced across processes at <=12 requests/minute with >=5-second spacing.
HTTP errors stop without replacing the old cache. The publisher's v3 product API
and current read limits are documented at
https://openfoodfacts.github.io/openfoodfacts-server/api/ . Enrichment is attributed
to Open Food Facts contributors (ODbL), remains local and remains unverified as a
Lidl identity. OFF refresh cannot force a Lidl API request.

## Deferred by owner

The original Linux Lidl fetcher connection and live AI-provider check were deferred
on 25 October 2026. Their local adapters and synthetic workflow tests remain ready.
The official OL550UK PDF is deferred separately; see RECIPE-SOURCES-TODO.md. Device
checks belong to the owner. No device/emulator is used by these build/test scripts.

## Updating an already imported planner

Use Settings → Templates → Food planner’s menu → Replace from file and choose the new
`Agora-Food-Planner-SIMPLE.agora-template.json`. This native operation changes only the interface
answer, retaining the existing template ID, source-folder grant, reference options and Skill link.
Close the old card and open `/food` again in a chat. Do not use a catalogue-wide Replace merely to
update this interface. The ZIP is for a first import; importing it with Update replaces the full
portable entry and therefore requires reselecting a device-local Reference folder.

Browser QA loads the production base styles, e-ink profile and template styles at 411×914. It
checks that Go ahead is visible, uses the real 525-recipe library through a mock local bridge,
requires an actual browser user activation for save/send, and checks computed visible text/fill/
border/outline colours plus opacity in default/e-ink and optional/disabled states. It does not
contact an AI provider or operate the physical device. Native template validators, synthetic CAS
and workflow tests remain separate from live account compliance and owner device acceptance.
