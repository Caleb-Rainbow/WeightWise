package com.example.weight.data

import com.dylanc.mmkv.MMKVOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.serializer

const val DEFAULT_HEIGHT = 170.0
const val DEFAULT_TARGET_WEIGHT = 0.0

object LocalStorageData : MMKVOwner(mmapID = "settings") {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /*--------基础信息---------*/
    val height by mmkvDouble(default = DEFAULT_HEIGHT).asStateFlow()

    /*--------目标相关---------*/
    val targetWeight by mmkvDouble(default = DEFAULT_TARGET_WEIGHT).asStateFlow()
    /*--------其他---------*/
    var isFirst by mmkvBool(default = true)

    /*--------AI 提供商相关---------*/
    val doubaoModelId by mmkvString(default = "doubao-seed-2-0-lite-260215").asStateFlow()

    /*--------运动偏好与限制---------*/
    private var exerciseBlacklistJson by mmkvString(default = "[]")
    private var exerciseWhitelistJson by mmkvString(default = "[]")
    val exerciseScene by mmkvString(default = "").asStateFlow()

    /*--------旅程相关---------*/
    // MainScreen 等需要响应式判断旅程图标路由，用 asStateFlow()
    val activeJourneyId by mmkvInt(default = 0).asStateFlow()

    private val _exerciseBlacklist = MutableStateFlow(parseSet(exerciseBlacklistJson))
    val exerciseBlacklist: StateFlow<Set<String>> = _exerciseBlacklist.asStateFlow()

    private val _exerciseWhitelist = MutableStateFlow(parseSet(exerciseWhitelistJson))
    val exerciseWhitelist: StateFlow<Set<String>> = _exerciseWhitelist.asStateFlow()

    fun updateBlacklist(set: Set<String>) {
        exerciseBlacklistJson = json.encodeToString(SetSerializer(serializer<String>()), set)
        _exerciseBlacklist.value = set
    }

    fun updateWhitelist(set: Set<String>) {
        exerciseWhitelistJson = json.encodeToString(SetSerializer(serializer<String>()), set)
        _exerciseWhitelist.value = set
    }

    private fun parseSet(value: String): Set<String> {
        return runCatching { json.decodeFromString<Set<String>>(value) }.getOrDefault(emptySet())
    }
}
