package com.food.sync

import com.food.core.Lv
import com.food.core.LvFood
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mirrors the Livsmedelsverket API into a raw cache:
 *
 *   list-sv.json, list-en.json   every food, Swedish and English names
 *   n/{nummer}.json              nutrients, as returned
 *   k/{nummer}.json              classifications, as returned
 *   index.json                   nummer → the version those two files were fetched at
 *
 * A food is fetched again only when the list shows a new version, so a re-sync is quick.
 * The database is then rebuilt from the cache, never straight from the network.
 */
class LvFetch(private val cache: File, private val threads: Int = 3) {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
    private val indexFile = File(cache, "index.json")
    private val indexSerializer = MapSerializer(String.serializer(), String.serializer())
    private val foodsSerializer = ListSerializer(LvFood.serializer())

    data class Result(val foods: Int, val fetched: Int, val failed: List<Int>)

    fun run(full: Boolean = false, log: (String) -> Unit = ::println): Result {
        File(cache, "n").mkdirs()
        File(cache, "k").mkdirs()

        val sv = list(Lv.SV)
        val en = list(Lv.EN)
        File(cache, "list-sv.json").writeText(Lv.json.encodeToString(foodsSerializer, sv))
        File(cache, "list-en.json").writeText(Lv.json.encodeToString(foodsSerializer, en))
        remember("seen-sv.json", sv)
        remember("seen-en.json", en)
        log("Livsmedelsverket lists ${sv.size} foods (${en.size} with English names)")

        val index = ConcurrentHashMap(if (full || !indexFile.exists()) emptyMap() else
            Lv.json.decodeFromString(indexSerializer, indexFile.readText()))
        val todo = sv.filter { food ->
            index[food.nummer.toString()] != food.version ||
                !nutrientsFile(food.nummer).exists() || !classesFile(food.nummer).exists()
        }
        log("${todo.size} new or changed")

        val done = AtomicInteger()
        val failed = ConcurrentHashMap.newKeySet<Int>()
        val pool = Executors.newFixedThreadPool(threads)
        for (food in todo) pool.execute {
            runCatching {
                // Written to temp names first, so an interrupted run never leaves half a file.
                val n = get(Lv.nutrientsUrl(food.nummer))
                val k = get(Lv.classesUrl(food.nummer))
                Lv.parseNutrients(n)
                Lv.parseClasses(k)
                writeAtomic(nutrientsFile(food.nummer), n)
                writeAtomic(classesFile(food.nummer), k)
                index[food.nummer.toString()] = food.version ?: ""
            }.onFailure {
                failed += food.nummer
                log("  #${food.nummer} ${food.namn}: ${it.message}")
            }
            val d = done.incrementAndGet()
            if (d % 200 == 0) {
                log("  $d / ${todo.size}")
                saveIndex(index)
            }
        }
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.HOURS)
        saveIndex(index)
        return Result(sv.size, todo.size - failed.size, failed.sorted())
    }

    fun nutrientsFile(nummer: Int) = File(cache, "n/$nummer.json")
    fun classesFile(nummer: Int) = File(cache, "k/$nummer.json")

    /** Every food ever listed, so one Livsmedelsverket drops can stay in foods.db as removed. */
    private fun remember(name: String, foods: List<LvFood>) {
        val file = File(cache, name)
        val seen = if (file.exists()) Lv.json.decodeFromString(foodsSerializer, file.readText()) else emptyList()
        val merged = (seen + foods).associateBy { it.nummer }.toSortedMap().values.toList()
        writeAtomic(file, Lv.json.encodeToString(foodsSerializer, merged))
    }

    private fun list(lang: Int): List<LvFood> {
        val out = mutableListOf<LvFood>()
        var total: Int
        do {
            val page = Lv.parseList(get(Lv.listUrl(out.size, PAGE, lang)))
            total = page.meta.totalRecords
            out += page.livsmedel
        } while (page.livsmedel.isNotEmpty() && out.size < total)
        check(out.size == total) { "list ended at ${out.size} of $total" }
        return out
    }

    private fun get(url: String): String {
        var wait = 1000L
        repeat(RETRIES) { attempt ->
            val result = runCatching {
                val req = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json").GET().build()
                http.send(req, HttpResponse.BodyHandlers.ofString())
            }
            val res = result.getOrNull()
            if (res != null && res.statusCode() == 200) return res.body()
            if (attempt == RETRIES - 1) {
                throw IllegalStateException(res?.let { "HTTP ${it.statusCode()} for $url" }
                    ?: "${result.exceptionOrNull()?.message} for $url")
            }
            Thread.sleep(wait)
            wait *= 3
        }
        error("unreachable")
    }

    @Synchronized
    private fun saveIndex(index: Map<String, String>) =
        writeAtomic(indexFile, Lv.json.encodeToString(indexSerializer, index.toSortedMap(compareBy { it.toInt() })))

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        check(tmp.renameTo(file)) { "could not write $file" }
    }

    private companion object {
        const val PAGE = 3000
        const val RETRIES = 4
    }
}
