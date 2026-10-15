package com.food.app

import android.app.Application
import com.food.app.data.Foods
import com.food.app.data.FoodsDb
import com.food.app.data.UserDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FoodApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val user by lazy { UserDb.open(this) }

    /** Livsmedelsverket's foods and your label foods. Installing foods.db on first start takes a moment. */
    lateinit var foods: Foods
        private set

    override fun onCreate() {
        super.onCreate()
        app = this
        foods = Foods(FoodsDb(this), user, scope)
    }

    companion object {
        lateinit var app: FoodApp
            private set
    }
}
