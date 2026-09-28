package com.example.weight.data.update

import kotlinx.serialization.Serializable

/**
 * 检查更新接口响应（GET /api/v1/check-update?app=&versionCode=&platform=&deviceId=）。
 * 与 AppAdmin 后端裸 JSON 契约对齐（snake_case 字段直映射，无 code/msg 包装）。
 */
@Serializable
data class CheckUpdateResponse(
    val has_update: Boolean = false,
    val release: ReleaseInfo? = null,
)

/**
 * Release 信息，对应后端 Release 模型。
 */
@Serializable
data class ReleaseInfo(
    val id: Long = 0,
    val version_name: String = "",
    val version_code: Int = 0,
    val download_url: String = "",
    val changelog: String = "",
    val force_update: Boolean = false,
    val created_at: String = "",
    // 扩展字段：下载后完整性校验与展示
    val file_md5: String = "",
    val file_size: Long = 0,
)
