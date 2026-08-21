package com.example.weight.ui.diet

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.LocalStorageData
import com.example.weight.data.RecommendedIntakeProvider
import com.example.weight.data.chat.ChatRepository
import com.example.weight.data.diet.AiDietResponse
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.diet.DietPromptBuilder
import com.example.weight.data.diet.FallbackDietAnalyzer
import com.example.weight.data.diet.Macros
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.util.ImageCompressor
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.annotation.KoinViewModel

/**
 *@description: 饮食记录 ViewModel
 *@author: 杨帅林
 *@create: 2026/4/11
 **/

enum class MealType(val label: String, val displayName: String) {
    BREAKFAST("早餐", "🌅 早餐"),
    LUNCH("午餐", "☀️ 午餐"),
    DINNER("晚餐", "🌙 晚餐"),
    SNACK("加餐", "🍎 加餐"),
}

data class DietRecordUiState(
    val selectedImageUri: Uri? = null,
    val hasCapturedBitmap: Boolean = false,
    val selectedMealType: MealType = MealType.LUNCH,
    val isAnalyzing: Boolean = false,
    val aiResponse: AiDietResponse? = null,
    val recognizedFoods: List<RecognizedFoodItem> = emptyList(),
    val editableCalories: Int = 0,
    val macros: Macros = Macros(),
    val trafficLight: String = "",
    val aiAdvice: String = "",
    val todayRecords: List<DietRecord> = emptyList(),
    val todayTotalCalories: Int = 0,
    /** 每日建议摄入（kcal）；档案不全或无体重记录时为 null，界面展示降级形态 */
    val recommendedCalories: Int? = null,
)

@KoinViewModel
class DietRecordViewModel(
    private val application: Application,
    private val dietRecordDao: DietRecordDao,
    private val chatRepository: ChatRepository,
    private val json: Json,
    recommendedIntakeProvider: RecommendedIntakeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DietRecordUiState())
    val uiState: StateFlow<DietRecordUiState> = _uiState.asStateFlow()

    /** “今天”的口径：跨午夜后由 UI 在 ON_RESUME 时调用 [refreshTodayDate] 刷新 */
    private val _todayDate = MutableStateFlow(TimeUtils.getCurrentDate())

    /** Camera-captured bitmap, kept outside UI state to avoid large objects in StateFlow snapshots */
    private var _capturedBitmap: Bitmap? = null

    /** Expose captured bitmap for UI preview (read-only, not in StateFlow) */
    val previewBitmap: Bitmap? get() = _capturedBitmap

    /** Cached compression result from analysis, reused during save to avoid double-compression */
    private var _cachedCompressionResult: ImageCompressor.CompressionResult? = null

    /** Active analysis coroutine, cancelled when a new analysis starts */
    private var analysisJob: Job? = null

    init {
        observeTodayData()
        // 每日建议摄入与报告页共用的冷流，档案/最新体重变化时自动重算
        viewModelScope.launch {
            recommendedIntakeProvider.flow.collect { recommended ->
                _uiState.update { it.copy(recommendedCalories = recommended) }
            }
        }
    }

    /** 跨午夜后回到页面时刷新“今天”的口径；订阅随日期键自动切换查询 */
    fun refreshTodayDate() {
        val today = TimeUtils.getCurrentDate()
        if (_todayDate.value != today) _todayDate.value = today
    }

    fun onImageSelected(uri: Uri) {
        recycleBitmap()
        _cachedCompressionResult = null
        _uiState.update { it.copy(selectedImageUri = uri, hasCapturedBitmap = false) }
    }

    fun onBitmapCaptured(bitmap: Bitmap) {
        recycleBitmap()
        _capturedBitmap = bitmap
        _cachedCompressionResult = null
        _uiState.update { it.copy(hasCapturedBitmap = true, selectedImageUri = null) }
    }

    fun onMealTypeSelected(mealType: MealType) {
        _uiState.update { it.copy(selectedMealType = mealType) }
    }

    fun clearImage() {
        recycleBitmap()
        _cachedCompressionResult = null
        _uiState.update { it.copy(selectedImageUri = null, hasCapturedBitmap = false) }
    }

    fun analyzeImage(userNote: String) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch(Dispatchers.IO) {
            // Snapshot all needed state upfront to avoid TOCTOU races
            val snapshot = _uiState.value
            val bitmap = _capturedBitmap
            val uri = snapshot.selectedImageUri
            val mealType = snapshot.selectedMealType

            _uiState.update { it.copy(isAnalyzing = true) }

            try {
                val base64: String = when {
                    bitmap != null -> {
                        val result = ImageCompressor.compressBitmap(bitmap)
                        _cachedCompressionResult = result
                        result.base64
                    }
                    uri != null -> {
                        // 用 applicationContext：分析协程存活可达数十秒，持有 Activity 会在
                        // 旋转/退出时泄漏；此处只做内容解析，不需要界面上下文
                        val result = ImageCompressor.compressAndEncode(application, uri)
                        _cachedCompressionResult = result
                        result.base64
                    }
                    else -> {
                        performTextOnlyAnalysis(userNote, mealType)
                        return@launch
                    }
                }

                val prompt = DietPromptBuilder.buildFoodRecognitionPrompt(
                    imageBase64 = base64,
                    userNote = userNote,
                    mealType = mealType.label,
                )
                val aiResponse = callAi(prompt)
                applyAiResponse(aiResponse)
            } catch (e: Exception) {
                Log.w(TAG, "AI image analysis failed, using fallback", e)
                val fallback = FallbackDietAnalyzer.generateFallback(userNote, mealType.name)
                applyAiResponse(fallback)
            }
        }
    }

    fun analyzeTextOnly(userNote: String) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch(Dispatchers.IO) {
            val snapshot = _uiState.value
            _uiState.update { it.copy(isAnalyzing = true) }
            performTextOnlyAnalysis(userNote, snapshot.selectedMealType)
        }
    }

    private suspend fun performTextOnlyAnalysis(userNote: String, mealType: MealType) {
        try {
            val prompt = DietPromptBuilder.buildTextOnlyPrompt(
                userDescription = userNote,
                mealType = mealType.label,
            )
            val aiResponse = callAi(prompt)
            applyAiResponse(aiResponse)
        } catch (e: Exception) {
            Log.w(TAG, "AI text analysis failed, using fallback", e)
            val fallback = FallbackDietAnalyzer.generateFallback(userNote, mealType.name)
            applyAiResponse(fallback)
        }
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

    private fun applyAiResponse(response: AiDietResponse) {
        _uiState.update {
            it.copy(
                isAnalyzing = false,
                aiResponse = response,
                recognizedFoods = response.foods,
                editableCalories = response.totalCalories,
                macros = response.macros,
                trafficLight = response.trafficLight,
                aiAdvice = response.advice,
            )
        }
    }

    fun updateFoodItem(index: Int, updated: RecognizedFoodItem) {
        val foods = _uiState.value.recognizedFoods
        if (index !in foods.indices) return
        val newFoods = foods.mapIndexed { i, item -> if (i == index) updated else item }
        _uiState.update {
            it.copy(
                recognizedFoods = newFoods,
                editableCalories = newFoods.sumOf { item -> item.estimatedCalories },
            )
        }
    }

    fun removeFoodItem(index: Int) {
        val foods = _uiState.value.recognizedFoods
        if (index !in foods.indices) return
        val newFoods = foods.filterIndexed { i, _ -> i != index }
        _uiState.update {
            it.copy(
                recognizedFoods = newFoods,
                editableCalories = newFoods.sumOf { item -> item.estimatedCalories },
            )
        }
    }

    fun addFoodItem(item: RecognizedFoodItem) {
        val newFoods = _uiState.value.recognizedFoods + item
        _uiState.update {
            it.copy(
                recognizedFoods = newFoods,
                editableCalories = newFoods.sumOf { food -> food.estimatedCalories },
            )
        }
    }

    fun saveRecord(userNote: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            if (state.recognizedFoods.isEmpty()) return@launch

            val imagePath = buildImagePath(state)

            val record = DietRecord(
                date = _todayDate.value,
                timestamp = System.currentTimeMillis(),
                mealType = state.selectedMealType.name,
                imageUri = imagePath,
                userInput = userNote,
                recognizedFoodJson = json.encodeToString(
                    serializer = kotlinx.serialization.serializer<List<RecognizedFoodItem>>(),
                    value = state.recognizedFoods,
                ),
                estimatedCalories = state.editableCalories,
                trafficLight = state.trafficLight,
            )
            dietRecordDao.insert(record)

            recycleBitmap()
            _cachedCompressionResult = null
            _uiState.update {
                it.copy(
                    selectedImageUri = null,
                    hasCapturedBitmap = false,
                    aiResponse = null,
                    recognizedFoods = emptyList(),
                    editableCalories = 0,
                    macros = Macros(),
                    trafficLight = "",
                    aiAdvice = "",
                )
            }
        }
    }

    private suspend fun buildImagePath(state: DietRecordUiState): String {
        if (state.hasCapturedBitmap) {
            val bitmap = _cachedCompressionResult?.bitmap ?: _capturedBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                return ImageCompressor.saveImage(application, bitmap)
            }
            return ""
        }
        val uri = state.selectedImageUri ?: return ""
        return try {
            // 缓存未命中时只做「解码+降采样」：保存路径只要 Bitmap，
            // 不必重跑 JPEG 压缩 + Base64 编码再把大字符串直接丢弃
            val bitmap = _cachedCompressionResult?.bitmap
                ?: ImageCompressor.decodeScaled(application, uri)
                    ?: return ""
            ImageCompressor.saveImage(application, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            ""
        }
    }

    fun deleteRecord(record: DietRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            ImageCompressor.deleteImage(record.imageUri)
            dietRecordDao.delete(record)
        }
    }

    /**
     * 今日记录与热量合计同源订阅：插入/删除后 Room 失效通知自动重发，
     * 不再需要手动 loadTodayRecords 的重复一次性查询；日期键变化时 flatMapLatest 切换查询
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTodayData() {
        viewModelScope.launch {
            _todayDate.flatMapLatest { date ->
                combine(
                    dietRecordDao.getByDate(date),
                    dietRecordDao.getDailyCaloriesFlow(date),
                ) { records, calories -> records to calories }
            }.collect { (records, calories) ->
                _uiState.update {
                    it.copy(todayRecords = records, todayTotalCalories = calories)
                }
            }
        }
    }

    private fun recycleBitmap() {
        _capturedBitmap?.recycle()
        _capturedBitmap = null
    }

    override fun onCleared() {
        super.onCleared()
        recycleBitmap()
        _cachedCompressionResult = null
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
