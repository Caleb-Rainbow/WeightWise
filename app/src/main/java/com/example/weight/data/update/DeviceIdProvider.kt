package com.example.weight.data.update

import com.tencent.mmkv.MMKV
import java.util.UUID

/**
 * 设备 ID：首次生成 UUID 并持久化（MMKV），随 check-update 上报，
 * 供服务端定向更新（设备白名单灰度）使用。
 */
object DeviceIdProvider {

    private const val KEY = "device_id"

    private val mmkv by lazy { MMKV.mmkvWithID("weight_update") }

    @Volatile
    private var cached: String? = null

    fun get(): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val stored = mmkv.decodeString(KEY)
            val id = if (!stored.isNullOrBlank()) stored else {
                UUID.randomUUID().toString().also { mmkv.encode(KEY, it) }
            }
            cached = id
            return id
        }
    }
}
