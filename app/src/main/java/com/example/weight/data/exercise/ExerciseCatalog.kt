package com.example.weight.data.exercise

object ExerciseCatalog {

    enum class Category(val label: String) {
        NEAT("日常活动"),
        CARDIO("轻度有氧"),
        STRETCHING("拉伸放松"),
        BODYWEIGHT("徒手训练")
    }

    enum class Intensity(val label: String) {
        LIGHT("轻松"),
        MODERATE("适中"),
        VIGOROUS("有些强度")
    }

    data class CatalogExercise(
        val name: String,
        val description: String,
        val category: Category,
        val intensity: Intensity,
        val durationMinutes: Int,
        val estimatedCalories: Int,
        val tags: Set<String> = emptySet(),
    )

    val easyExercises: List<CatalogExercise> = listOf(
        CatalogExercise("走楼梯代替电梯", "上楼时走楼梯，楼层太高可以走一半", Category.NEAT, Intensity.LIGHT, 5, 25, setOf("膝盖负荷")),
        CatalogExercise("提前一站下车步行", "公交/地铁提前一站下车，步行到目的地", Category.NEAT, Intensity.LIGHT, 10, 40, setOf("户外", "散步")),
        CatalogExercise("睡前拉伸", "简单拉伸放松全身肌肉，帮助睡眠", Category.STRETCHING, Intensity.LIGHT, 10, 20, setOf("室内", "拉伸")),
        CatalogExercise("家务时间", "扫地、拖地、整理房间等日常家务", Category.NEAT, Intensity.LIGHT, 20, 60, setOf("室内", "日常活动")),
        CatalogExercise("饭后散步", "饭后慢走帮助消化，不要剧烈运动", Category.CARDIO, Intensity.LIGHT, 15, 45, setOf("户外", "散步")),
        CatalogExercise("站立办公或看电视", "减少久坐时间，站着做事或看电视", Category.NEAT, Intensity.LIGHT, 15, 30, setOf("室内", "站立")),
        CatalogExercise("靠墙站立", "饭后靠墙站立10分钟，帮助消化又塑形", Category.BODYWEIGHT, Intensity.LIGHT, 10, 25, setOf("室内", "站立")),
        CatalogExercise("踮脚刷牙", "刷牙时踮起脚尖，锻炼小腿肌肉", Category.NEAT, Intensity.LIGHT, 5, 15, setOf("室内")),
    )

    val mediumExercises: List<CatalogExercise> = listOf(
        CatalogExercise("快步走", "保持较快步速，微微出汗的节奏", Category.CARDIO, Intensity.MODERATE, 20, 80, setOf("户外", "散步", "出汗")),
        CatalogExercise("骑车出行", "用自行车代替短途公共交通", Category.CARDIO, Intensity.MODERATE, 20, 70, setOf("户外", "器械")),
        CatalogExercise("拉伸+深蹲组合", "5分钟拉伸热身+5分钟深蹲+5分钟拉伸放松", Category.BODYWEIGHT, Intensity.MODERATE, 15, 55, setOf("室内", "深蹲", "膝盖负荷")),
        CatalogExercise("广场舞或跟跳", "跟着视频跳一段简单的舞蹈", Category.CARDIO, Intensity.MODERATE, 15, 65, setOf("跳跃", "出汗")),
        CatalogExercise("公园慢跑", "轻松的慢跑，能正常说话的速度", Category.CARDIO, Intensity.MODERATE, 15, 90, setOf("户外", "跑步", "出汗")),
        CatalogExercise("爬楼梯训练", "连续爬楼梯3-5层，休息后重复", Category.BODYWEIGHT, Intensity.MODERATE, 10, 50, setOf("膝盖负荷")),
        CatalogExercise("办公室拉伸操", "针对久坐族的颈椎、肩部、腰部拉伸", Category.STRETCHING, Intensity.MODERATE, 15, 30, setOf("室内", "办公室", "拉伸")),
    )

    val hardExercises: List<CatalogExercise> = listOf(
        CatalogExercise("慢跑3公里", "保持匀速，注意呼吸节奏", Category.CARDIO, Intensity.VIGOROUS, 25, 150, setOf("户外", "跑步", "出汗")),
        CatalogExercise("HIIT入门", "20秒运动+10秒休息，8个动作循环", Category.BODYWEIGHT, Intensity.VIGOROUS, 20, 120, setOf("室内", "跳跃", "出汗", "大动作")),
        CatalogExercise("跳绳", "匀速跳绳，中间可短暂休息", Category.CARDIO, Intensity.VIGOROUS, 15, 110, setOf("跳跃", "出汗")),
        CatalogExercise("全身力量训练", "俯卧撑+深蹲+平板支撑组合", Category.BODYWEIGHT, Intensity.VIGOROUS, 20, 100, setOf("室内", "深蹲", "大动作")),
        CatalogExercise("快走+慢跑交替", "3分钟快走+2分钟慢跑，循环进行", Category.CARDIO, Intensity.VIGOROUS, 25, 130, setOf("户外", "跑步", "出汗")),
    )

    private val safeExercises: List<CatalogExercise> = listOf(
        CatalogExercise("腹式呼吸放松", "深吸气让腹部隆起，缓慢呼气，重复10次", Category.STRETCHING, Intensity.LIGHT, 5, 10, emptySet()),
        CatalogExercise("坐在椅子上伸展手臂", "坐在椅子上缓慢向上伸展双臂，保持5秒后放松", Category.STRETCHING, Intensity.LIGHT, 5, 10, setOf("室内")),
        CatalogExercise("深呼吸练习", "用鼻子深吸气4秒，屏息4秒，缓慢呼气6秒", Category.NEAT, Intensity.LIGHT, 5, 5, emptySet()),
    )

    private val allExercisesByDifficulty: Map<Int, List<CatalogExercise>> = mapOf(
        1 to easyExercises,
        2 to mediumExercises,
        3 to hardExercises,
    )

    fun getExercisesForDifficulty(level: Int): List<CatalogExercise> {
        return allExercisesByDifficulty[level] ?: easyExercises
    }

    fun getRandomExercise(
        difficulty: Int,
        excludeNames: List<String>,
        excludedTags: Set<String> = emptySet(),
        preferredTags: Set<String> = emptySet(),
    ): CatalogExercise? {
        val pool = getExercisesForDifficulty(difficulty)
            .filter { it.name !in excludeNames }
            .filter { exercise -> exercise.tags.none { it in excludedTags } }

        if (pool.isEmpty()) return getSafeExercise()

        val preferred = pool.filter { exercise ->
            exercise.tags.any { it in preferredTags }
        }
        return (preferred.ifEmpty { pool }).randomOrNull() ?: getSafeExercise()
    }

    fun getFilteredExercisesForDifficulty(
        level: Int,
        excludedTags: Set<String> = emptySet(),
        preferredTags: Set<String> = emptySet(),
    ): List<CatalogExercise> {
        val base = getExercisesForDifficulty(level)
        val filtered = base.filter { exercise ->
            exercise.tags.none { it in excludedTags }
        }
        return if (preferredTags.isNotEmpty()) {
            filtered.sortedByDescending { exercise ->
                exercise.tags.count { it in preferredTags }
            }
        } else {
            filtered
        }
    }

    fun getSafeExercise(): CatalogExercise? = safeExercises.randomOrNull()

    fun getDifficultyLevel(intensity: String): Int = when (intensity) {
        "LIGHT" -> 1
        "MODERATE" -> 2
        "VIGOROUS" -> 3
        else -> 2
    }

    fun toExerciseItem(exercise: CatalogExercise): ExerciseItem = ExerciseItem(
        name = exercise.name,
        description = exercise.description,
        durationMinutes = exercise.durationMinutes,
        estimatedCalories = exercise.estimatedCalories,
        category = exercise.category.name,
        intensity = exercise.intensity.name,
    )
}
