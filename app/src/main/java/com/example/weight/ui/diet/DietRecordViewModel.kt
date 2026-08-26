package com.example.weight.ui.diet

import android.app.Application
import android.graphics.Bitmap
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
import com.example.weight.data.diet.FrequentFoodProvider
import com.example.weight.data.diet.Macros
import com.example.weight.data.diet.MealType
import com.example.weight.data.diet.MealTypeInference
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.data.diet.TrafficLightCalculator
import com.example.weight.data.diet.mergeFoods
import com.example.weight.data.diet.resolveTrafficLight
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
    /** 用餐日期（yyyy-MM-dd），默认今天；改为过去日期即补记，保存落在所选日 */
    val date: String = TimeUtils.getCurrentDate(),
    val isAnalyzing: Boolean = false,
    val isSaving: Boolean = false,
    val aiResponse: AiDietResponse? = null,
    val recognizedFoods: List<RecognizedFoodItem> = emptyList(),
    /** AI 评级时的食物快照(OV1B):保存时与 recognizedFoods 比对,一致才采信 AI 评级 */
    val ratedFoods: List<RecognizedFoodItem> = emptyList(),
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
    /** 时间口径（天）：30/90/180，复用主屏 ScopeSelector 心智 */
    val rangeDays: Int = 30,
    /**
     * 完整日期序列（OV4B，新→旧）：含无记录的空档日。
     * 空档日 records 为空；脏 JSON 日 records 非空但 trafficLight 为 null（灰点+保留合计）
     */
    val days: List<HistoryDay> = emptyList(),
    /** 建议摄入（趋势摘要达标口径用）；null=档案不全不可比 */
    val recommendedIntake: Int? = null,
)

/** 历史页单日：records 空=空档日；trafficLight null=有记录但评级不可知（食物 JSON 全部解析失败） */
data class HistoryDay(
    val date: String,
    val records: List<DietRecord> = emptyList(),
    val totalCalories: Int? = null,
    val trafficLight: String? = null,
)

/** 跨 Tab 单次事件（SnackBar 文案、切 Tab 动线） */
sealed interface DietEvent {
    /** date=保存落在的日期；非今天时 remainingCalories 恒为 null（今日口径算不出该日余量） */
    data class RecordSaved(val date: String, val remainingCalories: Int?) : DietEvent
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
    private val frequentFoodProvider: FrequentFoodProvider,
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

    /** 分析时压缩出的位图缓存，保存路径复用避免二次解码降采样 */
    private var cachedAnalysisBitmap: Bitmap? = null

    /**
     * 食物 JSON 解析缓存（含解析失败的负缓存）。Room 表级失效让任一记录写入都会
     * 重发今日列表与整个历史区间的全量记录，未变化记录的 JSON 逐条重新解码是纯浪费；
     * 以 JSON 文本为键（记录未变则文本相同），容量上限防长期驻留膨胀。
     * 今日聚合在主线程、历史聚合在 Default 线程并发访问，须同步。
     */
    private val foodDecodeCache =
        object : LinkedHashMap<String, List<RecognizedFoodItem>?>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RecognizedFoodItem>?>): Boolean =
                size > 512
        }

    private fun decodeFoodsCached(text: String): List<RecognizedFoodItem>? = synchronized(foodDecodeCache) {
        if (foodDecodeCache.containsKey(text)) {
            foodDecodeCache[text]
        } else {
            runCatching { json.decodeFromString<List<RecognizedFoodItem>>(text) }.getOrNull()
                .also { foodDecodeCache[text] = it }
        }
    }

    /** 活跃分析协程，「取消分析」与重新分析时取消 */
    private var analysisJob: Job? = null

    init {
        observeTodayData()
        observeHistory()
        viewModelScope.launch {
            recommendedIntakeProvider.flow.collect { recommended ->
                _todayTab.update { it.copy(recommendedCalories = recommended) }
                _historyTab.update { it.copy(recommendedIntake = recommended) }
            }
        }
        observeFrequentFoods()
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
        // 目录 stat+删除是磁盘操作，丢到 IO 执行：ON_RESUME 挂在主线程，
        // 回前台不该同步做文件操作；keep 先快照，避免与新拍照赋值竞态
        val keepFile = _addTab.value.captureFile
        viewModelScope.launch(Dispatchers.IO) {
            // 保留当前会话刚拍的照片：从相机返回正好触发 ON_RESUME
            ImageCompressor.clearCameraCaptures(application, keep = keepFile)
        }
        if (_todayDate.value != today) {
            val oldToday = _todayDate.value
            _todayDate.value = today
            _addTab.update {
                it.copy(
                    mealTypeManuallySelected = false,
                    selectedMealType = MealTypeInference.inferNow(),
                    date = advanceAddTabDate(it.date, oldToday, today),
                )
            }
        }
        // 常用食物窗口随 today 滑动由 observeFrequentFoods 响应，无需手动刷新
    }

    // ================================ 添加 Tab ================================

    fun onMealTypeSelected(mealType: MealType) {
        _addTab.update { it.copy(selectedMealType = mealType, mealTypeManuallySelected = true) }
    }

    fun onDateSelected(date: String) {
        _addTab.update { it.copy(date = date) }
    }

    fun onGallerySelected(uri: Uri) {
        cachedAnalysisBitmap = null
        _addTab.update { it.copy(selectedImageUri = uri, captureFile = null) }
    }

    /** TakePicture 回调：ok=true 不代表文件可用（MIUI 异步写盘/存储满），先校验再入状态 */
    fun onCaptureResult(file: File?) {
        if (file == null || !file.exists() || file.length() == 0L) {
            _events.trySend(DietEvent.CaptureInvalid)
            return
        }
        cachedAnalysisBitmap = null
        _addTab.update { it.copy(captureFile = file, selectedImageUri = null) }
    }

    fun clearImage() {
        cachedAnalysisBitmap = null
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
                        compressAndEncodeForAnalysis(Uri.fromFile(snapshot.captureFile))
                    snapshot.selectedImageUri != null ->
                        compressAndEncodeForAnalysis(snapshot.selectedImageUri)
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

    /**
     * 压缩编码供 AI 分析用。缓存只留位图给保存路径复用：Base64 大字符串
     * （UTF-16 约为字节数两倍）仅在构造分析请求的瞬间需要，取走即弃、不进缓存，
     * 否则从分析完成到用户保存的整个交互期，它与 Bitmap（长边 1024 约 3MB）双份驻留
     */
    private suspend fun compressAndEncodeForAnalysis(uri: Uri): String {
        // 用 applicationContext：分析协程存活可达数十秒，持有 Activity 会在旋转/退出时泄漏
        val result = ImageCompressor.compressAndEncode(application, uri)
        cachedAnalysisBitmap = result.bitmap
        return result.base64
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
            // E1A:合并手动项与 AI 结果(接入点在写回时,保证 index 与展示列表一致);
            // ratedFoods 快照与合并结果同步,供保存时判定评级来源(OV1B)
            val merged = mergeFoods(it.recognizedFoods, response.foods)
            it.copy(
                isAnalyzing = false,
                aiResponse = response,
                recognizedFoods = merged,
                ratedFoods = merged,
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
        _addTab.update {
            it.copy(recognizedFoods = it.recognizedFoods + item.copy(isManuallyAdded = true))
        }
    }

    /** 快速路径清空已选(E1A):仅清食物列表,不动图片与备注 */
    fun clearFoods() {
        _addTab.update { it.copy(recognizedFoods = emptyList(), ratedFoods = emptyList()) }
    }

    /** 「放弃本次识别」(OV10):清掉 AI 结果,保留手动添加的食物与图片,可重新拍摄/识别 */
    fun discardAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        _addTab.update {
            it.copy(
                isAnalyzing = false,
                aiResponse = null,
                trafficLight = "",
                aiAdvice = "",
                isFallback = false,
                ratedFoods = emptyList(),
                recognizedFoods = it.recognizedFoods.filter { food -> food.isManuallyAdded },
            )
        }
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
                        date = snapshot.date,
                        userInput = userNote,
                        imageUri = imagePath,
                        // OV1B:食物清单与 AI 评级快照一致才采信 AI 评级,否则本地重算,
                        // 杜绝「AI 评级盖在改动后的食物上」污染报告页统计
                        trafficLight = resolveTrafficLight(
                            snapshot.recognizedFoods,
                            snapshot.ratedFoods,
                            snapshot.aiResponse?.trafficLight,
                        ),
                    )
                )
                cachedAnalysisBitmap = null
                _addTab.update {
                    it.copy(
                        selectedImageUri = null,
                        captureFile = null,
                        aiResponse = null,
                        recognizedFoods = emptyList(),
                        ratedFoods = emptyList(),
                        trafficLight = "",
                        aiAdvice = "",
                        isFallback = false,
                        // 补记日期不跨次保留：下次进添加页回到默认的今天
                        date = _todayDate.value,
                    )
                }
                // SnackBar 余量用本地计算：Room Flow 尚未重发，直接读会拿到旧值；
                // _todayTab 是今天口径，补记别的日期时算不出该日余量，传 null 让文案走补记分支
                val remaining = if (snapshot.date == _todayDate.value) {
                    _todayTab.value.recommendedCalories?.let {
                        it - (_todayTab.value.totalCalories + savedCalories)
                    }
                } else {
                    null
                }
                _events.send(DietEvent.RecordSaved(snapshot.date, remaining))
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
            val bitmap = cachedAnalysisBitmap
                ?: ImageCompressor.decodeScaled(application, sourceUri)
                ?: return ""
            ImageCompressor.saveImage(application, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            ""
        }
    }

    /** 近 60 天高频食物：表任意写操作（编辑/删除/撤销/主屏 QuickAdd 落库）自动重发，today 变化滑动窗口 */
    private fun observeFrequentFoods() {
        viewModelScope.launch {
            frequentFoodProvider.frequentFoods(_todayDate).collect { foods ->
                _addTab.update { it.copy(frequentFoods = foods) }
            }
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
                        macros = DailyMacroAggregator.aggregate(records) { decodeFoodsCached(it) },
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
                dietRecordDao.getByDateRange(historyStartDate(state.rangeDays), historyEndDateExclusive)
                    .map { records -> buildHistoryDays(records, state.rangeDays) }
                    .flowOn(Dispatchers.Default)
            }.collect { days ->
                _historyTab.update { it.copy(days = days) }
            }
        }
    }

    private fun historyStartDate(rangeDays: Int): String =
        LocalDate.now().minusDays((rangeDays - 1).toLong()).toString()

    private val historyEndDateExclusive: String
        get() = LocalDate.now().plusDays(1).toString() // 排他:明天零点

    /**
     * 完整日期序列(OV4B):空档日也占一行「当天未记录」;有记录但食物 JSON 全部解析失败的日,
     * trafficLight 为 null(UI 灰点+保留合计,E3A)。新→旧排序
     */
    private fun buildHistoryDays(records: List<DietRecord>, rangeDays: Int): List<HistoryDay> {
        val today = LocalDate.now()
        val byDate = records.groupBy { it.date }
        val lights = computeDayLights(records)
        return TimeUtils.lastNDates(today, rangeDays).reversed().map { date ->
            val dayRecords = byDate[date].orEmpty()
            if (dayRecords.isEmpty()) {
                HistoryDay(date)
            } else {
                HistoryDay(
                    date = date,
                    records = dayRecords,
                    totalCalories = dayRecords.sumOf { it.estimatedCalories },
                    trafficLight = lights[date],
                )
            }
        }
    }

    /** 历史日头红绿灯;单条脏 JSON 跳过,不参与当日推导;全天解析失败则该日无评级 */
    private fun computeDayLights(records: List<DietRecord>): Map<String, String> {
        val foodsByDate = HashMap<String, MutableList<RecognizedFoodItem>>()
        for (record in records) {
            decodeFoodsCached(record.recognizedFoodJson)
                ?.let { foodsByDate.getOrPut(record.date) { mutableListOf() }.addAll(it) }
        }
        return foodsByDate.mapValues { (_, foods) -> TrafficLightCalculator.compute(foods) }
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
        cachedAnalysisBitmap = null
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

/** 跨午夜时添加页日期跟随规则：仅当用户未手动改过（仍等于旧「今天」）才前进到新「今天」，手动补记的日期保留 */
internal fun advanceAddTabDate(current: String, oldToday: String, newToday: String): String =
    if (current == oldToday) newToday else current

/** 保存成功 SnackBar 文案：今天给今日余量，补记给人性化日期（「已补记昨天」「已补记 8月24日 周六」） */
internal fun savedMessage(date: String, today: LocalDate, remainingCalories: Int?): String =
    if (date == today.toString()) {
        remainingCalories?.let { "已记录,今日还可摄入 $it kcal" } ?: "已记录"
    } else {
        "已补记 ${TimeUtils.humanizeDate(today, date)}"
    }
