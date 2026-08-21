# DESIGN.md — WeightWise 设计系统

> 2026-08-21 由饮食记录 v1.6 重设计的 /plan-eng-review T9 建立(OV6A:先文档后对齐)。
> 改 UI 前先读本文;新组件先在这里登记再实现。视觉基准稿:~/.gstack/projects/WeightWise/designs/diet-ui-20260821/design-board-v2.html

## 基准 token

| Token | 值 | 说明 |
|---|---|---|
| 卡片圆角 | 12dp | `Card()` 默认或 `RoundedCornerShape(12.dp)`,全 app 统一 |
| 页水平边距 | 15dp | LazyColumn/Scaffold contentPadding |
| 卡间距 | 14dp | `Arrangement.spacedBy(14.dp)` |
| 行最小高度 | 48dp | `heightIn(min = 48.dp)`;可点元素触控目标 ≥48dp(`minimumInteractiveComponentSize`) |
| 行内边距 | 12-16dp | 行 padding(horizontal 12-16, vertical 10-12) |
| 主按钮 | 全宽 12dp 圆角 | `Button(shape = RoundedCornerShape(12.dp))` |
| chips | 视觉 32dp + 触控 48dp | FilterChip + `minimumInteractiveComponentSize()` |

## 颜色:三套语义编码,互不借用(出处 `ui/diet/DietTrafficLight.kt`)

| 常量对象 | 语义 | 使用位置 |
|---|---|---|
| `TrafficLightColors` | **食物质量**(绿=健康饮食/琥珀=尚可/红=放纵一下) | B 行色条、结果卡 chip、历史日头圆点 |
| `IntakeRingColors` | **额度状态**(今日热量预算) | 今日页 hero 圆环(唯一使用处) |
| `DietMacroColors` | **宏量营养类别**(蛋白钢蓝/碳水 teal/脂肪棕) | 堆叠条、图例、结果卡宏量行 |

规则:红绿灯三色永远配文字标签(lightchip),不做纯颜色语义;文字用深色变体(琥珀 #B8860B 级)保 ≥4.5:1。数值相同也要用各自常量对象,靠 import 边界隔离语义。

## 共享组件(ui/diet/)

- **B 行** `DietRecordRow`:4dp 红绿灯色条 + 40dp 缩略图(有图才出现)+ 食物名标题 + 克数·备注副标 + 右对齐 kcal;点击整行进编辑器,行上无图标按钮
- **lightchip** `lightChipColors(light)`:三态容器底/圆点/深色文字
- **SelectedFoodsSection**:「已选 n 项 · 共 X kcal」汇总条 + 可改可删条目行(添加页与 QuickAddSheet 共用)
- **状态圆环** `RingBox`:额度唯一编码,环心中性「已用 N%」,超支画满红
- **DietPrimaryAction** `dietPrimaryAction()`:添加页主按钮四输入状态机(纯函数,有单测)

## 列表行模式

label(bodyLarge/600) 左,value(bodyMedium/600) 右,副标 bodySmall + onSurfaceVariant;日期说人话(`TimeUtils.humanizeDate`:今天/昨天/M月d日 周X);数字一律带单位(kcal/g)。

## 弹窗模式

滚轮选择(NumberSelector)/单选弹窗/确认弹窗(AlertDialog)/编辑弹层(ModalBottomSheet,skipPartiallyExpanded,水平边距 15dp)。破坏性操作:即时删除 + 10 秒撤销 SnackBar(应用级 scope)。

## 空态原则

空态 = 插画级图形 + 一句话 + 主行动按钮;禁止只有一行灰字。

## 已知例外

- `RecordScreen.IncreaseColor`(0xFFEF5350)是体重上涨色,语义独立,不属红绿灯
- 旧记录无宏量字段:堆叠条区域显示「宏量数据 —」降级
