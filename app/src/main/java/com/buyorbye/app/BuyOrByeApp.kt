package com.buyorbye.app

import android.app.Application
import com.buyorbye.app.data.AppContainer

class BuyOrByeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
