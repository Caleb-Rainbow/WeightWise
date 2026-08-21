# TODOS

- [ ] **建立 DESIGN.md 设计系统文档**
  - **What:** 把隐性设计约定固化成文档：卡片 12dp 圆角（默认 `Card()`）、间距体系（页边距 15dp/卡间距 14dp/行高 48-56dp）、`MaterialTheme.colorScheme` 语义色角色、列表行模式（label+value+chevron）、弹窗模式（滚轮选择/单选/确认）。
  - **Why:** 2026-08-21 设置页重设计审查中，所有视觉规格都靠反推 BMICard/首页等现有页面得出；无文档时跨会话重设计要重新推断，AI 生成也容易跑偏。
  - **Pros:** 新页面有统一基准；设计审查有校准依据；新人/AI 上手快。
  - **Cons:** 需要一次专项时间（/design-consultation 约半小时），后续改设计需同步文档。
  - **Context:** 源自 plan-design-review Pass 5 发现：项目无 DESIGN.md，评分被拉低且规格决策全靠反推。
  - **Depends on:** 无。

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

- [ ] **T-4 主屏 QuickAddSheet 一级入口（若最终门未纳入本次）**
  - **What:** 主屏工具栏「饮食」改为弹出快速添加 BottomSheet（常用食物 chips + 保存），sheet 内提供「拍照识别」跳转完整饮食页。
  - **Why:** 决策审计 #20（口味决策）：快速添加是对冲豆包 API 单点依赖、降低记录摩擦的最高频路径，一级入口省一次页面跳转。
  - **Pros:** 最高频动作直达；复用 Writer/聚合器。
  - **Cons:** 触碰 MainScreen（爆炸半径 3-5 文件）；与 T-1 小组件入口功能重叠，可二选一或都做。
  - **Context:** 源自 2026-08-21 饮食重设计设计评审（F14）。起点：`MainScreen.kt` 工具栏 + 饮食重设计计划 §11.3 快速添加规格。
  - **Effort:** S-M（human ~1 天 / CC ~1 小时）
  - **Priority:** P2
  - **Depends on:** 饮食重设计落地（Writer + 聚合器 + QuickAdd 规格）
