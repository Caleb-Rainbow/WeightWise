package com.example.weight.data.health

import android.app.Application
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MealType as HealthMealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record as HealthRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.core.net.toUri
import com.example.weight.BuildConfig
import com.example.weight.data.LocalStorageData
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.diet.FoodQuality
import com.example.weight.data.diet.MealType
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.data.diet.TrafficLightCalculator
import com.example.weight.data.record.BodyComposition
import com.example.weight.data.record.BodyCompositionJson
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import com.example.weight.data.widget.WidgetUpdater
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE,
    DISABLED_FOR_BUILD,
}

data class HealthActivitySummary(
    val rangeDays: Int = 7,
    val steps: Long = 0,
    /** Health Connect 的 TotalCaloriesBurned 包含基础代谢与活动消耗 */
    val totalCaloriesBurned: Int = 0,
    val sleepMinutes: Long = 0,
)

data class HealthConnectSyncResult(
    val importedWeights: Int,
    val importedNutrition: Int,
    val exportedRecords: Int,
    val exportedNutrition: Int,
)

data class HealthConnectState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val enabled: Boolean = false,
    val grantedPermissions: Set<String> = emptySet(),
    val isSyncing: Boolean = false,
    val lastSyncAt: Long = 0,
    val summary: HealthActivitySummary? = null,
    val lastResult: HealthConnectSyncResult? = null,
    val error: String? = null,
) {
    val hasAllPermissions: Boolean get() = grantedPermissions.containsAll(HealthConnectManager.PERMISSIONS)
}

/**
 * Health Connect 前台同步中枢。
 *
 * - 只在用户开启且权限齐全时同步，不申请后台读取权限。
 * - Weight/Nutrition 使用稳定 clientRecordId upsert；外部来源落库后带 origin，禁止回写形成回环。
 * - 初次读取最近 30 天，后续从上次成功时间前移 24 小时重叠读取，以 id 去重并覆盖更新。
 * - 身体成分按两分钟窗口合并到同次体重，兼容不同设备各指标分开发送的行为。
 */
@Single
class HealthConnectManager(
    private val application: Application,
    private val recordDao: RecordDao,
    private val dietRecordDao: DietRecordDao,
    private val widgetUpdater: WidgetUpdater,
    private val json: Json,
) {
    private val syncing = AtomicBoolean(false)
    private val _state = MutableStateFlow(
        HealthConnectState(
            availability = resolveAvailability(),
            enabled = LocalStorageData.healthConnectEnabled.value,
            lastSyncAt = LocalStorageData.healthConnectLastSyncAt.value,
        )
    )
    val state: StateFlow<HealthConnectState> = _state.asStateFlow()

    val clientOrNull: HealthConnectClient?
        get() = if (resolveAvailability() == HealthConnectAvailability.AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(application) }.getOrNull()
        } else null

    suspend fun refreshStatus() {
        purgeTestOriginImports()
        val availability = resolveAvailability()
        val granted = if (availability == HealthConnectAvailability.AVAILABLE) {
            runCatching { clientOrNull?.permissionController?.getGrantedPermissions().orEmpty() }
                .getOrDefault(emptySet())
        } else emptySet()
        _state.update {
            it.copy(
                availability = availability,
                enabled = LocalStorageData.healthConnectEnabled.value,
                grantedPermissions = granted,
                lastSyncAt = LocalStorageData.healthConnectLastSyncAt.value,
            )
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        LocalStorageData.healthConnectEnabled.update { enabled }
        _state.update { it.copy(enabled = enabled, error = null) }
        if (enabled) syncNow()
    }

    suspend fun onPermissionsResult(granted: Set<String>) {
        _state.update { it.copy(grantedPermissions = granted, error = null) }
        if (granted.containsAll(PERMISSIONS)) {
            LocalStorageData.healthConnectEnabled.update { true }
            _state.update { it.copy(enabled = true) }
            syncNow()
        }
    }

    suspend fun syncIfEnabled() {
        refreshStatus()
        if (_state.value.enabled && _state.value.hasAllPermissions) syncNow()
    }

    /**
     * 读取指定报告周期内的活动与恢复摘要。
     *
     * 报告页只做按需读取，不会因此开启同步；用户关闭 Health Connect、权限不足或服务不可用时返回 null。
     */
    suspend fun readActivitySummary(
        start: Instant,
        end: Instant,
        rangeDays: Int,
    ): HealthActivitySummary? =
        withContext(Dispatchers.IO) {
            if (!LocalStorageData.healthConnectEnabled.value || !end.isAfter(start)) return@withContext null
            val client = clientOrNull ?: return@withContext null
            val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrNull()
                ?: return@withContext null
            if (!granted.containsAll(ACTIVITY_READ_PERMISSIONS)) return@withContext null
            runCatching { aggregateActivitySummary(client, start, end, rangeDays) }.getOrNull()
        }

    suspend fun syncNow(): HealthConnectSyncResult? = withContext(Dispatchers.IO) {
        if (!syncing.compareAndSet(false, true)) return@withContext null
        val client = clientOrNull
        if (client == null) {
            syncing.set(false)
            refreshStatus()
            return@withContext null
        }
        refreshStatus()
        if (!_state.value.hasAllPermissions) {
            syncing.set(false)
            _state.update { it.copy(error = "Health Connect 权限不完整") }
            return@withContext null
        }
        _state.update { it.copy(isSyncing = true, error = null) }
        try {
            val readStart = importWindowStart(_state.value.lastSyncAt, Instant.now())
            val result = coroutineScope {
                // 本地推送与外部拉取互不依赖；来源过滤和稳定 clientRecordId 保证不会形成回环。
                val push = async { pushLocalData(client) }
                val pull = async { pullExternalData(client, readStart, Instant.now()) }
                val summary = async {
                    val end = Instant.now()
                    aggregateActivitySummary(client, end.minus(Duration.ofDays(7)), end, rangeDays = 7)
                }
                val pushed = push.await()
                val pulled = pull.await()
                _state.update { it.copy(summary = summary.await()) }
                HealthConnectSyncResult(
                    importedWeights = pulled.first,
                    importedNutrition = pulled.second,
                    exportedRecords = pushed.first,
                    exportedNutrition = pushed.second,
                )
            }
            val now = System.currentTimeMillis()
            LocalStorageData.healthConnectLastSyncAt.update { now }
            if (result.importedWeights > 0) widgetUpdater.notifyDataChanged()
            _state.update { it.copy(lastSyncAt = now, lastResult = result, error = null) }
            result
        } catch (e: SecurityException) {
            refreshStatus()
            _state.update { it.copy(error = "Health Connect 权限已变化，请重新授权") }
            null
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Health Connect 同步失败") }
            null
        } finally {
            syncing.set(false)
            _state.update { it.copy(isSyncing = false) }
        }
    }

    fun manageAccessIntent(): Intent = HealthConnectClient.getHealthConnectManageDataIntent(application)

    fun installOrUpdateIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        data = "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding".toUri()
        setPackage("com.android.vending")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun resolveAvailability(): HealthConnectAvailability {
        if (!BuildConfig.HEALTH_CONNECT_RUNTIME_ENABLED) {
            return HealthConnectAvailability.DISABLED_FOR_BUILD
        }
        return when (HealthConnectClient.getSdkStatus(application)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }
    }

    /**
     * 旧版本可能已把 Debug 写入 Health Connect 的合成数据导入正式库。
     * 每次前台刷新做幂等清理，且只删除由当前正式包对应的 `.debug` 包写入的数据。
     */
    private suspend fun purgeTestOriginImports() {
        val testOrigin = HealthConnectOriginPolicy.testOriginFor(application.packageName)
        val deletedWeights = recordDao.deleteByHealthConnectOrigin(testOrigin)
        dietRecordDao.deleteByHealthConnectOrigin(testOrigin)
        if (deletedWeights > 0) widgetUpdater.notifyDataChanged()
    }

    private suspend fun pushLocalData(client: HealthConnectClient): Pair<Int, Int> {
        val records = recordDao.getLocalRecordsForHealthConnect()
        val dietRecords = dietRecordDao.getLocalRecordsForHealthConnect()
        val version = System.currentTimeMillis()

        records.chunked(WRITE_BATCH_SIZE).forEach { batch ->
            val payload = batch.flatMap { it.toHealthRecords(version) }
            if (payload.isNotEmpty()) client.insertRecords(payload)
        }
        dietRecords.chunked(WRITE_BATCH_SIZE).forEach { batch ->
            val payload = batch.mapNotNull { it.toHealthNutrition(version) }
            if (payload.isNotEmpty()) client.insertRecords(payload)
        }
        return records.size to dietRecords.size
    }

    private suspend fun pullExternalData(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): Pair<Int, Int> {
        val weights = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                pageSize = READ_PAGE_SIZE,
            )
        ).records.filterNotFromSelf()

        var importedWeights = 0
        for (weight in weights) {
            val id = weight.metadata.id
            val timestamp = weight.time.toEpochMilli()
            val existing = recordDao.getByHealthConnectId(id)
                ?: recordDao.findNearest(timestamp, BODY_MERGE_TOLERANCE_MS)
                    ?.takeIf { kotlin.math.abs(it.weight - weight.weight.inKilograms) < 0.01 }
            val origin = weight.metadata.dataOrigin.packageName
            if (existing != null) {
                recordDao.update(
                    existing.copy(
                        weight = weight.weight.inKilograms,
                        timestamp = timestamp,
                        healthConnectId = id,
                        healthConnectOrigin = origin,
                    )
                )
            } else {
                recordDao.insert(
                    Record(
                        weight = weight.weight.inKilograms,
                        log = "来自 Health Connect",
                        timestamp = timestamp,
                        healthConnectId = id,
                        healthConnectOrigin = origin,
                    )
                )
                importedWeights++
            }
        }

        mergeBodyComposition(client, start, end)

        val nutrition = client.readRecords(
            ReadRecordsRequest(
                recordType = NutritionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                pageSize = READ_PAGE_SIZE,
            )
        ).records.filterNotFromSelf()
        var importedNutrition = 0
        for (item in nutrition) {
            val mapped = item.toDietRecord() ?: continue
            val existing = dietRecordDao.getByHealthConnectId(item.metadata.id)
                ?: dietRecordDao.findNearest(mapped.timestamp, NUTRITION_DEDUP_TOLERANCE_MS)
                    ?.takeIf { it.estimatedCalories == mapped.estimatedCalories && it.mealType == mapped.mealType }
            if (existing != null) {
                dietRecordDao.update(
                    mapped.copy(
                        id = existing.id,
                        imageUri = existing.imageUri,
                        userInput = existing.userInput.ifBlank { mapped.userInput },
                    )
                )
            } else {
                dietRecordDao.insert(mapped)
                importedNutrition++
            }
        }
        return importedWeights to importedNutrition
    }

    private suspend fun mergeBodyComposition(client: HealthConnectClient, start: Instant, end: Instant) {
        suspend fun updateAt(timestamp: Long, transform: (BodyComposition, Double) -> BodyComposition) {
            val record = recordDao.findNearest(timestamp, BODY_MERGE_TOLERANCE_MS) ?: return
            val current = BodyCompositionJson.decode(record.bodyComposition) ?: BodyComposition()
            val updated = transform(current, record.weight)
            recordDao.update(
                record.copy(
                    bodyComposition = BodyCompositionJson.encode(updated),
                    fatRatio = updated.fatRatio,
                    muscleRatio = updated.muscleRatio,
                    waterRatio = updated.waterRatio,
                )
            )
        }

        client.readRecords(
            ReadRecordsRequest(BodyFatRecord::class, TimeRangeFilter.between(start, end), pageSize = READ_PAGE_SIZE)
        ).records.filterNotFromSelf().forEach { item ->
            updateAt(item.time.toEpochMilli()) { c, _ -> c.copy(fatRatio = item.percentage.value) }
        }
        client.readRecords(
            ReadRecordsRequest(LeanBodyMassRecord::class, TimeRangeFilter.between(start, end), pageSize = READ_PAGE_SIZE)
        ).records.filterNotFromSelf().forEach { item ->
            // 去脂体重不等于肌肉量；只落 FFM，避免把骨骼、器官等去脂组织误标为肌肉率。
            updateAt(item.time.toEpochMilli()) { c, _ -> c.copy(ffm = item.mass.inKilograms) }
        }
        client.readRecords(
            ReadRecordsRequest(BodyWaterMassRecord::class, TimeRangeFilter.between(start, end), pageSize = READ_PAGE_SIZE)
        ).records.filterNotFromSelf().forEach { item ->
            updateAt(item.time.toEpochMilli()) { c, weight ->
                c.copy(waterRatio = item.mass.inKilograms / weight * 100)
            }
        }
        client.readRecords(
            ReadRecordsRequest(BoneMassRecord::class, TimeRangeFilter.between(start, end), pageSize = READ_PAGE_SIZE)
        ).records.filterNotFromSelf().forEach { item ->
            updateAt(item.time.toEpochMilli()) { c, _ -> c.copy(boneMass = item.mass.inKilograms) }
        }
    }

    private suspend fun aggregateActivitySummary(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        rangeDays: Int,
    ): HealthActivitySummary {
        val result: AggregationResult = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    SleepSessionRecord.SLEEP_DURATION_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        )
        return HealthActivitySummary(
            rangeDays = rangeDays.coerceAtLeast(1),
            steps = result[StepsRecord.COUNT_TOTAL] ?: 0,
            totalCaloriesBurned = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]
                ?.inKilocalories?.roundToInt() ?: 0,
            sleepMinutes = result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes() ?: 0,
        )
    }

    private fun Record.toHealthRecords(version: Long): List<HealthRecord> {
        val instant = Instant.ofEpochMilli(timestamp)
        val offset = zoneOffsetAt(instant)
        val composition = BodyCompositionJson.decode(bodyComposition)
        val baseId = "weightwise-record-$id"
        return buildList {
            add(
                WeightRecord(
                    time = instant,
                    zoneOffset = offset,
                    weight = Mass.kilograms(weight),
                    metadata = Metadata.manualEntry(baseId, version),
                )
            )
            composition?.fatRatio?.takeIf { it > 0 }?.let {
                add(
                    BodyFatRecord(
                        time = instant,
                        zoneOffset = offset,
                        percentage = Percentage(it),
                        metadata = Metadata.manualEntry("$baseId-body-fat", version),
                    )
                )
            }
            composition?.ffm?.takeIf { it > 0 }?.let {
                add(
                    LeanBodyMassRecord(
                        time = instant,
                        zoneOffset = offset,
                        mass = Mass.kilograms(it),
                        metadata = Metadata.manualEntry("$baseId-lean-mass", version),
                    )
                )
            }
            composition?.waterRatio?.takeIf { it > 0 }?.let {
                add(
                    BodyWaterMassRecord(
                        time = instant,
                        zoneOffset = offset,
                        mass = Mass.kilograms(weight * it / 100.0),
                        metadata = Metadata.manualEntry("$baseId-water-mass", version),
                    )
                )
            }
            composition?.boneMass?.takeIf { it > 0 }?.let {
                add(
                    BoneMassRecord(
                        time = instant,
                        zoneOffset = offset,
                        mass = Mass.kilograms(it),
                        metadata = Metadata.manualEntry("$baseId-bone-mass", version),
                    )
                )
            }
        }
    }

    private fun DietRecord.toHealthNutrition(version: Long): NutritionRecord? {
        val foods = runCatching { json.decodeFromString<List<RecognizedFoodItem>>(recognizedFoodJson) }
            .getOrNull().orEmpty()
        if (estimatedCalories <= 0 && foods.isEmpty()) return null
        val instant = Instant.ofEpochMilli(timestamp)
        val end = instant.plusSeconds(60)
        val offset = zoneOffsetAt(instant)
        val protein = foods.sumIfComplete { it.protein }
        val carbs = foods.sumIfComplete { it.carbs }
        val fat = foods.sumIfComplete { it.fat }
        return NutritionRecord(
            startTime = instant,
            startZoneOffset = offset,
            endTime = end,
            endZoneOffset = zoneOffsetAt(end),
            metadata = Metadata.manualEntry("weightwise-diet-$id", version),
            energy = estimatedCalories.takeIf { it > 0 }?.let { Energy.kilocalories(it.toDouble()) },
            protein = protein?.let { Mass.grams(it.toDouble()) },
            totalCarbohydrate = carbs?.let { Mass.grams(it.toDouble()) },
            totalFat = fat?.let { Mass.grams(it.toDouble()) },
            name = foods.joinToString("、") { it.name }.take(200).ifBlank { null },
            mealType = mealType.toHealthMealType(),
        )
    }

    private fun NutritionRecord.toDietRecord(): DietRecord? {
        val proteinG = protein?.inGrams?.roundToInt()
        val carbsG = totalCarbohydrate?.inGrams?.roundToInt()
        val fatG = totalFat?.inGrams?.roundToInt()
        if (energy == null && proteinG == null && carbsG == null && fatG == null) return null
        val calories = energy?.inKilocalories?.roundToInt()
            ?: ((proteinG ?: 0) * 4 + (carbsG ?: 0) * 4 + (fatG ?: 0) * 9)
        val food = RecognizedFoodItem(
            name = name?.takeIf { it.isNotBlank() } ?: "Health Connect 饮食",
            estimatedCalories = calories,
            category = "Health Connect",
            quality = FoodQuality.SOMETIMES,
            protein = proteinG,
            carbs = carbsG,
            fat = fatG,
        )
        val foods = listOf(food)
        val timestamp = startTime.toEpochMilli()
        return DietRecord(
            date = startTime.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            timestamp = timestamp,
            mealType = mealType.toLocalMealType().name,
            userInput = "来自 Health Connect",
            recognizedFoodJson = json.encodeToString(foods),
            estimatedCalories = calories,
            trafficLight = TrafficLightCalculator.compute(foods),
            healthConnectId = metadata.id,
            healthConnectOrigin = metadata.dataOrigin.packageName,
        )
    }

    /** 空食物列表或任一食物缺失该宏量字段时返回 null，避免把“未知”同步成 0 克。 */
    private inline fun List<RecognizedFoodItem>.sumIfComplete(
        selector: (RecognizedFoodItem) -> Int?,
    ): Int? {
        if (isEmpty()) return null
        val values = mapNotNull(selector)
        return values.takeIf { it.size == size }?.sum()
    }

    private fun <T : HealthRecord> List<T>.filterNotFromSelf(): List<T> =
        filter {
            HealthConnectOriginPolicy.shouldImport(
                originPackage = it.metadata.dataOrigin.packageName,
                ownPackage = application.packageName,
            )
        }

    private fun String.toHealthMealType(): Int = when (this) {
        MealType.BREAKFAST.name -> HealthMealType.MEAL_TYPE_BREAKFAST
        MealType.LUNCH.name -> HealthMealType.MEAL_TYPE_LUNCH
        MealType.DINNER.name -> HealthMealType.MEAL_TYPE_DINNER
        MealType.SNACK.name -> HealthMealType.MEAL_TYPE_SNACK
        else -> HealthMealType.MEAL_TYPE_UNKNOWN
    }

    private fun Int.toLocalMealType(): MealType = when (this) {
        HealthMealType.MEAL_TYPE_BREAKFAST -> MealType.BREAKFAST
        HealthMealType.MEAL_TYPE_LUNCH -> MealType.LUNCH
        HealthMealType.MEAL_TYPE_DINNER -> MealType.DINNER
        HealthMealType.MEAL_TYPE_SNACK -> MealType.SNACK
        else -> MealType.LUNCH
    }

    private fun zoneOffsetAt(instant: Instant): ZoneOffset =
        ZoneId.systemDefault().rules.getOffset(instant)

    companion object {
        private const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        private const val WRITE_BATCH_SIZE = 250
        private const val READ_PAGE_SIZE = 5000
        private const val BODY_MERGE_TOLERANCE_MS = 2 * 60 * 1000L
        private const val NUTRITION_DEDUP_TOLERANCE_MS = 60 * 1000L

        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getWritePermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(LeanBodyMassRecord::class),
            HealthPermission.getWritePermission(LeanBodyMassRecord::class),
            HealthPermission.getReadPermission(BodyWaterMassRecord::class),
            HealthPermission.getWritePermission(BodyWaterMassRecord::class),
            HealthPermission.getReadPermission(BoneMassRecord::class),
            HealthPermission.getWritePermission(BoneMassRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )

        private val ACTIVITY_READ_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )

        internal fun importWindowStart(lastSyncAt: Long, now: Instant): Instant =
            if (lastSyncAt > 0) {
                Instant.ofEpochMilli(lastSyncAt).minus(Duration.ofDays(1))
            } else {
                now.minus(Duration.ofDays(30))
            }
    }
}

/** Release 拒绝导入同一应用 Debug 变体的数据；Debug 自身数据仍由“排除自己”规则覆盖。 */
internal object HealthConnectOriginPolicy {
    fun testOriginFor(packageName: String): String =
        "${packageName.removeSuffix(".debug")}.debug"

    fun shouldImport(originPackage: String, ownPackage: String): Boolean =
        originPackage != ownPackage && originPackage != testOriginFor(ownPackage)
}
