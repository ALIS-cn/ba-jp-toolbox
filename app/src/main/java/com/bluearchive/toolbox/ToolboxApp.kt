package com.bluearchive.toolbox

import android.app.Application
import com.bluearchive.toolbox.core.log.LogCollector

class ToolboxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogCollector.init(this)
    }
}
