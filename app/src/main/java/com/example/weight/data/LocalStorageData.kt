package com.example.weight.data

import com.dylanc.mmkv.MMKVOwner

const val DEFAULT_HEIGHT = 150.0
const val DEFAULT_TARGET_WEIGHT = 0.0

object LocalStorageData:MMKVOwner(mmapID = "settings") {
    /*--------基础信息---------*/
    val height by mmkvDouble(default = DEFAULT_HEIGHT).asStateFlow()

    /*--------目标相关---------*/
    val targetWeight by mmkvDouble(default = DEFAULT_TARGET_WEIGHT).asStateFlow()
    val completeDays by mmkvInt(default = 0).asStateFlow()
    /*--------其他---------*/
    var isFirst by mmkvBool(default = true)
}