package com.example.weight.ui.diet

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weight.data.RecommendedIntakeProvider
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.diet.DietRecordWriter
import com.example.weight.data.diet.FrequentFoodProvider
import com.example.weight.data.diet.MealType
import com.example.weight.data.diet.MealTypeInference
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.annotation.KoinViewModel

/**
 *@description: 主屏快速记饮食弹层的 ViewModel（评审决策 #20：一级入口，离线零 AI 依赖）。
 *               与饮食页共用 DietRecordWriter 落库链路与常用食物聚合器
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
sealed interface QuickAddEvent {
    data class Saved(val remainingCalories: Int?) : QuickAddEvent
    data object SaveFailed : QuickAddEvent
}

data class QuickAddState(
    val frequentFoods: List<RecognizedFoodItem> = emptyList(),
    val selectedFoods: List<RecognizedFoodItem> = emptyList(),
    val selectedMealType: MealType = MealTypeInference.inferNow(),
    val isSaving: Boolean = false,
    val todayTotalCalories: Int = 0,
    val recommendedCalories: Int? = null,
)

@KoinViewModel
class QuickAddViewModel(
    private val application: Application,
    private val dietRecordDao: DietRecordDao,
    private val writer: DietRecordWriter,
    private val json: Json,
    private val frequentFoodProvider: FrequentFoodProvider,
    recommendedIntakeProvider: RecommendedIntakeProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddState())
    val state: StateFlow<QuickAddState> = _state.asStateFlow()

    private val _events = Channel<QuickAddEvent>(Channel.BUFFERED)
    val events: Flow<QuickAddEvent> = _events.receiveAsFlow()

    /** 常用食物窗口锚点；保存时刷新（跨午夜首存即滑动窗口），表变更由 Room Flow 自动重发 */
    private val today = MutableStateFlow(TimeUtils.getCurrentDate())

    init {
        observeFrequentFoods()
        viewModelScope.launch {
            recommendedIntakeProvider.flow.collect { recommended ->
                _state.update { it.copy(recommendedCalories = recommended) }
            }
        }
        viewModelScope.launch {
            dietRecordDao.getDailyCaloriesFlow(TimeUtils.getCurrentDate()).collect { total ->
                _state.update { it.copy(todayTotalCalories = total) }
            }
        }
    }

    fun toggleFood(item: RecognizedFoodItem) {
        _state.update { state ->
            val alreadySelected = state.selectedFoods.any { it.name == item.name }
            state.copy(
                selectedFoods = if (alreadySelected) {
                    state.selectedFoods.filterNot { it.name == item.name }
                } else {
                    state.selectedFoods + item
                }
            )
        }
    }

    fun updateSelectedFood(index: Int, updated: RecognizedFoodItem) {
        _state.update { state ->
            if (index !in state.selectedFoods.indices) return@update state
            state.copy(selectedFoods = state.selectedFoods.mapIndexed { i, item -> if (i == index) updated else item })
        }
    }

    fun removeSelectedFood(index: Int) {
        _state.update { state ->
            state.copy(selectedFoods = state.selectedFoods.filterIndexed { i, _ -> i != index })
        }
    }

    fun setMealType(mealType: MealType) {
        _state.update { it.copy(selectedMealType = mealType) }
    }

    fun save() {
        val snapshot = _state.value
        if (snapshot.selectedFoods.isEmpty() || snapshot.isSaving) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true) }
            try {
                val savedCalories = snapshot.selectedFoods.sumOf { it.estimatedCalories }
                writer.insert(
                    DietRecordWriter.Draft(
                        foods = snapshot.selectedFoods,
                        mealType = snapshot.selectedMealType,
                        date = TimeUtils.getCurrentDate(),
                        userInput = "快速添加",
                        // 无 AI：红绿灯由 Writer 内本地推导
                        trafficLight = null,
                    )
                )
                _state.update { it.copy(selectedFoods = emptyList()) }
                today.value = TimeUtils.getCurrentDate()
                val remaining = snapshot.recommendedCalories?.let {
                    it - (snapshot.todayTotalCalories + savedCalories)
                }
                _events.send(QuickAddEvent.Saved(remaining))
            } catch (e: Exception) {
                Log.e("QuickAdd", "快速添加保存失败", e)
                _events.send(QuickAddEvent.SaveFailed)
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    /** 与饮食页共用同一聚合数据源：编辑/删除/撤销/本入口落库均自动重发 */
    private fun observeFrequentFoods() {
        viewModelScope.launch {
            frequentFoodProvider.frequentFoods(today).collect { foods ->
                _state.update { it.copy(frequentFoods = foods) }
            }
        }
    }
}
