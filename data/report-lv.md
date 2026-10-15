# Livsmedelsverket sync report

Source: Livsmedelsverket, Livsmedelsdatabasen (CC BY 4.0). Values per 100 g edible part.

- Foods listed: 2606
  - Analyserat: 1717
  - Beräknat: 889
- Removed by Livsmedelsverket but kept: 0
- Nutrient values: 152318, of which assumed zeros (Logisk nolla): 10557
- Foods with an inedible share (WASTE > 0): 813
- Foods without a main group: 0
- Foods without an English name: 0

## Unknown nutrients

None: every code + unit pair is in the catalog.

## Coverage per nutrient

Missing means the food has no value at all: unknown, which the app must not count as zero.

| Nutrient | Unit | Code | Has a value | Of which assumed zero | Missing |
|---|---|---|---:|---:|---:|
| Energy | kcal | `ENERC_KCAL` | 2606 | 0 | 0 |
| Energy (kJ) | kJ | `ENERC_KJ` | 2606 | 0 | 0 |
| Protein | g | `PROT` | 2606 | 10 | 0 |
| Fat, total | g | `FAT` | 2606 | 47 | 0 |
| Carbohydrates, available | g | `CHO_AVAIL` | 2606 | 58 | 0 |
| Fibre | g | `FIBT` | 2606 | 454 | 0 |
| Alcohol | g | `ALC` | 2606 | 1334 | 0 |
| Water | g | `WATER` | 2606 | 14 | 0 |
| Ash | g | `ASH` | 2606 | 2 | 0 |
| Sugars, total | g | `SUGAR` | 2606 | 181 | 0 |
| Added sugar | g | `SUGAD` | 2606 | 1145 | 0 |
| Free sugars | g | `SUGFR` | 2606 | 1092 | 0 |
| Monosaccharides | g | `MNSAC` | 2606 | 85 | 0 |
| Disaccharides | g | `DISAC` | 2606 | 84 | 0 |
| Sucrose | g | `SUCS` | 2606 | 222 | 0 |
| Whole grains, total | g | `WHOLET` | 2606 | 14 | 0 |
| Whole grains from pseudocereals | g | `WHOLE_PSEUDO` | 2567 | 1707 | 39 |
| Saturated fat | g | `FASAT` | 2606 | 82 | 0 |
| Monounsaturated fat | g | `FAMS` | 2606 | 82 | 0 |
| Polyunsaturated fat | g | `FAPU` | 2606 | 82 | 0 |
| Cholesterol | mg | `CHORL` | 2604 | 608 | 2 |
| Fatty acids 4:0–10:0 | g | `F4-10:0` | 2605 | 82 | 1 |
| Lauric acid C12:0 | g | `F12:0` | 2605 | 102 | 1 |
| Myristic acid C14:0 | g | `F14:0` | 2606 | 98 | 0 |
| Palmitic acid C16:0 | g | `F16:0` | 2606 | 98 | 0 |
| Palmitoleic acid C16:1 | g | `F16:1` | 2606 | 100 | 0 |
| Stearic acid C18:0 | g | `F18:0` | 2606 | 98 | 0 |
| Oleic acid C18:1 | g | `F18:1` | 2606 | 98 | 0 |
| Linoleic acid C18:2 (omega-6) | g | `F18:2` | 2605 | 98 | 1 |
| Linolenic acid C18:3 (omega-3) | g | `F18:3` | 2606 | 83 | 0 |
| Arachidic acid C20:0 | g | `F20:0` | 2606 | 99 | 0 |
| Arachidonic acid C20:4 (omega-6) | g | `F20:4` | 2605 | 90 | 1 |
| EPA C20:5 (omega-3) | g | `F20:5` | 2604 | 91 | 2 |
| DPA C22:5 (omega-3) | g | `F22:5` | 2603 | 93 | 3 |
| DHA C22:6 (omega-3) | g | `F22:6` | 2603 | 93 | 3 |
| Vitamin A (RE) | µg | `VITA_RE` | 2605 | 0 | 1 |
| Retinol | µg | `RETOL` | 2606 | 198 | 0 |
| Beta-carotene | µg | `CARTBTOT` | 2605 | 89 | 1 |
| Vitamin D | µg | `VITD` | 2606 | 249 | 0 |
| Vitamin D incl. 25-OH-D3 | µg | `VITD_25OH` | 1291 | 0 | 1315 |
| Vitamin E | mg | `VITE` | 2605 | 0 | 1 |
| Thiamin (B1) | mg | `THIA` | 2606 | 39 | 0 |
| Riboflavin (B2) | mg | `RIBF` | 2606 | 40 | 0 |
| Niacin (B3) | mg | `NIA` | 2606 | 40 | 0 |
| Niacin equivalents | mg | `NIAEQ` | 2605 | 1 | 1 |
| Vitamin B6 | mg | `VITB6` | 2606 | 40 | 0 |
| Folate, total | µg | `FOL` | 2606 | 44 | 0 |
| Vitamin B12 | µg | `VITB12` | 2606 | 607 | 0 |
| Vitamin C | mg | `VITC` | 2606 | 581 | 0 |
| Calcium | mg | `CA` | 2606 | 0 | 0 |
| Phosphorus | mg | `P` | 2606 | 0 | 0 |
| Magnesium | mg | `MG` | 2606 | 0 | 0 |
| Potassium | mg | `K` | 2606 | 0 | 0 |
| Sodium | mg | `NA` | 2606 | 0 | 0 |
| Salt | g | `NACL` | 2605 | 0 | 1 |
| Iron | mg | `FE` | 2606 | 1 | 0 |
| Zinc | mg | `ZN` | 2606 | 1 | 0 |
| Iodine | µg | `ID` | 2543 | 1 | 63 |
| Selenium | µg | `SE` | 2606 | 0 | 0 |

## Not in Livsmedelsverket at all

Candidates for the USDA gap-fill. Omega-3 and omega-6 totals can be summed from the fatty acids above.

- Vitamin K
- Pantothenic acid (B5)
- Biotin (B7)
- Choline
- Copper
- Manganese
- Chromium
- Molybdenum
- Fluoride
- Chloride
- Trans fat
- Caffeine
- Amino acids
