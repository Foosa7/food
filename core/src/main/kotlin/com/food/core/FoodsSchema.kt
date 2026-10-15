package com.food.core

/**
 * foods.db: reference data, shipped with the app and replaced as a whole on update. Nothing
 * the user enters lives here, so an update can never touch their logs. Written by the sync
 * tool on the PC (JDBC) and, later, by the in-app update (Android SQLite) from the same DDL.
 */
object FoodsSchema {
    /** Also stored as PRAGMA user_version. Bump on any change below. */
    const val VERSION = 1

    val DDL = listOf(
        """CREATE TABLE meta (
            key TEXT NOT NULL PRIMARY KEY,
            value TEXT NOT NULL)""",
        """CREATE TABLE nutrient (
            code TEXT NOT NULL PRIMARY KEY,
            name_sv TEXT NOT NULL,
            name_en TEXT NOT NULL,
            unit TEXT NOT NULL,
            grp TEXT NOT NULL,
            sort INTEGER NOT NULL,
            lv_code TEXT,
            lv_unit TEXT)""",
        """CREATE TABLE food (
            id TEXT NOT NULL PRIMARY KEY,
            source TEXT NOT NULL,
            source_id TEXT NOT NULL,
            name_sv TEXT NOT NULL,
            name_en TEXT,
            scientific_name TEXT,
            lv_type TEXT,
            food_group TEXT,
            note TEXT,
            version TEXT,
            waste_pct REAL,
            removed INTEGER NOT NULL DEFAULT 0)""",
        """CREATE TABLE food_nutrient (
            food_id TEXT NOT NULL REFERENCES food(id),
            nutrient TEXT NOT NULL REFERENCES nutrient(code),
            value REAL NOT NULL,
            source TEXT NOT NULL,
            value_type TEXT,
            logical_zero INTEGER NOT NULL DEFAULT 0,
            origin TEXT,
            method TEXT,
            comment TEXT,
            usda_fdc_id INTEGER,
            PRIMARY KEY (food_id, nutrient)) WITHOUT ROWID""",
    )

    object Meta {
        const val SCHEMA = "schema_version"
        const val LV_SYNCED_AT = "lv_synced_at"
        const val LV_FOODS = "lv_foods"
        const val ATTRIBUTION = "attribution"
        const val LICENSE = "license"
    }
}
