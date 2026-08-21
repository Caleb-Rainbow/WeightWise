package com.example.weight.ui.diet

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.RecommendedIntakeProvider
import com.example.weight.data.chat.ChatRepository
import com.example.weight.data.diet.AiDietResponse
import com.example.weight.data.diet.DailyMacroAggregator
import com.example.weight.data.diet.DailyMacros
import com.example.weight.data.diet.DietDeleteUndoManager
import com.example.weight.data.diet.DietPromptBuilder
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.diet.DietRecordWriter
import com.example.weight.data.diet.FallbackDietAnalyzer
import com.example.weight.data.diet.FrequentFoodAggregator
import com.example.weight.data.diet.Macros
import com.example.weight.data.diet.MealType
import com.example.weight.data.diet.MealTypeInference
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.data.diet.TrafficLightCalculator
import com.example.weight.util.ImageCompressor
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import org.koin.core.annotation.KoinViewModel

/**
 *@description: 饮食记录 ViewModel。三 Tab 各持独立 StateFlow（避免单流字段变化导致全页重组），
 *               输入态备注留在 Composable 本地（HorizontalPager 保活 + rememberSaveable）
 *@author: 杨帅林
 *@create: 2026/4/11
 **/

/** 「添加」Tab：拍照/相册/备注/AI 分析/快速添加的输入与分析状态 */
data class AddTabState(
    /** 相册选图 */
    val selectedImageUri: Uri? = null,
    /** TakePicture 输出文件（拍照路径），预览与保存共用 */
    val captureFile: File? = null,
    val selectedMealType: MealType = MealTypeInference.inferNow(),
    /** 手动选过餐次后本会话（VM 生命周期，跨午夜重置）不再自动覆盖 */
    val mealTypeManuallySelected: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isSaving: Boolean = false,
    val aiResponse: AiDietResponse? = null,
    val recognizedFoods: List<RecognizedFoodItem> = emptyList(),
    val trafficLight: String = "",
    val aiAdvice: String = "",
    /** AI 失败走离线兜底时为 true：结果卡显示「离线估算」徽章降级样式 */
    val isFallback: Boolean = false,
    /** 常用食物 chips（近 60 天高频聚合），空则整区隐藏 */
    val frequentFoods: List<RecognizedFoodItem> = emptyList(),
) {
    val hasImage: Boolean get() = selectedImageUri != null || captureFile != null
}

data class TodayTabState(
    val records: List<DietRecord> = emptyList(),
    val totalCalories: Int = 0,
    val recommendedCalories: Int? = null,
    /** 当日宏量合计；旧记录无宏量字段时 hasMacroData=false，UI 显示「—」降级 */
    val macros: DailyMacros? = null,
)

data class HistoryTabState(
    val records: List<DietRecord> = emptyList(),
    /** 时间口径（天）：30/90/180，复用主屏 ScopeSelector 心智 */
    val rangeDays: Int = 30,
    /** 日期 → 当日红绿灯（按当日全部食物 isHealthy 本地推导，全 App 单一算法） */
    val dayLights: Map<String, String> = emptyMap(),
    /** 日期 → 当日热量合计 */
    val dayTotals: Map<String, Int> = emptyMap(),
)

/** 跨 Tab 单次事件（SnackBar 文案、切 Tab 动线） */
sealed interface DietEvent {
    data class RecordSaved(val remainingCalories: Int?) : DietEvent
    data object SaveFailed : DietEvent

    /** 拍照回调 ok=true 但文件缺失/0 字节（MIUI 异步写盘、存储满） */
    data object CaptureInvalid : DietEvent
    data object EditorSaved : DietEvent
}

/** 已保存记录的编辑会话；红绿灯实时预览由 UI 按 foods 现算 */
data class EditingSession(
    val record: DietRecord,
    val foods: List<RecognizedFoodItem>,
    val mealType: MealType,
    val date: String,
)

@KoinViewModel
class DietRecordViewModel(
    private val application: Application,
    private val dietRecordDao: DietRecordDao,
    private val chatRepository: ChatRepository,
    private val writer: DietRecordWriter,
    private val deleteUndoManager: DietDeleteUndoManager,
    private val json: Json,
    recommendedIntakeProvider: RecommendedIntakeProvider,
) : ViewModel() {

    private val _addTab = MutableStateFlow(AddTabState())
    val addTab: StateFlow<AddTabState> = _addTab.asStateFlow()

    private val _todayTab = MutableStateFlow(TodayTabState())
    val todayTab: StateFlow<TodayTabState> = _todayTab.asStateFlow()

    private val _historyTab = MutableStateFlow(HistoryTabState())
    val historyTab: StateFlow<HistoryTabState> = _historyTab.asStateFlow()

    private val _editing = MutableStateFlow<EditingSession?>(null)
    val editing: StateFlow<EditingSession?> = _editing.asStateFlow()

    private val _events = Channel<DietEvent>(Channel.BUFFERED)
    val events: Flow<DietEvent> = _events.receiveAsFlow()

    /** 「今天」的口径：跨午夜后由 UI 在 ON_RESUME 时调用 [refreshTodayDate] 刷新 */
    private val _todayDate = MutableStateFlow(TimeUtils.getCurrentDate())

    /** 缓存压缩结果，保存时复用避免二次解码压缩 */
    private var cachedCompressionResult: ImageCompressor.CompressionResult? = null

    /** 活跃分析协程，「取消分析」与重新分析时取消 */
    private var analysisJob: Job? = null

    init {
        observeTodayData()
        observeHistory()
        viewModelScope.launch {
            recommendedIntakeProvider.flow.collect { recommended ->
                _todayTab.update { it.copy(recommendedCalories = recommended) }
            }
        }
        refreshFrequentFoods()
    }

    /** 待撤销删除条数透传给 UI 弹撤销 SnackBar */
    val pendingDeleteRecords: StateFlow<List<DietRecord>> = deleteUndoManager.pendingRecords

    fun undoDeleteAll() = deleteUndoManager.undoAll()
    fun commitPendingDeletes() = deleteUndoManager.commitPending()

    /**
     * 跨午夜后回到页面时刷新「今天」口径；同时：
     * 餐次推断跨午夜重置（评审定稿）、清理拍照临时目录（覆盖进程死亡残留）
     */
    fun refreshTodayDate() {
        val today = TimeUtils.getCurrentDate()
        // 保留当前会话刚拍的照片：从相机返回正好触发 ON_RESUME
        ImageCompressor.clearCameraCaptures(application, keep = _addTab.value.captureFile)
        if (_todayDate.value != today) {
            _todayDate.value = today
            _addTab.update {
                it.copy(
                    mealTypeManuallySelected = false,
                    selectedMealType = MealTypeInference.inferNow(),
                )
            }
        }
        refreshFrequentFoods()
    }

    // ================================ 添加 Tab ================================

    fun onMealTypeSelected(mealType: MealType) {
        _addTab.update { it.copy(selectedMealType = mealType, mealTypeManuallySelected = true) }
    }

    fun onGallerySelected(uri: Uri) {
        cachedCompressionResult = null
        _addTab.update { it.copy(selectedImageUri = uri, captureFile = null) }
    }

    /** TakePicture 回调：ok=true 不代表文件可用（MIUI 异步写盘/存储满），先校验再入状态 */
    fun onCaptureResult(file: File?) {
        if (file == null || !file.exists() || file.length() == 0L) {
            _events.trySend(DietEvent.CaptureInvalid)
            return
        }
        cachedCompressionResult = null
        _addTab.update { it.copy(captureFile = file, selectedImageUri = null) }
    }

    fun clearImage() {
        cachedCompressionResult = null
        _addTab.update { it.copy(selectedImageUri = null, captureFile = null) }
    }

    /**
     * 统一分析入口：有图走图片识别，无图且备注非空走文本识别（合并原两个按钮）。
     * 再次点击「开始分析」或「取消分析」都会取消进行中的协程
     */
    fun startAnalysis(userNote: String) {
        val snapshot = _addTab.value
        if (!snapshot.hasImage && userNote.isBlank()) return
        analysisJob?.cancel()
        val mealType = snapshot.selectedMealType
        analysisJob = viewModelScope.launch(Dispatchers.IO) {
            _addTab.update { it.copy(isAnalyzing = true) }
            try {
                val base64: String? = when {
                    snapshot.captureFile != null ->
                        compressAndCache(Uri.fromFile(snapshot.captureFile)).base64
                    snapshot.selectedImageUri != null ->
                        compressAndCache(snapshot.selectedImageUri).base64
                    else -> null
                }
                if (base64 != null) {
                    val prompt = DietPromptBuilder.buildFoodRecognitionPrompt(
                        imageBase64 = base64,
                        userNote = userNote,
                        mealType = mealType.label,
                    )
                    applyAiResponse(callAi(prompt), isFallback = false)
                } else {
                    performTextOnlyAnalysis(userNote, mealType)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "AI 分析失败，使用离线兜底", e)
                applyAiResponse(FallbackDietAnalyzer.generateFallback(userNote, mealType.name), isFallback = true)
            }
        }
    }

    /** 取消分析：恢复初始输入态（已选图保留） */
    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        _addTab.update { it.copy(isAnalyzing = false) }
    }

    private suspend fun performTextOnlyAnalysis(userNote: String, mealType: MealType) {
        try {
            val prompt = DietPromptBuilder.buildTextOnlyPrompt(
                userDescription = userNote,
                mealType = mealType.label,
            )
            applyAiResponse(callAi(prompt), isFallback = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "AI 文本分析失败，使用离线兜底", e)
            applyAiResponse(FallbackDietAnalyzer.generateFallback(userNote, mealType.name), isFallback = true)
        }
    }

    private suspend fun compressAndCache(uri: Uri): ImageCompressor.CompressionResult {
        // 用 applicationContext：分析协程存活可达数十秒，持有 Activity 会在旋转/退出时泄漏
        val result = ImageCompressor.compressAndEncode(application, uri)
        cachedCompressionResult = result
        return result
    }

    private suspend fun callAi(chatBody: com.example.weight.data.chat.ChatBodyModel): AiDietResponse {
        val responseText = StringBuilder()
        chatRepository.chat(chatBody) { message ->
            message.content.text?.let { responseText.append(it) }
        }
        val text = stripMarkdownFences(responseText.toString())
        if (text.isBlank()) {
            throw IllegalStateException("AI 返回了空响应")
        }
        return json.decodeFromString<AiDietResponse>(text)
    }

    private fun applyAiResponse(response: AiDietResponse, isFallback: Boolean) {
        _addTab.update {
            it.copy(
                isAnalyzing = false,
                aiResponse = response,
                recognizedFoods = response.foods,
                trafficLight = response.trafficLight,
                aiAdvice = response.advice,
                isFallback = isFallback,
            )
        }
    }

    fun updateFoodItem(index: Int, updated: RecognizedFoodItem) {
        _addTab.update { state ->
            if (index !in state.recognizedFoods.indices) return@update state
            val newFoods = state.recognizedFoods.mapIndexed { i, item -> if (i == index) updated else item }
            state.copy(recognizedFoods = newFoods)
        }
    }

    fun removeFoodItem(index: Int) {
        _addTab.update { state ->
            val newFoods = state.recognizedFoods.filterIndexed { i, _ -> i != index }
            state.copy(recognizedFoods = newFoods)
        }
    }

    /** 快速添加 chip 与手动添加共用同一入口：加入下方食物列表后再统一保存 */
    fun addFoodItem(item: RecognizedFoodItem) {
        _addTab.update { it.copy(recognizedFoods = it.recognizedFoods + item) }
    }

    fun saveRecord(userNote: String) {
        val snapshot = _addTab.value
        if (snapshot.recognizedFoods.isEmpty() || snapshot.isSaving) return
        viewModelScope.launch(Dispatchers.IO) {
            _addTab.update { it.copy(isSaving = true) }
            try {
                val imagePath = buildImagePath(snapshot)
                val savedCalories = snapshot.recognizedFoods.sumOf { it.estimatedCalories }
                writer.insert(
                    DietRecordWriter.Draft(
                        foods = snapshot.recognizedFoods,
                        mealType = snapshot.selectedMealType,
                        date = _todayDate.value,
                        userInput = userNote,
                        imageUri = imagePath,
                        // AI 路径保留 AI 评级；快速添加等本地路径本地推导（红绿灯语义单一化）
                        trafficLight = snapshot.aiResponse?.trafficLight
                            ?: TrafficLightCalculator.compute(snapshot.recognizedFoods),
                    )
                )
                cachedCompressionResult = null
                _addTab.update {
                    it.copy(
                        selectedImageUri = null,
                        captureFile = null,
                        aiResponse = null,
                        recognizedFoods = emptyList(),
                        trafficLight = "",
                        aiAdvice = "",
                        isFallback = false,
                    )
                }
                refreshFrequentFoods()
                // SnackBar 余量用本地计算：Room Flow 尚未重发，直接读会拿到旧值
                val remaining = _todayTab.value.recommendedCalories?.let {
                    it - (_todayTab.value.totalCalories + savedCalories)
                }
                _events.send(DietEvent.RecordSaved(remaining))
            } catch (e: Exception) {
                Log.e(TAG, "保存饮食记录失败", e)
                _events.send(DietEvent.SaveFailed)
            } finally {
                _addTab.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun buildImagePath(state: AddTabState): String {
        val sourceUri: Uri = when {
            state.captureFile != null -> Uri.fromFile(state.captureFile)
            state.selectedImageUri != null -> state.selectedImageUri
            else -> return ""
        }
        return try {
            // 缓存未命中时只做「解码+降采样」：保存路径只要 Bitmap，
            // 不必重跑 JPEG 压缩 + Base64 编码再把大字符串直接丢弃
            val bitmap = cachedCompressionResult?.bitmap
                ?: ImageCompressor.decodeScaled(application, sourceUri)
                ?: return ""
            ImageCompressor.saveImage(application, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            ""
        }
    }

    /** 近 60 天高频食物聚合（IO 线程）；轻量投影查询避免全表物化大字段 */
    private fun refreshFrequentFoods() {
        viewModelScope.launch(Dispatchers.IO) {
            val since = LocalDate.now().minusDays(59).toString()
            val foodJsonList = runCatching { dietRecordDao.getFoodJsonSince(since) }
                .onFailure { Log.w(TAG, "常用食物查询失败", it) }
                .getOrDefault(emptyList())
            val foods = FrequentFoodAggregator.topFoods(foodJsonList, json)
            _addTab.update { it.copy(frequentFoods = foods) }
        }
    }

    // ================================ 今日 Tab ================================

    fun deleteRecord(record: DietRecord) = deleteUndoManager.deleteWithUndo(record)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTodayData() {
        viewModelScope.launch {
            _todayDate.flatMapLatest { date ->
                combine(
                    dietRecordDao.getByDate(date),
                    dietRecordDao.getDailyCaloriesFlow(date),
                ) { records, calories -> records to calories }
            }.collect { (records, calories) ->
                _todayTab.update {
                    it.copy(
                        records = records,
                        totalCalories = calories,
                        macros = DailyMacroAggregator.aggregate(records, json),
                    )
                }
            }
        }
    }

    // ================================ 历史 Tab ================================

    fun setHistoryRange(days: Int) {
        _historyTab.update { it.copy(rangeDays = days) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeHistory() {
        viewModelScope.launch {
            _historyTab.flatMapLatest { state ->
                val endDate = LocalDate.now().plusDays(1).toString() // 排他：明天零点
                val startDate = LocalDate.now().minusDays((state.rangeDays - 1).toLong()).toString()
                dietRecordDao.getByDateRange(startDate, endDate)
                    .map { records -> records to computeDayMeta(records) }
                    .flowOn(Dispatchers.Default)
            }.collect { (records, meta) ->
                _historyTab.update {
                    it.copy(records = records, dayLights = meta.first, dayTotals = meta.second)
                }
            }
        }
    }

    /** 历史日头的红绿灯与合计；单条脏 JSON 跳过，不参与当日推导 */
    private fun computeDayMeta(records: List<DietRecord>): Pair<Map<String, String>, Map<String, Int>> {
        val foodsByDate = HashMap<String, MutableList<RecognizedFoodItem>>()
        val totals = HashMap<String, Int>()
        for (record in records) {
            totals.merge(record.date, record.estimatedCalories, Int::plus)
            runCatching {
                json.decodeFromString<List<RecognizedFoodItem>>(record.recognizedFoodJson)
            }.getOrNull()?.let { foodsByDate.getOrPut(record.date) { mutableListOf() }.addAll(it) }
        }
        val lights = foodsByDate.mapValues { (_, foods) -> TrafficLightCalculator.compute(foods) }
        return lights to totals
    }

    // ================================ 编辑会话 ================================

    fun openEditor(record: DietRecord) {
        _editing.value = EditingSession(
            record = record,
            foods = runCatching {
                json.decodeFromString<List<RecognizedFoodItem>>(record.recognizedFoodJson)
            }.onFailure { Log.w(TAG, "编辑打开：记录 JSON 解析失败", it) }
                .getOrDefault(emptyList()),
            mealType = MealType.entries.firstOrNull { it.name == record.mealType } ?: MealType.LUNCH,
            date = record.date,
        )
    }

    fun dismissEditor() {
        _editing.value = null
    }

    fun editorUpdateFood(index: Int, updated: RecognizedFoodItem) {
        _editing.update { session ->
            session?.takeIf { index in it.foods.indices }?.let {
                it.copy(foods = it.foods.mapIndexed { i, item -> if (i == index) updated else item })
            }
        }
    }

    fun editorRemoveFood(index: Int) {
        _editing.update { session ->
            session?.copy(foods = session.foods.filterIndexed { i, _ -> i != index })
        }
    }

    fun editorAddFood(item: RecognizedFoodItem) {
        _editing.update { it?.copy(foods = it.foods + item) }
    }

    fun editorSetMealType(mealType: MealType) {
        _editing.update { it?.copy(mealType = mealType) }
    }

    fun editorSetDate(date: String) {
        _editing.update { it?.copy(date = date) }
    }

    fun saveEditor() {
        val session = _editing.value ?: return
        if (session.foods.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            writer.update(session.record, session.foods, session.mealType, session.date)
            _editing.value = null
            _events.send(DietEvent.EditorSaved)
        }
    }

    /** 编辑器内清空全部食物 → 删除整条记录（走撤销链路） */
    fun deleteEditedRecord() {
        val session = _editing.value ?: return
        _editing.value = null
        deleteUndoManager.deleteWithUndo(session.record)
    }

    override fun onCleared() {
        super.onCleared()
        analysisJob?.cancel()
        cachedCompressionResult = null
    }

    companion object {
        private const val TAG = "DietRecord"
    }
}

private fun stripMarkdownFences(text: String): String = text
    .trim()
    .removePrefix("```json").removePrefix("```")
    .removeSuffix("```")
    .trim()
