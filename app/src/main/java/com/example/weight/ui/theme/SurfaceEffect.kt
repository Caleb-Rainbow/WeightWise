package com.example.weight.ui.theme

/** App-wide material preference; older devices always render the compatible blur. */
enum class SurfaceEffect(val label: String) {
    BLUR("模糊"),
    GLASS("玻璃");

    fun forSdk(sdk: Int): SurfaceEffect = if (sdk < 33) BLUR else this

    companion object {
        fun fromId(id: String): SurfaceEffect = entries.find { it.name == id } ?: BLUR
    }
}
