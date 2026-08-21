package com.example.weight.data

import com.example.weight.data.record.RecordDao
import com.example.weight.util.ActivityLevel
import com.example.weight.util.CalorieCalculator
import com.example.weight.util.Gender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.Single

/**
 * 每日建议摄入冷流：档案五项 + 最新体重 → Mifflin-St Jeor/TDEE 目标缺口。
 * 原先饮食页与报告页各写一份「五项档案 + 最新体重」的 combine + 计算，抽到这里共用；
 * 调用方各自 stateIn(WhileSubscribed) 决定生命周期。
 */
@Single
class RecommendedIntakeProvider(recordDao: RecordDao) {

    /** 档案快照：MMKV 五项一次性合并 */
    private data class ProfileSnapshot(
        val heightCm: Double,
        val age: Int,
        val gender: Gender?,
        val activityLevel: ActivityLevel?,
        val targetWeightKg: Double,
    )

    val flow: Flow<Int?> = combine(
        LocalStorageData.height,
        LocalStorageData.age,
        LocalStorageData.gender,
        LocalStorageData.activityLevel,
        LocalStorageData.targetWeight,
    ) { height, age, gender, activityLevel, targetWeight ->
        ProfileSnapshot(
            heightCm = height,
            age = age,
            gender = Gender.entries.find { it.name == gender },
            activityLevel = ActivityLevel.entries.find { it.name == activityLevel },
            targetWeightKg = targetWeight,
        )
    }.let { profileFlow ->
        combine(profileFlow, recordDao.getLastDataFlow()) { profile, lastRecord ->
            val weightKg = lastRecord?.weight
            if (weightKg == null) null
            else CalorieCalculator.recommendedIntake(
                gender = profile.gender,
                weightKg = weightKg,
                heightCm = profile.heightCm,
                age = profile.age,
                activityLevel = profile.activityLevel,
                targetWeightKg = profile.targetWeightKg,
            )
        }
    }
}
