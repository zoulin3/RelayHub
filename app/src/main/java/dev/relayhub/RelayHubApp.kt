package dev.relayhub

import android.app.Application
import dev.relayhub.data.RelayDatabase
import kotlinx.coroutines.runBlocking

class RelayHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 在后台线程建库 / 跑迁移，避免占用启动时的主线程；
        // 这样首屏查询队列时不必再等数据库首次打开。
        Thread {
            runCatching {
                val dao = RelayDatabase.get(this).endpointDao()
                runBlocking { dao.findById(-1L) }
            }
        }.apply {
            name = "relayhub-db-warmup"
            priority = Thread.MIN_PRIORITY
        }.start()
    }
}
