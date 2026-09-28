package com.example.weight.ui.diet

import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.util.TimeUtils
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal data class DietHistoryData(val dates: List<String>, val records: List<DietRecord>)

/** 查询边界与空档日共用同一个北京日快照；回前台跨日或切范围时重新订阅。 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun dietHistoryData(
    dao: DietRecordDao,
    todayDates: Flow<String>,
    rangeDays: Flow<Int>,
): Flow<DietHistoryData> = combine(todayDates, rangeDays.distinctUntilChanged()) { date, days ->
    LocalDate.parse(date) to days
}.distinctUntilChanged().flatMapLatest { (today, days) ->
    val dates = TimeUtils.lastNDates(today, days).reversed()
    val endExclusive = today.plusDays(1).toString()
    dao.getByDateRange(dates.lastOrNull() ?: endExclusive, endExclusive).map { records ->
        DietHistoryData(dates, records)
    }
}
