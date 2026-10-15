# food

A personal food and nutrition tracker for Android, built around Swedish groceries. It tracks energy, macros and every micronutrient, compared with the Nordic Nutrition Recommendations 2023, and it works offline.

Work in progress. Done so far: **milestone (a), the Livsmedelsverket sync**.

## Data

Nutrient data comes from **Livsmedelsverket, Livsmedelsdatabasen** (the Swedish Food Agency's food database), licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Its 2,606 foods are downloaded once into a local SQLite database (`foods.db`), which ships with the app; the API has no search, so all searching is local.

Some rules the data needs:

- **Energy:** kJ and kcal share the code `ENERC`, so nutrients are keyed on code + unit.
- **Carbs:** Livsmedelsverket's are *available* carbohydrates (`CHO_AVAIL`, fibre excluded), kept apart from USDA's by-difference carbs.
- **Vitamin A:** given as retinol equivalents (`VITA_RE`), which differ from USDA's RAE.
- **Assumed zeros:** "Logisk nolla" values are flagged as assumed, not measured.
- **Missing values:** a food with no value for a nutrient is unknown, not zero.

[`data/report-lv.md`](data/report-lv.md) shows the coverage for every nutrient and what Livsmedelsverket doesn't have (vitamin K, B5, biotin, copper, manganese and more). Those gaps will be filled from USDA FoodData Central, and only through matches reviewed by hand.

## Build

JDK 17:

```
./gradlew test                       # unit tests for parsing and the nutrient rules
./gradlew :sync:run --args="lv"      # fetch what changed, rebuild data/foods.db and the report
```

The first sync downloads every food (about a minute and a half). Later syncs only fetch foods whose `version` changed. Raw responses are cached in `data/raw/lv` and the database is always rebuilt from that cache.

## Layout

- `core/`: pure Kotlin: the nutrient catalog, Livsmedelsverket parsing, the foods.db schema and all the rules. Shared by the sync tool and the app.
- `sync/`: the command-line tool that builds foods.db.
