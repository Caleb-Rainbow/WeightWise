package com.example.weight.data.update

/**
 * App 检查更新服务配置（AppAdmin 发版后台）。
 */
object UpdateConfig {
    /** 更新服务基地址（AppAdmin，多应用发版管理后台）。 */
    const val SERVER_URL = "https://app-admin.yingluozhiwei.cn"

    /**
     * 接入标识：与服务端 App 管理里的 appKey 一致（用包名）。
     * 固定用 release 包名而非运行时 packageName——debug 构建的 applicationId 带 .debug
     * 后缀，固定值保证两端查同一个发版应用（Debug 构建不做启动自动检查）。
     */
    const val APP_KEY = "com.example.weight"
}
