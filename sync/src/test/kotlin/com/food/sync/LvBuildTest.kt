package com.food.sync

import com.food.core.FoodsSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager

class LvBuildTest {
    @get:Rule val tmp = TemporaryFolder()

    private val fixtures = File("core/src/test/resources/lv")

    /** A cache holding #142, plus #9999 which an earlier sync saw but the list no longer has. */
    private fun cache(): File {
        val cache = tmp.newFolder("lv")
        File(cache, "n").mkdirs(); File(cache, "k").mkdirs()
        val sv = File(fixtures, "list-sv.json").readText()
        val gone = """{"nummer":9999,"namn":"Borttagen mat","version":"2020-01-01"}"""
        File(cache, "list-sv.json").writeText(sv)
        File(cache, "list-en.json").writeText(File(fixtures, "list-en.json").readText())
        File(cache, "seen-sv.json").writeText(sv.trimEnd().removeSuffix("]") + ",$gone]")
        File(fixtures, "142-naringsvarden.json").copyTo(File(cache, "n/142.json"))
        File(fixtures, "142-klassificeringar.json").copyTo(File(cache, "k/142.json"))
        File(fixtures, "142-naringsvarden.json").copyTo(File(cache, "n/9999.json"))
        return cache
    }

    @Test fun `builds foods db that reads back`() {
        val out = File(tmp.root, "foods.db")
        val result = LvBuild.run(cache(), out)
        assertEquals(1, result.listed)

        DriverManager.getConnection("jdbc:sqlite:${out.path}").use { db ->
            fun one(sql: String) = db.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getString(1) } }
            assertEquals(FoodsSchema.VERSION.toString(), one("PRAGMA user_version"))
            assertEquals("Livsmedelsverket, Livsmedelsdatabasen", one("SELECT value FROM meta WHERE key='attribution'"))
            assertEquals("60.0", one("SELECT value FROM food_nutrient WHERE food_id='lv:142' AND nutrient='ENERC_KCAL'"))
            assertEquals("LV", one("SELECT source FROM food_nutrient WHERE food_id='lv:142' AND nutrient='PROT'"))
            assertEquals("1", one("SELECT logical_zero FROM food_nutrient WHERE food_id='lv:142' AND nutrient='FIBT'"))
            assertEquals("0", one("SELECT removed FROM food WHERE id='lv:142'"))
            assertEquals("1", one("SELECT removed FROM food WHERE id='lv:9999'"))
        }
    }

    @Test fun `report lists the gaps`() {
        val report = LvReport.render(LvBuild.run(cache(), File(tmp.root, "foods.db")))
        assertTrue("Vitamin K" in report)
        assertTrue("None: every code + unit pair is in the catalog." in report)
    }
}
