package com.example.weight.data.exercise

object BlacklistTags {
    val ALL: List<String> = listOf(
        "不伤膝盖",
        "不要跑跳",
        "无器械",
        "讨厌流汗",
        "不能大动作",
    )
}

object WhitelistTags {
    val ALL: List<String> = listOf(
        "喜欢散步",
        "喜欢拉伸",
        "喜欢做家务",
        "轻度燃脂",
        "喜欢站立活动",
    )
}

object SceneTags {
    val ALL: List<String> = listOf(
        "室内/居家",
        "办公室",
        "户外",
    )
}

object BlacklistToCatalogTagMap {
    private val mapping: Map<String, Set<String>> = mapOf(
        "不伤膝盖" to setOf("膝盖负荷", "跳跃", "跑步", "深蹲"),
        "不要跑跳" to setOf("跳跃", "跑步"),
        "无器械" to setOf("器械"),
        "讨厌流汗" to setOf("出汗"),
        "不能大动作" to setOf("大动作"),
    )

    fun getExcludedCatalogTags(blacklist: Set<String>): Set<String> {
        return blacklist.flatMap { mapping[it] ?: emptySet() }.toSet()
    }
}

object WhitelistToCatalogTagMap {
    private val mapping: Map<String, Set<String>> = mapOf(
        "喜欢散步" to setOf("散步"),
        "喜欢拉伸" to setOf("拉伸"),
        "喜欢做家务" to setOf("日常活动"),
        "轻度燃脂" to setOf("低强度"),
        "喜欢站立活动" to setOf("站立"),
    )

    fun getPreferredCatalogTags(whitelist: Set<String>): Set<String> {
        return whitelist.flatMap { mapping[it] ?: emptySet() }.toSet()
    }
}
