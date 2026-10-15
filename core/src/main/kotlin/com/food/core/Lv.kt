package com.food.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Livsmedelsverket food database API, as its JSON comes back.
 * Base https://dataportal.livsmedelsverket.se/livsmedel/api/v1. It has no search, so
 * everything is downloaded once and searched locally. Values are per 100 g edible part.
 */
object Lv {
    const val BASE = "https://dataportal.livsmedelsverket.se/livsmedel/api/v1"
    const val SV = 1
    const val EN = 2

    /** Required by the CC BY 4.0 licence wherever the data is shown. */
    const val ATTRIBUTION = "Livsmedelsverket, Livsmedelsdatabasen"
    const val LICENSE = "CC BY 4.0"

    val json = Json { ignoreUnknownKeys = true }

    fun listUrl(offset: Int, limit: Int, lang: Int) = "$BASE/livsmedel?offset=$offset&limit=$limit&sprak=$lang"
    fun nutrientsUrl(nummer: Int) = "$BASE/livsmedel/$nummer/naringsvarden?sprak=$SV"
    fun classesUrl(nummer: Int) = "$BASE/livsmedel/$nummer/klassificeringar?sprak=$SV"

    fun parseList(body: String): LvListPage = json.decodeFromString(body)
    fun parseNutrients(body: String): List<LvNutrient> = json.decodeFromString(body)
    fun parseClasses(body: String): List<LvClass> = json.decodeFromString(body)

    /** Livsmedelsverket's value types (vardetypkod), in English, for "where did this number come from". */
    val VALUE_TYPES = mapOf(
        "BE" to "Best estimate",
        "LZ" to "Logical zero: assumed, not measured",
        "MN" to "Mean of analyses",
        "AV" to "Average",
        "W" to "Weighted value",
        "MD" to "Median",
        "BL" to "Not detected",
        "TR" to "Trace",
        "BQ" to "Below the quantification limit",
        "AR" to "Reported",
        "MX" to "Maximum",
        "E" to "Other value type",
        "N" to "Unknown",
        "X" to "Unknown value type",
    )

    /** The "Huvudgrupp" classification, e.g. "Naturell fil yoghurt". */
    fun mainGroup(classes: List<LvClass>): String? = classes.firstOrNull { it.typ == "Huvudgrupp" }?.kod
}

@Serializable
data class LvListPage(
    @SerialName("_meta") val meta: LvMeta,
    val livsmedel: List<LvFood>,
)

@Serializable
data class LvMeta(val totalRecords: Int, val offset: Int, val limit: Int, val count: Int)

@Serializable
data class LvFood(
    val nummer: Int,
    val namn: String,
    /** Changes whenever the food's data changes, so re-syncs only fetch what moved. */
    val version: String? = null,
    val livsmedelsTyp: String? = null,
    val vetenskapligtNamn: String? = null,
    val projekt: String? = null,
    /** Which products were analysed, often with brand names. */
    val analys: String? = null,
)

@Serializable
data class LvNutrient(
    val namn: String,
    val euroFIRkod: String,
    val forkortning: String? = null,
    val varde: Double? = null,
    val enhet: String,
    val viktGram: Double? = null,
    val matrisenhet: String? = null,
    val vardetyp: String? = null,
    val vardetypkod: String? = null,
    val ursprung: String? = null,
    val ursprungkod: String? = null,
    val publikation: String? = null,
    val metodtyp: String? = null,
    val metodtypkod: String? = null,
    val kommentar: String? = null,
)

@Serializable
data class LvClass(
    val typ: String,
    val fasett: String? = null,
    val namn: String? = null,
    val kod: String? = null,
)
