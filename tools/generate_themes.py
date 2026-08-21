"""离线生成 WeightWise 主题色板与语义色的 Kotlin 常量。

用法: python tools/generate_themes.py
依赖: pip install material-color-utilities

产物直接粘贴进 app/src/main/java/com/example/weight/ui/theme/ThemePalettes.kt。
参数口径: Variant.TONALSPOT + contrast_level=0.0 —— 已验证可精确复现现款钢蓝
Color.kt(Material Theme Builder 默认配置, 仅 secondary 有 ±1 舍入差)。
语义三色板用同库 TonalPalette 推导: 浅色 base=tone40/container=tone90/text=tone20,
深色 base=tone80/container=tone30/text=tone90。
"""

import os

from material_color_utilities import _core as mcu

# 主题预设种子(钢蓝走现有 Color.kt 不在此列)
THEMES = [
    ("Indigo", "#4A5AA8"),    # 靛青
    ("Wisteria", "#7E57C2"),  # 紫藤
    ("Rose", "#B04A6A"),      # 蔷薇
    ("Terracotta", "#A6553D"),  # 陶土
]

# 语义色种子: 食物质量红绿灯 / 额度环共用这套色相
SEMANTIC = [
    ("Green", "#3F8E4D"),
    ("Amber", "#D09A1E"),
    ("Red", "#CF4036"),
]

SLOTS = [
    ("primary", "primary"), ("onPrimary", "on_primary"),
    ("primaryContainer", "primary_container"), ("onPrimaryContainer", "on_primary_container"),
    ("secondary", "secondary"), ("onSecondary", "on_secondary"),
    ("secondaryContainer", "secondary_container"), ("onSecondaryContainer", "on_secondary_container"),
    ("tertiary", "tertiary"), ("onTertiary", "on_tertiary"),
    ("tertiaryContainer", "tertiary_container"), ("onTertiaryContainer", "on_tertiary_container"),
    ("error", "error"), ("onError", "on_error"),
    ("errorContainer", "error_container"), ("onErrorContainer", "on_error_container"),
    ("background", "background"), ("onBackground", "on_surface"),  # 新版 M3 onBackground 恒等于 onSurface
    ("surface", "surface"), ("onSurface", "on_surface"),
    ("surfaceVariant", "surface_variant"), ("onSurfaceVariant", "on_surface_variant"),
    ("outline", "outline"), ("outlineVariant", "outline_variant"),
    ("scrim", "scrim"),
    ("inverseSurface", "inverse_surface"), ("inverseOnSurface", "inverse_on_surface"),
    ("inversePrimary", "inverse_primary"),
    ("surfaceDim", "surface_dim"), ("surfaceBright", "surface_bright"),
    ("surfaceContainerLowest", "surface_container_lowest"),
    ("surfaceContainerLow", "surface_container_low"),
    ("surfaceContainer", "surface_container"),
    ("surfaceContainerHigh", "surface_container_high"),
    ("surfaceContainerHighest", "surface_container_highest"),
]


def khex(hexstr: str) -> str:
    return "0xFF" + hexstr.lstrip("#").upper()


def emit_scheme(name: str, scheme) -> str:
    lines = [f"internal val {name} = lightColorScheme(" if not scheme.is_dark else f"internal val {name} = darkColorScheme("]
    for kname, prop in SLOTS:
        lines.append(f"    {kname} = Color({khex(getattr(scheme, prop))}),")
    lines.append(")")
    return "\n".join(lines)


def main() -> None:
    out = []
    out.append("""package com.example.weight.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 由 tools/generate_themes.py 离线生成(material-color-utilities,TONALSPOT + contrast 0,
// 已验证与现款钢蓝 Color.kt 同口径)。钢蓝默认主题直接用 Color.kt 的 lightScheme/darkScheme。
// 新增或调整主题:改脚本 THEMES 种子列表重跑本脚本即可。""")

    for name, seed in THEMES:
        t = mcu.theme_from_color(seed, contrast_level=0.0, variant=mcu.Variant.TONALSPOT)
        cn = {"Indigo": "靛青", "Wisteria": "紫藤", "Rose": "蔷薇", "Terracotta": "陶土"}.get(name, name)
        out.append(f"\n// {cn} {name},种子 {seed},浅色主色 {t.schemes.light.primary}")
        out.append(emit_scheme(f"{name.lower()}LightScheme", t.schemes.light))
        out.append("")
        out.append(emit_scheme(f"{name.lower()}DarkScheme", t.schemes.dark))

    path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..",
                          "app/src/main/java/com/example/weight/ui/theme/ThemePalettes.kt"))
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")
    print(f"written: {path}")

    print("\n===== 语义三色板 tonal 推导 =====")
    for name, seed in SEMANTIC:
        pal = mcu.TonalPalette(mcu.argb_from_hex(seed))
        tones = {tone: mcu.hex_from_argb(pal.get_argb(tone)) for tone in (10, 20, 30, 40, 45, 50, 60, 70, 80, 90)}
        print(f"\n{name} 种子 {seed}: " + " ".join(f"t{t_}={h}" for t_, h in tones.items()))
        # 对比度校验: 文字角色 vs 容器角色 ≥4.5:1
        pairs = [("浅色文字 t20 vs 容器 t90", 20, 90), ("深色文字 t80 vs 容器 t30", 80, 30)]
        for label, ft, bg in pairs:
            ratio = mcu.get_contrast_ratio(mcu.hex_from_argb(pal.get_argb(ft)), mcu.hex_from_argb(pal.get_argb(bg)))
            print(f"  {label}: {ratio:.2f}:1 {'OK' if ratio >= 4.5 else '!! 不足'}")


if __name__ == "__main__":
    main()
