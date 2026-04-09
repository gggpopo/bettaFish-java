# BettaFish 开发对齐计划

> 目标仓库：https://github.com/666ghj/BettaFish.git（Python/Flask+Streamlit）
> 本地项目：Java 21 / Spring Boot 3.4.5 / Spring AI 1.0.1 重写版
> 生成日期：2026-04-09

---

## 一、整体差距概览

| 维度 | 目标项目 | 本地项目 | 完成度 |
|------|---------|---------|--------|
| ReportEngine 渲染器 | HTML(6536行)/PDF(1609行)/Markdown(994行)/Chart-SVG/Math-SVG | HtmlRenderer(104行) | ~5% |
| ReportEngine 流水线节点 | 模板选择/文档布局/字数预算/章节存储/拼接器 | ChapterGenerationNode 仅基础生成 | ~15% |
| 各引擎 Prompt | 平均 400-600 行，含完整领域逻辑 | 平均 40-200 行，骨架级别 | ~30% |
| MindSpider 爬虫 | 完整的话题提取+深度爬取+数据库模型(~3300行) | 空壳 stub(~60行) | ~2% |
| InsightEngine 工具 | MediaCrawlerDB(462行)/KeywordOptimizer(297行)/Sentiment(703行) | stub 级别(~90行) | ~10% |
| ForumEngine 监控 | monitor.py(858行) 完整日志监控 | ForumTranscriptService(100行) | ~12% |
| Web UI | Flask 仪表盘(1069行) + Streamlit 单引擎应用(737行) | 仅 REST API | 0% |
| Docker/CI | Dockerfile + docker-compose + GitHub Actions | 无 | 0% |
| 测试覆盖 | 含报告清洗、论坛监控等专项测试 | 基础单元测试 | ~40% |

---

## 二、分阶段开发计划

### 第一阶段：核心渲染与报告生成（优先级最高）

报告引擎是系统的核心交付物，目标项目在此投入最大。

#### 1.1 Document IR 增强
- [ ] 扩展 `DocumentBlock` sealed class，补充缺失的 block 类型：
  - `ChartBlock`（图表数据 + 配置）
  - `MathBlock`（LaTeX 公式）
  - `ImageBlock`（图片引用）
  - `MetadataBlock`（元数据）
- [ ] 实现 `DocumentIrValidator`，参考目标 `ir/validator.py`
  - 结构完整性校验
  - 内容密度校验（最小字符数）
  - Block 类型合法性校验
- [ ] 实现 `DocumentIrSchema`，定义 IR 的 JSON Schema

#### 1.2 HTML 渲染器重写
- [ ] 重写 `HtmlRenderer.java`，对齐目标 `html_renderer.py`(6536行) 的能力：
  - 完整的 CSS 样式系统（响应式布局、打印样式）
  - 目录生成（TOC）
  - 图表渲染（集成 Chart.js 或 ECharts）
  - 数学公式渲染（集成 MathJax 或 KaTeX）
  - 表格高级渲染（合并单元格、排序、样式）
  - 代码高亮（集成 Prism.js 或 highlight.js）
  - 引用和脚注系统
  - 中文排版优化
- [ ] 引入前端 JS 库资源（Chart.js、MathJax 等）
- [ ] 引入中文字体资源

#### 1.3 PDF 渲染器（新增）
- [ ] 新建 `PdfRenderer.java`，参考目标 `pdf_renderer.py`(1609行)
  - 基于 OpenPDF（已有依赖）实现 PDF 生成
  - 页面布局优化（页眉页脚、页码、分页控制）
  - 图表嵌入（SVG → PDF）
  - 中文字体支持
- [ ] 新建 `PdfLayoutOptimizer.java`，参考目标 `pdf_layout_optimizer.py`(1410行)
  - 智能分页算法
  - 图表/表格跨页处理
  - 孤行寡行控制

#### 1.4 Markdown 渲染器（新增）
- [ ] 新建 `MarkdownRenderer.java`，参考目标 `markdown_renderer.py`(994行)
  - Document IR → Markdown 转换
  - 表格 Markdown 格式化
  - 图表占位符处理

#### 1.5 图表处理（新增）
- [ ] 新建 `ChartToSvgConverter.java`，参考目标 `chart_to_svg.py`(1214行)
  - 图表数据 → SVG 渲染
  - 支持柱状图、折线图、饼图、雷达图等
- [ ] 新建 `MathToSvgConverter.java`，参考目标 `math_to_svg.py`(223行)
- [ ] 新建 `ChartValidator.java`，参考目标 `chart_validator.py`(730行)
- [ ] 新建 `ChartReviewService.java`，参考目标 `chart_review_service.py`(631行)
- [ ] 新建 `ChartRepairService.java`，参考目标 `chart_repair_api.py`(630行)
- [ ] 新建 `TableValidator.java`，参考目标 `table_validator.py`(516行)

#### 1.6 报告流水线节点（新增）
- [ ] 新建 `TemplateSelectionNode.java` — 根据分析类型选择报告模板
- [ ] 新建 `DocumentLayoutNode.java` — 文档布局规划
- [ ] 新建 `WordBudgetNode.java` — 各章节字数预算分配
- [ ] 新建 `ChapterStorage.java` — 章节持久化与缓存
- [ ] 新建 `ReportStitcher.java` — 多章节拼接为完整文档
- [ ] 新建 `TemplateParser.java` — 报告模板解析
- [ ] 扩展 `ReportPrompts.java`（当前38行 → 目标~500行）

---

### 第二阶段：Prompt 工程与引擎逻辑完善

各引擎的 Prompt 是系统的"灵魂"，目标项目的 Prompt 包含了完整的领域逻辑和输出格式约束。

#### 2.1 QueryEngine Prompt 扩展
- [ ] 扩展 `QueryPrompts.java`（当前172行 → 目标~446行）
  - 补充段落规划的详细指令（主题分解、角度覆盖）
  - 补充搜索决策的评估标准（信息充分性、来源多样性）
  - 补充反思环节的深度分析指令
  - 补充报告格式化的结构化输出约束
  - 补充 Forum Guidance 集成指令

#### 2.2 MediaEngine Prompt 扩展
- [ ] 扩展 `MediaPrompts.java`（当前166行 → 目标~450行）
  - 补充多模态内容分析指令
  - 补充媒体来源可信度评估
  - 补充视觉内容描述与引用格式
  - 补充结构化叙事输出的详细约束

#### 2.3 InsightEngine Prompt 扩展
- [ ] 扩展 `InsightPrompts.java`（当前207行 → 目标~626行）
  - 补充数据库查询策略指令
  - 补充情感分析结果解读指令
  - 补充关键词优化的上下文指令
  - 补充跨平台数据整合分析指令

#### 2.4 ForumEngine Prompt 扩展
- [ ] 扩展 `ForumPrompts.java`（当前37行 → 目标~262行）
  - 补充主持人角色定义与行为约束
  - 补充辩论引导策略
  - 补充共识提取与分歧标注指令
  - 补充证据质量评估标准

#### 2.5 ReportEngine Prompt 扩展
- [ ] 扩展 `ReportPrompts.java`（当前38行 → 目标~515行）
  - 补充章节生成的详细格式约束
  - 补充图表数据提取与生成指令
  - 补充引用和来源标注规范
  - 补充中文学术写作风格指令

---

### 第三阶段：MindSpider 爬虫系统实现

目标项目的爬虫系统分为"广度话题提取"和"深度情感爬取"两个子系统。

#### 3.1 广度话题提取（BroadTopicExtraction）
- [ ] 新建 `NewsCollector.java` — 新闻采集服务
  - 多来源新闻聚合
  - 去重与过滤
- [ ] 新建 `TopicExtractor.java` — 话题提取
  - LLM 驱动的话题识别
  - 话题聚类与分类
- [ ] 新建 `TopicDatabaseManager.java` — 话题数据库管理
  - 话题存储与更新
  - 话题热度追踪

#### 3.2 深度情感爬取（DeepSentimentCrawling）
- [ ] 新建 `KeywordManager.java` — 关键词管理
  - 关键词生成与优化
  - 关键词轮换策略
- [ ] 实现 `BilibiliCrawler.java`（当前空壳）
  - B站视频/评论爬取
  - 弹幕数据采集
  - 反爬策略
- [ ] 实现 `WeiboCrawler.java`（当前空壳）
  - 微博帖子/评论爬取
  - 话题热搜追踪
- [ ] 实现 `XiaohongshuCrawler.java`（当前空壳）
  - 小红书笔记/评论爬取
- [ ] 考虑新增平台爬虫：
  - `DouyinCrawler.java`（抖音）
  - `KuaishouCrawler.java`（快手）

#### 3.3 数据库模型
- [ ] 新建爬虫数据 Schema/Entity 类：
  - `BilibiliContent.java`
  - `WeiboContent.java`
  - `XiaohongshuContent.java`
  - `DouyinContent.java`（可选）
  - `KuaishouContent.java`（可选）
- [ ] 配置 JPA/MyBatis 数据访问层

---

### 第四阶段：InsightEngine 工具完善

#### 4.1 MediaCrawlerDB 工具实现
- [ ] 重写 `MediaCrawlerDbTool.java`（当前24行 → 目标~462行）
  - 实现多平台数据库查询（热门内容、话题搜索、评论获取）
  - 支持按日期范围、平台、关键词过滤
  - 结果数量限制与分页
  - 内容长度截断（防止 LLM 上下文溢出）
  - 查询超时控制

#### 4.2 KeywordOptimizer 增强
- [ ] 扩展 `KeywordOptimizer.java`（当前46行 → 目标~297行）
  - 多轮关键词优化
  - 同义词扩展
  - 平台特定关键词适配
  - 搜索结果反馈驱动的关键词调整

#### 4.3 情感分析增强
- [ ] 升级 `OnnxSentimentAnalyzer.java`（当前关键词匹配 → ONNX 模型推理）
  - 集成真实的 ONNX 情感分析模型
  - 支持中文文本的细粒度情感分析
  - 置信度校准
  - 批量分析支持

---

### 第五阶段：ForumEngine 监控与增强

#### 5.1 论坛日志监控
- [ ] 新建 `ForumMonitor.java`，参考目标 `monitor.py`(858行)
  - 实时日志解析与结构化
  - Agent 发言统计
  - 辩论质量评估指标
  - 异常检测（Agent 卡死、循环发言等）

#### 5.2 ForumTranscriptService 增强
- [ ] 扩展 `ForumTranscriptService.java`（当前100行）
  - 完整的对话历史管理
  - Guidance 历史追踪与版本化
  - 辩论轮次控制
  - 共识收敛检测

---

### 第六阶段：Web UI 与可视化

目标项目提供了 Flask Web 仪表盘和 Streamlit 单引擎调试应用。

#### 6.1 Web 仪表盘
- [ ] 选择前端技术栈（建议：React/Vue + Vite，或 Thymeleaf 服务端渲染）
- [ ] 实现分析任务管理页面
  - 创建新分析任务
  - 任务列表与状态展示
  - 实时进度追踪（WebSocket 集成）
- [ ] 实现分析结果展示页面
  - 报告在线预览
  - 图表交互展示
  - PDF 下载
- [ ] 实现 Agent 辩论实时展示
  - 论坛对话流式展示
  - Agent 角色标识
  - Guidance 可视化

#### 6.2 单引擎调试应用（可选）
- [ ] 提供各引擎的独立调试入口
  - QueryEngine 独立测试
  - MediaEngine 独立测试
  - InsightEngine 独立测试

---

### 第七阶段：DevOps 与部署

#### 7.1 容器化
- [ ] 编写 `Dockerfile`（多阶段构建，基于 Eclipse Temurin JDK 21）
- [ ] 编写 `docker-compose.yml`
  - 主应用服务
  - Sentiment MCP 服务
  - PostgreSQL 数据库
  - 可选：Redis 缓存

#### 7.2 CI/CD
- [ ] 编写 GitHub Actions workflow
  - 代码编译与测试
  - Docker 镜像构建与推送
  - 代码质量检查（Checkstyle/SpotBugs）

#### 7.3 配置管理
- [ ] 补充 Anspire 搜索配置（`AnspireProperties.java`）
- [ ] 补充 InsightEngine 搜索限制参数配置
- [ ] 补充数据库连接池配置（HikariCP）
- [ ] 环境变量与 Profile 管理（dev/staging/prod）

---

### 第八阶段：测试补全

#### 8.1 报告引擎测试
- [ ] HTML 渲染器全面测试（各 Block 类型渲染、CSS 样式、中文排版）
- [ ] PDF 渲染器测试（布局、分页、字体）
- [ ] 图表验证器测试
- [ ] 报告清洗/消毒测试，参考目标 `test_report_engine_sanitization.py`

#### 8.2 论坛引擎测试
- [ ] 论坛监控测试，参考目标 `test_monitor.py`(340行)
- [ ] 辩论流程集成测试

#### 8.3 爬虫测试
- [ ] 各平台爬虫单元测试
- [ ] 爬虫调度集成测试

#### 8.4 端到端测试
- [ ] 完整分析流程 E2E 测试（创建任务 → 引擎分析 → 论坛辩论 → 报告生成）

---

## 三、工作量估算

| 阶段 | 预估新增代码量 | 复杂度 |
|------|-------------|--------|
| 第一阶段：报告渲染 | ~8000-12000 行 | 极高 |
| 第二阶段：Prompt 工程 | ~2000-3000 行 | 中 |
| 第三阶段：MindSpider | ~3000-4000 行 | 高 |
| 第四阶段：InsightEngine | ~1500-2000 行 | 中 |
| 第五阶段：ForumEngine | ~1000-1500 行 | 中 |
| 第六阶段：Web UI | ~3000-5000 行 | 高 |
| 第七阶段：DevOps | ~500-800 行 | 低 |
| 第八阶段：测试补全 | ~2000-3000 行 | 中 |
| **合计** | **~21000-31300 行** | — |

---

## 四、建议开发顺序

```
第二阶段(Prompt) ──→ 第一阶段(报告渲染) ──→ 第八阶段(测试)
      │                                          ↑
      ├──→ 第四阶段(Insight工具) ────────────────┤
      │                                          │
      ├──→ 第五阶段(Forum增强) ─────────────────┤
      │                                          │
      └──→ 第三阶段(MindSpider) ────────────────┘
                                                  │
                              第六阶段(Web UI) ───┘
                                    │
                              第七阶段(DevOps) ───→ 完成
```

**推荐优先级：** Prompt 工程 > 报告渲染 > Insight 工具 > MindSpider > Forum 增强 > Web UI > DevOps > 测试补全

> Prompt 放在最前面是因为它决定了各引擎的分析质量，且改动成本最低、收益最高。
> 报告渲染是用户可见的核心交付物，直接影响产品价值。
> 测试贯穿各阶段，建议每完成一个模块就补充对应测试。
