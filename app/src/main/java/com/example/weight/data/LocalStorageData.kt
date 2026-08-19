package com.example.weight.data

import com.dylanc.mmkv.MMKVOwner
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_HEIGHT = 170.0
const val DEFAULT_TARGET_WEIGHT = 0.0

object LocalStorageData : MMKVOwner(mmapID = "settings") {

    /*--------基础信息---------*/
    val height by mmkvDouble(default = DEFAULT_HEIGHT).asStateFlow()

    /*--------目标相关---------*/
    val targetWeight by mmkvDouble(default = DEFAULT_TARGET_WEIGHT).asStateFlow()
    /*--------其他---------*/
    var isFirst by mmkvBool(default = true)

    /*--------AI 提供商相关---------*/
    val doubaoModelId by mmkvString(default = "doubao-seed-2-0-lite-260215").asStateFlow()
}
