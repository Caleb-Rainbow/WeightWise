package com.example.weight.data.diet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.time.LocalDate

/**
 *@description: 常用食物响应式数据源。饮食页与主屏 QuickAddSheet 共用（此前两处各自
 *               suspend 查询+手动刷新，编辑/删除/撤销/另一入口写入后 chips 会滞留旧数据）。
 *               Room Flow 随表任意写操作重发；today 流变化时窗口跟随滑动（跨午夜），
 *               上界由 DAO 查询保证，未来日期补记不参与统计。
 *@author: 杨帅林
 *@create: 2026/8/26
 **/
@Single
class FrequentFoodProvider(
    private val dietRecordDao: DietRecordDao,
    private val json: Json,
) {
    /** @param today 「今天」口径（yyyy-MM-dd），消费方跨午夜时更新该流即可滑动窗口 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun frequentFoods(today: Flow<String>): Flow<List<RecognizedFoodItem>> =
        today.distinctUntilChanged().flatMapLatest { day ->
            dietRecordDao.getFoodJsonBetween(
                LocalDate.parse(day).minusDays(FrequentFoodAggregator.WINDOW_DAYS - 1).toString(),
                day,
            )
                // 聚合在 IO 跑：flatMapLatest 内层流按收集者上下文执行，Room 发射线程不约束 map
                .map { FrequentFoodAggregator.topFoods(it, json) }
                .flowOn(Dispatchers.IO)
        }
}
