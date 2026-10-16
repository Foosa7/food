# food

A personal food and nutrition tracker for Android, built around Swedish groceries. It tracks energy, macros and every micronutrient, compared with the Nordic Nutrition Recommendations 2023, and it works offline (only the optional AI analysis goes online).

Work in progress. Done so far:

- **(a)** the Livsmedelsverket sync to SQLite
- **(b)** the app: search Swedish and English names, log food by gram or ml to a meal, and see a day's energy and macros, with every nutrient of a food and where each value came from
- **(c)** every nutrient against your **NNR 2023** targets (this day or a 7-day average), with low / over flags and where each nutrient came from; a profile with every field and target editable; activity (steps, calories burned) from Fitbit via Health Connect; history with weekly averages; export to JSON + SQLite
- foods from a package label, optionally based on a database food for the micronutrients the label doesn't list
- **(d)** barcode scanning (EAN-13/EAN-8, offline): a known product logs in one tap with its usual portion; an unknown one is linked to a database food, with your name for it, as you log it
- **(e)** an **Analysis** tab: this week against last (energy, protein, fibre, salt, vitamin D, EPA+DHA omega-3, fermented servings, nutrients on target), and a short AI take from Kimi with your own key. The model reads every nutrient against your targets, week by week for 12 weeks and day by day for two, and keeps its own notes between runs so it follows your progress over time
- gut food: fibre against NNR, and servings of fermented foods with live cultures (by name, or ticked on a label food)

## Data

Nutrient data comes from **Livsmedelsverket, Livsmedelsdatabasen** (the Swedish Food Agency's food database), licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Its 2,606 foods are downloaded once into a local SQLite database (`foods.db`), which ships with the app; the API has no search, so all searching is local.

Some rules the data needs:

- **Energy:** kJ and kcal share the code `ENERC`, so nutrients are keyed on code + unit.
- **Carbs:** Livsmedelsverket's are *available* carbohydrates (`CHO_AVAIL`, fibre excluded), kept apart from USDA's by-difference carbs.
- **Vitamin A:** given as retinol equivalents (`VITA_RE`), which differ from USDA's RAE.
- **Assumed zeros:** "Logisk nolla" values are flagged as assumed, not measured.
- **Missing values:** a food with no value for a nutrient is unknown, not zero.

Targets follow the Nordic Nutrition Recommendations 2023 (Blomhoff et al., Nord 2023:003): energy from NNR's Henry equations × activity level, macronutrient ranges in % of energy, and the RI/AI, average requirements and upper limits for vitamins and minerals. Vitamin A is in retinol equivalents in both NNR and the database. A total that leaves out foods with no value is shown as "≥".

[`data/report-lv.md`](data/report-lv.md) shows the coverage for every nutrient and what Livsmedelsverket doesn't have (vitamin K, B5, biotin, copper, manganese and more).

**USDA FoodData Central** (public domain) is the second source: SR Legacy (7,793 foods, analysed and literature values) and Foundation Foods (469 foods, new lab analyses), searchable next to Livsmedelsverket's, so each food can come from whichever source is the most accurate for it. A label food can be based on either. USDA's values go through the same rules, converted where USDA reports them differently:

- **Carbs:** USDA's are by difference and include fibre, so available carbs = that minus fibre (unknown when there's no fibre value and the carbs aren't zero).
- **Vitamin A:** RAE isn't RE, so retinol equivalents are worked out from retinol + β-carotene/6 + other provitamin A carotenoids/12.
- **Salt:** sodium × 2.5. **Niacin equivalents:** niacin + tryptophan/60, when tryptophan is known.
- Foundation Foods' alternative nutrient numbers (Atwater energy, 18:2 n-6 c,c and the like) are used only where the usual one is missing, and each such value says so.

## Build

JDK 17:

```
./gradlew test                       # unit tests for parsing and the nutrient rules
./gradlew :sync:run --args="lv"      # fetch what changed, rebuild data/foods.db and the report
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk (needs data/foods.db and the Android SDK)
```

USDA's data comes from its CSV downloads at https://fdc.nal.usda.gov/download-datasets: put the SR Legacy and Foundation Foods zips in `data/raw/usda/` and every build includes them.

The first sync downloads every food (about a minute and a half). Later syncs only fetch foods whose `version` changed. Raw responses are cached in `data/raw/lv` and the database is always rebuilt from that cache.

## Layout

- `core/`: pure Kotlin: the nutrient catalog, Livsmedelsverket parsing, the foods.db schema and all the rules. Shared by the sync tool and the app.
- `sync/`: the command-line tool that builds foods.db.
- `app/`: the Android app (Kotlin, Jetpack Compose, Room). It bundles foods.db read-only; your log lives in a separate `user.db` on the phone. It leaves the phone only when you export it, or as a summary sent to the AI model when you ask for an analysis; the API key is kept in the app's private storage, never in this repo.
