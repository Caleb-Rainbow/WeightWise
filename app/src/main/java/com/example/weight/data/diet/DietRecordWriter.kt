package com.example.weight.data.diet

import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 *@description: 饮食记录共享写入器。饮食页与主屏 QuickAddSheet 两条入口共用同一条
 *               构建落库链路（热量求和、红绿灯推导、timestamp 去重），避免保存逻辑双份漂移
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
@Single
class DietRecordWriter(
    private val dietRecordDao: DietRecordDao,
    private val json: Json,
) {

    data class Draft(
        val foods: List<RecognizedFoodItem>,
        val mealType: MealType,
        val date: String,
        val userInput: String = "",
        val imageUri: String = "",
        /** AI 路径传 AI 评级；null（快速添加等本地路径）走 TrafficLightCalculator 本地推导 */
        val trafficLight: String? = null,
    )

    suspend fun insert(draft: Draft): Long {
        val record = DietRecord(
            date = draft.date,
            timestamp = newTimestamp(),
            mealType = draft.mealType.name,
            imageUri = draft.imageUri,
            userInput = draft.userInput,
            recognizedFoodJson = encodeFoods(draft.foods),
            estimatedCalories = draft.foods.sumOf { it.estimatedCalories },
            trafficLight = draft.trafficLight ?: TrafficLightCalculator.compute(draft.foods),
        )
        return dietRecordDao.insert(record)
    }

    /** 编辑保存：餐次/日期/食物列表可改，热量与红绿灯一律本地重算（不调 AI），id/timestamp/图片/备注保留 */
    suspend fun update(
        original: DietRecord,
        foods: List<RecognizedFoodItem>,
        mealType: MealType,
        date: String,
    ): DietRecord {
        val updated = original.copy(
            date = date,
            mealType = mealType.name,
            recognizedFoodJson = encodeFoods(foods),
            estimatedCalories = foods.sumOf { it.estimatedCalories },
            trafficLight = TrafficLightCalculator.compute(foods),
        )
        dietRecordDao.update(updated)
        return updated
    }

    private fun encodeFoods(foods: List<RecognizedFoodItem>): String = json.encodeToString(
        serializer = kotlinx.serialization.serializer<List<RecognizedFoodItem>>(),
        value = foods,
    )

    /**
     * 备份去重键是 (date, timestamp, mealType)，而 timestamp 取自 System.currentTimeMillis()——
     * 部分设备毫秒精度实际为 10-16ms，快速添加「连续两次秒存」会同毫秒同餐次，重导入时被静默去重。
     * 单例内保证单调递增即可覆盖同进程碰撞
     */
    private var lastTimestamp = 0L
    private fun newTimestamp(): Long {
        val now = System.currentTimeMillis()
        val ts = if (now <= lastTimestamp) lastTimestamp + 1 else now
        lastTimestamp = ts
        return ts
    }
}
