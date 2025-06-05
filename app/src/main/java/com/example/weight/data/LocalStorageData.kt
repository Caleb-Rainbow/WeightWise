package com.example.weight.data

import com.dylanc.mmkv.MMKVOwner

object LocalStorageData:MMKVOwner(mmapID = "settings") {
    var height by mmkvDouble(default = 100.0)
}