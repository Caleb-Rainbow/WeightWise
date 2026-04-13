package com.example.weight.ui.diet

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.annotation.KoinViewModel

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
    val userNote: String = "",
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
)

@KoinViewModel
class DietRecordViewModel(
    private val dietRecordDao: DietRecordDao,
    private val chatRepository: ChatRepository,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DietRecordUiState())
    val uiState: StateFlow<DietRecordUiState> = _uiState.asStateFlow()

    private val todayDate: String
        get() = TimeUtils.getCurrentDate()

    /** Camera-captured bitmap, kept outside UI state to avoid large objects in StateFlow snapshots */
    private var _capturedBitmap: Bitmap? = null

    /** Expose captured bitmap for UI preview (read-only, not in StateFlow) */
    val previewBitmap: Bitmap? get() = _capturedBitmap

    /** Cached compression result from analysis, reused during save to avoid double-compression */
    private var _cachedCompressionResult: ImageCompressor.CompressionResult? = null

    /** Active analysis coroutine, cancelled when a new analysis starts */
    private var analysisJob: Job? = null

    private lateinit var todayCaloriesFlow: StateFlow<Int>

    init {
        loadTodayRecords()
        observeTodayCalories()
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

    fun onUserNoteChanged(note: String) {
        _uiState.update { it.copy(userNote = note) }
    }

    fun onMealTypeSelected(mealType: MealType) {
        _uiState.update { it.copy(selectedMealType = mealType) }
    }

    fun clearImage() {
        recycleBitmap()
        _cachedCompressionResult = null
        _uiState.update { it.copy(selectedImageUri = null, hasCapturedBitmap = false) }
    }

    fun analyzeImage(context: Context) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch(Dispatchers.IO) {
            // Snapshot all needed state upfront to avoid TOCTOU races
            val snapshot = _uiState.value
            val bitmap = _capturedBitmap
            val uri = snapshot.selectedImageUri
            val userNote = snapshot.userNote
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
                        val result = ImageCompressor.compressAndEncode(context, uri)
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

    fun analyzeTextOnly() {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch(Dispatchers.IO) {
            val snapshot = _uiState.value
            _uiState.update { it.copy(isAnalyzing = true) }
            performTextOnlyAnalysis(snapshot.userNote, snapshot.selectedMealType)
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

    fun saveRecord(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            if (state.recognizedFoods.isEmpty()) return@launch

            val imagePath = buildImagePath(context, state)

            val record = DietRecord(
                date = todayDate,
                timestamp = System.currentTimeMillis(),
                mealType = state.selectedMealType.name,
                imageUri = imagePath,
                userInput = state.userNote,
                recognizedFoodJson = json.encodeToString(
                    serializer = kotlinx.serialization.serializer<List<RecognizedFoodItem>>(),
                    value = state.recognizedFoods,
                ),
                estimatedCalories = state.editableCalories,
                trafficLight = state.trafficLight,
            )
            dietRecordDao.insert(record)
            loadTodayRecords()

            recycleBitmap()
            _cachedCompressionResult = null
            _uiState.update {
                it.copy(
                    selectedImageUri = null,
                    hasCapturedBitmap = false,
                    userNote = "",
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

    private suspend fun buildImagePath(context: Context, state: DietRecordUiState): String {
        if (state.hasCapturedBitmap) {
            val bitmap = _cachedCompressionResult?.bitmap ?: _capturedBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                return ImageCompressor.saveToCache(context, bitmap)
            }
            return ""
        }
        val uri = state.selectedImageUri ?: return ""
        return try {
            val bitmap = _cachedCompressionResult?.bitmap
                ?: ImageCompressor.compressAndEncode(context, uri).bitmap
            ImageCompressor.saveToCache(context, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image to cache", e)
            ""
        }
    }

    fun deleteRecord(record: DietRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            ImageCompressor.deleteCacheImage(record.imageUri)
            dietRecordDao.delete(record)
            loadTodayRecords()
        }
    }

    private fun loadTodayRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = dietRecordDao.getByDateOnce(todayDate)
            _uiState.update { it.copy(todayRecords = records) }
        }
    }

    private fun observeTodayCalories() {
        viewModelScope.launch {
            todayCaloriesFlow = dietRecordDao.getDailyCaloriesFlow(todayDate)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
            todayCaloriesFlow.collect { calories ->
                _uiState.update { it.copy(todayTotalCalories = calories) }
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
