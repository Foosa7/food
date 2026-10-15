package com.food.app

import android.app.Application
import com.food.app.data.Catalog
import com.food.app.data.FoodsDb
import com.food.app.data.UserDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class FoodApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val foods by lazy { FoodsDb(this) }
    val user by lazy { UserDb.open(this) }

    /** Loaded once in the background; installing foods.db on first start takes a moment. */
    lateinit var catalog: Deferred<Catalog>
        private set

    override fun onCreate() {
        super.onCreate()
        app = this
        catalog = scope.async(Dispatchers.IO) { foods.catalog() }
    }

    companion object {
        lateinit var app: FoodApp
            private set
    }
}
