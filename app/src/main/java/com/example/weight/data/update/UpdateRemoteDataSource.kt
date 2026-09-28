package com.example.weight.data.update

import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 检查更新远程数据源。
 *
 * 直连更新服务（标准 HTTPS，无签名/解密），仅负责发请求与解析 JSON。
 * 携带 app 标识 / 版本号 / 平台 / 设备 ID，服务端据此做多应用路由与定向更新。
 */
class UpdateRemoteDataSource(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    /**
     * @param serverUrl 更新服务基地址（如 `https://app-admin.yingluozhiwei.cn`）
     * @param versionCode 当前 App 版本号
     * @param deviceId 设备 ID（定向更新用）
     * @return [CheckUpdateResponse]，失败时返回 null
     */
    suspend fun checkUpdate(serverUrl: String, versionCode: Int, deviceId: String): CheckUpdateResponse? {
        val url = "$serverUrl/api/v1/check-update" +
            "?app=${UpdateConfig.APP_KEY}" +
            "&versionCode=$versionCode" +
            "&platform=android" +
            "&deviceId=$deviceId"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        Log.d(TAG, "→ GET $url")

        return try {
            okHttpClient.newCall(request).execute().use { resp ->
                val raw = resp.body.string()
                Log.d(TAG, "← HTTP ${resp.code}  bodyLen=${raw.length}")
                if (!resp.isSuccessful) {
                    Log.w(TAG, "检查更新失败：HTTP ${resp.code}")
                    null
                } else {
                    json.decodeFromString<CheckUpdateResponse>(raw)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新异常：${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    private companion object {
        const val TAG = "WW-Update"
    }
}
