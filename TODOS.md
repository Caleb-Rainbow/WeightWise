# TODOS

- [x] **建立 DESIGN.md 设计系统文档**
  - **What:** 把隐性设计约定固化成文档：卡片 12dp 圆角（默认 `Card()`）、间距体系（页边距 15dp/卡间距 14dp/行高 48-56dp）、`MaterialTheme.colorScheme` 语义色角色、列表行模式（label+value+chevron）、弹窗模式（滚轮选择/单选/确认）。
  - **Why:** 2026-08-21 设置页重设计审查中，所有视觉规格都靠反推 BMICard/首页等现有页面得出；无文档时跨会话重设计要重新推断，AI 生成也容易跑偏。
  - **Pros:** 新页面有统一基准；设计审查有校准依据；新人/AI 上手快。
  - **Cons:** 需要一次专项时间（/design-consultation 约半小时），后续改设计需同步文档。
  - **Context:** 源自 plan-design-review Pass 5 发现：项目无 DESIGN.md，评分被拉低且规格决策全靠反推。
  - **Depends on:** 无。

- [x] **T-4 统一一级目的地导航**
  - **What:** 调整 Navigation3 结构，让设置、饮食、记录、报告等一级目的地始终保留底部导航；饮食内部继续使用“今日 / 历史”二级分段，新增记录保持独立任务流。
  - **Why:** 本轮已修复饮食默认落点与新增流程，但进入饮食后底部导航仍消失，用户对应用整体位置的感知会中断。只对饮食页做局部底栏会制造双重导航，应在应用级统一处理。
  - **Pros:** 一级导航心智稳定；各模块返回行为一致；后续深链更容易落到正确目的地。
  - **Cons:** 涉及 `MainActivity` / `MainScreen` 的路由宿主与返回栈策略，需要回归所有一级入口。
  - **Context:** 2026-08-23 饮食设计评审 F1；Today-first 已落地，剩余为应用级架构问题。
  - **Effort:** M（human ~1-2 天 / CC ~1-2 小时）
  - **Priority:** P1
  - **Done:** 2026-08-26 落地。MainBottomToolbar 上提为全局层挂宿主 Scaffold bottomBar；navigateToTopLevel 清栈重置语义（切 tab 丢弃二级页）；一级间转场 fade+scale 120ms；深链/中央记体重/饮食长按全部改走新管道。真机回归通过。

- [x] **T-5 饮食历史趋势摘要**
  - **What:** 在历史页增加迷你日历热力格、近 30 天记录天数、日均热量和达标天数；保留当前“只显示有记录日期”的时间线。
  - **Why:** 当前已消除连续空日期噪音，但历史页仍以逐条回看为主，缺少能回答“最近记录得怎么样”的趋势摘要。
  - **Pros:** 提升历史页复访价值；漏记与连续记录更直观；复用现有 Room 数据即可完成。
  - **Cons:** 需要定义达标口径并处理旧记录缺少宏量数据的降级状态。
  - **Context:** 2026-08-23 饮食设计评审 F4；空日期压缩已完成，趋势层为后续增强。
  - **Effort:** M（human ~1 天 / CC ~1 小时）
  - **Priority:** P2
  - **Done:** 2026-08-26 落地。HistoryTrendAggregator 纯函数（口径对齐 ReportAggregator/intakeStatus）+ 摘要卡（记录天数/日均/超出额度天数）+ 周对齐迷你热力格；档案不全降级提示。真机验证通过。

- [x] **T-6 食物质量三态数据模型**
  - **What:** 将 `RecognizedFoodItem.isHealthy: Boolean` 迁移为可持久化的三态质量字段，并兼容旧 JSON、AI prompt、常用食物聚合、编辑器和 `TrafficLightCalculator`。
  - **Why:** 本轮 UI 已将二元标签改为中性的“适合经常吃 / 偶尔吃”，记录级红绿灯也有文字标签；若要让单个食物真正支持“健康 / 尚可 / 放纵一下”，必须先升级数据模型，不能只做第三个 UI 按钮。
  - **Pros:** 单项质量与整餐三态语义完全一致；后续报告可提供更细粒度分析。
  - **Cons:** 涉及序列化兼容与历史数据迁移，且 AI 判断精度需要重新评测。
  - **Context:** 2026-08-23 饮食设计评审 F3；本轮刻意避免伪造无法持久化的第三状态。
  - **Effort:** M
  - **Priority:** P3
  - **Done:** 2026-08-26 落地。FoodQuality 枚举（OFTEN/SOMETIMES/INDULGENT）+ effectiveQuality 旧值映射（false 保守归 SOMETIMES）；TrafficLightCalculator 计分制（INDULGENT=1/SOMETIMES=0.5，≥1/3 判红，全 SOMETIMES 保持红）；prompt 协议升级；宏量同批可空化（null=无数据/0=真 0）。序列化兼容测试锁定。

- [ ] **T-1 桌面小组件快速记饮食**
  - **What:** 主屏 Glance 小组件增加「记一笔」入口，弹出常用食物快速添加（或直达饮食页添加 Tab）。
  - **Why:** 记录摩擦是饮食记录留存的第一杠杆；小组件是零打开成本的最低摩擦入口。现有小组件只有体重数据，饮食数据从未接入。
  - **Pros:** 3 秒记完一餐的终极形态；复用饮食重设计产出的 `DietRecordWriter` 与常用食物聚合器，增量小。
  - **Cons:** Glance 弹 BottomSheet 受限 RemoteViews 交互模型，可能只做「直达页面」深链；需处理小组件进程无 ViewModel 的取数路径。
  - **Context:** 源自 2026-08-21 饮食重设计 CEO 评审（发现 8a）。起点：`widget/WeightWidget.kt`，参考现有「点击直达记体重」深链（`MainActivity.EXTRA_OPEN_ADD_DIALOG`）。
  - **Effort:** M（human ~2 天 / CC ~1-2 小时）
  - **Priority:** P2
  - **Depends on:** 饮食重设计落地（Writer + 聚合器）

- [ ] **T-2 AI 估算质量评测集**
  - **What:** 收集 20-50 张日常食物照片 + 人工标注（食物名/克数/热量），建可重复运行的对比基线（同一 prompt 改动前后跑同一批图比对误差）。
  - **Why:** 拍照升级不改变 AI 输入（ImageCompressor 统一 1024px），识别质量的真实杠杆是 prompt 与压缩参数，但当前无任何度量手段。前提门已确认纯自用定位，不阻塞重设计；若未来面向用户，这是必修第一课。
  - **Pros:** prompt/压缩参数调优从玄学变工程；能回答「估算失准率」核心指标。
  - **Cons:** 标注耗时且人工估热量本身不准；纯自用阶段收益有限。
  - **Context:** 源自 2026-08-21 饮食重设计 CEO 评审（发现 7/9）。起点：`data/diet/DietPromptBuilder.kt` + `util/ImageCompressor.kt` 压缩参数。
  - **Effort:** M（标注为主，human ~1 天 / CC ~1 小时建脚手架）
  - **Priority:** P3（自用）；升 P1（若转向产品）

- [ ] **T-3 饮食记录行为指标查看**
  - **What:** 基于现有 DB 做只读小视图（设置页或报告页角落）：近 30 天日均记录条数、AI 结果被人工修改的比例。
  - **Why:** 重设计成功与否需事后校验（痛点全部来自代码推断，需求存在性未验证）；两指标均可从现有表算出，无需埋点。
  - **Pros:** 零基建成本（纯 SQL + 小 Composable）；为后续迭代提供事实依据。
  - **Cons:** 自用场景下看几次就够，低频功能。
  - **Context:** 源自 2026-08-21 饮食重设计 CEO 评审（发现 2）。「AI 结果被修改」需编辑功能先落地（编辑行为即修改信号）；起点：`DietRecordDao` 聚合查询。
- **Effort:** S（human ~半天 / CC ~30 分钟）
- **Priority:** P3
- **Depends on:** 饮食重设计编辑功能落地
- **Note:** 2026-08-28 控制回路工程评审顺延为独立后续 PR：护栏口径（周覆盖率/已记录日均摄入）已由 WeeklyControlEngine 统一提供，此视图改为消费其输出，见 docs/designs/weekly-control-loop-v1.md 交付切片 4。

- [ ] **T-7 周报推送携带每周决策结论**
  - **What:** 扩展 ReportPushWorker，周一推送摘要行携带本周决策卡结论（保持/微调一件事/数据不足），通知深链直达报告页决策卡。
  - **Why:** 决策卡目前只在用户主动进报告页时可见；周一主动触达才构成"每周控制回路"的完整闭环。
  - **Pros:** 决策触达零操作成本；复用现有 EXTRA_OPEN_REPORT 深链与 Worker 排程。
  - **Cons:** WorkManager 周推送的时区与后台限制是已知真机回归坑；通知文案须覆盖三态降级。
  - **Context:** 每周控制回路 v1 范围裁决（2026-08-28 /plan-eng-review）将本项从 3 切片后置为独立 PR，避免一次交付面过大。起点：data/report/ReportPushWorker.kt + docs/designs/weekly-control-loop-v1.md 交付切片 4。
  - **Effort:** S（human ~半天 / CC ~1 小时）
  - **Priority:** P2
  - **Depends on:** 每周控制回路 3 切片（引擎+决策卡）落地

- [ ] **T-8 报告窗口时区统一为北京时区**
  - **What:** ReportPeriod.kt:48 与 TimeUtils.kt:18 的通用日期转换改用与体重归日一致的 UTC+8 常量，消除系统默认时区依赖。
  - **Why:** 体重按北京时区归日，但报告周期窗口用系统默认时区切界——旅行或修改设备时区会切错报告周期边界，且控制回路的"完整自然周"界定依赖同一口径。
  - **Pros:** 全 App 时间口径单一化；控制回路周界定不再受设备时区影响。
  - **Cons:** 需回归全部用日期边界的页面（报告/趋势/饮食历史）；海外用户按北京时区切周在直觉上可议（当前用户群为单人自用，不构成问题）。
  - **Context:** 2026-08-28 /plan-eng-review Codex 冷读发现的存量不一致（#7）；决策卡已在本 PR 内显式使用 UTC+8 常量规避，本 TODO 收尾存量面。起点：util/ReportPeriod.kt、util/TimeUtils.kt，对照 data/record DAO 的北京时区归组 SQL。
  - **Effort:** S
  - **Priority:** P2
  - **Depends on:** 无
