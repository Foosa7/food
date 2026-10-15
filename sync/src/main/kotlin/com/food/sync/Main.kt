package com.food.sync

import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """Usage: ./gradlew :sync:run --args="<command> [--full]"

  lv      fetch what changed at Livsmedelsverket, then build data/foods.db (the usual one)
  fetch   only update the raw cache in data/raw/lv
  build   only rebuild data/foods.db and data/report-lv.md from the cache

  --full  fetch every food again, ignoring the cache
"""

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: run { println(USAGE); exitProcess(2) }
    val full = "--full" in args
    val cache = File("data/raw/lv")

    when (command) {
        "lv" -> { fetch(cache, full); build(cache) }
        "fetch" -> fetch(cache, full)
        "build" -> build(cache)
        else -> { println(USAGE); exitProcess(2) }
    }
}

private fun fetch(cache: File, full: Boolean) {
    val result = LvFetch(cache).run(full)
    println("Fetched ${result.fetched} foods")
    if (result.failed.isNotEmpty()) {
        println("FAILED: ${result.failed.joinToString()} (run again to retry just these)")
        exitProcess(1)
    }
}

private fun build(cache: File) {
    val result = LvBuild.run(cache, File("data/foods.db"))
    File("data/report-lv.md").writeText(LvReport.render(result))
    val unknown = result.foods.sumOf { it.unknown.size }
    println("Wrote ${result.out} (${result.out.length() / 1024} KB): ${result.foods.size} foods, " +
        "${result.foods.sumOf { it.nutrients.size }} values. Report: data/report-lv.md")
    if (unknown > 0) println("WARNING: $unknown values with nutrients the catalog doesn't know; see the report")
}
