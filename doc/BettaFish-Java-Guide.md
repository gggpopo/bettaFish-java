# BettaFish (微舆) Java 实现完整指南

> 基于 Spring AI 框架的多 Agent 舆情分析系统 Java 实现详细文档

---

# BettaFish（微舆）多智能体舆情分析系统 —— Java/Spring AI 全实现指南

# 第一部分：项目总览与基础架构

---

# 第一章 项目概述与架构设计

## 1.1 BettaFish 项目简介

BettaFish（微舆）是一套面向中文互联网舆情的**多智能体协作分析系统**。它的核心设计思想是：将一个复杂的舆情分析任务拆解为若干专业分工明确的 AI Agent，每个 Agent 各自调用专属工具（外部搜索 API、本地数据库、情感分析模型等）独立完成深度研究，最后通过一个"论坛式"讨论机制让 Agent 互相审阅、质疑、补充，再由报告引擎将所有成果融合为一份结构完整、数据详实的 HTML 交互式报告。

整个系统在 Python 原版中由以下六大组件构成：

| 组件 | 名称 | 核心职责 | 驱动模型（推荐） |
|------|------|----------|-------------------|
| **QueryEngine** | 深度新闻搜索引擎 | 利用 Tavily API 搜索全网新闻，逐段落进行搜索-反思-精炼循环，生成事实驱动的新闻分析报告 | DeepSeek (`deepseek-chat`) |
| **MediaEngine** | 多模态媒体分析引擎 | 通过 Bocha/Anspire API 进行多模态搜索（网页、图片、AI 摘要、结构化数据卡片），生成融合分析报告 | Gemini (`gemini-2.5-pro` via AiHubMix) |
| **InsightEngine** | 社交舆情洞察引擎 | 查询本地 MySQL/PostgreSQL 数据库中 7 大社交平台的爬取数据，结合 22 语种情感分析模型，生成公众情绪分析报告 | Kimi (`kimi-k2-0711-preview` via Moonshot) |
| **ForumEngine** | 论坛协调引擎 | 充当"主持人"角色，阅读三个分析引擎的发言日志，引导多轮讨论，梳理事件脉络、整合观点分歧、预测趋势 | Qwen (`qwen3-235b` via SiliconFlow) |
| **ReportEngine** | 报告生成引擎 | 接收三引擎报告 + 论坛讨论记录，选择报告模板，规划文档布局与字数预算，逐章生成 IR JSON，渲染为交互式 HTML | Gemini (`gemini-2.5-pro` via AiHubMix) |
| **MindSpider** | 社交媒体爬虫 | 基于每日热点新闻提取话题关键词，调度 7 平台爬虫（B站/抖音/快手/微博/小红书/知乎/贴吧），将内容写入数据库供 InsightEngine 查询 | DeepSeek（关键词优化用） |

此外，还有一个独立的 **KeywordOptimizer** 组件（由 InsightEngine 内部调用），使用 Qwen 模型将用户原始查询扩展为多个面向不同平台话语风格的搜索关键词。

### 1.1.1 数据库规模

系统的数据库层包含 **26 张数据表**，分布在三个类别中：

1. **核心调度表（4 张）**：`daily_news`（每日热点新闻）、`daily_topics`（提取的话题）、`topic_news_relation`（话题-新闻多对多关联）、`crawling_tasks`（爬虫任务调度）
2. **社交媒体大数据表（21 张）**：覆盖 7 个平台，每个平台有内容表（帖子/视频/笔记）、评论表、用户信息表
3. **视图（2 个）**：`v_topic_crawling_stats`（话题爬取统计）、`v_daily_summary`（每日汇总）

平台数据表详细分布如下：

| 平台 | 内容表 | 评论表 | 用户表 | 附加表 |
|------|--------|--------|--------|--------|
| Bilibili | `bilibili_video` | `bilibili_video_comment` | `bilibili_up_info` | `bilibili_contact_info`, `bilibili_up_dynamic` |
| 抖音 | `douyin_aweme` | `douyin_aweme_comment` | `dy_creator` | — |
| 快手 | `kuaishou_video` | `kuaishou_video_comment` | — | — |
| 微博 | `weibo_note` | `weibo_note_comment` | `weibo_creator` | — |
| 小红书 | `xhs_note` | `xhs_note_comment` | `xhs_creator` | — |
| 知乎 | `zhihu_content` | `zhihu_comment` | `zhihu_creator` | — |
| 贴吧 | `tieba_note` | `tieba_comment` | `tieba_creator` | — |

### 1.1.2 工具生态

三大分析引擎各自拥有不同的工具集：

**QueryEngine — 6 个 Tavily 新闻搜索工具：**
- `basic_search_news` — 通用快速搜索（`search_depth="basic"`，7 条结果）
- `deep_search_news` — 深度分析搜索（`search_depth="advanced"`，20 条结果，含 AI 摘要）
- `search_news_last_24_hours` — 24 小时内突发新闻（`time_range='d'`，10 条）
- `search_news_last_week` — 一周内新闻趋势（`time_range='w'`，10 条）
- `search_images_for_news` — 图片搜索（含图片描述，5 条）
- `search_news_by_date` — 按日期范围历史搜索（需 `start_date`、`end_date`，15 条）

**MediaEngine — 5 个 Bocha 多模态搜索工具（或 3 个 Anspire 工具）：**
- `comprehensive_search` — 全量搜索（网页+图片+AI摘要+结构化卡片，10 条）
- `web_search_only` — 纯网页搜索（更快更便宜，15 条）
- `search_for_structured_data` — 结构化数据查询（天气/股票/汇率/百科，5 条+卡片）
- `search_last_24_hours` — 24 小时内内容（`freshness='oneDay'`）
- `search_last_week` — 一周内内容（`freshness='oneWeek'`）

**InsightEngine — 6 个本地数据库+情感分析工具：**
- `search_hot_content` — 按真实互动数据（点赞/评论/转发）排序的热门内容
- `search_topic_globally` — 跨 7 平台 15 张表全局话题搜索
- `search_topic_by_date` — 按日期范围追踪话题演变
- `get_comments_for_topic` — 深度挖掘用户评论真实态度
- `search_topic_on_platform` — 单平台定向搜索（需指定 `platform` 参数）
- `analyze_sentiment` — 独立情感分析（22 语种，5 级分类）

---

## 1.2 系统架构全景图

下图展示了 BettaFish 系统从用户输入到最终报告的完整数据流：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          用户查询 (User Query)                              │
│                     "分析武汉大学樱花季舆情热度"                              │
└─────────────────────┬───────────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application (bettafish-app)                   │
│                                                                             │
│   WebSocket/REST API → AnalysisCoordinator → 并发启动三大引擎                │
└──────┬──────────────────────┬───────────────────────┬───────────────────────┘
       │                      │                       │
       ▼                      ▼                       ▼
┌──────────────┐   ┌───────────────────┐   ┌──────────────────┐
│ QueryEngine  │   │  MediaEngine      │   │  InsightEngine   │
│              │   │                   │   │                  │
│ LLM:DeepSeek│   │ LLM:Gemini        │   │ LLM:Kimi         │
│ 工具:Tavily  │   │ 工具:Bocha/Anspire│   │ 工具:本地DB       │
│ (6个搜索工具)│   │ (5个多模态工具)    │   │ (6个查询+情感工具)│
│              │   │                   │   │                  │
│ ┌──────────┐ │   │ ┌──────────┐      │   │ ┌──────────────┐ │
│ │Search-   │ │   │ │Search-   │      │   │ │Search-       │ │
│ │Reflect   │ │   │ │Reflect   │      │   │ │Reflect Loop  │ │
│ │Loop ×3   │ │   │ │Loop ×3   │      │   │ │×3 +情感分析  │ │
│ └──────────┘ │   │ └──────────┘      │   │ │+关键词优化   │ │
│              │   │                   │   │ │+聚类去重     │ │
└──────┬───────┘   └────────┬──────────┘   │ └──────────────┘ │
       │                    │              └────────┬─────────┘
       │                    │                       │
       ▼                    ▼                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   ForumEngine (论坛协调引擎)                  │
│                                                             │
│   LLM: Qwen3-235B (via SiliconFlow, temp=0.6)              │
│                                                             │
│   Python 原版: 文件日志轮询 (forum_reader.py)                │
│   Java  版本: Spring ApplicationEvent + @EventListener      │
│                                                             │
│   ┌─────────────────────────────────────────────────────┐   │
│   │  第 1 轮: 三引擎各提交首次分析结果                     │   │
│   │       → ForumHost 梳理事件脉络、整合初步观点           │   │
│   │  第 2 轮: 三引擎继续深化（反思搜索完成后的补充发言）    │   │
│   │       → ForumHost 纠正错误、对比分歧                   │   │
│   │  第 3 轮: 最终发言                                     │   │
│   │       → ForumHost 趋势预测、综合结论                   │   │
│   └─────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  ReportEngine (报告生成引擎)                  │
│                                                             │
│   LLM: Gemini 2.5 Pro (via AiHubMix)                       │
│                                                             │
│   输入: Query报告 + Media报告 + Insight报告 + Forum讨论记录  │
│                                                             │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ 1. 模板选择 (6 种报告模板)                            │   │
│   │ 2. 文档布局设计 (标题/副标题/Hero/TOC)                │   │
│   │ 3. 字数预算规划 (~40,000 字, 按章节分配)              │   │
│   │ 4. 逐章 IR JSON 生成 (14 种 Block 类型)               │   │
│   │ 5. JSON 验证与修复                                    │   │
│   │ 6. HTML 渲染 (Chart.js 图表 + 暗色模式 + 打印导出)    │   │
│   └─────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│           最终输出: 交互式 HTML 舆情分析报告                   │
│           (30,000-40,000 字, 含图表/TOC/暗色模式)             │
└─────────────────────────────────────────────────────────────┘

旁路:
┌──────────────────┐
│   MindSpider     │
│ (社交媒体爬虫)    │
│                  │
│ 每日热点新闻 →   │
│ LLM话题提取 →    │
│ 关键词生成 →     │
│ 7 平台爬虫调度 → │
│ 数据入库 26 表   │
│                  │
│ 为 InsightEngine │
│ 提供数据源       │
└──────────────────┘
```

---

## 1.3 Search-Reflect Loop 详解

**Search-Reflect Loop（搜索-反思循环）** 是三大分析引擎共享的核心工作流范式。这个设计受到学术研究中 "Iterative Retrieval-Augmented Generation" 思想的启发——单轮搜索往往无法覆盖一个复杂话题的全部维度，因此系统让 LLM 在每次搜索后进行反思，识别信息缺口，再发起针对性的补充搜索，反复迭代直到信息密度达到专业标准。

### 1.3.1 完整流程图

```
用户查询: "分析武汉大学樱花季舆情热度"
    │
    ▼
╔═══════════════════════════════════════════════════════════════╗
║  Stage 1: Report Structure Planning (报告结构规划)            ║
║                                                               ║
║  LLM 输入: 用户查询                                           ║
║  LLM 输出: JSON 数组, 最多 5 个段落的 {title, content}        ║
║                                                               ║
║  示例输出:                                                    ║
║  [                                                            ║
║    {"title": "武大樱花季事件概述与时间线",                      ║
║     "content": "梳理2025年武大樱花季的关键事件..."},            ║
║    {"title": "社交媒体传播热度分析",                           ║
║     "content": "分析各平台讨论量、传播路径..."},               ║
║    {"title": "公众情绪与观点分布",                             ║
║     "content": "挖掘正面/负面/中性情绪比例..."},               ║
║    {"title": "跨平台差异与群体画像",                           ║
║     "content": "对比B站/微博/小红书用户特征..."},              ║
║    {"title": "舆情走势预测与风险评估",                         ║
║     "content": "基于历史数据预测未来发展趋势..."}              ║
║  ]                                                            ║
╚════════════════════════════════════╤══════════════════════════╝
                                     │
    ┌────────────────────────────────┘
    │
    │  ╔══════════════════════════════════════════════════╗
    │  ║  对每个段落 (paragraph) 执行以下子循环:           ║
    │  ╚══════════════════════════════════════════════════╝
    │
    ▼
╔═══════════════════════════════════════════════════════════════╗
║  Stage 2: First Search (首次搜索)                             ║
║                                                               ║
║  LLM 输入: 段落标题 + 期望内容描述 + 可用工具列表              ║
║  LLM 输出: {search_query, search_tool, reasoning,            ║
║             [start_date, end_date]}                           ║
║                                                               ║
║  → Agent 调用选定的搜索工具执行查询                            ║
║  → 获取搜索结果 (TavilyResponse / BochaResponse / DBResponse) ║
╚════════════════════════════════════╤══════════════════════════╝
                                     │
                                     ▼
╔═══════════════════════════════════════════════════════════════╗
║  Stage 3: First Summary (首次总结)                            ║
║                                                               ║
║  LLM 输入: 段落标题 + 搜索结果                                ║
║  LLM 输出: {paragraph_latest_state} — 800-1200 字的结构化分析  ║
║                                                               ║
║  要求:                                                        ║
║  - 核心事件概述 + 多源报道分析 + 关键数据提取                  ║
║  - 深层背景分析 + 发展趋势评估                                ║
║  - 每 100 字至少包含 2-3 个具体数据点                         ║
╚════════════════════════════════════╤══════════════════════════╝
                                     │
                                     ▼
╔═══════════════════════════════════════════════════════════════╗
║  Stage 4-5: Reflection Loop (反思循环) × MAX_REFLECTIONS (=3) ║
║                                                               ║
║  ┌─────────────────────────────────────────────────────────┐  ║
║  │  Reflection (反思):                                      │  ║
║  │  LLM 输入: 当前段落最新状态 + 工具列表                    │  ║
║  │  LLM 输出: {search_query, search_tool, reasoning}        │  ║
║  │  → 识别信息缺口, 选择新工具, 构造补充查询                 │  ║
║  │                                                          │  ║
║  │  执行搜索工具 → 获取新搜索结果                            │  ║
║  │                                                          │  ║
║  │  Reflection Summary (反思总结):                           │  ║
║  │  LLM 输入: 当前段落状态 + 新搜索结果                      │  ║
║  │  LLM 输出: {updated_paragraph_latest_state}              │  ║
║  │  → 将新发现融入现有内容, 不删除已有关键信息               │  ║
║  └─────────────────────────────────────────────────────────┘  ║
║                                                               ║
║  ★ InsightEngine 特殊处理:                                    ║
║    - 每次搜索前进行关键词优化 (KeywordOptimizer, Qwen模型)    ║
║    - 搜索后自动附加情感分析结果                               ║
║    - 结果经过聚类采样和去重                                   ║
║                                                               ║
║  ★ ForumEngine 注入点:                                        ║
║    - 每轮反思完成后, Agent 将最新发言写入论坛日志              ║
║    - ForumHost 读取所有发言, 生成主持人评论                   ║
║    - 主持人评论反馈给各 Agent, 影响下一轮反思方向             ║
╚════════════════════════════════════╤══════════════════════════╝
                                     │
                                     ▼
╔═══════════════════════════════════════════════════════════════╗
║  Stage 6: Report Formatting (报告格式化)                      ║
║                                                               ║
║  LLM 输入: 所有段落的最终状态                                  ║
║  LLM 输出: 完整 Markdown 格式报告 (≥10,000 字)                ║
║                                                               ║
║  三引擎输出格式差异:                                          ║
║  - QueryEngine: 新闻调查报告 (事件时间线+多源对比+事实核查)   ║
║  - MediaEngine: 多维融合报告 (文本+视觉+数据交叉验证)        ║
║  - InsightEngine: 舆情洞察报告 (情感分布+用户原声+群体画像)   ║
╚═══════════════════════════════════════════════════════════════╝
```

### 1.3.2 ForumEngine 的协调机制

ForumEngine 在整个分析流程中扮演着"润滑剂"和"质量控制员"的双重角色。在 Python 原版中，协调通过**文件日志**实现：

```
Python 原版的协调方式:
1. 每个 Agent 通过 subprocess 独立运行
2. Agent 将发言写入共享的文本日志文件 (forum_logs.txt)
3. ForumHost 通过 forum_reader.py 定期轮询日志文件
4. 读取新增发言后, ForumHost 调用 Qwen API 生成主持人回复
5. 主持人回复也写入同一日志文件
6. 其他 Agent 在下一轮反思前读取日志中的新发言
```

在 Java/Spring AI 版本中，这一机制被优雅地替换为**事件驱动架构**：

```
Java 版本的协调方式:
1. 每个 Agent 作为 Spring Bean, 通过 @Async 并发执行
2. Agent 发言通过 ApplicationEventPublisher.publishEvent(AgentSpeechEvent)
3. ForumCoordinator 通过 @EventListener 监听 AgentSpeechEvent
4. 收集到足够发言后, ForumHost 调用 Qwen API 生成评论
5. 主持人评论通过 HostCommentEvent 发布
6. 各 Agent 的 ReflectionNode 通过 @EventListener 接收主持人反馈
7. WebSocket 实时推送进度给前端
```

ForumHost 的四大职责：
1. **事件梳理** — 从各 Agent 发言中识别关键事件、人物、时间节点，按时间顺序整理脉络
2. **观点整合** — 综合 INSIGHT/MEDIA/QUERY 三方视角，找出共识与分歧
3. **错误纠正** — 发现事实错误或逻辑矛盾时明确指出
4. **趋势预测** — 分析舆情发展趋势，提出风险点和后续讨论方向

---

## 1.4 Python → Java 技术映射总表

下表给出了从 Python 原版到 Java/Spring AI 版本的全面技术对应关系：

| 维度 | Python 原版 | Java/Spring AI 版 | 说明 |
|------|-------------|-------------------|------|
| **Web 框架** | Flask + Flask-SocketIO | Spring Boot 3.3+ + WebSocket (STOMP) | Spring Boot 提供完整的企业级 Web 支持 |
| **LLM 调用** | `openai` Python SDK | Spring AI `ChatClient` | 统一接口，支持多 Provider |
| **Agent 框架** | 从零实现（无框架） | Spring AI Agentic Patterns + 自定义 | 利用 Spring AI 的 advisor 链 |
| **工具调用** | 手动 HTTP 请求 (`requests`) | Spring AI `@Tool` + MCP Client | 声明式工具定义 |
| **进程协作** | `subprocess` + 文件日志轮询 | Spring `ApplicationEvent` + `@Async` | 事件驱动替代文件 I/O |
| **数据库** | SQLAlchemy async | Spring Data JPA / Hibernate | 标准 ORM 映射 |
| **数据库驱动** | `asyncmy` / `asyncpg` | MySQL Connector/J / PostgreSQL JDBC | JDBC 标准驱动 |
| **情感分析** | 本地 PyTorch Transformers 模型 | ONNX Runtime / 独立 MCP Server | 模型推理独立部署 |
| **爬虫** | Playwright + 自研 MediaCrawler | Playwright4J / Jsoup + Spring Batch | Java 爬虫技术栈 |
| **报告渲染** | 自研 IR JSON → HTML/PDF | Thymeleaf / FreeMarker + OpenPDF | Java 模板引擎 |
| **配置管理** | Pydantic `BaseSettings` + `.env` | `@ConfigurationProperties` + `application.yml` | Spring 标准配置体系 |
| **实时通信** | Flask-SocketIO | Spring WebSocket + `SimpMessagingTemplate` | STOMP 协议 |
| **重试机制** | `@with_graceful_retry` 装饰器 | Spring Retry `@Retryable` | 声明式重试 |
| **JSON 解析** | Python `json` / Pydantic | Jackson `ObjectMapper` | Java 标准 JSON 库 |
| **HTTP 客户端** | `requests` / `httpx` | Spring `RestClient` / `WebClient` | 响应式/同步可选 |
| **日志** | Python `logging` | SLF4J + Logback | Java 标准日志栈 |
| **环境变量** | `python-dotenv` + `os.environ` | Spring Boot `application.yml` + `${ENV_VAR}` | 统一配置注入 |
| **异步** | `asyncio` + `async/await` | `@Async` + `CompletableFuture` | Java 异步模型 |
| **类型系统** | Python Type Hints + Pydantic | Java Record / DTO + Validation | 编译期类型安全 |
| **依赖注入** | 全局变量 / 函数参数传递 | Spring IoC `@Autowired` / 构造器注入 | 完整 DI 容器 |
| **LLM 提供商** | 通过 OpenAI SDK 兼容接口 | Spring AI OpenAI 兼容 Provider | 同一 API 协议 |
| **搜索工具 - Query** | `TavilyClient` (tavily-python) | Spring AI MCP Client + Tavily MCP Server | MCP 协议封装 |
| **搜索工具 - Media** | `requests.post` (Bocha/Anspire API) | `@Tool` 注解方法 + `RestClient` | 声明式工具 |
| **搜索工具 - Insight** | SQLAlchemy 原生 SQL (async) | Spring Data JPA `@Query` + Native SQL | Repository 模式 |
| **情感分析模型** | `tabularisai/multilingual-sentiment-analysis` | ONNX 导出 + ONNX Runtime Java | 跨平台推理 |
| **关键词优化** | 独立 Qwen API 调用 | 独立 `ChatClient` Bean (`@Qualifier`) | Spring Bean 隔离 |
| **论坛日志** | 文本文件读写 (`forum_logs.txt`) | `ApplicationEvent` / Redis Pub/Sub | 内存事件 + 可选持久化 |
| **进度通知** | SocketIO `emit()` | `SimpMessagingTemplate.convertAndSend()` | WebSocket 推送 |
| **聚类算法** | `SentenceTransformer` + scikit-learn KMeans | DJL (Deep Java Library) + Smile KMeans | Java ML 库 |
| **项目结构** | 平铺目录 | Maven 多模块 | 企业级模块化 |
| **部署** | 直接 `python app.py` | Spring Boot 可执行 JAR / Docker | 标准化部署 |
| **测试** | pytest | JUnit 5 + Mockito + Spring Boot Test | 完整测试支持 |

### 1.4.1 关键架构差异详解

**差异 1：进程模型**

Python 原版中，每个 Engine 通过 `subprocess` 以独立进程方式运行，Engine 之间通过文件系统（论坛日志文件）进行通信。这种设计简单直观，但存在文件 I/O 竞争和轮询延迟的问题。

Java 版本采用 Spring 的 `@Async` 异步方法在同一 JVM 进程内并发执行各 Agent。Agent 之间通过 Spring 的 `ApplicationEvent` 进行通信，这是一种类型安全的、零延迟的内存事件机制。如果需要跨服务部署，可以用 Redis Pub/Sub 或 Spring Cloud Stream 替代。

**差异 2：LLM 调用抽象**

Python 原版直接使用 `openai` SDK 创建 client，每个 Engine 各自管理自己的 API key、base URL 和 model name。7 个不同的 LLM 配置散布在各模块代码中。

Java 版本通过 `LlmAutoConfiguration` 集中管理所有 7 个 `ChatClient` Bean，每个 Bean 通过 `@Qualifier` 注解区分。Agent 只需在构造函数中声明依赖，Spring 自动注入正确的 `ChatClient`。这样做的好处是：配置集中化、易于测试（可用 mock 替换）、支持运行时切换模型。

**差异 3：工具注册机制**

Python 原版中，工具调用是通过 LLM 输出 JSON（指定 `search_tool` 名称和参数），Agent 代码中用 if-else 或字典映射分发到对应方法。这本质上是一种手动的函数调度。

Java 版本中，可以利用 Spring AI 的 `@Tool` 注解将 Java 方法直接注册为 LLM 可调用的工具。`ChatClient` 在调用时会自动将工具描述发送给 LLM，LLM 返回的工具调用请求由 Spring AI 框架自动路由到对应的 Java 方法。此外，对于外部工具（如 Tavily），可以通过 Spring AI MCP Client 直接连接 MCP Server。

**差异 4：报告引擎的 IR 格式**

Python 原版的 ReportEngine 使用自研的 IR (Intermediate Representation) JSON 格式，包含 14 种 Block 类型（heading、paragraph、list、table、widget、callout、blockquote、hr、kpiGrid、code、math、swotTable、pestTable、engineQuote）。

Java 版本需要完整实现这套 IR 的 Java 数据模型（使用 sealed interface 或 record 类型），以及 IR → HTML 的渲染器（基于 Thymeleaf 或直接 StringBuilder 拼接）。

---

## 1.5 本指南的目标与范围

本指南旨在提供一份**极其详尽的、可直接编码实施的**从 Python 到 Java/Spring AI 的完整迁移方案。全指南分为多个章节，涵盖：

1. **第一章（本章）**：项目概述、架构全景、Search-Reflect Loop 详解、技术映射
2. **第二章**：Maven 多模块配置、Spring Boot 项目骨架、全局配置类、LLM 自动配置
3. **第三章**：公共模型与事件体系（AgentState、ForumMessage、事件定义）
4. **第四章**：工具层实现（Tavily MCP、Bocha/Anspire @Tool、InsightEngine DB 查询）
5. **第五章**：三大分析引擎的 Agent 实现（Search-Reflect Loop Java 版）
6. **第六章**：ForumEngine 事件驱动协调
7. **第七章**：ReportEngine 的 IR 生成与 HTML 渲染
8. **第八章**：MindSpider 爬虫调度
9. **第九章**：WebSocket 实时通信与前端集成
10. **第十章**：测试策略与部署

每一章都包含**完整的、可编译的 Java 源代码**，并附有详细的中文注释说明设计决策。

---

# 第二章 技术选型与项目配置

## 2.1 Maven 多模块项目结构

BettaFish Java 版采用 Maven 多模块结构，将系统拆分为 **9 个子模块**，每个模块职责清晰、依赖关系明确：

```
bettafish-java/
├── pom.xml                                    ← 父 POM (版本管理中心)
│
├── bettafish-common/                          ← 公共模块 (配置/模型/事件/工具)
│   ├── pom.xml
│   └── src/main/java/com/bettafish/common/
│       ├── config/
│       │   ├── BettaFishProperties.java       ← @ConfigurationProperties 全局配置
│       │   └── LlmAutoConfiguration.java      ← 7 个 ChatClient Bean 自动配置
│       ├── model/
│       │   ├── AgentState.java                ← Agent 状态机
│       │   ├── ParagraphState.java            ← 段落研究状态
│       │   ├── SearchDecision.java            ← LLM 搜索决策 DTO
│       │   ├── ForumMessage.java              ← 论坛消息
│       │   └── AnalysisResult.java            ← 分析结果
│       ├── event/
│       │   ├── AgentSpeechEvent.java          ← Agent 发言事件
│       │   ├── HostCommentEvent.java          ← 主持人评论事件
│       │   └── AnalysisCompleteEvent.java     ← 分析完成事件
│       └── util/
│           ├── RetryHelper.java               ← 重试工具
│           ├── JsonParser.java                ← JSON 安全解析
│           └── DateValidator.java             ← 日期格式校验
│
├── bettafish-query-engine/                    ← QueryEngine (Tavily + DeepSeek)
│   ├── pom.xml
│   └── src/main/java/com/bettafish/query/
│       ├── QueryAgent.java
│       ├── node/                              ← 6 个处理节点
│       ├── tool/                              ← Tavily MCP 工具封装
│       └── prompt/
│           └── QueryPrompts.java              ← 全部 Prompt 常量
│
├── bettafish-media-engine/                    ← MediaEngine (Bocha/Anspire + Gemini)
│   ├── pom.xml
│   └── src/main/java/com/bettafish/media/
│       ├── MediaAgent.java
│       ├── node/
│       ├── tool/                              ← @Tool 注解的搜索工具
│       └── prompt/
│           └── MediaPrompts.java
│
├── bettafish-insight-engine/                  ← InsightEngine (DB + Kimi)
│   ├── pom.xml
│   └── src/main/java/com/bettafish/insight/
│       ├── InsightAgent.java
│       ├── node/
│       ├── tool/
│       │   ├── MediaCrawlerDbTool.java        ← 6 个数据库查询工具
│       │   └── SentimentTool.java             ← 情感分析工具
│       ├── keyword/
│       │   └── KeywordOptimizer.java          ← 关键词优化
│       └── prompt/
│           └── InsightPrompts.java
│
├── bettafish-forum-engine/                    ← ForumEngine (Qwen + 事件协调)
│   ├── pom.xml
│   └── src/main/java/com/bettafish/forum/
│       ├── ForumCoordinator.java              ← 事件监听 + 协调逻辑
│       ├── ForumHost.java                     ← LLM 主持人
│       └── prompt/
│           └── ForumPrompts.java
│
├── bettafish-report-engine/                   ← ReportEngine (Gemini + IR + HTML)
│   ├── pom.xml
│   └── src/main/java/com/bettafish/report/
│       ├── ReportAgent.java
│       ├── template/                          ← 6 种报告模板
│       ├── ir/                                ← IR JSON 数据模型
│       ├── renderer/                          ← HTML 渲染器
│       └── prompt/
│           └── ReportPrompts.java
│
├── bettafish-mind-spider/                     ← MindSpider 爬虫服务
│   ├── pom.xml
│   └── src/main/java/com/bettafish/spider/
│       ├── CrawlerService.java
│       ├── scheduler/                         ← 爬虫任务调度
│       └── platform/                          ← 7 平台爬虫适配器
│
├── bettafish-sentiment-mcp/                   ← 情感分析 MCP Server
│   ├── pom.xml
│   └── src/main/java/com/bettafish/sentiment/
│       ├── SentimentMcpServer.java
│       └── OnnxSentimentAnalyzer.java
│
└── bettafish-app/                             ← 主启动模块 (Spring Boot Application)
    ├── pom.xml
    └── src/main/java/com/bettafish/app/
        ├── BettaFishApplication.java          ← @SpringBootApplication 启动类
        ├── controller/
        │   ├── AnalysisController.java        ← REST API
        │   └── WebSocketController.java       ← WebSocket 端点
        └── service/
            └── AnalysisCoordinator.java       ← 分析流程编排
```

---

## 2.2 核心依赖清单

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.3.6 | 基础框架 |
| Spring AI | 1.0.0 | LLM 调用、ChatClient、Tool、MCP |
| Spring AI OpenAI Starter | 1.0.0 | OpenAI 兼容 API（适用于所有 Provider） |
| Spring AI MCP Client | 1.0.0 | MCP 协议客户端（连接 Tavily MCP Server 等） |
| Spring Boot Starter Data JPA | 3.3.6 | 数据库 ORM |
| Spring Boot Starter WebSocket | 3.3.6 | WebSocket 支持 |
| Spring Boot Starter Validation | 3.3.6 | Bean Validation |
| Spring Retry | 2.0.5 | 声明式重试 |
| MySQL Connector/J | 8.3.0 | MySQL 驱动 |
| PostgreSQL JDBC | 42.7.3 | PostgreSQL 驱动 |
| HikariCP | 5.1.0 | 数据库连接池（Spring Boot 内置） |
| Jackson | 2.17.x | JSON 序列化/反序列化（Spring Boot 内置） |
| Lombok | 1.18.32 | 减少样板代码（可选） |
| ONNX Runtime | 1.17.0 | 情感分析模型推理 |
| OpenPDF | 2.0.2 | PDF 报告导出 |
| Chart.js | 4.x | 前端图表（嵌入 HTML 报告） |

---

## 2.3 父 POM 完整配置

以下是 `bettafish-java/pom.xml` 的完整内容：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- ========== 父级 Spring Boot Starter ========== -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.6</version>
        <relativePath/>
    </parent>

    <!-- ========== 项目坐标 ========== -->
    <groupId>com.bettafish</groupId>
    <artifactId>bettafish-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>BettaFish Parent</name>
    <description>微舆 - 多智能体舆情分析系统 (Java/Spring AI 版)</description>

    <!-- ========== 版本号集中管理 ========== -->
    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- Spring AI BOM 版本 -->
        <spring-ai.version>1.0.0</spring-ai.version>

        <!-- 数据库驱动 -->
        <mysql-connector.version>8.3.0</mysql-connector.version>
        <postgresql.version>42.7.3</postgresql.version>

        <!-- 工具库 -->
        <lombok.version>1.18.32</lombok.version>
        <onnxruntime.version>1.17.0</onnxruntime.version>
        <openpdf.version>2.0.2</openpdf.version>
        <spring-retry.version>2.0.5</spring-retry.version>
    </properties>

    <!-- ========== 子模块声明 ========== -->
    <modules>
        <module>bettafish-common</module>
        <module>bettafish-query-engine</module>
        <module>bettafish-media-engine</module>
        <module>bettafish-insight-engine</module>
        <module>bettafish-forum-engine</module>
        <module>bettafish-report-engine</module>
        <module>bettafish-mind-spider</module>
        <module>bettafish-sentiment-mcp</module>
        <module>bettafish-app</module>
    </modules>

    <!-- ========== BOM 依赖管理 ========== -->
    <dependencyManagement>
        <dependencies>
            <!-- Spring AI BOM — 统一管理 Spring AI 所有模块版本 -->
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- ========== 内部模块互相引用 ========== -->
            <dependency>
                <groupId>com.bettafish</groupId>
                <artifactId>bettafish-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bettafish</groupId>
                <artifactId>bettafish-query-engine</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bettafish</groupId>
                <artifactId>bettafish-media-engine</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bettafish</groupId>
                <artifactId>bettafish-insight-engine</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bettafish</groupId>
                <artifactId>bettafish-forum-engine</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bettafish</groupId>
                <artifactId>bettafish-report-engine</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bettafish</groupId>
                <artifactId>bettafish-mind-spider</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.bettafish</groupId>
                <artifactId>bettafish-sentiment-mcp</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- ========== 第三方库 ========== -->
            <dependency>
                <groupId>com.mysql</groupId>
                <artifactId>mysql-connector-j</artifactId>
                <version>${mysql-connector.version}</version>
            </dependency>
            <dependency>
                <groupId>org.postgresql</groupId>
                <artifactId>postgresql</artifactId>
                <version>${postgresql.version}</version>
            </dependency>
            <dependency>
                <groupId>com.microsoft.onnxruntime</groupId>
                <artifactId>onnxruntime</artifactId>
                <version>${onnxruntime.version}</version>
            </dependency>
            <dependency>
                <groupId>com.github.librepdf</groupId>
                <artifactId>openpdf</artifactId>
                <version>${openpdf.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.retry</groupId>
                <artifactId>spring-retry</artifactId>
                <version>${spring-retry.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <!-- ========== 所有子模块共享的依赖 ========== -->
    <dependencies>
        <!-- Lombok (全局可用, 编译期注解处理器) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- SLF4J + Logback (Spring Boot 内置, 显式声明用于 IDE 提示) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- ========== 构建配置 ========== -->
    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <configuration>
                        <excludes>
                            <exclude>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                            </exclude>
                        </excludes>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>

    <!-- ========== Spring AI 里程碑仓库 (如使用 Release 版可移除) ========== -->
    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>

</project>
```

---

## 2.4 全局配置属性类：BettaFishProperties.java

这是整个 Java 版本最核心的配置类，它将 Python 原版 `config.py` 中的 `Settings` 类完整映射到 Spring Boot 的 `@ConfigurationProperties` 体系中。每一个字段都对应 Python 原版的一个环境变量。

```java
package com.bettafish.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * BettaFish 全局配置属性.
 *
 * <p>映射自 Python 原版 config.py 的 Settings 类。所有字段通过
 * application.yml 中的 {@code bettafish.*} 前缀注入。</p>
 *
 * <p>Python 原版使用 pydantic-settings 的 BaseSettings，支持 .env 文件
 * 和环境变量自动加载。Java 版本使用 Spring Boot 的
 * {@code @ConfigurationProperties} 实现相同功能，同时支持
 * application.yml 文件配置和 ${ENV_VAR} 环境变量占位符。</p>
 *
 * <h3>配置层级结构：</h3>
 * <pre>
 * bettafish:
 *   server:          # Flask 服务器配置 → Spring Boot 服务器配置
 *   db:              # 数据库连接配置
 *   llm:
 *     insight:       # InsightEngine LLM (Moonshot/Kimi)
 *     media:         # MediaEngine LLM (AiHubMix/Gemini)
 *     query:         # QueryEngine LLM (DeepSeek)
 *     report:        # ReportEngine LLM (AiHubMix/Gemini)
 *     mindspider:    # MindSpider LLM (DeepSeek)
 *     forum-host:    # ForumEngine LLM (SiliconFlow/Qwen)
 *     keyword-optimizer: # 关键词优化 LLM (SiliconFlow/Qwen)
 *   search:          # 外部搜索工具配置
 *   insight-limits:  # InsightEngine 搜索限制参数
 * </pre>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "bettafish")
public class BettaFishProperties {

    // ==================== 服务器配置 ====================

    /**
     * 服务器配置.
     * <p>Python 原版: HOST="0.0.0.0", PORT=5000 (Flask)</p>
     * <p>Java 版: 使用 Spring Boot 标准 server.port，此处仅保留兼容字段。</p>
     */
    @Valid
    private Server server = new Server();

    // ==================== 数据库配置 ====================

    /**
     * 数据库连接配置.
     * <p>Python 原版: DB_DIALECT, DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME, DB_CHARSET</p>
     * <p>Java 版: 同时生成 JDBC URL 供 Spring Data JPA 使用。</p>
     */
    @Valid
    private Db db = new Db();

    // ==================== LLM 配置 ====================

    /**
     * 所有 LLM 提供商配置的容器.
     * <p>BettaFish 使用 7 个不同的 LLM 配置，每个 Agent 有独立的
     * API Key、Base URL 和 Model Name，以便灵活选择最适合的模型。</p>
     */
    @Valid
    private Llm llm = new Llm();

    // ==================== 搜索工具配置 ====================

    /**
     * 外部搜索 API 配置.
     * <p>Python 原版: TAVILY_API_KEY, SEARCH_TOOL_TYPE, BOCHA_*, ANSPIRE_*</p>
     */
    @Valid
    private Search search = new Search();

    // ==================== InsightEngine 搜索限制 ====================

    /**
     * InsightEngine 数据库查询的各项限制参数.
     * <p>Python 原版: DEFAULT_SEARCH_HOT_CONTENT_LIMIT 等 12 个参数。</p>
     * <p>这些限制确保 LLM 不会请求过多数据，防止 token 溢出。</p>
     */
    @Valid
    private InsightLimits insightLimits = new InsightLimits();

    // ========================================================================
    // 内部类定义
    // ========================================================================

    /**
     * 服务器配置.
     */
    @Data
    public static class Server {
        /** 服务器绑定地址. Python 原版: HOST = "0.0.0.0" */
        private String host = "0.0.0.0";

        /** 服务器端口. Python 原版: PORT = 5000 */
        @Min(1) @Max(65535)
        private int port = 5000;
    }

    /**
     * 数据库连接配置.
     *
     * <p>Python 原版使用 DB_DIALECT 区分 mysql 和 postgresql，
     * 并在运行时拼接 SQLAlchemy 连接字符串。Java 版本同样支持
     * 两种数据库方言，通过 {@link #buildJdbcUrl()} 生成 JDBC URL。</p>
     */
    @Data
    public static class Db {

        /**
         * 数据库方言: "mysql" 或 "postgresql".
         * <p>Python 原版: DB_DIALECT = "postgresql"</p>
         * <p>决定 JDBC URL 前缀和驱动类。</p>
         */
        @NotBlank
        private String dialect = "postgresql";

        /** 数据库主机. Python 原版: DB_HOST */
        @NotBlank
        private String host = "localhost";

        /** 数据库端口. Python 原版: DB_PORT = 3306 */
        @Min(1) @Max(65535)
        private int port = 5432;

        /** 数据库用户名. Python 原版: DB_USER */
        @NotBlank
        private String user = "bettafish";

        /** 数据库密码. Python 原版: DB_PASSWORD */
        @NotBlank
        private String password;

        /** 数据库名称. Python 原版: DB_NAME = "your_db_name" */
        @NotBlank
        private String name = "bettafish";

        /** 字符集. Python 原版: DB_CHARSET = "utf8mb4" */
        private String charset = "utf8mb4";

        /**
         * 根据配置动态构建 JDBC URL.
         *
         * <p>对标 Python 原版中 SQLAlchemy 的连接字符串拼接逻辑：</p>
         * <pre>
         * # Python 原版 (config.py 隐含逻辑):
         * if DB_DIALECT == "mysql":
         *     url = f"mysql+asyncmy://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}?charset={DB_CHARSET}"
         * else:
         *     url = f"postgresql+asyncpg://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
         * </pre>
         *
         * @return 完整的 JDBC URL 字符串
         */
        public String buildJdbcUrl() {
            if ("mysql".equalsIgnoreCase(dialect)) {
                return String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=%s"
                        + "&useSSL=false&serverTimezone=Asia/Shanghai"
                        + "&allowPublicKeyRetrieval=true",
                    host, port, name, charset
                );
            } else {
                // PostgreSQL
                return String.format(
                    "jdbc:postgresql://%s:%d/%s",
                    host, port, name
                );
            }
        }

        /**
         * 获取对应方言的 JDBC 驱动类全限定名.
         *
         * @return 驱动类名
         */
        public String getDriverClassName() {
            if ("mysql".equalsIgnoreCase(dialect)) {
                return "com.mysql.cj.jdbc.Driver";
            } else {
                return "org.postgresql.Driver";
            }
        }

        /**
         * 获取对应方言的 Hibernate dialect 类名.
         *
         * @return Hibernate 方言类全限定名
         */
        public String getHibernateDialect() {
            if ("mysql".equalsIgnoreCase(dialect)) {
                return "org.hibernate.dialect.MySQLDialect";
            } else {
                return "org.hibernate.dialect.PostgreSQLDialect";
            }
        }
    }

    /**
     * 所有 LLM 提供商配置容器.
     *
     * <p>BettaFish 系统使用 7 个独立的 LLM 配置，分别服务于不同的 Agent。
     * 每个配置包含 apiKey、baseUrl 和 modelName 三个字段，对应 Python
     * 原版中的 *_API_KEY、*_BASE_URL、*_MODEL_NAME 环境变量。</p>
     */
    @Data
    public static class Llm {

        /** InsightEngine LLM. Python: INSIGHT_ENGINE_API_KEY/BASE_URL/MODEL_NAME */
        @Valid
        private LlmConfig insight = new LlmConfig(
            null,
            "https://api.moonshot.cn/v1",
            "kimi-k2-0711-preview"
        );

        /** MediaEngine LLM. Python: MEDIA_ENGINE_API_KEY/BASE_URL/MODEL_NAME */
        @Valid
        private LlmConfig media = new LlmConfig(
            null,
            "https://aihubmix.com/v1",
            "gemini-2.5-pro"
        );

        /** QueryEngine LLM. Python: QUERY_ENGINE_API_KEY/BASE_URL/MODEL_NAME */
        @Valid
        private LlmConfig query = new LlmConfig(
            null,
            "https://api.deepseek.com",
            "deepseek-chat"
        );

        /** ReportEngine LLM. Python: REPORT_ENGINE_API_KEY/BASE_URL/MODEL_NAME */
        @Valid
        private LlmConfig report = new LlmConfig(
            null,
            "https://aihubmix.com/v1",
            "gemini-2.5-pro"
        );

        /** MindSpider LLM. Python: MINDSPIDER_API_KEY/BASE_URL/MODEL_NAME */
        @Valid
        private LlmConfig mindspider = new LlmConfig(
            null,
            "https://api.deepseek.com",
            "deepseek-chat"
        );

        /** ForumEngine 主持人 LLM. Python: FORUM_HOST_API_KEY/BASE_URL/MODEL_NAME */
        @Valid
        private LlmConfig forumHost = new LlmConfig(
            null,
            "https://api.siliconflow.cn/v1",
            "Qwen/qwen3-235b"
        );

        /** 关键词优化器 LLM. Python: KEYWORD_OPTIMIZER_API_KEY/BASE_URL/MODEL_NAME */
        @Valid
        private LlmConfig keywordOptimizer = new LlmConfig(
            null,
            "https://api.siliconflow.cn/v1",
            "Qwen/qwen3-235b"
        );
    }

    /**
     * 单个 LLM 提供商的配置.
     *
     * <p>所有提供商（DeepSeek、Kimi/Moonshot、Gemini/AiHubMix、Qwen/SiliconFlow）
     * 均兼容 OpenAI API 协议，因此 Spring AI 的 OpenAI Starter 可以统一处理。
     * 区别仅在于 baseUrl 和 modelName 不同。</p>
     *
     * <p>Python 原版中，每个 Agent 各自从环境变量读取这三个值：</p>
     * <pre>
     * INSIGHT_ENGINE_API_KEY=sk-xxx
     * INSIGHT_ENGINE_BASE_URL=https://api.moonshot.cn/v1
     * INSIGHT_ENGINE_MODEL_NAME=kimi-k2-0711-preview
     * </pre>
     */
    @Data
    public static class LlmConfig {
        /**
         * API 密钥.
         * <p>Python 原版: *_API_KEY 环境变量</p>
         */
        private String apiKey;

        /**
         * API 基础 URL (OpenAI 兼容格式).
         * <p>Python 原版: *_BASE_URL 环境变量</p>
         * <p>所有提供商均使用 /v1 后缀的 OpenAI 兼容端点。</p>
         */
        private String baseUrl;

        /**
         * 模型名称.
         * <p>Python 原版: *_MODEL_NAME 环境变量</p>
         */
        private String modelName;

        /** 默认无参构造 (Spring Boot 绑定需要). */
        public LlmConfig() {}

        /**
         * 全参构造.
         *
         * @param apiKey    API 密钥
         * @param baseUrl   API 基础 URL
         * @param modelName 模型名称
         */
        public LlmConfig(String apiKey, String baseUrl, String modelName) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.modelName = modelName;
        }

        /**
         * 检查此 LLM 配置是否可用 (API Key 已设置).
         *
         * @return 如果 apiKey 非空则返回 true
         */
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * 外部搜索工具配置.
     *
     * <p>Python 原版支持两种搜索后端：Bocha AI Search 和 Anspire AI Search，
     * 通过 SEARCH_TOOL_TYPE 环境变量切换。Tavily 用于 QueryEngine，独立配置。</p>
     */
    @Data
    public static class Search {

        /**
         * Tavily API 密钥 (QueryEngine 专用).
         * <p>Python 原版: TAVILY_API_KEY</p>
         */
        private String tavilyApiKey;

        /**
         * MediaEngine 搜索后端类型: "BochaAPI" 或 "AnspireAPI".
         * <p>Python 原版: SEARCH_TOOL_TYPE = "AnspireAPI"</p>
         * <p>决定 MediaEngine 使用哪个搜索 API。</p>
         */
        private String toolType = "AnspireAPI";

        /**
         * Bocha AI Search API 基础 URL.
         * <p>Python 原版: BOCHA_BASE_URL = "https://api.bocha.cn/v1/ai-search"</p>
         */
        private String bochaBaseUrl = "https://api.bocha.cn/v1/ai-search";

        /**
         * Bocha AI Search API 密钥.
         * <p>Python 原版: BOCHA_WEB_SEARCH_API_KEY</p>
         */
        private String bochaApiKey;

        /**
         * Anspire AI Search API 基础 URL.
         * <p>Python 原版: ANSPIRE_BASE_URL = "https://plugin.anspire.cn/api/ntsearch/search"</p>
         */
        private String anspireBaseUrl = "https://plugin.anspire.cn/api/ntsearch/search";

        /**
         * Anspire AI Search API 密钥.
         * <p>Python 原版: ANSPIRE_API_KEY</p>
         */
        private String anspireApiKey;
    }

    /**
     * InsightEngine 数据库查询限制参数.
     *
     * <p>这些限制参数控制 InsightEngine 的 6 个数据库查询工具的结果数量上限。
     * Python 原版中，这些参数由 config.py 的 Settings 类直接提供，
     * 防止 LLM Agent 请求过量数据导致 token 溢出或内存不足。</p>
     *
     * <p>特别注意：Python 原版的 InsightAgent 在 execute_search_tool 中
     * 强制使用 config 中的限制值，忽略 LLM 建议的值。Java 版本同样需要
     * 在工具层强制覆盖。</p>
     */
    @Data
    public static class InsightLimits {

        /**
         * 热门内容搜索的最大条目数.
         * <p>Python 原版: DEFAULT_SEARCH_HOT_CONTENT_LIMIT = 100</p>
         * <p>用于 search_hot_content 工具。</p>
         */
        private int searchHotContentLimit = 100;

        /**
         * 全局话题搜索每张表的最大条目数.
         * <p>Python 原版: DEFAULT_SEARCH_TOPIC_GLOBALLY_LIMIT_PER_TABLE = 50</p>
         * <p>用于 search_topic_globally 工具，跨 15 张表搜索。</p>
         */
        private int searchTopicGloballyLimitPerTable = 50;

        /**
         * 按日期搜索话题每张表的最大条目数.
         * <p>Python 原版: DEFAULT_SEARCH_TOPIC_BY_DATE_LIMIT_PER_TABLE = 100</p>
         * <p>用于 search_topic_by_date 工具。</p>
         */
        private int searchTopicByDateLimitPerTable = 100;

        /**
         * 获取话题评论的最大条目数.
         * <p>Python 原版: DEFAULT_GET_COMMENTS_FOR_TOPIC_LIMIT = 500</p>
         * <p>用于 get_comments_for_topic 工具。</p>
         */
        private int getCommentsForTopicLimit = 500;

        /**
         * 单平台话题搜索的最大条目数.
         * <p>Python 原版: DEFAULT_SEARCH_TOPIC_ON_PLATFORM_LIMIT = 200</p>
         * <p>用于 search_topic_on_platform 工具。</p>
         */
        private int searchTopicOnPlatformLimit = 200;

        /**
         * 送入 LLM 的最大搜索结果数. 0 表示不限制.
         * <p>Python 原版: MAX_SEARCH_RESULTS_FOR_LLM = 0</p>
         * <p>在搜索结果传入 LLM 之前进行截断，防止超出上下文窗口。</p>
         */
        private int maxSearchResultsForLlm = 0;

        /**
         * 高置信度情感分析结果的最大数量. 0 表示不限制.
         * <p>Python 原版: MAX_HIGH_CONFIDENCE_SENTIMENT_RESULTS = 0</p>
         */
        private int maxHighConfidenceSentimentResults = 0;

        /**
         * 最大反思轮数.
         * <p>Python 原版: MAX_REFLECTIONS = 3</p>
         * <p>控制 Search-Reflect Loop 中反思阶段的迭代次数。</p>
         */
        @Min(1) @Max(10)
        private int maxReflections = 3;

        /**
         * 最大报告段落数.
         * <p>Python 原版: MAX_PARAGRAPHS = 6</p>
         * <p>Report Structure 阶段生成的最大段落数。</p>
         */
        @Min(1) @Max(20)
        private int maxParagraphs = 6;

        /**
         * 搜索请求超时时间（秒）.
         * <p>Python 原版: SEARCH_TIMEOUT = 240</p>
         */
        @Min(10)
        private int searchTimeout = 240;

        /**
         * 搜索内容的最大字符长度.
         * <p>Python 原版: MAX_CONTENT_LENGTH = 500000</p>
         * <p>超过此长度的搜索结果会被截断。</p>
         */
        private int maxContentLength = 500000;
    }
}
```

---

## 2.5 完整 application.yml 配置

以下是 `bettafish-app/src/main/resources/application.yml` 的完整配置。所有敏感信息使用 `${ENV_VAR:default}` 语法，支持通过环境变量注入：

```yaml
# ============================================================================
# BettaFish (微舆) - Java/Spring AI 版本配置文件
# ============================================================================
# 所有密钥通过环境变量注入，切勿在此文件中硬编码敏感信息。
# 启动时需设置: INSIGHT_API_KEY, MEDIA_API_KEY, QUERY_API_KEY,
#               REPORT_API_KEY, MINDSPIDER_API_KEY, FORUM_HOST_API_KEY,
#               KEYWORD_OPTIMIZER_API_KEY, TAVILY_API_KEY,
#               DB_PASSWORD, DB_HOST 等

# ======================== Spring Boot 基础配置 ========================
spring:
  application:
    name: bettafish

  # ==================== 数据源配置 (HikariCP) ====================
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/bettafish}
    username: ${DB_USER:bettafish}
    password: ${DB_PASSWORD:}
    driver-class-name: ${DB_DRIVER:org.postgresql.Driver}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000          # 5 分钟
      max-lifetime: 1800000         # 30 分钟
      connection-timeout: 30000     # 30 秒
      pool-name: BettaFish-HikariCP

  # ==================== JPA / Hibernate ====================
  jpa:
    hibernate:
      ddl-auto: validate              # 生产环境使用 validate，开发可用 update
    show-sql: false
    properties:
      hibernate:
        dialect: ${HIBERNATE_DIALECT:org.hibernate.dialect.PostgreSQLDialect}
        format_sql: true
        jdbc:
          batch_size: 50
          batch_versioned_data: true
        order_inserts: true
        order_updates: true
    open-in-view: false                # 禁用 OSIV，避免 LazyLoading 隐患

  # ==================== Spring AI 基础配置 ====================
  ai:
    openai:
      # 默认 OpenAI 配置 (被 LlmAutoConfiguration 中的各 ChatClient 覆盖)
      # 此处仅作为 fallback
      api-key: ${REPORT_API_KEY:sk-placeholder}
base-url: https://aihubmix.com/v1
      chat:
        options:
          model: gemini-2.5-pro
          temperature: 0.7

    # ==================== MCP 客户端配置 (Tavily MCP Server) ====================
    mcp:
      client:
        stdio:
          connections:
            tavily-server:
              command: npx
              args:
                - "-y"
                - "@anthropic/tavily-mcp-server"
              env:
                TAVILY_API_KEY: ${TAVILY_API_KEY:}

  # ==================== 异步任务配置 ====================
  task:
    execution:
      pool:
        core-size: 5
        max-size: 20
        queue-capacity: 100
      thread-name-prefix: bettafish-async-

# ======================== 服务器配置 ========================
server:
  port: ${SERVER_PORT:8080}
  servlet:
    encoding:
      charset: UTF-8
      force: true

# ======================== BettaFish 自定义配置 ========================
bettafish:
  server:
    host: "0.0.0.0"
    port: ${SERVER_PORT:8080}

  # ==================== 数据库配置 ====================
  db:
    dialect: ${DB_DIALECT:postgresql}
    host: ${DB_HOST:localhost}
    port: ${DB_PORT:5432}
    user: ${DB_USER:bettafish}
    password: ${DB_PASSWORD:}
    name: ${DB_NAME:bettafish}
    charset: utf8mb4

  # ==================== LLM 配置 (7 个独立 Agent) ====================
  llm:
    # InsightEngine: Moonshot / Kimi
    insight:
      api-key: ${INSIGHT_API_KEY:}
      base-url: ${INSIGHT_BASE_URL:https://api.moonshot.cn/v1}
      model-name: ${INSIGHT_MODEL:kimi-k2-0711-preview}

    # MediaEngine: AiHubMix / Gemini
    media:
      api-key: ${MEDIA_API_KEY:}
      base-url: ${MEDIA_BASE_URL:https://aihubmix.com/v1}
      model-name: ${MEDIA_MODEL:gemini-2.5-pro}

    # QueryEngine: DeepSeek
    query:
      api-key: ${QUERY_API_KEY:}
      base-url: ${QUERY_BASE_URL:https://api.deepseek.com}
      model-name: ${QUERY_MODEL:deepseek-chat}

    # ReportEngine: AiHubMix / Gemini (同 Media，但独立 key 以控制配额)
    report:
      api-key: ${REPORT_API_KEY:}
      base-url: ${REPORT_BASE_URL:https://aihubmix.com/v1}
      model-name: ${REPORT_MODEL:gemini-2.5-pro}

    # MindSpider: DeepSeek (用于话题提取和关键词生成)
    mindspider:
      api-key: ${MINDSPIDER_API_KEY:}
      base-url: ${MINDSPIDER_BASE_URL:https://api.deepseek.com}
      model-name: ${MINDSPIDER_MODEL:deepseek-chat}

    # ForumEngine 主持人: SiliconFlow / Qwen
    forum-host:
      api-key: ${FORUM_HOST_API_KEY:}
      base-url: ${FORUM_HOST_BASE_URL:https://api.siliconflow.cn/v1}
      model-name: ${FORUM_HOST_MODEL:Qwen/qwen3-235b}

    # 关键词优化器: SiliconFlow / Qwen (InsightEngine 内部使用)
    keyword-optimizer:
      api-key: ${KEYWORD_OPTIMIZER_API_KEY:}
      base-url: ${KEYWORD_OPTIMIZER_BASE_URL:https://api.siliconflow.cn/v1}
      model-name: ${KEYWORD_OPTIMIZER_MODEL:Qwen/qwen3-235b}

  # ==================== 搜索工具配置 ====================
  search:
    tavily-api-key: ${TAVILY_API_KEY:}
    tool-type: ${SEARCH_TOOL_TYPE:AnspireAPI}     # "BochaAPI" 或 "AnspireAPI"
    bocha-base-url: ${BOCHA_BASE_URL:https://api.bocha.cn/v1/ai-search}
    bocha-api-key: ${BOCHA_API_KEY:}
    anspire-base-url: ${ANSPIRE_BASE_URL:https://plugin.anspire.cn/api/ntsearch/search}
    anspire-api-key: ${ANSPIRE_API_KEY:}

  # ==================== InsightEngine 搜索限制 ====================
  insight-limits:
    search-hot-content-limit: 100
    search-topic-globally-limit-per-table: 50
    search-topic-by-date-limit-per-table: 100
    get-comments-for-topic-limit: 500
    search-topic-on-platform-limit: 200
    max-search-results-for-llm: 0              # 0 = 不限制
    max-high-confidence-sentiment-results: 0   # 0 = 不限制
    max-reflections: 3                          # Search-Reflect Loop 反思轮数
    max-paragraphs: 6                           # 报告最大段落数
    search-timeout: 240                         # 搜索超时（秒）
    max-content-length: 500000                  # 搜索内容最大字符数

# ======================== Actuator 监控 ========================
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,env
  endpoint:
    health:
      show-details: when_authorized

# ======================== 日志配置 ========================
logging:
  level:
    root: INFO
    com.bettafish: DEBUG
    org.springframework.ai: DEBUG
    org.hibernate.SQL: WARN
    org.hibernate.type.descriptor.sql: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

---

## 2.6 LLM 自动配置类：LlmAutoConfiguration.java

这是将 7 个不同 LLM 提供商配置转化为 Spring Bean 的核心配置类。每个 `ChatClient` Bean 通过 `@Qualifier` 注解命名，供各 Agent 按需注入。

```java
package com.bettafish.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LLM 自动配置类 — 为 BettaFish 系统中的 7 个 Agent 创建独立的 ChatClient Bean.
 *
 * <h3>设计思路：</h3>
 * <p>Python 原版中，每个 Engine 内部各自创建 OpenAI 客户端实例：</p>
 * <pre>
 * # Python (QueryEngine/agent.py):
 * self.llm_client = LLMClient(
 *     api_key=settings.QUERY_ENGINE_API_KEY,
 *     base_url=settings.QUERY_ENGINE_BASE_URL,
 *     model_name=settings.QUERY_ENGINE_MODEL_NAME
 * )
 * </pre>
 *
 * <p>Java 版本将所有 LLM 客户端的创建集中到此配置类，利用 Spring AI 的
 * {@link OpenAiApi} + {@link OpenAiChatModel} + {@link ChatClient} 三层抽象。
 * 关键点在于：所有提供商（DeepSeek、Kimi、Gemini via AiHubMix、Qwen via SiliconFlow）
 * 均兼容 OpenAI API 协议，因此统一使用 Spring AI 的 OpenAI 模块即可。</p>
 *
 * <h3>Bean 命名与注入方式：</h3>
 * <ul>
 *   <li>{@code @Qualifier("queryChatClient")} — QueryAgent 注入</li>
 *   <li>{@code @Qualifier("mediaChatClient")} — MediaAgent 注入</li>
 *   <li>{@code @Qualifier("insightChatClient")} — InsightAgent 注入</li>
 *   <li>{@code @Qualifier("reportChatClient")} — ReportAgent 注入 (同时是 @Primary)</li>
 *   <li>{@code @Qualifier("forumHostChatClient")} — ForumHost 注入</li>
 *   <li>{@code @Qualifier("keywordOptimizerChatClient")} — KeywordOptimizer 注入</li>
 *   <li>{@code @Qualifier("mindspiderChatClient")} — MindSpider 注入</li>
 * </ul>
 *
 * <h3>Python 原版 LLM 提供商映射：</h3>
 * <table>
 *   <tr><th>Agent</th><th>Provider</th><th>Model</th></tr>
 *   <tr><td>QueryEngine</td><td>DeepSeek</td><td>deepseek-chat</td></tr>
 *   <tr><td>MediaEngine</td><td>AiHubMix (Gemini)</td><td>gemini-2.5-pro</td></tr>
 *   <tr><td>InsightEngine</td><td>Moonshot (Kimi)</td><td>kimi-k2-0711-preview</td></tr>
 *   <tr><td>ReportEngine</td><td>AiHubMix (Gemini)</td><td>gemini-2.5-pro</td></tr>
 *   <tr><td>MindSpider</td><td>DeepSeek</td><td>deepseek-chat</td></tr>
 *   <tr><td>ForumHost</td><td>SiliconFlow (Qwen)</td><td>Qwen/qwen3-235b</td></tr>
 *   <tr><td>KeywordOptimizer</td><td>SiliconFlow (Qwen)</td><td>Qwen/qwen3-235b</td></tr>
 * </table>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(BettaFishProperties.class)
public class LlmAutoConfiguration {

    private final BettaFishProperties properties;

    // ========================================================================
    // QueryEngine ChatClient — DeepSeek
    // ========================================================================

    /**
     * QueryEngine 专用 ChatClient.
     *
     * <p>对标 Python 原版:</p>
     * <pre>
     * # QueryEngine/agent.py
     * self.llm_client = LLMClient(
     *     api_key=settings.QUERY_ENGINE_API_KEY,
     *     base_url="https://api.deepseek.com",
     *     model_name="deepseek-chat"
     * )
     * </pre>
     *
     * <p>DeepSeek 的 API 完全兼容 OpenAI 协议，base_url 指向
     * {@code https://api.deepseek.com} 即可。默认温度 0.7。</p>
     */
    @Bean("queryChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.query", name = "api-key")
    public ChatClient queryChatClient() {
        BettaFishProperties.LlmConfig config = properties.getLlm().getQuery();
        log.info("初始化 QueryEngine ChatClient: model={}, baseUrl={}",
                 config.getModelName(), config.getBaseUrl());
        return buildChatClient(config, 0.7);
    }

    // ========================================================================
    // MediaEngine ChatClient — Gemini via AiHubMix
    // ========================================================================

    /**
     * MediaEngine 专用 ChatClient.
     *
     * <p>对标 Python 原版:</p>
     * <pre>
     * # MediaEngine/agent.py
     * self.llm_client = LLMClient(
     *     api_key=settings.MEDIA_ENGINE_API_KEY,
     *     base_url="https://aihubmix.com/v1",
     *     model_name="gemini-2.5-pro"
     * )
     * </pre>
     *
     * <p>AiHubMix 是 Gemini 的 OpenAI 兼容代理，使用标准 OpenAI 协议即可
     * 调用 Gemini 模型。温度 0.7。</p>
     */
    @Bean("mediaChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.media", name = "api-key")
    public ChatClient mediaChatClient() {
        BettaFishProperties.LlmConfig config = properties.getLlm().getMedia();
        log.info("初始化 MediaEngine ChatClient: model={}, baseUrl={}",
                 config.getModelName(), config.getBaseUrl());
        return buildChatClient(config, 0.7);
    }

    // ========================================================================
    // InsightEngine ChatClient — Kimi via Moonshot
    // ========================================================================

    /**
     * InsightEngine 专用 ChatClient.
     *
     * <p>对标 Python 原版:</p>
     * <pre>
     * # InsightEngine/agent.py
     * self.llm_client = LLMClient(
     *     api_key=settings.INSIGHT_ENGINE_API_KEY,
     *     base_url="https://api.moonshot.cn/v1",
     *     model_name="kimi-k2-0711-preview"
     * )
     * </pre>
     *
     * <p>Moonshot/Kimi 的 API 同样兼容 OpenAI 协议。Kimi 擅长长文本理解，
     * 适合处理大量社交媒体数据。温度 0.7。</p>
     */
    @Bean("insightChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.insight", name = "api-key")
    public ChatClient insightChatClient() {
        BettaFishProperties.LlmConfig config = properties.getLlm().getInsight();
        log.info("初始化 InsightEngine ChatClient: model={}, baseUrl={}",
                 config.getModelName(), config.getBaseUrl());
        return buildChatClient(config, 0.7);
    }

    // ========================================================================
    // ReportEngine ChatClient — Gemini via AiHubMix (@Primary)
    // ========================================================================

    /**
     * ReportEngine 专用 ChatClient (同时标记为 @Primary).
     *
     * <p>对标 Python 原版:</p>
     * <pre>
     * # ReportEngine 内部
     * self.llm_client = LLMClient(
     *     api_key=settings.REPORT_ENGINE_API_KEY,
     *     base_url="https://aihubmix.com/v1",
     *     model_name="gemini-2.5-pro"
     * )
     * </pre>
     *
     * <p>标记为 {@code @Primary} 的原因：当某些通用组件需要注入 ChatClient
     * 但未指定 @Qualifier 时，默认使用 ReportEngine 的配置。ReportEngine
     * 需要最强的综合能力（模板选择、布局设计、章节生成），因此使用 Gemini 2.5 Pro。</p>
     */
    @Primary
    @Bean("reportChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.report", name = "api-key")
    public ChatClient reportChatClient() {
        BettaFishProperties.LlmConfig config = properties.getLlm().getReport();
        log.info("初始化 ReportEngine ChatClient [PRIMARY]: model={}, baseUrl={}",
                 config.getModelName(), config.getBaseUrl());
        return buildChatClient(config, 0.7);
    }

    // ========================================================================
    // ForumHost ChatClient — Qwen via SiliconFlow
    // ========================================================================

    /**
     * ForumEngine 主持人专用 ChatClient.
     *
     * <p>对标 Python 原版 (ForumEngine/llm_host.py):</p>
     * <pre>
     * self.client = OpenAI(
     *     api_key=settings.FORUM_HOST_API_KEY,
     *     base_url=settings.FORUM_HOST_BASE_URL  # SiliconFlow
     * )
     * # temperature=0.6, top_p=0.9
     * </pre>
     *
     * <p>Python 原版中 ForumHost 使用较低的温度 (0.6)，以保持主持人发言
     * 的稳定性和客观性。Java 版本在此保持一致。</p>
     *
     * <p>SiliconFlow 是 Qwen 模型的 OpenAI 兼容代理平台。</p>
     */
    @Bean("forumHostChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.forum-host", name = "api-key")
    public ChatClient forumHostChatClient() {
        BettaFishProperties.LlmConfig config = properties.getLlm().getForumHost();
        log.info("初始化 ForumHost ChatClient: model={}, baseUrl={}, temperature=0.6",
                 config.getModelName(), config.getBaseUrl());
        return buildChatClient(config, 0.6);  // 注意：温度 0.6，区别于其他 Agent
    }

    // ========================================================================
    // KeywordOptimizer ChatClient — Qwen via SiliconFlow
    // ========================================================================

    /**
     * 关键词优化器专用 ChatClient.
     *
     * <p>Python 原版中，KeywordOptimizer 是 InsightEngine 内部使用的子组件，
     * 调用 Qwen 模型将用户查询扩展为多个适合不同平台的搜索关键词。</p>
     *
     * <p>对标 Python 原版:</p>
     * <pre>
     * # InsightEngine/keyword/keyword_optimizer.py
     * self.client = OpenAI(
     *     api_key=settings.KEYWORD_OPTIMIZER_API_KEY,
     *     base_url=settings.KEYWORD_OPTIMIZER_BASE_URL  # SiliconFlow
     * )
     * </pre>
     */
    @Bean("keywordOptimizerChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.keyword-optimizer", name = "api-key")
    public ChatClient keywordOptimizerChatClient() {
        BettaFishProperties.LlmConfig config = properties.getLlm().getKeywordOptimizer();
        log.info("初始化 KeywordOptimizer ChatClient: model={}, baseUrl={}",
                 config.getModelName(), config.getBaseUrl());
        return buildChatClient(config, 0.7);
    }

    // ========================================================================
    // MindSpider ChatClient — DeepSeek
    // ========================================================================

    /**
     * MindSpider 爬虫服务专用 ChatClient.
     *
     * <p>MindSpider 使用 LLM 来：</p>
     * <ul>
     *   <li>从每日热点新闻中提取话题</li>
     *   <li>生成适合各平台的搜索关键词</li>
     *   <li>优化爬虫任务的参数配置</li>
     * </ul>
     *
     * <p>对标 Python 原版:</p>
     * <pre>
     * # MindSpider 内部
     * self.llm_client = LLMClient(
     *     api_key=settings.MINDSPIDER_API_KEY,
     *     base_url=settings.MINDSPIDER_BASE_URL,
     *     model_name=settings.MINDSPIDER_MODEL_NAME
     * )
     * </pre>
     */
    @Bean("mindspiderChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.mindspider", name = "api-key")
    public ChatClient mindspiderChatClient() {
        BettaFishProperties.LlmConfig config = properties.getLlm().getMindspider();
        log.info("初始化 MindSpider ChatClient: model={}, baseUrl={}",
                 config.getModelName(), config.getBaseUrl());
        return buildChatClient(config, 0.7);
    }

    // ========================================================================
    // 私有辅助方法
    // ========================================================================

    /**
     * 根据 LLM 配置构建 ChatClient 实例.
     *
     * <p>构建链路：</p>
     * <ol>
     *   <li>{@link OpenAiApi} — 底层 HTTP 客户端，持有 API Key 和 Base URL</li>
     *   <li>{@link OpenAiChatModel} — 模型层，持有默认参数（model name、temperature）</li>
     *   <li>{@link ChatClient} — 最上层应用接口，提供 Fluent API 用于构建 Prompt</li>
     * </ol>
     *
     * <p>这三层抽象对应 Python 原版中的：</p>
     * <pre>
     * # Python:
     * client = OpenAI(api_key=key, base_url=url)     # ← OpenAiApi
     * response = client.chat.completions.create(      # ← OpenAiChatModel
     *     model=model_name,
     *     temperature=temp,
     *     messages=[...]
     * )
     * # Java ChatClient 额外提供了 advisor chain、tool binding 等高级功能
     * </pre>
     *
     * @param config      LLM 配置（apiKey, baseUrl, modelName）
     * @param temperature 温度参数（0.0-2.0）
     * @return 配置好的 ChatClient 实例
     */
    private ChatClient buildChatClient(BettaFishProperties.LlmConfig config,
                                       double temperature) {
        // 1. 创建底层 API 客户端（指向特定提供商的 base URL）
        OpenAiApi api = OpenAiApi.builder()
            .apiKey(config.getApiKey())
            .baseUrl(config.getBaseUrl())
            .build();

        // 2. 创建 Chat Model（设置默认模型名称和温度）
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(config.getModelName())
            .temperature(temperature)
            .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .build();

        // 3. 创建 ChatClient（最终应用层接口）
        return ChatClient.builder(chatModel).build();
    }
}
```

---

## 2.7 各 Agent 的 ChatClient 注入示例

以下展示每个 Agent 如何通过 `@Qualifier` 注入正确的 `ChatClient`：

### 2.7.1 QueryAgent

```java
package com.bettafish.query;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * QueryEngine 的核心 Agent.
 *
 * <p>使用 DeepSeek 模型，通过 Tavily MCP Server 执行 6 种新闻搜索，
 * 实现 Search-Reflect Loop 生成深度新闻分析报告。</p>
 */
@Slf4j
@Component
public class QueryAgent {

    private final ChatClient chatClient;

    /**
     * 构造函数注入 QueryEngine 专用的 ChatClient.
     *
     * <p>{@code @Qualifier("queryChatClient")} 确保注入的是
     * {@link com.bettafish.common.config.LlmAutoConfiguration#queryChatClient()}
     * 创建的 DeepSeek ChatClient，而非其他 Agent 的。</p>
     *
     * @param chatClient QueryEngine 专用 ChatClient (DeepSeek)
     */
    public QueryAgent(@Qualifier("queryChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
        log.info("QueryAgent 初始化完成, ChatClient: DeepSeek");
    }

    // ... Agent 方法实现见后续章节
}
```

### 2.7.2 MediaAgent

```java
package com.bettafish.media;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * MediaEngine 的核心 Agent.
 *
 * <p>使用 Gemini 模型（通过 AiHubMix），调用 Bocha/Anspire 多模态搜索工具，
 * 实现 Search-Reflect Loop 生成多维融合分析报告。</p>
 */
@Slf4j
@Component
public class MediaAgent {

    private final ChatClient chatClient;

    /**
     * @param chatClient MediaEngine 专用 ChatClient (Gemini via AiHubMix)
     */
    public MediaAgent(@Qualifier("mediaChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
        log.info("MediaAgent 初始化完成, ChatClient: Gemini (AiHubMix)");
    }

    // ... Agent 方法实现见后续章节
}
```

### 2.7.3 InsightAgent

```java
package com.bettafish.insight;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * InsightEngine 的核心 Agent.
 *
 * <p>使用 Kimi 模型（通过 Moonshot），查询本地社交媒体数据库，
 * 结合情感分析、关键词优化、聚类去重等独有功能，
 * 实现 Search-Reflect Loop 生成舆情洞察报告。</p>
 */
@Slf4j
@Component
public class InsightAgent {

    private final ChatClient chatClient;
    private final ChatClient keywordOptimizerClient;

    /**
     * InsightAgent 需要两个 ChatClient：
     * <ol>
     *   <li>主 ChatClient (Kimi) — 用于 Search-Reflect Loop 的 LLM 推理</li>
     *   <li>关键词优化 ChatClient (Qwen) — 用于将查询扩展为平台化关键词</li>
     * </ol>
     *
     * @param chatClient              InsightEngine 主 ChatClient (Kimi)
     * @param keywordOptimizerClient  关键词优化 ChatClient (Qwen)
     */
    public InsightAgent(
            @Qualifier("insightChatClient") ChatClient chatClient,
            @Qualifier("keywordOptimizerChatClient") ChatClient keywordOptimizerClient) {
        this.chatClient = chatClient;
        this.keywordOptimizerClient = keywordOptimizerClient;
        log.info("InsightAgent 初始化完成, 主ChatClient: Kimi, 关键词优化: Qwen");
    }

    // ... Agent 方法实现见后续章节
}
```

### 2.7.4 ForumHost

```java
package com.bettafish.forum;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 论坛主持人 — 协调多 Agent 讨论.
 *
 * <p>对标 Python 原版 ForumEngine/llm_host.py 的 ForumHost 类。</p>
 *
 * <p>使用 Qwen3-235B 模型，温度 0.6（比其他 Agent 低），
 * 以保证主持人发言的稳定性和客观性。</p>
 */
@Slf4j
@Component
public class ForumHost {

    private final ChatClient chatClient;

    /**
     * @param chatClient ForumHost 专用 ChatClient (Qwen, temp=0.6)
     */
    public ForumHost(@Qualifier("forumHostChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
        log.info("ForumHost 初始化完成, ChatClient: Qwen3-235B (temp=0.6)");
    }

    // ... 主持人方法实现见后续章节
}
```

### 2.7.5 ReportAgent

```java
package com.bettafish.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 报告生成 Agent.
 *
 * <p>使用 Gemini 模型，负责模板选择、文档布局、字数预算、
 * 逐章 IR JSON 生成、JSON 修复等完整的报告生成流水线。</p>
 *
 * <p>注意：ReportAgent 的 ChatClient 被标记为 @Primary，
 * 因此不使用 @Qualifier 时也能正确注入。此处仍显式标注以保持一致性。</p>
 */
@Slf4j
@Component
public class ReportAgent {

    private final ChatClient chatClient;

    /**
     * @param chatClient ReportEngine 专用 ChatClient (Gemini, @Primary)
     */
    public ReportAgent(@Qualifier("reportChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
        log.info("ReportAgent 初始化完成, ChatClient: Gemini 2.5 Pro (AiHubMix)");
    }

    // ... 报告生成方法实现见后续章节
}
```

---

## 2.8 LLM 配置启动验证器

为了在应用启动时及早发现配置问题，我们提供一个启动验证器：

```java
package com.bettafish.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动时验证 LLM 配置完整性.
 *
 * <p>在 ApplicationReady 事件触发时检查所有 7 个 LLM 配置的 API Key 是否已设置。
 * 未配置的 Agent 会打印警告日志，但不会阻止应用启动（某些 Agent 可能是可选的）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmConfigValidator {

    private final BettaFishProperties properties;

    /**
     * 应用启动完成后执行配置验证.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        log.info("========== BettaFish LLM 配置检查 ==========");

        BettaFishProperties.Llm llm = properties.getLlm();
        int configuredCount = 0;
        int totalCount = 7;

        configuredCount += checkConfig("QueryEngine  (DeepSeek)", llm.getQuery());
        configuredCount += checkConfig("MediaEngine  (Gemini) ", llm.getMedia());
        configuredCount += checkConfig("InsightEngine(Kimi)   ", llm.getInsight());
        configuredCount += checkConfig("ReportEngine (Gemini) ", llm.getReport());
        configuredCount += checkConfig("ForumHost    (Qwen)   ", llm.getForumHost());
        configuredCount += checkConfig("KeywordOpt   (Qwen)   ", llm.getKeywordOptimizer());
        configuredCount += checkConfig("MindSpider   (DeepSeek)", llm.getMindspider());

        log.info("配置完成: {}/{} 个 LLM Agent 已配置 API Key", configuredCount, totalCount);

        if (configuredCount < 4) {
            log.warn("警告: 已配置的 LLM Agent 不足 4 个，系统功能可能受限！");
            log.warn("至少需要配置: QueryEngine, MediaEngine/InsightEngine(二选一), "
                     + "ForumHost, ReportEngine");
        }

        // 检查搜索工具配置
        BettaFishProperties.Search search = properties.getSearch();
        if (search.getTavilyApiKey() == null || search.getTavilyApiKey().isBlank()) {
            log.warn("Tavily API Key 未配置，QueryEngine 搜索功能不可用");
        }
        String toolType = search.getToolType();
        if ("BochaAPI".equalsIgnoreCase(toolType)) {
            if (search.getBochaApiKey() == null || search.getBochaApiKey().isBlank()) {
                log.warn("Bocha API Key 未配置，但 SEARCH_TOOL_TYPE=BochaAPI");
            }
        } else {
            if (search.getAnspireApiKey() == null || search.getAnspireApiKey().isBlank()) {
                log.warn("Anspire API Key 未配置，但 SEARCH_TOOL_TYPE=AnspireAPI");
            }
        }

        log.info("========== 配置检查完成 ==========");
    }

    /**
     * 检查单个 LLM 配置.
     *
     * @param name   Agent 名称（用于日志）
     * @param config LLM 配置
     * @return 1 如果已配置，0 如果未配置
     */
    private int checkConfig(String name, BettaFishProperties.LlmConfig config) {
        if (config.isConfigured()) {
            log.info("  ✓ {} — model: {}, baseUrl: {}",
                     name, config.getModelName(), config.getBaseUrl());
            return 1;
        } else {
            log.warn("  ✗ {} — API Key 未配置", name);
            return 0;
        }
    }
}
```

---

## 2.9 本章小结

本章完成了 BettaFish Java 版本的完整项目骨架搭建：

1. **Maven 多模块结构**：9 个子模块（common、query-engine、media-engine、insight-engine、forum-engine、report-engine、mind-spider、sentiment-mcp、app），职责清晰，依赖关系通过父 POM 统一管理。

2. **BettaFishProperties**：完整映射了 Python 原版 `config.py` 中 `Settings` 类的全部字段，包括：
   - 服务器配置（host、port）
   - 数据库配置（dialect、host、port、user、password、name、charset + 动态 JDBC URL 生成）
   - 7 个 LLM 提供商配置（apiKey、baseUrl、modelName）
   - 搜索工具配置（Tavily、Bocha、Anspire）
   - InsightEngine 12 个搜索限制参数

3. **LlmAutoConfiguration**：创建 7 个独立的 `ChatClient` Bean，每个通过 `@Qualifier` 命名，支持 `@ConditionalOnProperty` 条件加载，`reportChatClient` 标记为 `@Primary`。

4. **application.yml**：完整的配置文件，所有敏感信息通过 `${ENV_VAR}` 环境变量注入，包含 HikariCP 连接池、JPA、MCP Client、Actuator、日志等全方位配置。

5. **Agent 注入示例**：展示了 5 个 Agent（QueryAgent、MediaAgent、InsightAgent、ForumHost、ReportAgent）如何通过 `@Qualifier` 注入正确的 ChatClient。

6. **LlmConfigValidator**：启动时验证所有 LLM 配置和搜索工具配置的完整性，提供清晰的日志输出。

下一章将实现公共模型层（AgentState、ParagraphState、ForumMessage 等）和事件体系（AgentSpeechEvent、HostCommentEvent 等），为后续的 Agent 实现打下基础。

## Chapter 3: 数据库表设计与 JPA 实体

BettaFish（微舆）的数据层是整个多智能体舆情分析系统的基石。Python 原版基于 SQLAlchemy ORM 定义了 26 张业务表、2 个视图，覆盖了从新闻采集、话题提取、爬取任务调度到七大社交媒体平台（Bilibili、抖音、快手、微博、小红书、贴吧、知乎）的全链路数据存储。本章将这些表 **逐一** 映射为 Spring Data JPA 实体，并为 Java 版新增论坛消息表，最终给出完整的 Repository 层设计。

> **约定说明**
> - 所有实体统一放在 `com.bettafish.entity` 及其子包下。
> - 主键策略采用 `GenerationType.IDENTITY`（MySQL / PostgreSQL 自增）。
> - Python 原版中以 `BigInteger` 存储的 Unix 时间戳，在 Java 中保留为 `Long` 类型（字段名不变），便于与前端/爬虫对接。
> - `topic_id` / `crawling_task_id` 两个关联字段在平台内容表中均为 **可空外键**（`SET NULL`），JPA 中用 `@Column` 直接映射为 String。
> - JSON 字段使用 `@Column(columnDefinition = "TEXT")` 存储，读写时通过 Jackson 手动序列化。

---

### 3.1 核心调度表（4 张表）

核心调度表构成了 BroadTopicExtraction 流水线的骨干：新闻采集 -> 话题提取 -> 话题-新闻关联 -> 平台爬取任务分发。

#### 3.1.1 `daily_news` — 每日热点新闻

该表存储系统从各热搜榜单抓取的原始新闻条目。每条记录通过 `(news_id, source_platform, crawl_date)` 三元组唯一标识。

```java
package com.bettafish.entity.core;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "daily_news",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_daily_news_id_unique", columnNames = {"news_id"}),
        @UniqueConstraint(
            name = "uq_daily_news_composite",
            columnNames = {"news_id", "source_platform", "crawl_date"}
        )
    },
    indexes = {
        @Index(name = "idx_daily_news_date", columnList = "crawl_date"),
        @Index(name = "idx_daily_news_platform", columnList = "source_platform"),
        @Index(name = "idx_daily_news_rank", columnList = "rank_position"),
        @Index(name = "idx_news_date_platform", columnList = "crawl_date, source_platform")
    }
)
public class DailyNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 新闻唯一标识符，由爬虫生成 */
    @Column(name = "news_id", nullable = false, length = 128)
    private String newsId;

    /** 来源平台：weibo, zhihu, bilibili, toutiao, douyin 等 */
    @Column(name = "source_platform", nullable = false, length = 32)
    private String sourcePlatform;

    /** 新闻标题 */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /** 新闻链接 URL */
    @Column(name = "url", length = 512)
    private String url;

    /** 新闻描述/摘要 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 附加信息，JSON 格式存储 */
    @Column(name = "extra_info", columnDefinition = "TEXT")
    private String extraInfo;

    /** 新闻抓取日期 */
    @Column(name = "crawl_date", nullable = false)
    private LocalDate crawlDate;

    /** 在热搜榜单中的排名位置 */
    @Column(name = "rank_position")
    private Integer rankPosition;

    /** 记录创建时间戳（Unix 毫秒） */
    @Column(name = "add_ts", nullable = false)
    private Long addTs;

    /** 最后修改时间戳（Unix 毫秒） */
    @Column(name = "last_modify_ts", nullable = false)
    private Long lastModifyTs;

    // ========== Constructors ==========

    public DailyNews() {}

    public DailyNews(String newsId, String sourcePlatform, String title,
                     LocalDate crawlDate, Long addTs, Long lastModifyTs) {
        this.newsId = newsId;
        this.sourcePlatform = sourcePlatform;
        this.title = title;
        this.crawlDate = crawlDate;
        this.addTs = addTs;
        this.lastModifyTs = lastModifyTs;
    }

    // ========== Getters & Setters ==========

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNewsId() { return newsId; }
    public void setNewsId(String newsId) { this.newsId = newsId; }

    public String getSourcePlatform() { return sourcePlatform; }
    public void setSourcePlatform(String sourcePlatform) { this.sourcePlatform = sourcePlatform; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getExtraInfo() { return extraInfo; }
    public void setExtraInfo(String extraInfo) { this.extraInfo = extraInfo; }

    public LocalDate getCrawlDate() { return crawlDate; }
    public void setCrawlDate(LocalDate crawlDate) { this.crawlDate = crawlDate; }

    public Integer getRankPosition() { return rankPosition; }
    public void setRankPosition(Integer rankPosition) { this.rankPosition = rankPosition; }

    public Long getAddTs() { return addTs; }
    public void setAddTs(Long addTs) { this.addTs = addTs; }

    public Long getLastModifyTs() { return lastModifyTs; }
    public void setLastModifyTs(Long lastModifyTs) { this.lastModifyTs = lastModifyTs; }
}
```

---

#### 3.1.2 `daily_topics` — 每日提取话题

LLM 从当日新闻中提取的话题聚合结果。`processing_status` 字段驱动整个爬取流水线的状态机：`pending -> processing -> completed / failed`。

```java
package com.bettafish.entity.core;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "daily_topics",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_daily_topics_id_unique", columnNames = {"topic_id"}),
        @UniqueConstraint(
            name = "uq_daily_topics_composite",
            columnNames = {"topic_id", "extract_date"}
        )
    },
    indexes = {
        @Index(name = "idx_daily_topics_date", columnList = "extract_date"),
        @Index(name = "idx_daily_topics_status", columnList = "processing_status"),
        @Index(name = "idx_daily_topics_score", columnList = "relevance_score"),
        @Index(name = "idx_topic_date_status", columnList = "extract_date, processing_status")
    }
)
public class DailyTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 话题唯一标识符 */
    @Column(name = "topic_id", nullable = false, length = 64)
    private String topicId;

    /** 话题名称 */
    @Column(name = "topic_name", nullable = false, length = 255)
    private String topicName;

    /** 话题描述 */
    @Column(name = "topic_description", columnDefinition = "TEXT")
    private String topicDescription;

    /** 关键词列表，JSON 格式：["关键词1", "关键词2", ...] */
    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    /** 话题提取日期 */
    @Column(name = "extract_date", nullable = false)
    private LocalDate extractDate;

    /** 话题与当日热点的关联分数 */
    @Column(name = "relevance_score")
    private Float relevanceScore;

    /** 关联新闻数量 */
    @Column(name = "news_count", columnDefinition = "INTEGER DEFAULT 0")
    private Integer newsCount = 0;

    /** 处理状态：pending / processing / completed / failed */
    @Column(name = "processing_status", length = 16,
            columnDefinition = "VARCHAR(16) DEFAULT 'pending'")
    private String processingStatus = "pending";

    /** 记录创建时间戳 */
    @Column(name = "add_ts", nullable = false)
    private Long addTs;

    /** 最后修改时间戳 */
    @Column(name = "last_modify_ts", nullable = false)
    private Long lastModifyTs;

    // ========== Constructors ==========

    public DailyTopic() {}

    public DailyTopic(String topicId, String topicName, LocalDate extractDate,
                      Long addTs, Long lastModifyTs) {
        this.topicId = topicId;
        this.topicName = topicName;
        this.extractDate = extractDate;
        this.addTs = addTs;
        this.lastModifyTs = lastModifyTs;
    }

    // ========== Getters & Setters ==========

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }

    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }

    public String getTopicDescription() { return topicDescription; }
    public void setTopicDescription(String td) { this.topicDescription = td; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public LocalDate getExtractDate() { return extractDate; }
    public void setExtractDate(LocalDate extractDate) { this.extractDate = extractDate; }

    public Float getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(Float relevanceScore) { this.relevanceScore = relevanceScore; }

    public Integer getNewsCount() { return newsCount; }
    public void setNewsCount(Integer newsCount) { this.newsCount = newsCount; }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String s) { this.processingStatus = s; }

    public Long getAddTs() { return addTs; }
    public void setAddTs(Long addTs) { this.addTs = addTs; }

    public Long getLastModifyTs() { return lastModifyTs; }
    public void setLastModifyTs(Long lastModifyTs) { this.lastModifyTs = lastModifyTs; }
}
```

---

#### 3.1.3 `topic_news_relation` — 话题-新闻多对多关联

一条新闻可能属于多个话题，一个话题也包含多条新闻。`relation_score` 表示该新闻与话题的关联强度（由 LLM 评分）。通过 `(topic_id, news_id, extract_date)` 三元组保证唯一性。

```java
package com.bettafish.entity.core;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "topic_news_relation",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_topic_news_date",
            columnNames = {"topic_id", "news_id", "extract_date"}
        )
    },
    indexes = {
        @Index(name = "idx_relation_extract_date", columnList = "extract_date")
    }
)
public class TopicNewsRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 关联话题 ID，外键指向 daily_topics.topic_id，级联删除。
     * 使用 String 直接映射，因为 daily_topics 的业务主键是 topic_id。
     */
    @Column(name = "topic_id", nullable = false, length = 64)
    private String topicId;

    /** 关联新闻 ID，外键指向 daily_news.news_id，级联删除 */
    @Column(name = "news_id", nullable = false, length = 128)
    private String newsId;

    /** 关联评分，由 LLM 给出 */
    @Column(name = "relation_score")
    private Float relationScore;

    /** 提取日期 */
    @Column(name = "extract_date", nullable = false)
    private LocalDate extractDate;

    /** 记录创建时间戳 */
    @Column(name = "add_ts", nullable = false)
    private Long addTs;

    // ========== Constructors ==========

    public TopicNewsRelation() {}

    public TopicNewsRelation(String topicId, String newsId,
                             LocalDate extractDate, Long addTs) {
        this.topicId = topicId;
        this.newsId = newsId;
        this.extractDate = extractDate;
        this.addTs = addTs;
    }

    // ========== Getters & Setters ==========

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }

    public String getNewsId() { return newsId; }
    public void setNewsId(String newsId) { this.newsId = newsId; }

    public Float getRelationScore() { return relationScore; }
    public void setRelationScore(Float relationScore) { this.relationScore = relationScore; }

    public LocalDate getExtractDate() { return extractDate; }
    public void setExtractDate(LocalDate extractDate) { this.extractDate = extractDate; }

    public Long getAddTs() { return addTs; }
    public void setAddTs(Long addTs) { this.addTs = addTs; }
}
```

---

#### 3.1.4 `crawling_tasks` — 平台爬取任务

当话题被提取后，系统会为每个话题在各目标平台上创建一个爬取任务。该表是整个爬虫调度的中枢，`task_status` 状态机为：`pending -> running -> completed / failed / paused`。

```java
package com.bettafish.entity.core;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
    name = "crawling_tasks",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_crawling_tasks_task_id", columnNames = {"task_id"})
    },
    indexes = {
        @Index(name = "idx_task_topic_id", columnList = "topic_id"),
        @Index(name = "idx_task_platform", columnList = "platform"),
        @Index(name = "idx_task_status", columnList = "task_status"),
        @Index(name = "idx_task_scheduled_date", columnList = "scheduled_date"),
        @Index(name = "idx_task_topic_platform",
               columnList = "topic_id, platform, task_status")
    }
)
public class CrawlingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 任务唯一标识符 */
    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    /** 关联话题 ID，外键指向 daily_topics.topic_id */
    @Column(name = "topic_id", nullable = false, length = 64)
    private String topicId;

    /** 目标平台：xhs, dy, ks, bili, wb, tieba, zhihu */
    @Column(name = "platform", nullable = false, length = 32)
    private String platform;

    /** 搜索关键词，JSON 数组格式 */
    @Column(name = "search_keywords", nullable = false, columnDefinition = "TEXT")
    private String searchKeywords;

    /** 任务状态：pending / running / completed / failed / paused */
    @Column(name = "task_status", length = 16,
            columnDefinition = "VARCHAR(16) DEFAULT 'pending'")
    private String taskStatus = "pending";

    /** 任务开始时间戳 */
    @Column(name = "start_time")
    private Long startTime;

    /** 任务结束时间戳 */
    @Column(name = "end_time")
    private Long endTime;

    /** 总抓取条数 */
    @Column(name = "total_crawled", columnDefinition = "INTEGER DEFAULT 0")
    private Integer totalCrawled = 0;

    /** 成功抓取条数 */
    @Column(name = "success_count", columnDefinition = "INTEGER DEFAULT 0")
    private Integer successCount = 0;

    /** 错误条数 */
    @Column(name = "error_count", columnDefinition = "INTEGER DEFAULT 0")
    private Integer errorCount = 0;

    /** 错误信息详情 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 爬取配置参数，JSON 格式 */
    @Column(name = "config_params", columnDefinition = "TEXT")
    private String configParams;

    /** 计划执行日期 */
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    /** 记录创建时间戳 */
    @Column(name = "add_ts", nullable = false)
    private Long addTs;

    /** 最后修改时间戳 */
    @Column(name = "last_modify_ts", nullable = false)
    private Long lastModifyTs;

    // ========== Constructors ==========

    public CrawlingTask() {}

    public CrawlingTask(String taskId, String topicId, String platform,
                        String searchKeywords, LocalDate scheduledDate,
                        Long addTs, Long lastModifyTs) {
        this.taskId = taskId;
        this.topicId = topicId;
        this.platform = platform;
        this.searchKeywords = searchKeywords;
        this.scheduledDate = scheduledDate;
        this.addTs = addTs;
        this.lastModifyTs = lastModifyTs;
    }

    // ========== Getters & Setters ==========

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getSearchKeywords() { return searchKeywords; }
    public void setSearchKeywords(String sk) { this.searchKeywords = sk; }

    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }

    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }

    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }

    public Integer getTotalCrawled() { return totalCrawled; }
    public void setTotalCrawled(Integer tc) { this.totalCrawled = tc; }

    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer sc) { this.successCount = sc; }

    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer ec) { this.errorCount = ec; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String em) { this.errorMessage = em; }

    public String getConfigParams() { return configParams; }
    public void setConfigParams(String cp) { this.configParams = cp; }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate sd) { this.scheduledDate = sd; }

    public Long getAddTs() { return addTs; }
    public void setAddTs(Long addTs) { this.addTs = addTs; }

    public Long getLastModifyTs() { return lastModifyTs; }
    public void setLastModifyTs(Long lmt) { this.lastModifyTs = lmt; }
}
```

---

### 3.2 平台数据表（7 平台 x 多表）

Python 原版为七大社交媒体平台各设计了 2~5 张表，总共 22 张平台数据表。所有内容主表（note / video / aweme / content）都包含两个可空外键字段 `topic_id` 和 `crawling_task_id`，用于将抓取到的内容回溯关联到触发抓取的话题和任务。

#### 3.2.1 通用模式说明

每个平台的表结构遵循相似的模式：

| 表类型 | 命名模式 | 核心字段 | 说明 |
|--------|----------|----------|------|
| 内容表 | `{platform}_note` / `_video` / `_aweme` | 内容ID、用户信息、标题、描述、互动数据、URL | 主内容表 |
| 评论表 | `{platform}_note_comment` / `_video_comment` | 评论ID、关联内容ID、用户信息、评论文本、子评论数 | 评论数据 |
| 创作者表 | `{platform}_creator` / `_up_info` | 用户ID、昵称、头像、粉丝数、关注数 | 创作者画像 |

**公共字段模式**（几乎所有表都包含）：
- `id` — 自增主键
- `add_ts` / `last_modify_ts` — Unix 时间戳
- `user_id` / `nickname` / `avatar` — 用户基本信息
- `ip_location` — IP 属地（部分平台提供）

#### 3.2.2 小红书（Xiaohongshu / XHS）完整示例

小红书是最具代表性的平台，下面给出三张表的完整 JPA 实体代码。

##### `xhs_note` — 小红书笔记

```java
package com.bettafish.entity.platform.xhs;

import jakarta.persistence.*;

@Entity
@Table(
    name = "xhs_note",
    indexes = {
        @Index(name = "idx_xhs_note_id", columnList = "note_id"),
        @Index(name = "idx_xhs_note_time", columnList = "time")
    }
)
public class XhsNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "nickname", columnDefinition = "TEXT")
    private String nickname;

    @Column(name = "avatar", columnDefinition = "TEXT")
    private String avatar;

    @Column(name = "ip_location", columnDefinition = "TEXT")
    private String ipLocation;

    @Column(name = "add_ts")
    private Long addTs;

    @Column(name = "last_modify_ts")
    private Long lastModifyTs;

    /** 笔记唯一 ID */
    @Column(name = "note_id", length = 255)
    private String noteId;

    /** 笔记类型：normal（图文）/ video */
    @Column(name = "type", columnDefinition = "TEXT")
    private String type;

    /** 笔记标题 */
    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    /** 笔记正文描述 */
    @Column(name = "desc", columnDefinition = "TEXT")
    private String desc;

    /** 视频 URL（视频类型笔记） */
    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    /** 发布时间戳 */
    @Column(name = "time")
    private Long time;

    /** 最后更新时间戳 */
    @Column(name = "last_update_time")
    private Long lastUpdateTime;

    /** 点赞数 */
    @Column(name = "liked_count", columnDefinition = "TEXT")
    private String likedCount;

    /** 收藏数 */
    @Column(name = "collected_count", columnDefinition = "TEXT")
    private String collectedCount;

    /** 评论数 */
    @Column(name = "comment_count", columnDefinition = "TEXT")
    private String commentCount;

    /** 分享数 */
    @Column(name = "share_count", columnDefinition = "TEXT")
    private String shareCount;

    /** 图片列表，JSON 数组 */
    @Column(name = "image_list", columnDefinition = "TEXT")
    private String imageList;

    /** 标签列表，JSON 数组 */
    @Column(name = "tag_list", columnDefinition = "TEXT")
    private String tagList;

    /** 笔记 URL */
    @Column(name = "note_url", columnDefinition = "TEXT")
    private String noteUrl;

    /** 来源搜索关键词 */
    @Column(name = "source_keyword", columnDefinition = "TEXT DEFAULT ''")
    private String sourceKeyword = "";

    /** xsec_token 安全令牌 */
    @Column(name = "xsec_token", columnDefinition = "TEXT")
    private String xsecToken;

    /** 关联话题 ID（可空，SET NULL） */
    @Column(name = "topic_id", length = 64)
    private String topicId;

    /** 关联爬取任务 ID（可空，SET NULL） */
    @Column(name = "crawling_task_id", length = 64)
    private String crawlingTaskId;

    // ========== Constructors ==========
    public XhsNote() {}

    // ========== Getters & Setters ==========

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getIpLocation() { return ipLocation; }
    public void setIpLocation(String ipLocation) { this.ipLocation = ipLocation; }

    public Long getAddTs() { return addTs; }
    public void setAddTs(Long addTs) { this.addTs = addTs; }

    public Long getLastModifyTs() { return lastModifyTs; }
    public void setLastModifyTs(Long lastModifyTs) { this.lastModifyTs = lastModifyTs; }

    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public Long getTime() { return time; }
    public void setTime(Long time) { this.time = time; }

    public Long getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(Long t) { this.lastUpdateTime = t; }

    public String getLikedCount() { return likedCount; }
    public void setLikedCount(String likedCount) { this.likedCount = likedCount; }

    public String getCollectedCount() { return collectedCount; }
    public void setCollectedCount(String c) { this.collectedCount = c; }

    public String getCommentCount() { return commentCount; }
    public void setCommentCount(String c) { this.commentCount = c; }

    public String getShareCount() { return shareCount; }
    public void setShareCount(String shareCount) { this.shareCount = shareCount; }

    public String getImageList() { return imageList; }
    public void setImageList(String imageList) { this.imageList = imageList; }

    public String getTagList() { return tagList; }
    public void setTagList(String tagList) { this.tagList = tagList; }

    public String getNoteUrl() { return noteUrl; }
    public void setNoteUrl(String noteUrl) { this.noteUrl = noteUrl; }

    public String getSourceKeyword() { return sourceKeyword; }
    public void setSourceKeyword(String sk) { this.sourceKeyword = sk; }

    public String getXsecToken() { return xsecToken; }
    public void setXsecToken(String xsecToken) { this.xsecToken = xsecToken; }

    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }

    public String getCrawlingTaskId() { return crawlingTaskId; }
    public void setCrawlingTaskId(String cti) { this.crawlingTaskId = cti; }
}
```

##### `xhs_note_comment` — 小红书笔记评论

```java
package com.bettafish.entity.platform.xhs;

import jakarta.persistence.*;

@Entity
@Table(
    name = "xhs_note_comment",
    indexes = {
        @Index(name = "idx_xhs_comment_id", columnList = "comment_id"),
        @Index(name = "idx_xhs_comment_time", columnList = "create_time")
    }
)
public class XhsNoteComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "nickname", columnDefinition = "TEXT")
    private String nickname;

    @Column(name = "avatar", columnDefinition = "TEXT")
    private String avatar;

    @Column(name = "ip_location", columnDefinition = "TEXT")
    private String ipLocation;

    @Column(name = "add_ts")
    private Long addTs;

    @Column(name = "last_modify_ts")
    private Long lastModifyTs;

    /** 评论唯一 ID */
    @Column(name = "comment_id", length = 255)
    private String commentId;

    /** 评论创建时间戳 */
    @Column(name = "create_time")
    private Long createTime;

    /** 关联的笔记 ID */
    @Column(name = "note_id", length = 255)
    private String noteId;

    /** 评论内容 */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 子评论数量 */
    @Column(name = "sub_comment_count")
    private Integer subCommentCount;

    /** 评论图片列表，JSON */
    @Column(name = "pictures", columnDefinition = "TEXT")
    private String pictures;

    /** 父评论 ID（非顶级评论时有值） */
    @Column(name = "parent_comment_id", length = 255)
    private String parentCommentId;

    /** 点赞数 */
    @Column(name = "like_count", columnDefinition = "TEXT")
    private String likeCount;

    // ========== Constructors ==========
    public XhsNoteComment() {}

    // ========== Getters & Setters ==========
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getIpLocation() { return ipLocation; }
    public void setIpLocation(String ipLocation) { this.ipLocation = ipLocation; }
    public Long getAddTs() { return addTs; }
    public void setAddTs(Long addTs) { this.addTs = addTs; }
    public Long getLastModifyTs() { return lastModifyTs; }
    public void setLastModifyTs(Long t) { this.lastModifyTs = t; }
    public String getCommentId() { return commentId; }
    public void setCommentId(String commentId) { this.commentId = commentId; }
    public Long getCreateTime() { return createTime; }
    public void setCreateTime(Long createTime) { this.createTime = createTime; }
    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getSubCommentCount() { return subCommentCount; }
    public void setSubCommentCount(Integer c) { this.subCommentCount = c; }
    public String getPictures() { return pictures; }
    public void setPictures(String pictures) { this.pictures = pictures; }
    public String getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(String p) { this.parentCommentId = p; }
    public String getLikeCount() { return likeCount; }
    public void setLikeCount(String likeCount) { this.likeCount = likeCount; }
}
```

##### `xhs_creator` — 小红书创作者

```java
package com.bettafish.entity.platform.xhs;

import jakarta.persistence.*;

@Entity
@Table(name = "xhs_creator")
public class XhsCreator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "nickname", columnDefinition = "TEXT")
    private String nickname;

    @Column(name = "avatar", columnDefinition = "TEXT")
    private String avatar;

    @Column(name = "ip_location", columnDefinition = "TEXT")
    private String ipLocation;

    @Column(name = "add_ts")
    private Long addTs;

    @Column(name = "last_modify_ts")
    private Long lastModifyTs;

    @Column(name = "desc", columnDefinition = "TEXT")
    private String desc;

    @Column(name = "gender", columnDefinition = "TEXT")
    private String gender;

    @Column(name = "follows", columnDefinition = "TEXT")
    private String follows;

    @Column(name = "fans", columnDefinition = "TEXT")
    private String fans;

    @Column(name = "interaction", columnDefinition = "TEXT")
    private String interaction;

    @Column(name = "tag_list", columnDefinition = "TEXT")
    private String tagList;

    public XhsCreator() {}

    // ========== Getters & Setters ==========
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getIpLocation() { return ipLocation; }
    public void setIpLocation(String ip) { this.ipLocation = ip; }
    public Long getAddTs() { return addTs; }
    public void setAddTs(Long addTs) { this.addTs = addTs; }
    public Long getLastModifyTs() { return lastModifyTs; }
    public void setLastModifyTs(Long t) { this.lastModifyTs = t; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getFollows() { return follows; }
    public void setFollows(String follows) { this.follows = follows; }
    public String getFans() { return fans; }
    public void setFans(String fans) { this.fans = fans; }
    public String getInteraction() { return interaction; }
    public void setInteraction(String interaction) { this.interaction = interaction; }
    public String getTagList() { return tagList; }
    public void setTagList(String tagList) { this.tagList = tagList; }
}
```

---

#### 3.2.3 Bilibili（哔哩哔哩）— 5 张表

Bilibili 是表数量最多的平台，共 5 张表：`bilibili_video`、`bilibili_video_comment`、`bilibili_up_info`、`bilibili_contact_info`、`bilibili_up_dynamic`。

##### `bilibili_video` — 视频内容

```java
package com.bettafish.entity.platform.bilibili;

import jakarta.persistence.*;

@Entity
@Table(
    name = "bilibili_video",
    indexes = {
        @Index(name = "idx_bili_video_id", columnList = "video_id"),
        @Index(name = "idx_bili_video_user", columnList = "user_id"),
        @Index(name = "idx_bili_video_time", columnList = "create_time")
    }
)
public class BilibiliVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "video_id", unique = true)
    private Long videoId;

    @Column(name = "video_url", nullable = false, columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "nickname", columnDefinition = "TEXT")
    private String nickname;

    @Column(name = "avatar", columnDefinition = "TEXT")
    private String avatar;

    @Column(name = "liked_count")
    private Integer likedCount;

    @Column(name = "add_ts")
    private Long addTs;

    @Column(name = "last_modify_ts")
    private Long lastModifyTs;

    @Column(name = "video_type", columnDefinition = "TEXT")
    private String videoType;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "desc", columnDefinition = "TEXT")
    private String desc;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "disliked_count", columnDefinition = "TEXT")
    private String dislikedCount;

    @Column(name = "video_play_count", columnDefinition = "TEXT")
    private String videoPlayCount;

    @Column(name = "video_favorite_count", columnDefinition = "TEXT")
    private String videoFavoriteCount;

    @Column(name = "video_share_count", columnDefinition = "TEXT")
    private String videoShareCount;

    @Column(name = "video_coin_count", columnDefinition = "TEXT")
    private String videoCoinCount;

    @Column(name = "video_danmaku", columnDefinition = "TEXT")
    private String videoDanmaku;

    @Column(name = "video_comment", columnDefinition = "TEXT")
    private String videoComment;

    @Column(name = "video_cover_url", columnDefinition = "TEXT")
    private String videoCoverUrl;

    @Column(name = "source_keyword", columnDefinition = "TEXT DEFAULT ''")
    private String sourceKeyword = "";

    @Column(name = "topic_id", length = 64)
    private String topicId;

    @Column(name = "crawling_task_id", length = 64)
    private String crawlingTaskId;

    public BilibiliVideo() {}

    // Getters & Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Integer getLikedCount() { return likedCount; }
    public void setLikedCount(Integer lc) { this.likedCount = lc; }
    public Long getAddTs() { return addTs; }
    public void setAddTs(Long addTs) { this.addTs = addTs; }
    public Long getLastModifyTs() { return lastModifyTs; }
    public void setLastModifyTs(Long t) { this.lastModifyTs = t; }
    public String getVideoType() { return videoType; }
    public void setVideoType(String vt) { this.videoType = vt; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public Long getCreateTime() { return createTime; }
    public void setCreateTime(Long ct) { this.createTime = ct; }
    public String getDislikedCount() { return dislikedCount; }
    public void setDislikedCount(String dc) { this.dislikedCount = dc; }
    public String getVideoPlayCount() { return videoPlayCount; }
    public void setVideoPlayCount(String c) { this.videoPlayCount = c; }
    public String getVideoFavoriteCount() { return videoFavoriteCount; }
    public void setVideoFavoriteCount(String c) { this.videoFavoriteCount = c; }
    public String getVideoShareCount() { return videoShareCount; }
    public void setVideoShareCount(String c) { this.videoShareCount = c; }
    public String getVideoCoinCount() { return videoCoinCount; }
    public void setVideoCoinCount(String c) { this.videoCoinCount = c; }
    public String getVideoDanmaku() { return videoDanmaku; }
    public void setVideoDanmaku(String d) { this.videoDanmaku = d; }
    public String getVideoComment() { return videoComment; }
    public void setVideoComment(String vc) { this.videoComment = vc; }
    public String getVideoCoverUrl() { return videoCoverUrl; }
    public void setVideoCoverUrl(String u) { this.videoCoverUrl = u; }
    public String getSourceKeyword() { return sourceKeyword; }
    public void setSourceKeyword(String sk) { this.sourceKeyword = sk; }
    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }
    public String getCrawlingTaskId() { return crawlingTaskId; }
    public void setCrawlingTaskId(String c) { this.crawlingTaskId = c; }
}
```

##### `bilibili_video_comment` — 视频评论

```java
package com.bettafish.entity.platform.bilibili;

import jakarta.persistence.*;

@Entity
@Table(
    name = "bilibili_video_comment",
    indexes = {
        @Index(name = "idx_bili_comment_id", columnList = "comment_id"),
        @Index(name = "idx_bili_comment_video", columnList = "video_id")
    }
)
public class BilibiliVideoComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "nickname", columnDefinition = "TEXT")
    private String nickname;

    @Column(name = "sex", columnDefinition = "TEXT")
    private String sex;

    @Column(name = "sign", columnDefinition = "TEXT")
    private String sign;

    @Column(name = "avatar", columnDefinition = "TEXT")
    private String avatar;

    @Column(name = "add_ts")
    private Long addTs;

    @Column(name = "last_modify_ts")
    private Long lastModifyTs;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "video_id")
    private Long videoId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "sub_comment_count", columnDefinition = "TEXT")
    private String subCommentCount;

    @Column(name = "parent_comment_id", length = 255)
    private String parentCommentId;

    @Column(name = "like_count", columnDefinition = "TEXT DEFAULT '0'")
    private String likeCount = "0";

    public BilibiliVideoComment() {}

    // Getters & Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String u) { this.userId = u; }
    public String getNickname() { return nickname; }
    public void setNickname(String n) { this.nickname = n; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public String getSign() { return sign; }
    public void setSign(String sign) { this.sign = sign; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String a) { this.avatar = a; }
    public Long getAddTs() { return addTs; }
    public void setAddTs(Long t) { this.addTs = t; }
    public Long getLastModifyTs() { return lastModifyTs; }
    public void setLastModifyTs(Long t) { this.lastModifyTs = t; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long c) { this.commentId = c; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long v) { this.videoId = v; }
    public String getContent() { return content; }
    public void setContent(String c) { this.content = c; }
    public Long getCreateTime() { return createTime; }
    public void setCreateTime(Long ct) { this.createTime = ct; }
    public String getSubCommentCount() { return subCommentCount; }
    public void setSubCommentCount(String s) { this.subCommentCount = s; }
    public String getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(String p) { this.parentCommentId = p; }
    public String getLikeCount() { return likeCount; }
    public void setLikeCount(String lc) { this.likeCount = lc; }
}
```

##### `bilibili_up_info` — UP主信息

```java
package com.bettafish.entity.platform.bilibili;

import jakarta.persistence.*;

@Entity
@Table(name = "bilibili_up_info",
       indexes = { @Index(name = "idx_bili_up_user", columnList = "user_id") })
public class BilibiliUpInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "nickname", columnDefinition = "TEXT")
    private String nickname;

    @Column(name = "sex", columnDefinition = "TEXT")
    private String sex;

    @Column(name = "sign", columnDefinition = "TEXT")
    private String sign;

    @Column(name = "avatar", columnDefinition = "TEXT")
    private String avatar;

    @Column(name = "add_ts")
    private Long addTs;

    @Column(name = "last_modify_ts")
    private Long lastModifyTs;

    @Column(name = "total_fans")
    private Integer totalFans;

    @Column(name = "total_liked")
    private Integer totalLiked;

    @Column(name = "user_rank")
    private Integer userRank;

    @Column(name = "is_official")
    private Integer isOfficial;

    public BilibiliUpInfo() {}

    // Getters & Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long u) { this.userId = u; }
    public String getNickname() { return nickname; }
    public void setNickname(String n) { this.nickname = n; }
    public String getSex() { return sex; }
    public void setSex(String s) { this.sex = s; }
    public String getSign() { return sign; }
    public void setSign(String s) { this.sign = s; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String a) { this.avatar = a; }
    public Long getAddTs() { return addTs; }
    public void setAddTs(Long t) { this.addTs = t; }
    public Long getLastModifyTs() { return lastModifyTs; }
    public void setLastModifyTs(Long t) { this.lastModifyTs = t; }
    public Integer getTotalFans() { return totalFans; }
    public void setTotalFans(Integer tf) { this.totalFans = tf; }
    public Integer getTotalLiked() { return totalLiked; }
    public void setTotalLiked(Integer tl) { this.totalLiked = tl; }
    public Integer getUserRank() { return userRank; }
    public void setUserRank(Integer ur) { this.userRank = ur; }
    public Integer getIsOfficial() { return isOfficial; }
    public void setIsOfficial(Integer io) { this.isOfficial = io; }
}
```

> Bilibili 另有 `bilibili_contact_info`（UP主-粉丝联系表，字段：`up_id`, `fan_id`, `up_name`, `fan_name`, `up_sign`, `fan_sign`, `up_avatar`, `fan_avatar`）和 `bilibili_up_dynamic`（UP主动态表，字段：`dynamic_id`, `user_id`, `user_name`, `text`, `type`, `pub_ts`, `total_comments`, `total_forwards`, `total_liked`），结构类似，此处省略完整代码。

---

#### 3.2.4 抖音（Douyin）— 3 张表

##### `douyin_aweme` — 抖音短视频

```java
package com.bettafish.entity.platform.douyin;

import jakarta.persistence.*;

@Entity
@Table(
    name = "douyin_aweme",
    indexes = {
        @Index(name = "idx_dy_aweme_id", columnList = "aweme_id"),
        @Index(name = "idx_dy_aweme_time", columnList = "create_time")
    }
)
public class DouyinAweme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "sec_uid", length = 255)
    private String secUid;

    @Column(name = "short_user_id", length = 255)
    private String shortUserId;

    @Column(name = "user_unique_id", length = 255)
    private String userUniqueId;

    @Column(name = "nickname", columnDefinition = "TEXT")
    private String nickname;

    @Column(name = "avatar", columnDefinition = "TEXT")
    private String avatar;

    @Column(name = "user_signature", columnDefinition = "TEXT")
    private String userSignature;

    @Column(name = "ip_location", columnDefinition = "TEXT")
    private String ipLocation;

    @Column(name = "add_ts")
    private Long addTs;

    @Column(name = "last_modify_ts")
    private Long lastModifyTs;

    @Column(name = "aweme_id", length = 64)
    private String awemeId;

    @Column(name = "aweme_type", columnDefinition = "TEXT")
    private String awemeType;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "desc", columnDefinition = "TEXT")
    private String desc;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "liked_count", columnDefinition = "TEXT")
    private String likedCount;

    @Column(name = "comment_count", columnDefinition = "TEXT")
    private String commentCount;

    @Column(name = "share_count", columnDefinition = "TEXT")
    private String shareCount;

    @Column(name = "collected_count", columnDefinition = "TEXT")
    private String collectedCount;

    @Column(name = "aweme_url", columnDefinition = "TEXT")
    private String awemeUrl;

    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    @Column(name = "video_download_url", columnDefinition = "TEXT")
    private String videoDownloadUrl;

    @Column(name = "music_download_url", columnDefinition = "TEXT")
    private String musicDownloadUrl;

    @Column(name = "note_download_url", columnDefinition = "TEXT")
    private String noteDownloadUrl;

    @Column(name = "source_keyword", columnDefinition = "TEXT DEFAULT ''")
    private String sourceKeyword = "";

    @Column(name = "topic_id", length = 64)
    private String topicId;

    @Column(name = "crawling_task_id", length = 64)
    private String crawlingTaskId;

    public DouyinAweme() {}

    // Getters & Setters（模式与上述实体相同，完整代码参见项目源码）
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String u) { this.userId = u; }
    public String getSecUid() { return secUid; }
    public void setSecUid(String s) { this.secUid = s; }
    public String getAwemeId() { return awemeId; }
    public void setAwemeId(String a) { this.awemeId = a; }
    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }
    public String getDesc() { return desc; }
    public void setDesc(String d) { this.desc = d; }
    public String getTopicId() { return topicId; }
    public void setTopicId(String t) { this.topicId = t; }
    public String getCrawlingTaskId() { return crawlingTaskId; }
    public void setCrawlingTaskId(String c) { this.crawlingTaskId = c; }
    // ... 其余 getter/setter 省略，模式完全相同
}
```

> `douyin_aweme_comment` 评论表特有字段：`sec_uid`, `short_user_id`, `user_unique_id`, `user_signature`, `pictures`。`dy_creator` 创作者表特有字段：`videos_count`（String 类型）。

---

#### 3.2.5 其余平台关键差异

由于各平台实体结构高度相似，以下列出每个平台相对于"公共模式"的**关键差异字段**，完整代码参见项目源码仓库。

##### 快手（Kuaishou）— 2 张表

- **`kuaishou_video`**：特有字段 `viewd_count`（注意原版拼写）、`video_cover_url`、`video_play_url`。含 `topic_id` / `crawling_task_id`。
- **`kuaishou_video_comment`**：字段精简，无 `parent_comment_id`、无 `like_count`。

> 快手没有独立的创作者表。

##### 微博（Weibo）— 3 张表

- **`weibo_note`**：特有字段 `gender`、`profile_url`、`create_date_time`（额外的日期时间字符串）、`comments_count`、`shared_count`。含 `topic_id` / `crawling_task_id`。
- **`weibo_note_comment`**：特有字段 `gender`、`profile_url`、`create_date_time`、`comment_like_count`。
- **`weibo_creator`**：特有字段 `tag_list`。

##### 贴吧（Tieba）— 3 张表

- **`tieba_note`**：结构差异较大，使用 `user_link`/`user_nickname`/`user_avatar` 替代标准命名，含 `tieba_id`、`tieba_name`、`tieba_link`、`total_replay_num`、`total_replay_page`，`publish_time` 为 String 类型。含 `topic_id` / `crawling_task_id`。
- **`tieba_comment`**：同样使用 `user_link`/`user_nickname`/`user_avatar`，含 `tieba_*` 字段。
- **`tieba_creator`**：特有字段 `user_name`（区别于 `nickname`）、`registration_duration`。

##### 知乎（Zhihu）— 3 张表

- **`zhihu_content`**：特有字段 `content_id`、`content_type`（answer/article/question）、`content_text`、`content_url`、`question_id`、`created_time`（String）、`updated_time`、`voteup_count`（Integer）、`user_link`、`user_url_token`。含 `topic_id` / `crawling_task_id`。
- **`zhihu_comment`**：特有字段 `content_id`、`content_type`、`publish_time`（String）、`dislike_count`（Integer）、`user_link`。
- **`zhihu_creator`**：特有字段 `url_token`、`anwser_count`（注意原版拼写）、`video_count`、`question_count`、`article_count`、`column_count`、`get_voteup_count`，数值型字段全部为 Integer（user_id 有 UNIQUE 约束）。

---

#### 3.2.6 完整平台数据表清单

| 序号 | 表名 | 平台 | 类型 | 实体类 | 包路径 |
|------|------|------|------|--------|--------|
| 1 | `bilibili_video` | Bilibili | 内容 | `BilibiliVideo` | `entity.platform.bilibili` |
| 2 | `bilibili_video_comment` | Bilibili | 评论 | `BilibiliVideoComment` | `entity.platform.bilibili` |
| 3 | `bilibili_up_info` | Bilibili | 创作者 | `BilibiliUpInfo` | `entity.platform.bilibili` |
| 4 | `bilibili_contact_info` | Bilibili | 联系人 | `BilibiliContactInfo` | `entity.platform.bilibili` |
| 5 | `bilibili_up_dynamic` | Bilibili | 动态 | `BilibiliUpDynamic` | `entity.platform.bilibili` |
| 6 | `douyin_aweme` | 抖音 | 内容 | `DouyinAweme` | `entity.platform.douyin` |
| 7 | `douyin_aweme_comment` | 抖音 | 评论 | `DouyinAwemeComment` | `entity.platform.douyin` |
| 8 | `dy_creator` | 抖音 | 创作者 | `DyCreator` | `entity.platform.douyin` |
| 9 | `kuaishou_video` | 快手 | 内容 | `KuaishouVideo` | `entity.platform.kuaishou` |
| 10 | `kuaishou_video_comment` | 快手 | 评论 | `KuaishouVideoComment` | `entity.platform.kuaishou` |
| 11 | `weibo_note` | 微博 | 内容 | `WeiboNote` | `entity.platform.weibo` |
| 12 | `weibo_note_comment` | 微博 | 评论 | `WeiboNoteComment` | `entity.platform.weibo` |
| 13 | `weibo_creator` | 微博 | 创作者 | `WeiboCreator` | `entity.platform.weibo` |
| 14 | `xhs_note` | 小红书 | 内容 | `XhsNote` | `entity.platform.xhs` |
| 15 | `xhs_note_comment` | 小红书 | 评论 | `XhsNoteComment` | `entity.platform.xhs` |
| 16 | `xhs_creator` | 小红书 | 创作者 | `XhsCreator` | `entity.platform.xhs` |
| 17 | `tieba_note` | 贴吧 | 内容 | `TiebaNoteEntity` | `entity.platform.tieba` |
| 18 | `tieba_comment` | 贴吧 | 评论 | `TiebaComment` | `entity.platform.tieba` |
| 19 | `tieba_creator` | 贴吧 | 创作者 | `TiebaCreator` | `entity.platform.tieba` |
| 20 | `zhihu_content` | 知乎 | 内容 | `ZhihuContent` | `entity.platform.zhihu` |
| 21 | `zhihu_comment` | 知乎 | 评论 | `ZhihuComment` | `entity.platform.zhihu` |
| 22 | `zhihu_creator` | 知乎 | 创作者 | `ZhihuCreator` | `entity.platform.zhihu` |

> **合计**：核心调度表 4 张 + 平台数据表 22 张 = **26 张业务表**，加上 Java 新增的论坛消息表共 **27 张**，外加 2 个视图。

---

### 3.3 论坛消息表（Java 版新增）

Java 版引入了 `ForumDiscussion` 多智能体讨论机制。多个分析引擎（Insight Engine、Media Engine、Query Engine）在一个虚拟"论坛"中围绕话题展开多轮讨论，需要一张表来持久化每一轮的发言记录。

#### 3.3.1 `forum_messages` 表设计

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT AUTO_INCREMENT | PK | 自增主键 |
| `session_id` | VARCHAR(64) | NOT NULL, INDEX | 讨论会话 ID |
| `sender_engine` | VARCHAR(32) | NOT NULL | 发言引擎标识 |
| `message_type` | VARCHAR(16) | NOT NULL | 消息类型 |
| `content` | TEXT | NOT NULL | 消息内容（Markdown） |
| `timestamp` | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 发言时间 |
| `round_number` | INT | NOT NULL | 讨论轮次编号 |
| `topic_id` | VARCHAR(64) | INDEX, nullable | 关联话题 ID |
| `parent_message_id` | BIGINT | nullable | 父消息 ID |
| `metadata` | TEXT | nullable | 附加元数据 JSON |

#### 3.3.2 JPA 实体

```java
package com.bettafish.entity.forum;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "forum_messages",
    indexes = {
        @Index(name = "idx_forum_session", columnList = "session_id"),
        @Index(name = "idx_forum_topic", columnList = "topic_id"),
        @Index(name = "idx_forum_round", columnList = "session_id, round_number"),
        @Index(name = "idx_forum_sender", columnList = "sender_engine"),
        @Index(name = "idx_forum_timestamp", columnList = "timestamp")
    }
)
public class ForumMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 讨论会话 ID */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /** 发言引擎标识 */
    @Column(name = "sender_engine", nullable = false, length = 32)
    private String senderEngine;

    /** 消息类型 */
    @Column(name = "message_type", nullable = false, length = 16)
    private String messageType;

    /** 消息内容（Markdown） */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 发言时间 */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /** 讨论轮次 */
    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    /** 关联话题 ID */
    @Column(name = "topic_id", length = 64)
    private String topicId;

    /** 父消息 ID（回复链） */
    @Column(name = "parent_message_id")
    private Long parentMessageId;

    /** 附加元数据 JSON */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    // ========== Constructors ==========

    public ForumMessage() {}

    public ForumMessage(String sessionId, String senderEngine, String messageType,
                        String content, Integer roundNumber) {
        this.sessionId = sessionId;
        this.senderEngine = senderEngine;
        this.messageType = messageType;
        this.content = content;
        this.roundNumber = roundNumber;
        this.timestamp = LocalDateTime.now();
    }

    // ========== Getters & Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getSenderEngine() { return senderEngine; }
    public void setSenderEngine(String se) { this.senderEngine = se; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String mt) { this.messageType = mt; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime ts) { this.timestamp = ts; }

    public Integer getRoundNumber() { return roundNumber; }
    public void setRoundNumber(Integer rn) { this.roundNumber = rn; }

    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }

    public Long getParentMessageId() { return parentMessageId; }
    public void setParentMessageId(Long pmi) { this.parentMessageId = pmi; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return String.format("[Round %d] %s (%s): %s",
            roundNumber, senderEngine, messageType,
            content.length() > 100 ? content.substring(0, 100) + "..." : content);
    }
}
```

#### 3.3.3 引擎与消息类型枚举

```java
package com.bettafish.entity.forum;

/**
 * 论坛发言引擎枚举
 */
public enum SenderEngine {
    INSIGHT("insight_engine", "洞察引擎"),
    MEDIA("media_engine", "媒体引擎"),
    QUERY("query_engine", "查询引擎"),
    FORUM_HOST("forum_host", "论坛主持人"),
    REPORT("report_engine", "报告引擎");

    private final String code;
    private final String displayName;

    SenderEngine(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
}
```

```java
package com.bettafish.entity.forum;

/**
 * 论坛消息类型枚举
 */
public enum MessageType {
    ANALYSIS("analysis", "分析发言"),
    QUESTION("question", "提问"),
    RESPONSE("response", "回应"),
    SUMMARY("summary", "轮次总结"),
    CONSENSUS("consensus", "共识结论");

    private final String code;
    private final String displayName;

    MessageType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
}
```

---

### 3.4 SQL DDL

本节给出核心表的 `CREATE TABLE` 语句、平台表的 `ALTER TABLE` 扩展语句，以及两个统计视图的定义。DDL 面向 MySQL 8.0+（InnoDB 引擎，`utf8mb4` 字符集），同时兼顾 PostgreSQL 的基本兼容性。

#### 3.4.1 核心调度表 DDL

```sql
-- ============================================================
-- 1. daily_news — 每日热点新闻
-- ============================================================
CREATE TABLE IF NOT EXISTS `daily_news` (
    `id`               INT           NOT NULL AUTO_INCREMENT,
    `news_id`          VARCHAR(128)  NOT NULL COMMENT '新闻唯一标识符',
    `source_platform`  VARCHAR(32)   NOT NULL COMMENT '来源平台',
    `title`            VARCHAR(500)  NOT NULL COMMENT '新闻标题',
    `url`              VARCHAR(512)  DEFAULT NULL COMMENT '新闻链接',
    `description`      TEXT          DEFAULT NULL COMMENT '新闻描述/摘要',
    `extra_info`       TEXT          DEFAULT NULL COMMENT '附加信息 JSON',
    `crawl_date`       DATE          NOT NULL COMMENT '抓取日期',
    `rank_position`    INT           DEFAULT NULL COMMENT '热搜排名',
    `add_ts`           BIGINT        NOT NULL COMMENT '创建时间戳',
    `last_modify_ts`   BIGINT        NOT NULL COMMENT '最后修改时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_daily_news_id_unique` (`news_id`),
    UNIQUE KEY `uq_daily_news_composite` (`news_id`, `source_platform`, `crawl_date`),
    INDEX `idx_daily_news_date` (`crawl_date`),
    INDEX `idx_daily_news_platform` (`source_platform`),
    INDEX `idx_daily_news_rank` (`rank_position`),
    INDEX `idx_news_date_platform` (`crawl_date`, `source_platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='每日热点新闻表';

-- ============================================================
-- 2. daily_topics — 每日提取话题
-- ============================================================
CREATE TABLE IF NOT EXISTS `daily_topics` (
    `id`                 INT           NOT NULL AUTO_INCREMENT,
    `topic_id`           VARCHAR(64)   NOT NULL COMMENT '话题唯一标识符',
    `topic_name`         VARCHAR(255)  NOT NULL COMMENT '话题名称',
    `topic_description`  TEXT          DEFAULT NULL COMMENT '话题描述',
    `keywords`           TEXT          DEFAULT NULL COMMENT '关键词 JSON',
    `extract_date`       DATE          NOT NULL COMMENT '提取日期',
    `relevance_score`    FLOAT         DEFAULT NULL COMMENT '关联评分',
    `news_count`         INT           DEFAULT 0 COMMENT '关联新闻数',
    `processing_status`  VARCHAR(16)   DEFAULT 'pending' COMMENT '处理状态',
    `add_ts`             BIGINT        NOT NULL COMMENT '创建时间戳',
    `last_modify_ts`     BIGINT        NOT NULL COMMENT '最后修改时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_daily_topics_id_unique` (`topic_id`),
    UNIQUE KEY `uq_daily_topics_composite` (`topic_id`, `extract_date`),
    INDEX `idx_daily_topics_date` (`extract_date`),
    INDEX `idx_daily_topics_status` (`processing_status`),
    INDEX `idx_daily_topics_score` (`relevance_score`),
    INDEX `idx_topic_date_status` (`extract_date`, `processing_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='每日话题提取表';

-- ============================================================
-- 3. topic_news_relation — 话题-新闻关联
-- ============================================================
CREATE TABLE IF NOT EXISTS `topic_news_relation` (
    `id`             INT          NOT NULL AUTO_INCREMENT,
    `topic_id`       VARCHAR(64)  NOT NULL COMMENT '话题 ID',
    `news_id`        VARCHAR(128) NOT NULL COMMENT '新闻 ID',
    `relation_score` FLOAT        DEFAULT NULL COMMENT '关联评分',
    `extract_date`   DATE         NOT NULL COMMENT '提取日期',
    `add_ts`         BIGINT       NOT NULL COMMENT '创建时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_topic_news_date` (`topic_id`, `news_id`, `extract_date`),
    INDEX `idx_relation_extract_date` (`extract_date`),
    CONSTRAINT `fk_relation_topic` FOREIGN KEY (`topic_id`)
        REFERENCES `daily_topics` (`topic_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_relation_news` FOREIGN KEY (`news_id`)
        REFERENCES `daily_news` (`news_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='话题-新闻多对多关联表';

-- ============================================================
-- 4. crawling_tasks — 平台爬取任务
-- ============================================================
CREATE TABLE IF NOT EXISTS `crawling_tasks` (
    `id`              INT          NOT NULL AUTO_INCREMENT,
    `task_id`         VARCHAR(64)  NOT NULL COMMENT '任务唯一标识',
    `topic_id`        VARCHAR(64)  NOT NULL COMMENT '关联话题',
    `platform`        VARCHAR(32)  NOT NULL COMMENT '目标平台',
    `search_keywords` TEXT         NOT NULL COMMENT '搜索关键词 JSON',
    `task_status`     VARCHAR(16)  DEFAULT 'pending' COMMENT '任务状态',
    `start_time`      BIGINT       DEFAULT NULL COMMENT '开始时间戳',
    `end_time`        BIGINT       DEFAULT NULL COMMENT '结束时间戳',
    `total_crawled`   INT          DEFAULT 0 COMMENT '总抓取数',
    `success_count`   INT          DEFAULT 0 COMMENT '成功数',
    `error_count`     INT          DEFAULT 0 COMMENT '错误数',
    `error_message`   TEXT         DEFAULT NULL COMMENT '错误信息',
    `config_params`   TEXT         DEFAULT NULL COMMENT '配置参数 JSON',
    `scheduled_date`  DATE         NOT NULL COMMENT '计划执行日期',
    `add_ts`          BIGINT       NOT NULL COMMENT '创建时间戳',
    `last_modify_ts`  BIGINT       NOT NULL COMMENT '最后修改时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_crawling_tasks_task_id` (`task_id`),
    INDEX `idx_task_topic_id` (`topic_id`),
    INDEX `idx_task_platform` (`platform`),
    INDEX `idx_task_status` (`task_status`),
    INDEX `idx_task_scheduled_date` (`scheduled_date`),
    INDEX `idx_task_topic_platform` (`topic_id`, `platform`, `task_status`),
    CONSTRAINT `fk_task_topic` FOREIGN KEY (`topic_id`)
        REFERENCES `daily_topics` (`topic_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='平台爬取任务调度表';

-- ============================================================
-- 5. forum_messages — 论坛讨论消息（Java 版新增）
-- ============================================================
CREATE TABLE IF NOT EXISTS `forum_messages` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `session_id`        VARCHAR(64)  NOT NULL COMMENT '讨论会话 ID',
    `sender_engine`     VARCHAR(32)  NOT NULL COMMENT '发言引擎',
    `message_type`      VARCHAR(16)  NOT NULL COMMENT '消息类型',
    `content`           TEXT         NOT NULL COMMENT '消息内容 Markdown',
    `timestamp`         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `round_number`      INT          NOT NULL COMMENT '讨论轮次',
    `topic_id`          VARCHAR(64)  DEFAULT NULL COMMENT '关联话题 ID',
    `parent_message_id` BIGINT       DEFAULT NULL COMMENT '父消息 ID',
    `metadata`          TEXT         DEFAULT NULL COMMENT '元数据 JSON',
    PRIMARY KEY (`id`),
    INDEX `idx_forum_session` (`session_id`),
    INDEX `idx_forum_topic` (`topic_id`),
    INDEX `idx_forum_round` (`session_id`, `round_number`),
    INDEX `idx_forum_sender` (`sender_engine`),
    INDEX `idx_forum_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='多智能体论坛讨论消息表';
```

#### 3.4.2 平台内容表 ALTER 语句

为每个平台内容主表添加 `topic_id` 和 `crawling_task_id` 关联字段（幂等操作）：

```sql
-- ============================================================
-- 为平台内容表添加话题/任务关联字段
-- ============================================================

ALTER TABLE `xhs_note`
    ADD COLUMN IF NOT EXISTS `topic_id`         VARCHAR(64) DEFAULT NULL
        COMMENT '关联话题 ID',
    ADD COLUMN IF NOT EXISTS `crawling_task_id` VARCHAR(64) DEFAULT NULL
        COMMENT '关联爬取任务 ID';

ALTER TABLE `douyin_aweme`
    ADD COLUMN IF NOT EXISTS `topic_id`         VARCHAR(64) DEFAULT NULL
        COMMENT '关联话题 ID',
    ADD COLUMN IF NOT EXISTS `crawling_task_id` VARCHAR(64) DEFAULT NULL
        COMMENT '关联爬取任务 ID';

ALTER TABLE `kuaishou_video`
    ADD COLUMN IF NOT EXISTS `topic_id`         VARCHAR(64) DEFAULT NULL
        COMMENT '关联话题 ID',
    ADD COLUMN IF NOT EXISTS `crawling_task_id` VARCHAR(64) DEFAULT NULL
        COMMENT '关联爬取任务 ID';

ALTER TABLE `bilibili_video`
    ADD COLUMN IF NOT EXISTS `topic_id`         VARCHAR(64) DEFAULT NULL
        COMMENT '关联话题 ID',
    ADD COLUMN IF NOT EXISTS `crawling_task_id` VARCHAR(64) DEFAULT NULL
        COMMENT '关联爬取任务 ID';

ALTER TABLE `weibo_note`
    ADD COLUMN IF NOT EXISTS `topic_id`         VARCHAR(64) DEFAULT NULL
        COMMENT '关联话题 ID',
    ADD COLUMN IF NOT EXISTS `crawling_task_id` VARCHAR(64) DEFAULT NULL
        COMMENT '关联爬取任务 ID';

ALTER TABLE `tieba_note`
    ADD COLUMN IF NOT EXISTS `topic_id`         VARCHAR(64) DEFAULT NULL
        COMMENT '关联话题 ID',
    ADD COLUMN IF NOT EXISTS `crawling_task_id` VARCHAR(64) DEFAULT NULL
        COMMENT '关联爬取任务 ID';

ALTER TABLE `zhihu_content`
    ADD COLUMN IF NOT EXISTS `topic_id`         VARCHAR(64) DEFAULT NULL
        COMMENT '关联话题 ID',
    ADD COLUMN IF NOT EXISTS `crawling_task_id` VARCHAR(64) DEFAULT NULL
        COMMENT '关联爬取任务 ID';
```

#### 3.4.3 统计视图 DDL

```sql
-- ============================================================
-- 视图 1: v_topic_crawling_stats — 话题爬取统计
-- ============================================================
CREATE OR REPLACE VIEW `v_topic_crawling_stats` AS
SELECT
    dt.topic_id,
    dt.topic_name,
    dt.extract_date,
    dt.processing_status,
    COUNT(ct.id)                                                    AS total_tasks,
    SUM(CASE WHEN ct.task_status = 'completed' THEN 1 ELSE 0 END)  AS completed_tasks,
    SUM(CASE WHEN ct.task_status = 'failed'    THEN 1 ELSE 0 END)  AS failed_tasks,
    SUM(CASE WHEN ct.task_status = 'running'   THEN 1 ELSE 0 END)  AS running_tasks,
    SUM(CASE WHEN ct.task_status = 'pending'   THEN 1 ELSE 0 END)  AS pending_tasks,
    COALESCE(SUM(ct.total_crawled), 0)                              AS total_content_crawled,
    COALESCE(SUM(ct.success_count), 0)                              AS total_success,
    COALESCE(SUM(ct.error_count), 0)                                AS total_errors
FROM `daily_topics` dt
LEFT JOIN `crawling_tasks` ct ON dt.topic_id = ct.topic_id
GROUP BY dt.topic_id, dt.topic_name, dt.extract_date, dt.processing_status;

-- ============================================================
-- 视图 2: v_daily_summary — 每日汇总统计
-- ============================================================
CREATE OR REPLACE VIEW `v_daily_summary` AS
SELECT
    dn.crawl_date                                                    AS summary_date,
    COUNT(DISTINCT dn.id)                                            AS total_news,
    COUNT(DISTINCT dn.source_platform)                               AS platforms_covered,
    COUNT(DISTINCT dt.topic_id)                                      AS topics_extracted,
    COUNT(DISTINCT ct.task_id)                                       AS tasks_created,
    SUM(CASE WHEN ct.task_status = 'completed' THEN 1 ELSE 0 END)   AS tasks_completed,
    COALESCE(SUM(ct.total_crawled), 0)                               AS total_content
FROM `daily_news` dn
LEFT JOIN `topic_news_relation` tnr
    ON dn.news_id = tnr.news_id AND dn.crawl_date = tnr.extract_date
LEFT JOIN `daily_topics` dt
    ON tnr.topic_id = dt.topic_id
LEFT JOIN `crawling_tasks` ct
    ON dt.topic_id = ct.topic_id AND dn.crawl_date = ct.scheduled_date
GROUP BY dn.crawl_date
ORDER BY dn.crawl_date DESC;
```

---

### 3.5 Spring Data JPA Repositories

Repository 层是 Spring Data JPA 的核心抽象，通过接口声明即可获得标准 CRUD 功能，并通过方法命名约定或 `@Query` 注解实现自定义查询。

#### 3.5.1 核心调度表 Repositories

##### DailyNewsRepository

```java
package com.bettafish.repository.core;

import com.bettafish.entity.core.DailyNews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyNewsRepository extends JpaRepository<DailyNews, Integer> {

    /** 按 newsId 查找（业务主键） */
    Optional<DailyNews> findByNewsId(String newsId);

    /** 按日期查找当日所有新闻 */
    List<DailyNews> findByCrawlDate(LocalDate crawlDate);

    /** 按日期 + 平台查找 */
    List<DailyNews> findByCrawlDateAndSourcePlatform(
        LocalDate crawlDate, String sourcePlatform);

    /** 按日期查找，按排名排序 */
    List<DailyNews> findByCrawlDateOrderByRankPositionAsc(LocalDate crawlDate);

    /** 按平台分页查找 */
    Page<DailyNews> findBySourcePlatform(String sourcePlatform, Pageable pageable);

    /** 按日期范围查找 */
    List<DailyNews> findByCrawlDateBetween(LocalDate start, LocalDate end);

    /** 统计某日各平台的新闻数量 */
    @Query("SELECT n.sourcePlatform, COUNT(n) FROM DailyNews n " +
           "WHERE n.crawlDate = :date GROUP BY n.sourcePlatform")
    List<Object[]> countByPlatformAndDate(@Param("date") LocalDate date);

    /** 获取某日期的热搜 Top N（使用 Pageable 控制数量） */
    @Query("SELECT n FROM DailyNews n WHERE n.crawlDate = :date " +
           "AND n.rankPosition IS NOT NULL ORDER BY n.rankPosition ASC")
    List<DailyNews> findTopRankedByDate(
        @Param("date") LocalDate date, Pageable pageable);

    /** 检查指定 newsId 是否已存在 */
    boolean existsByNewsId(String newsId);

    /** 按日期统计新闻数量 */
    long countByCrawlDate(LocalDate crawlDate);
}
```

##### DailyTopicRepository

```java
package com.bettafish.repository.core;

import com.bettafish.entity.core.DailyTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyTopicRepository extends JpaRepository<DailyTopic, Integer> {

    /** 按 topicId 查找（业务主键） */
    Optional<DailyTopic> findByTopicId(String topicId);

    /** 按日期查找所有话题 */
    List<DailyTopic> findByExtractDate(LocalDate extractDate);

    /** 按日期 + 状态查找 */
    List<DailyTopic> findByExtractDateAndProcessingStatus(
        LocalDate extractDate, String processingStatus);

    /** 查找所有待处理话题 */
    List<DailyTopic> findByProcessingStatus(String processingStatus);

    /** 按关联评分降序排列 */
    List<DailyTopic> findByExtractDateOrderByRelevanceScoreDesc(
        LocalDate extractDate);

    /** 更新话题处理状态 */
    @Modifying
    @Transactional
    @Query("UPDATE DailyTopic t SET t.processingStatus = :status, " +
           "t.lastModifyTs = :ts WHERE t.topicId = :topicId")
    int updateProcessingStatus(@Param("topicId") String topicId,
                               @Param("status") String status,
                               @Param("ts") Long ts);

    /** 增加关联新闻计数 */
    @Modifying
    @Transactional
    @Query("UPDATE DailyTopic t SET t.newsCount = t.newsCount + :delta, " +
           "t.lastModifyTs = :ts WHERE t.topicId = :topicId")
    int incrementNewsCount(@Param("topicId") String topicId,
                           @Param("delta") int delta,
                           @Param("ts") Long ts);

    /** 统计每日各状态的话题数 */
    @Query("SELECT t.processingStatus, COUNT(t) FROM DailyTopic t " +
           "WHERE t.extractDate = :date GROUP BY t.processingStatus")
    List<Object[]> countByStatusAndDate(@Param("date") LocalDate date);

    /** 检查指定 topicId 是否已存在 */
    boolean existsByTopicId(String topicId);
}
```

##### TopicNewsRelationRepository

```java
package com.bettafish.repository.core;

import com.bettafish.entity.core.TopicNewsRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TopicNewsRelationRepository
        extends JpaRepository<TopicNewsRelation, Integer> {

    /** 查找某个话题的所有关联新闻 */
    List<TopicNewsRelation> findByTopicId(String topicId);

    /** 查找某条新闻关联的所有话题 */
    List<TopicNewsRelation> findByNewsId(String newsId);

    /** 查找某日的所有关联记录 */
    List<TopicNewsRelation> findByExtractDate(LocalDate extractDate);

    /** 按评分降序获取某话题的高相关新闻 */
    List<TopicNewsRelation> findByTopicIdOrderByRelationScoreDesc(
        String topicId);

    /** 查找关联评分高于阈值的记录 */
    @Query("SELECT r FROM TopicNewsRelation r " +
           "WHERE r.topicId = :topicId AND r.relationScore >= :minScore " +
           "ORDER BY r.relationScore DESC")
    List<TopicNewsRelation> findHighRelevanceNews(
        @Param("topicId") String topicId,
        @Param("minScore") float minScore);

    /** 检查某个关联是否已存在 */
    boolean existsByTopicIdAndNewsIdAndExtractDate(
        String topicId, String newsId, LocalDate extractDate);

    /** 统计某话题的关联新闻数量 */
    long countByTopicId(String topicId);
}
```

##### CrawlingTaskRepository

```java
package com.bettafish.repository.core;

import com.bettafish.entity.core.CrawlingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CrawlingTaskRepository
        extends JpaRepository<CrawlingTask, Integer> {

    /** 按 taskId 查找（业务主键） */
    Optional<CrawlingTask> findByTaskId(String taskId);

    /** 查找某话题的所有任务 */
    List<CrawlingTask> findByTopicId(String topicId);

    /** 按话题 + 平台查找任务 */
    List<CrawlingTask> findByTopicIdAndPlatform(
        String topicId, String platform);

    /** 按状态查找所有任务 */
    List<CrawlingTask> findByTaskStatus(String taskStatus);

    /** 按平台查找所有任务 */
    List<CrawlingTask> findByPlatform(String platform);

    /** 按计划日期查找 */
    List<CrawlingTask> findByScheduledDate(LocalDate scheduledDate);

    /** 查找指定日期待执行的任务 */
    @Query("SELECT t FROM CrawlingTask t WHERE t.scheduledDate = :date " +
           "AND t.taskStatus = 'pending' ORDER BY t.addTs ASC")
    List<CrawlingTask> findPendingTasksByDate(
        @Param("date") LocalDate date);

    /** 更新任务状态与开始时间 */
    @Modifying
    @Transactional
    @Query("UPDATE CrawlingTask t SET t.taskStatus = :status, " +
           "t.startTime = :startTime, t.lastModifyTs = :ts " +
           "WHERE t.taskId = :taskId")
    int updateTaskStatusAndStartTime(
        @Param("taskId") String taskId,
        @Param("status") String status,
        @Param("startTime") Long startTime,
        @Param("ts") Long ts);

    /** 标记任务完成 */
    @Modifying
    @Transactional
    @Query("UPDATE CrawlingTask t SET t.taskStatus = 'completed', " +
           "t.endTime = :endTime, t.totalCrawled = :total, " +
           "t.successCount = :success, t.errorCount = :errors, " +
           "t.lastModifyTs = :ts WHERE t.taskId = :taskId")
    int completeTask(@Param("taskId") String taskId,
                     @Param("endTime") Long endTime,
                     @Param("total") int total,
                     @Param("success") int success,
                     @Param("errors") int errors,
                     @Param("ts") Long ts);

    /** 标记任务失败 */
    @Modifying
    @Transactional
    @Query("UPDATE CrawlingTask t SET t.taskStatus = 'failed', " +
           "t.endTime = :endTime, t.errorMessage = :errorMsg, " +
           "t.errorCount = t.errorCount + 1, t.lastModifyTs = :ts " +
           "WHERE t.taskId = :taskId")
    int failTask(@Param("taskId") String taskId,
                 @Param("endTime") Long endTime,
                 @Param("errorMsg") String errorMsg,
                 @Param("ts") Long ts);

    /** 话题维度的任务聚合统计 */
    @Query("SELECT t.topicId, t.platform, t.taskStatus, COUNT(t), " +
           "COALESCE(SUM(t.totalCrawled), 0), " +
           "COALESCE(SUM(t.successCount), 0) " +
           "FROM CrawlingTask t WHERE t.topicId = :topicId " +
           "GROUP BY t.topicId, t.platform, t.taskStatus")
    List<Object[]> aggregateByTopic(@Param("topicId") String topicId);

    /** 统计某平台某日期的任务完成情况 */
    @Query("SELECT t.taskStatus, COUNT(t) FROM CrawlingTask t " +
           "WHERE t.platform = :platform AND t.scheduledDate = :date " +
           "GROUP BY t.taskStatus")
    List<Object[]> countByPlatformStatusAndDate(
        @Param("platform") String platform,
        @Param("date") LocalDate date);
}
```

---

#### 3.5.2 论坛消息 Repository

```java
package com.bettafish.repository.forum;

import com.bettafish.entity.forum.ForumMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ForumMessageRepository
        extends JpaRepository<ForumMessage, Long> {

    /** 获取某次讨论会话的全部消息，按轮次和时间排序 */
    List<ForumMessage> findBySessionIdOrderByRoundNumberAscTimestampAsc(
        String sessionId);

    /** 获取某次讨论的特定轮次消息 */
    List<ForumMessage> findBySessionIdAndRoundNumberOrderByTimestampAsc(
        String sessionId, Integer roundNumber);

    /** 获取某个引擎在某次会话中的所有发言 */
    List<ForumMessage> findBySessionIdAndSenderEngineOrderByTimestampAsc(
        String sessionId, String senderEngine);

    /** 获取某话题的所有讨论会话 ID */
    @Query("SELECT DISTINCT m.sessionId FROM ForumMessage m " +
           "WHERE m.topicId = :topicId ORDER BY m.timestamp DESC")
    List<String> findSessionsByTopicId(
        @Param("topicId") String topicId);

    /** 获取某次讨论的最大轮次数 */
    @Query("SELECT MAX(m.roundNumber) FROM ForumMessage m " +
           "WHERE m.sessionId = :sessionId")
    Integer findMaxRoundNumber(
        @Param("sessionId") String sessionId);

    /** 获取某次讨论的共识/总结消息 */
    List<ForumMessage> findBySessionIdAndMessageTypeOrderByTimestampDesc(
        String sessionId, String messageType);

    /** 获取某个时间范围内的所有讨论消息 */
    List<ForumMessage> findByTimestampBetweenOrderByTimestampAsc(
        LocalDateTime start, LocalDateTime end);

    /** 统计每个引擎的发言次数 */
    @Query("SELECT m.senderEngine, COUNT(m) FROM ForumMessage m " +
           "WHERE m.sessionId = :sessionId GROUP BY m.senderEngine")
    List<Object[]> countBySenderEngine(
        @Param("sessionId") String sessionId);

    /** 按会话 ID 删除全部消息（用于重置讨论） */
    void deleteBySessionId(String sessionId);
}
```

---

#### 3.5.3 平台数据 Repositories（以小红书为例）

##### XhsNoteRepository

```java
package com.bettafish.repository.platform.xhs;

import com.bettafish.entity.platform.xhs.XhsNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface XhsNoteRepository
        extends JpaRepository<XhsNote, Integer> {

    /** 按笔记 ID 查找 */
    Optional<XhsNote> findByNoteId(String noteId);

    /** 按话题 ID 查找关联笔记 */
    List<XhsNote> findByTopicId(String topicId);

    /** 按爬取任务 ID 查找 */
    List<XhsNote> findByCrawlingTaskId(String crawlingTaskId);

    /** 按来源关键词查找 */
    List<XhsNote> findBySourceKeyword(String sourceKeyword);

    /** 按话题 ID 分页查找，按时间降序 */
    Page<XhsNote> findByTopicIdOrderByTimeDesc(
        String topicId, Pageable pageable);

    /** 全文搜索（标题或描述包含关键词） */
    @Query("SELECT n FROM XhsNote n WHERE n.topicId = :topicId " +
           "AND (n.title LIKE %:keyword% OR n.desc LIKE %:keyword%)")
    List<XhsNote> searchByKeyword(
        @Param("topicId") String topicId,
        @Param("keyword") String keyword);

    /** 获取某话题下的热门笔记（按点赞排序，原生 SQL） */
    @Query(value = "SELECT * FROM xhs_note WHERE topic_id = :topicId " +
           "ORDER BY CAST(liked_count AS UNSIGNED) DESC LIMIT :limit",
           nativeQuery = true)
    List<XhsNote> findTopLikedByTopic(
        @Param("topicId") String topicId,
        @Param("limit") int limit);

    /** 统计某话题下各类型笔记的数量 */
    @Query("SELECT n.type, COUNT(n) FROM XhsNote n " +
           "WHERE n.topicId = :topicId GROUP BY n.type")
    List<Object[]> countByTypeAndTopic(
        @Param("topicId") String topicId);

    /** 获取指定时间范围内的笔记 */
    List<XhsNote> findByTimeBetween(Long startTime, Long endTime);

    /** 检查笔记是否已存在 */
    boolean existsByNoteId(String noteId);
}
```

##### XhsNoteCommentRepository

```java
package com.bettafish.repository.platform.xhs;

import com.bettafish.entity.platform.xhs.XhsNoteComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface XhsNoteCommentRepository
        extends JpaRepository<XhsNoteComment, Integer> {

    /** 获取某笔记的全部评论 */
    List<XhsNoteComment> findByNoteIdOrderByCreateTimeDesc(String noteId);

    /** 获取某笔记的顶级评论（无父评论） */
    List<XhsNoteComment>
        findByNoteIdAndParentCommentIdIsNullOrderByCreateTimeDesc(
            String noteId);

    /** 获取某评论的子评论 */
    List<XhsNoteComment> findByParentCommentId(String parentCommentId);

    /** 分页获取某笔记的评论 */
    Page<XhsNoteComment> findByNoteId(String noteId, Pageable pageable);

    /** 获取某话题下所有笔记的评论（跨笔记聚合） */
    @Query("SELECT c FROM XhsNoteComment c WHERE c.noteId IN " +
           "(SELECT n.noteId FROM XhsNote n WHERE n.topicId = :topicId) " +
           "ORDER BY c.createTime DESC")
    List<XhsNoteComment> findCommentsByTopicId(
        @Param("topicId") String topicId);

    /** 统计某笔记的评论数量 */
    long countByNoteId(String noteId);
}
```

##### XhsCreatorRepository

```java
package com.bettafish.repository.platform.xhs;

import com.bettafish.entity.platform.xhs.XhsCreator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface XhsCreatorRepository
        extends JpaRepository<XhsCreator, Integer> {

    /** 按用户 ID 查找创作者 */
    Optional<XhsCreator> findByUserId(String userId);

    /** 检查创作者是否已存在 */
    boolean existsByUserId(String userId);
}
```

> 其余六个平台的 Repository 接口遵循相同的模式：`findBy{ContentId}`、`findByTopicId`、`findByCrawlingTaskId`。具体接口名视各平台内容 ID 字段而定（如 `findByVideoId`、`findByAwemeId`、`findByContentId` 等）。

---

#### 3.5.4 跨平台聚合查询服务

由于 JPA 不支持对不同实体的 UNION 查询，跨平台统计通过 `EntityManager` 原生 SQL 实现：

```java
package com.bettafish.service.platform;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlatformAggregationService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 获取某话题在所有平台上的内容总数。
     * 返回 List<Object[]>，每项为 [platform(String), count(Long)]。
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> countContentByTopicAcrossPlatforms(String topicId) {
        String sql = """
            SELECT 'xiaohongshu' AS platform, COUNT(*) AS cnt
              FROM xhs_note WHERE topic_id = :topicId
            UNION ALL
            SELECT 'douyin',    COUNT(*) FROM douyin_aweme    WHERE topic_id = :topicId
            UNION ALL
            SELECT 'kuaishou',  COUNT(*) FROM kuaishou_video  WHERE topic_id = :topicId
            UNION ALL
            SELECT 'bilibili',  COUNT(*) FROM bilibili_video  WHERE topic_id = :topicId
            UNION ALL
            SELECT 'weibo',     COUNT(*) FROM weibo_note      WHERE topic_id = :topicId
            UNION ALL
            SELECT 'tieba',     COUNT(*) FROM tieba_note      WHERE topic_id = :topicId
            UNION ALL
            SELECT 'zhihu',     COUNT(*) FROM zhihu_content   WHERE topic_id = :topicId
            """;
        return entityManager.createNativeQuery(sql)
            .setParameter("topicId", topicId)
            .getResultList();
    }

    /**
     * 获取某话题在所有平台上的评论总数。
     * 通过 JOIN 内容表过滤出属于该话题的评论。
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> countCommentsByTopicAcrossPlatforms(String topicId) {
        String sql = """
            SELECT 'xiaohongshu' AS platform, COUNT(*) AS cnt
              FROM xhs_note_comment c
              INNER JOIN xhs_note n ON c.note_id = n.note_id
              WHERE n.topic_id = :topicId
            UNION ALL
            SELECT 'douyin', COUNT(*)
              FROM douyin_aweme_comment c
              INNER JOIN douyin_aweme a ON c.aweme_id = a.aweme_id
              WHERE a.topic_id = :topicId
            UNION ALL
            SELECT 'bilibili', COUNT(*)
              FROM bilibili_video_comment c
              INNER JOIN bilibili_video v ON c.video_id = v.video_id
              WHERE v.topic_id = :topicId
            UNION ALL
            SELECT 'weibo', COUNT(*)
              FROM weibo_note_comment c
              INNER JOIN weibo_note n ON c.note_id = n.note_id
              WHERE n.topic_id = :topicId
            UNION ALL
            SELECT 'tieba', COUNT(*)
              FROM tieba_comment c
              INNER JOIN tieba_note n ON c.note_id = n.note_id
              WHERE n.topic_id = :topicId
            UNION ALL
            SELECT 'zhihu', COUNT(*)
              FROM zhihu_comment c
              INNER JOIN zhihu_content co ON c.content_id = co.content_id
              WHERE co.topic_id = :topicId
            """;
        return entityManager.createNativeQuery(sql)
            .setParameter("topicId", topicId)
            .getResultList();
    }
}
```

---

#### 3.5.5 全局表清单总览

下表汇总了整个系统的所有表（含 Java 版新增），以及对应的实体类和 Repository：

| # | 表名 | 分类 | 实体类 | Repository |
|---|------|------|--------|------------|
| 1 | `daily_news` | 核心调度 | `DailyNews` | `DailyNewsRepository` |
| 2 | `daily_topics` | 核心调度 | `DailyTopic` | `DailyTopicRepository` |
| 3 | `topic_news_relation` | 核心调度 | `TopicNewsRelation` | `TopicNewsRelationRepository` |
| 4 | `crawling_tasks` | 核心调度 | `CrawlingTask` | `CrawlingTaskRepository` |
| 5 | `forum_messages` | 论坛(新增) | `ForumMessage` | `ForumMessageRepository` |
| 6 | `bilibili_video` | Bilibili | `BilibiliVideo` | `BilibiliVideoRepository` |
| 7 | `bilibili_video_comment` | Bilibili | `BilibiliVideoComment` | `BilibiliVideoCommentRepository` |
| 8 | `bilibili_up_info` | Bilibili | `BilibiliUpInfo` | `BilibiliUpInfoRepository` |
| 9 | `bilibili_contact_info` | Bilibili | `BilibiliContactInfo` | `BilibiliContactInfoRepository` |
| 10 | `bilibili_up_dynamic` | Bilibili | `BilibiliUpDynamic` | `BilibiliUpDynamicRepository` |
| 11 | `douyin_aweme` | 抖音 | `DouyinAweme` | `DouyinAwemeRepository` |
| 12 | `douyin_aweme_comment` | 抖音 | `DouyinAwemeComment` | `DouyinAwemeCommentRepository` |
| 13 | `dy_creator` | 抖音 | `DyCreator` | `DyCreatorRepository` |
| 14 | `kuaishou_video` | 快手 | `KuaishouVideo` | `KuaishouVideoRepository` |
| 15 | `kuaishou_video_comment` | 快手 | `KuaishouVideoComment` | `KuaishouVideoCommentRepository` |
| 16 | `weibo_note` | 微博 | `WeiboNote` | `WeiboNoteRepository` |
| 17 | `weibo_note_comment` | 微博 | `WeiboNoteComment` | `WeiboNoteCommentRepository` |
| 18 | `weibo_creator` | 微博 | `WeiboCreator` | `WeiboCreatorRepository` |
| 19 | `xhs_note` | 小红书 | `XhsNote` | `XhsNoteRepository` |
| 20 | `xhs_note_comment` | 小红书 | `XhsNoteComment` | `XhsNoteCommentRepository` |
| 21 | `xhs_creator` | 小红书 | `XhsCreator` | `XhsCreatorRepository` |
| 22 | `tieba_note` | 贴吧 | `TiebaNoteEntity` | `TiebaNoteRepository` |
| 23 | `tieba_comment` | 贴吧 | `TiebaComment` | `TiebaCommentRepository` |
| 24 | `tieba_creator` | 贴吧 | `TiebaCreator` | `TiebaCreatorRepository` |
| 25 | `zhihu_content` | 知乎 | `ZhihuContent` | `ZhihuContentRepository` |
| 26 | `zhihu_comment` | 知乎 | `ZhihuComment` | `ZhihuCommentRepository` |
| 27 | `zhihu_creator` | 知乎 | `ZhihuCreator` | `ZhihuCreatorRepository` |

**视图**：`v_topic_crawling_stats`（话题爬取统计）、`v_daily_summary`（每日汇总）。

---

> **本章总结**
>
> 本章完成了 BettaFish 全部 27 张业务表（26 张 Python 原版 + 1 张 Java 新增 `forum_messages`）从 SQLAlchemy 到 Spring Data JPA 的完整映射。关键设计决策包括：
>
> 1. **保持字段名一致性**：JPA 实体的数据库列名与 Python 原版完全一致，确保存量数据无缝对接。
> 2. **时间戳策略**：沿用 `Long` 类型的 Unix 时间戳（`add_ts` / `last_modify_ts`），仅在 `forum_messages` 新表中使用 `LocalDateTime`。
> 3. **外键设计**：核心表之间使用业务主键（`topic_id` / `news_id` / `task_id`）而非自增 ID 作为外键目标，与 Python 原版一致。平台内容表通过可空 `topic_id` 和 `crawling_task_id` 实现松耦合关联。
> 4. **JSON 字段**：使用 `TEXT` 列 + 手动序列化，未引入 Hibernate 的 `@Type` JSON 扩展，保持框架无关性。
> 5. **Repository 设计**：每张表对应一个 Repository 接口，核心表提供丰富的自定义查询（状态更新、聚合统计、分页查询），平台表侧重按 `topic_id` 的检索。跨平台聚合通过 `PlatformAggregationService` 使用原生 SQL UNION 实现。

## Chapter 4: Agent 实现与 Prompt 设计

> 本章是 BettaFish（微舆）Java/Spring AI 重构方案的核心章节。我们将完整展示四类 Agent 的 Java 实现——包括接口定义、基类抽象、工具服务、Prompt 常量——并详细说明 ReportEngine 的六阶段流水线。所有 Prompt 均从 Python 原版翻译/适配而来，保留原始语义与结构。

---

### 4.1 Agent 基础架构

BettaFish 的三个分析引擎（QueryEngine、MediaEngine、InsightEngine）共享同一套 **搜索-反思循环（Search-Reflect Loop）** 模式。在 Java 版本中，我们将这一共性抽象为接口 + 抽象基类的层次结构，由各具体 Agent 注入不同的工具集和 Prompt 模板。

#### 4.1.1 AnalysisAgent 接口

```java
package com.bettafish.common.agent;

import com.bettafish.common.model.AgentState;
import com.bettafish.common.model.AnalysisResult;
import reactor.core.publisher.Mono;

/**
 * 所有分析 Agent 的统一接口。
 * 每个方法对应 Search-Reflect 流水线中的一个阶段。
 */
public interface AnalysisAgent {

    /**
     * 阶段 1：根据用户查询生成报告大纲（最多 5 个段落）。
     *
     * @param query 用户输入的舆情分析主题
     * @return 包含段落标题和期望内容描述的结构化列表
     */
    Mono<AgentState> generateReportStructure(String query);

    /**
     * 阶段 2：针对每个段落执行首次搜索。
     * Agent 选择最合适的工具并构造搜索查询。
     *
     * @param paragraphIndex 当前处理的段落索引
     * @return 更新后的 Agent 状态（含搜索结果）
     */
    Mono<AgentState> firstSearch(int paragraphIndex);

    /**
     * 阶段 3：对首次搜索结果进行总结，生成 800-1200 字的初始段落。
     *
     * @param paragraphIndex 当前处理的段落索引
     * @return 更新后的 Agent 状态（含段落初稿）
     */
    Mono<AgentState> firstSummary(int paragraphIndex);

    /**
     * 阶段 4：反思当前段落，识别信息缺口，选择补充搜索工具。
     * 此阶段会注入 ForumEngine 主持人的指导意见。
     *
     * @param paragraphIndex 当前处理的段落索引
     * @param roundIndex     当前反思轮次（0-2，共 3 轮）
     * @param forumGuidance  来自论坛主持人的指导（可为 null）
     * @return 更新后的 Agent 状态（含补充搜索结果）
     */
    Mono<AgentState> reflect(int paragraphIndex, int roundIndex, String forumGuidance);

    /**
     * 阶段 5：将反思搜索的新结果融入现有段落，扩充内容。
     *
     * @param paragraphIndex 当前处理的段落索引
     * @return 更新后的 Agent 状态（含扩充后的段落）
     */
    Mono<AgentState> reflectionSummary(int paragraphIndex);

    /**
     * 阶段 6：将所有段落汇总为最终格式化报告（10000+ 字）。
     *
     * @return 包含最终报告的分析结果
     */
    Mono<AnalysisResult> formatReport();
}
```

#### 4.1.2 核心数据模型

在展示基类之前，先定义流水线中使用的关键数据模型：

```java
package com.bettafish.common.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 运行时状态，贯穿整个 Search-Reflect 流水线。
 */
public class AgentState {

    private String query;
    private List<ParagraphState> paragraphs = new CopyOnWriteArrayList<>();
    private String finalReport;
    private String agentName; // "QUERY", "MEDIA", "INSIGHT"

    // --- ParagraphState 内部类 ---
    public static class ParagraphState {
        private String title;
        private String expectedContent;
        private String currentDraft;
        private List<SearchRecord> searchHistory = new CopyOnWriteArrayList<>();
        private boolean completed;

        public static class SearchRecord {
            private String toolName;
            private String searchQuery;
            private String reasoning;
            private String rawResults;
            private int roundIndex; // -1 = first search, 0/1/2 = reflection rounds
        }

        // getters, setters, builder omitted for brevity
    }

    // getters, setters omitted
}
```

```java
package com.bettafish.common.model;

/**
 * 分析结果 - Agent 完成后交付给 ForumEngine 和 ReportEngine 的产物。
 */
public record AnalysisResult(
    String agentName,
    String query,
    String report,
    Map<String, Object> metadata
) {}
```

#### 4.1.3 AbstractAnalysisAgent 基类

这是本系统最关键的类，它实现了完整的 Search-Reflect Loop 模式。三个具体 Agent 只需提供：(1) 工具列表 (2) Prompt 模板 (3) 搜索执行逻辑。

```java
package com.bettafish.common.agent;

import com.bettafish.common.model.AgentState;
import com.bettafish.common.model.AgentState.ParagraphState;
import com.bettafish.common.model.AgentState.ParagraphState.SearchRecord;
import com.bettafish.common.model.AnalysisResult;
import com.bettafish.common.model.ForumMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 抽象分析 Agent 基类。
 *
 * 实现了 BettaFish 所有分析引擎共享的 Search-Reflect Loop：
 *   1. generateReportStructure — 生成报告大纲
 *   2. firstSearch             — 每段首次搜索
 *   3. firstSummary            — 首次总结
 *   4. reflect                 — 反思循环（3 轮, 含论坛主持人指导注入）
 *   5. reflectionSummary       — 反思总结
 *   6. formatReport            — 最终报告格式化
 *
 * 子类需实现的抽象方法：
 *   - getAgentName()           — 返回 Agent 标识（QUERY/MEDIA/INSIGHT）
 *   - getPromptTemplate(stage) — 返回各阶段的 System Prompt
 *   - executeSearch(toolName, query, params) — 执行具体搜索并返回结果字符串
 *   - getToolDescriptions()    — 返回工具描述（注入到 Prompt 中）
 */
public abstract class AbstractAnalysisAgent implements AnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(AbstractAnalysisAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_REFLECTIONS = 3;

    protected final ChatClient chatClient;
    protected final ReactiveStringRedisTemplate redisTemplate;
    protected final AgentState state;

    // Redis 论坛消息频道
    private static final String FORUM_CHANNEL = "bettafish:forum:messages";

    protected AbstractAnalysisAgent(ChatClient chatClient,
                                    ReactiveStringRedisTemplate redisTemplate) {
        this.chatClient = chatClient;
        this.redisTemplate = redisTemplate;
        this.state = new AgentState();
    }

    // =====================================================
    // 子类必须实现的抽象方法
    // =====================================================

    /** 返回 Agent 名称标识，如 "QUERY", "MEDIA", "INSIGHT" */
    protected abstract String getAgentName();

    /**
     * 返回指定阶段的 System Prompt 模板。
     * @param stage 阶段枚举
     */
    protected abstract String getPromptTemplate(PromptStage stage);

    /**
     * 执行具体的搜索操作。
     * @param toolName 工具名称（由 LLM 选择）
     * @param query    搜索查询
     * @param params   附加参数（如 start_date, end_date, platform 等）
     * @return 搜索结果的文本表示
     */
    protected abstract Mono<String> executeSearch(String toolName,
                                                   String query,
                                                   Map<String, String> params);

    /** 返回当前 Agent 可用工具的文本描述（嵌入到 Prompt 中） */
    protected abstract String getToolDescriptions();

    // =====================================================
    // Prompt 阶段枚举
    // =====================================================

    public enum PromptStage {
        REPORT_STRUCTURE,
        FIRST_SEARCH,
        FIRST_SUMMARY,
        REFLECTION,
        REFLECTION_SUMMARY,
        REPORT_FORMATTING
    }

    // =====================================================
    // 主编排方法：执行完整的研究流水线
    // =====================================================

    /**
     * 执行完整的 Search-Reflect 研究流水线。
     * 这是外部调用的入口方法。
     */
    public Mono<AnalysisResult> research(String query) {
        state.setQuery(query);
        state.setAgentName(getAgentName());

        return generateReportStructure(query)
            .flatMap(s -> processParagraphsSequentially())
            .flatMap(s -> formatReport());
    }

    /**
     * 顺序处理所有段落（首次搜索 + 首次总结 + 反思循环）。
     */
    private Mono<AgentState> processParagraphsSequentially() {
        return Flux.range(0, state.getParagraphs().size())
            .concatMap(this::processSingleParagraph)
            .then(Mono.just(state));
    }

    /**
     * 处理单个段落的完整流程。
     */
    private Mono<AgentState> processSingleParagraph(int paragraphIndex) {
        log.info("[{}] 开始处理段落 {}/{}: {}",
            getAgentName(), paragraphIndex + 1,
            state.getParagraphs().size(),
            state.getParagraphs().get(paragraphIndex).getTitle());

        return firstSearch(paragraphIndex)
            .flatMap(s -> firstSummary(paragraphIndex))
            .flatMap(s -> executeReflectionLoop(paragraphIndex))
            .doOnSuccess(s -> {
                state.getParagraphs().get(paragraphIndex).setCompleted(true);
                log.info("[{}] 段落 {} 处理完成", getAgentName(), paragraphIndex + 1);
            });
    }

    /**
     * 执行反思循环（3 轮）。
     * 每轮都会尝试获取论坛主持人的最新指导意见并注入。
     */
    private Mono<AgentState> executeReflectionLoop(int paragraphIndex) {
        Mono<AgentState> chain = Mono.just(state);
        for (int round = 0; round < MAX_REFLECTIONS; round++) {
            final int r = round;
            chain = chain
                .flatMap(s -> fetchForumGuidance())
                .flatMap(guidance -> reflect(paragraphIndex, r, guidance))
                .flatMap(s -> reflectionSummary(paragraphIndex));
        }
        return chain;
    }

    // =====================================================
    // 阶段 1: 生成报告大纲
    // =====================================================

    @Override
    public Mono<AgentState> generateReportStructure(String query) {
        log.info("[{}] 阶段 1: 生成报告大纲 - query={}", getAgentName(), query);

        String systemPrompt = getPromptTemplate(PromptStage.REPORT_STRUCTURE);
        String userPrompt = "请为以下主题规划报告结构：\n\n" + query;

        return callLlm(systemPrompt, userPrompt)
            .flatMap(response -> {
                try {
                    List<Map<String, String>> structure = objectMapper.readValue(
                        extractJson(response),
                        new TypeReference<List<Map<String, String>>>() {}
                    );

                    List<ParagraphState> paragraphs = new ArrayList<>();
                    for (Map<String, String> item : structure) {
                        ParagraphState p = new ParagraphState();
                        p.setTitle(item.get("title"));
                        p.setExpectedContent(item.get("content"));
                        paragraphs.add(p);
                    }
                    state.setParagraphs(paragraphs);

                    log.info("[{}] 报告大纲生成完成，共 {} 个段落",
                        getAgentName(), paragraphs.size());

                    // 发布到论坛
                    return publishToForum(
                        "已完成报告结构规划，共 " + paragraphs.size() + " 个分析段落：\n" +
                        formatStructureForForum(paragraphs)
                    ).thenReturn(state);

                } catch (Exception e) {
                    log.error("[{}] 报告大纲解析失败", getAgentName(), e);
                    return Mono.error(new RuntimeException("报告大纲解析失败", e));
                }
            });
    }

    // =====================================================
    // 阶段 2: 首次搜索
    // =====================================================

    @Override
    public Mono<AgentState> firstSearch(int paragraphIndex) {
        ParagraphState paragraph = state.getParagraphs().get(paragraphIndex);
        log.info("[{}] 阶段 2: 首次搜索 - 段落「{}」", getAgentName(), paragraph.getTitle());

        String systemPrompt = getPromptTemplate(PromptStage.FIRST_SEARCH);
        String userPrompt = buildFirstSearchUserPrompt(paragraph);

        return callLlm(systemPrompt, userPrompt)
            .flatMap(response -> {
                try {
                    JsonNode decision = objectMapper.readTree(extractJson(response));
                    String toolName = decision.get("search_tool").asText();
                    String searchQuery = decision.get("search_query").asText();
                    String reasoning = decision.get("reasoning").asText();

                    // 提取可选参数
                    Map<String, String> params = new HashMap<>();
                    if (decision.has("start_date") && !decision.get("start_date").isNull()) {
                        params.put("start_date", decision.get("start_date").asText());
                    }
                    if (decision.has("end_date") && !decision.get("end_date").isNull()) {
                        params.put("end_date", decision.get("end_date").asText());
                    }
                    if (decision.has("platform") && !decision.get("platform").isNull()) {
                        params.put("platform", decision.get("platform").asText());
                    }
                    if (decision.has("time_period") && !decision.get("time_period").isNull()) {
                        params.put("time_period", decision.get("time_period").asText());
                    }

                    log.info("[{}] 选择工具: {} | 查询: {} | 理由: {}",
                        getAgentName(), toolName, searchQuery, reasoning);

                    return executeSearch(toolName, searchQuery, params)
                        .doOnSuccess(results -> {
                            SearchRecord record = new SearchRecord();
                            record.setToolName(toolName);
                            record.setSearchQuery(searchQuery);
                            record.setReasoning(reasoning);
                            record.setRawResults(results);
                            record.setRoundIndex(-1); // 首次搜索
                            paragraph.getSearchHistory().add(record);
                        })
                        .thenReturn(state);

                } catch (Exception e) {
                    log.error("[{}] 首次搜索决策解析失败", getAgentName(), e);
                    return Mono.error(new RuntimeException("首次搜索决策解析失败", e));
                }
            });
    }

    // =====================================================
    // 阶段 3: 首次总结
    // =====================================================

    @Override
    public Mono<AgentState> firstSummary(int paragraphIndex) {
        ParagraphState paragraph = state.getParagraphs().get(paragraphIndex);
        log.info("[{}] 阶段 3: 首次总结 - 段落「{}」", getAgentName(), paragraph.getTitle());

        String systemPrompt = getPromptTemplate(PromptStage.FIRST_SUMMARY);
        String userPrompt = buildFirstSummaryUserPrompt(paragraph);

        return callLlm(systemPrompt, userPrompt)
            .flatMap(response -> {
                try {
                    JsonNode result = objectMapper.readTree(extractJson(response));
                    String draft = result.get("paragraph_latest_state").asText();
                    paragraph.setCurrentDraft(draft);

                    log.info("[{}] 首次总结完成，段落长度: {} 字",
                        getAgentName(), draft.length());

                    // 发布初稿到论坛
                    return publishToForum(
                        "段落「" + paragraph.getTitle() + "」初稿完成（" +
                        draft.length() + " 字），正在进入反思循环..."
                    ).thenReturn(state);

                } catch (Exception e) {
                    log.error("[{}] 首次总结解析失败", getAgentName(), e);
                    return Mono.error(new RuntimeException("首次总结解析失败", e));
                }
            });
    }

    // =====================================================
    // 阶段 4: 反思（含论坛主持人指导注入）
    // =====================================================

    @Override
    public Mono<AgentState> reflect(int paragraphIndex, int roundIndex,
                                     String forumGuidance) {
        ParagraphState paragraph = state.getParagraphs().get(paragraphIndex);
        log.info("[{}] 阶段 4: 反思轮次 {}/3 - 段落「{}」",
            getAgentName(), roundIndex + 1, paragraph.getTitle());

        String systemPrompt = getPromptTemplate(PromptStage.REFLECTION);

        // 注入论坛主持人指导
        String userPrompt = buildReflectionUserPrompt(paragraph, forumGuidance);

        return callLlm(systemPrompt, userPrompt)
            .flatMap(response -> {
                try {
                    JsonNode decision = objectMapper.readTree(extractJson(response));
                    String toolName = decision.get("search_tool").asText();
                    String searchQuery = decision.get("search_query").asText();
                    String reasoning = decision.get("reasoning").asText();

                    Map<String, String> params = extractOptionalParams(decision);

                    log.info("[{}] 反思轮次 {} - 工具: {} | 查询: {}",
                        getAgentName(), roundIndex + 1, toolName, searchQuery);

                    return executeSearch(toolName, searchQuery, params)
                        .doOnSuccess(results -> {
                            SearchRecord record = new SearchRecord();
                            record.setToolName(toolName);
                            record.setSearchQuery(searchQuery);
                            record.setReasoning(reasoning);
                            record.setRawResults(results);
                            record.setRoundIndex(roundIndex);
                            paragraph.getSearchHistory().add(record);
                        })
                        .thenReturn(state);

                } catch (Exception e) {
                    log.error("[{}] 反思决策解析失败", getAgentName(), e);
                    return Mono.error(new RuntimeException("反思决策解析失败", e));
                }
            });
    }

    // =====================================================
    // 阶段 5: 反思总结
    // =====================================================

    @Override
    public Mono<AgentState> reflectionSummary(int paragraphIndex) {
        ParagraphState paragraph = state.getParagraphs().get(paragraphIndex);
        log.info("[{}] 阶段 5: 反思总结 - 段落「{}」", getAgentName(), paragraph.getTitle());

        String systemPrompt = getPromptTemplate(PromptStage.REFLECTION_SUMMARY);
        String userPrompt = buildReflectionSummaryUserPrompt(paragraph);

        return callLlm(systemPrompt, userPrompt)
            .flatMap(response -> {
                try {
                    JsonNode result = objectMapper.readTree(extractJson(response));
                    String updated = result.get("updated_paragraph_latest_state").asText();
                    paragraph.setCurrentDraft(updated);

                    log.info("[{}] 反思总结完成，段落更新至 {} 字",
                        getAgentName(), updated.length());

                    return publishToForum(
                        "段落「" + paragraph.getTitle() +
                        "」经过反思扩充至 " + updated.length() + " 字"
                    ).thenReturn(state);

                } catch (Exception e) {
                    log.error("[{}] 反思总结解析失败", getAgentName(), e);
                    return Mono.error(new RuntimeException("反思总结解析失败", e));
                }
            });
    }

    // =====================================================
    // 阶段 6: 最终报告格式化
    // =====================================================

    @Override
    public Mono<AnalysisResult> formatReport() {
        log.info("[{}] 阶段 6: 最终报告格式化", getAgentName());

        String systemPrompt = getPromptTemplate(PromptStage.REPORT_FORMATTING);
        String userPrompt = buildReportFormattingUserPrompt();

        return callLlm(systemPrompt, userPrompt)
            .flatMap(response -> {
                state.setFinalReport(response);

                log.info("[{}] 最终报告生成完成，总长度: {} 字",
                    getAgentName(), response.length());

                // 发布完成消息到论坛
                return publishToForum(
                    "研究报告生成完成！报告总长度: " + response.length() + " 字"
                ).thenReturn(new AnalysisResult(
                    getAgentName(),
                    state.getQuery(),
                    response,
                    Map.of(
                        "paragraphCount", state.getParagraphs().size(),
                        "totalSearches", countTotalSearches(),
                        "completedAt", LocalDateTime.now().toString()
                    )
                ));
            });
    }

    // =====================================================
    // LLM 调用核心方法
    // =====================================================

    /**
     * 调用 LLM，统一处理 System Prompt + User Prompt 的组合。
     */
    protected Mono<String> callLlm(String systemPrompt, String userPrompt) {
        return Mono.fromCallable(() ->
            chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content()
        ).onErrorResume(e -> {
            log.error("[{}] LLM 调用失败: {}", getAgentName(), e.getMessage());
            return Mono.error(new RuntimeException("LLM 调用失败", e));
        });
    }

    // =====================================================
    // 论坛消息发布（Redis Pub/Sub）
    // =====================================================

    /**
     * 将 Agent 的关键进展发布到论坛频道。
     * ForumEngine 会监听此频道并由 LLM 主持人进行整合分析。
     */
    protected Mono<Void> publishToForum(String content) {
        ForumMessage message = new ForumMessage(
            getAgentName(),
            content,
            LocalDateTime.now()
        );

        try {
            String json = objectMapper.writeValueAsString(message);
            return redisTemplate.convertAndSend(FORUM_CHANNEL, json).then();
        } catch (Exception e) {
            log.warn("[{}] 论坛消息发布失败: {}", getAgentName(), e.getMessage());
            return Mono.empty(); // 论坛发布失败不影响主流程
        }
    }

    /**
     * 从 Redis 获取最新的论坛主持人指导意见。
     */
    private Mono<String> fetchForumGuidance() {
        String guidanceKey = "bettafish:forum:guidance:" + state.getQuery().hashCode();
        return redisTemplate.opsForValue().get(guidanceKey)
            .defaultIfEmpty("");
    }

    // =====================================================
    // User Prompt 构建辅助方法
    // =====================================================

    private String buildFirstSearchUserPrompt(ParagraphState paragraph) {
        return String.format("""
            当前研究主题：%s

            当前段落标题：%s
            段落期望内容：%s

            可用搜索工具：
            %s

            请选择最合适的搜索工具并构造搜索查询。返回 JSON 格式。
            """,
            state.getQuery(),
            paragraph.getTitle(),
            paragraph.getExpectedContent(),
            getToolDescriptions()
        );
    }

    private String buildFirstSummaryUserPrompt(ParagraphState paragraph) {
        SearchRecord lastSearch = paragraph.getSearchHistory()
            .get(paragraph.getSearchHistory().size() - 1);

        return String.format("""
            研究主题：%s
            段落标题：%s
            段落期望内容：%s

            搜索工具：%s
            搜索查询：%s
            搜索结果：
            %s

            请基于以上搜索结果，为该段落撰写 800-1200 字的深度分析内容。返回 JSON 格式。
            """,
            state.getQuery(),
            paragraph.getTitle(),
            paragraph.getExpectedContent(),
            lastSearch.getToolName(),
            lastSearch.getSearchQuery(),
            truncateResults(lastSearch.getRawResults(), 8000)
        );
    }

    private String buildReflectionUserPrompt(ParagraphState paragraph,
                                              String forumGuidance) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
            研究主题：%s
            段落标题：%s
            段落期望内容：%s

            当前段落内容：
            %s

            """,
            state.getQuery(),
            paragraph.getTitle(),
            paragraph.getExpectedContent(),
            paragraph.getCurrentDraft()
        ));

        // 注入论坛主持人指导
        if (forumGuidance != null && !forumGuidance.isBlank()) {
            sb.append(String.format("""

            【论坛主持人指导意见】
            %s

            请结合主持人的指导意见，思考当前段落还缺少哪些关键信息。
            """, forumGuidance));
        }

        sb.append(String.format("""

            可用搜索工具：
            %s

            请反思当前段落的不足之处，选择合适的工具进行补充搜索。返回 JSON 格式。
            """,
            getToolDescriptions()
        ));

        return sb.toString();
    }

    private String buildReflectionSummaryUserPrompt(ParagraphState paragraph) {
        SearchRecord lastSearch = paragraph.getSearchHistory()
            .get(paragraph.getSearchHistory().size() - 1);

        return String.format("""
            研究主题：%s
            段落标题：%s

            当前段落内容：
            %s

            新的搜索结果（工具：%s，查询：%s）：
            %s

            请将新搜索结果中的有价值信息融入当前段落，丰富和扩充内容。
            注意：不要删除现有的关键信息，只进行增量扩充。返回 JSON 格式。
            """,
            state.getQuery(),
            paragraph.getTitle(),
            paragraph.getCurrentDraft(),
            lastSearch.getToolName(),
            lastSearch.getSearchQuery(),
            truncateResults(lastSearch.getRawResults(), 8000)
        );
    }

    private String buildReportFormattingUserPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("研究主题：").append(state.getQuery()).append("\n\n");
        sb.append("以下是各段落的研究成果，请整合为一份完整的专业分析报告：\n\n");

        for (int i = 0; i < state.getParagraphs().size(); i++) {
            ParagraphState p = state.getParagraphs().get(i);
            sb.append("---\n");
            sb.append("### 段落 ").append(i + 1).append(": ").append(p.getTitle()).append("\n\n");
            sb.append(p.getCurrentDraft()).append("\n\n");
        }

        return sb.toString();
    }

    // =====================================================
    // 工具方法
    // =====================================================

    private Map<String, String> extractOptionalParams(JsonNode decision) {
        Map<String, String> params = new HashMap<>();
        for (String key : List.of("start_date", "end_date", "platform",
                                   "time_period", "enable_sentiment")) {
            if (decision.has(key) && !decision.get(key).isNull()) {
                params.put(key, decision.get(key).asText());
            }
        }
        return params;
    }

    private String extractJson(String response) {
        // 提取 LLM 响应中的 JSON 部分（处理 ```json ``` 包裹的情况）
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private String truncateResults(String results, int maxLength) {
        if (results == null) return "";
        return results.length() > maxLength
            ? results.substring(0, maxLength) + "\n...[结果已截断]"
            : results;
    }

    private int countTotalSearches() {
        return state.getParagraphs().stream()
            .mapToInt(p -> p.getSearchHistory().size())
            .sum();
    }

    private String formatStructureForForum(List<ParagraphState> paragraphs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paragraphs.size(); i++) {
            sb.append(String.format("%d. %s - %s\n",
                i + 1,
                paragraphs.get(i).getTitle(),
                paragraphs.get(i).getExpectedContent()));
        }
        return sb.toString();
    }
}
```

以上基类完整实现了约 **350 行**的核心编排逻辑。三个具体 Agent 通过继承此基类，只需各自实现 4 个抽象方法即可接入不同的搜索后端和 Prompt 模板。

---

### 4.2 QueryEngine Agent

QueryEngine 是 BettaFish 的新闻搜索与深度研究引擎，基于 Tavily API 提供 6 种新闻搜索工具。它专注于从互联网新闻源获取信息，进行多源交叉验证和事实核查。

#### 4.2.1 TavilySearchService 实现

```java
package com.bettafish.query.service;

import com.bettafish.common.util.RetryHelper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tavily 新闻搜索服务。
 * 封装 Tavily Search API，提供 6 种面向 Agent 的搜索工具。
 *
 * 对应 Python 原版: QueryEngine/tools/search.py - TavilyNewsAgency
 */
@Service
public class TavilySearchService {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchService.class);
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    private final WebClient webClient;
    private final String apiKey;

    public TavilySearchService(
            @Value("${bettafish.tavily.api-key}") String apiKey,
            WebClient.Builder webClientBuilder) {
        this.apiKey = apiKey;
        this.webClient = webClientBuilder
            .baseUrl(TAVILY_API_URL)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    // =========================================================
    // 响应数据模型
    // =========================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResult(
        String title,
        String url,
        String content,
        double score,
        @JsonProperty("raw_content") String rawContent,
        @JsonProperty("published_date") String publishedDate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageResult(
        String url,
        String description
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TavilyResponse(
        String query,
        String answer,
        List<SearchResult> results,
        List<ImageResult> images,
        @JsonProperty("response_time") double responseTime
    ) {
        /** 创建搜索失败时的回退响应 */
        public static TavilyResponse fallback(String query) {
            return new TavilyResponse(query, "搜索失败", List.of(), List.of(), 0);
        }

        /** 将搜索结果转换为 Agent 可读的文本格式 */
        public String toReadableText() {
            StringBuilder sb = new StringBuilder();
            if (answer != null && !answer.isBlank()) {
                sb.append("【AI 摘要】\n").append(answer).append("\n\n");
            }
            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    SearchResult r = results.get(i);
                    sb.append(String.format("--- 结果 %d ---\n", i + 1));
                    sb.append("标题: ").append(r.title()).append("\n");
                    sb.append("来源: ").append(r.url()).append("\n");
                    if (r.publishedDate() != null) {
                        sb.append("日期: ").append(r.publishedDate()).append("\n");
                    }
                    sb.append("内容: ").append(r.content()).append("\n\n");
                }
            }
            if (images != null && !images.isEmpty()) {
                sb.append("【相关图片】\n");
                for (ImageResult img : images) {
                    sb.append("- ").append(img.url());
                    if (img.description() != null) {
                        sb.append(" (").append(img.description()).append(")");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        }
    }

    // =========================================================
    // 内部搜索方法（带重试机制）
    // =========================================================

    private Mono<TavilyResponse> searchInternal(Map<String, Object> params) {
        Map<String, Object> body = new HashMap<>(params);
        body.put("api_key", apiKey);
        body.putIfAbsent("topic", "general");

        return RetryHelper.withGracefulRetry(
            webClient.post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(TavilyResponse.class),
            3,                          // 最大重试次数
            Duration.ofSeconds(2),      // 初始退避间隔
            "Tavily Search"
        ).onErrorReturn(TavilyResponse.fallback(
            (String) params.getOrDefault("query", "unknown"))
        );
    }

    // =========================================================
    // 6 个公开搜索工具方法
    // =========================================================

    /**
     * 工具 1: basic_search_news — 基础新闻搜索。
     * 快速标准搜索，适合大多数通用场景。
     */
    public Mono<TavilyResponse> basicSearchNews(String query, int maxResults) {
        log.info("basicSearchNews: query={}, maxResults={}", query, maxResults);
        return searchInternal(Map.of(
            "query", query,
            "search_depth", "basic",
            "max_results", maxResults,
            "include_answer", false
        ));
    }

    public Mono<TavilyResponse> basicSearchNews(String query) {
        return basicSearchNews(query, 7);
    }

    /**
     * 工具 2: deep_search_news — 深度新闻搜索。
     * 使用高级 AI 摘要的综合深度分析，返回多达 20 条结果。
     */
    public Mono<TavilyResponse> deepSearchNews(String query) {
        log.info("deepSearchNews: query={}", query);
        return searchInternal(Map.of(
            "query", query,
            "search_depth", "advanced",
            "topic", "news",
            "max_results", 20,
            "include_answer", "advanced"
        ));
    }

    /**
     * 工具 3: search_news_last_24_hours — 最近 24 小时新闻。
     */
    public Mono<TavilyResponse> searchNewsLast24Hours(String query) {
        log.info("searchNewsLast24Hours: query={}", query);
        return searchInternal(Map.of(
            "query", query,
            "search_depth", "basic",
            "max_results", 10,
            "time_range", "d",
            "include_answer", false
        ));
    }

    /**
     * 工具 4: search_news_last_week — 最近一周新闻。
     */
    public Mono<TavilyResponse> searchNewsLastWeek(String query) {
        log.info("searchNewsLastWeek: query={}", query);
        return searchInternal(Map.of(
            "query", query,
            "search_depth", "basic",
            "max_results", 10,
            "time_range", "w",
            "include_answer", false
        ));
    }

    /**
     * 工具 5: search_images_for_news — 新闻图片搜索。
     */
    public Mono<TavilyResponse> searchImagesForNews(String query) {
        log.info("searchImagesForNews: query={}", query);
        return searchInternal(Map.of(
            "query", query,
            "search_depth", "basic",
            "max_results", 5,
            "include_images", true,
            "include_image_descriptions", true,
            "include_answer", false
        ));
    }

    /**
     * 工具 6: search_news_by_date — 按日期范围搜索历史新闻。
     */
    public Mono<TavilyResponse> searchNewsByDate(String query,
                                                   String startDate,
                                                   String endDate) {
        log.info("searchNewsByDate: query={}, range={} ~ {}", query, startDate, endDate);
        if (!isValidDate(startDate) || !isValidDate(endDate)) {
            log.warn("日期格式无效，回退至 basicSearchNews");
            return basicSearchNews(query);
        }
        return searchInternal(Map.of(
            "query", query + " " + startDate + ".." + endDate,
            "search_depth", "basic",
            "max_results", 15,
            "include_answer", false
        ));
    }

    private boolean isValidDate(String date) {
        if (date == null || date.isBlank()) return false;
        return date.matches("\\d{4}-\\d{2}-\\d{2}");
    }
}
```

#### 4.2.2 QueryAgent @Tool 注解工具类

在 Spring AI 中，使用 `@Tool` 注解将搜索方法暴露为 LLM 可调用的工具：

```java
package com.bettafish.query.tool;

import com.bettafish.query.service.TavilySearchService;
import com.bettafish.query.service.TavilySearchService.TavilyResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * QueryEngine 工具集 — 6 个新闻搜索工具。
 * 通过 Spring AI @Tool 注解暴露给 ChatClient 进行自动工具调用。
 *
 * 对应 Python: QueryEngine/tools/search.py - TavilyNewsAgency
 */
@Component
public class QuerySearchTools {

    private final TavilySearchService tavilyService;

    public QuerySearchTools(TavilySearchService tavilyService) {
        this.tavilyService = tavilyService;
    }

    @Tool(description = "基础新闻搜索工具。执行快速标准的新闻搜索，适合大多数通用搜索场景。" +
        "返回新闻标题、来源URL、内容摘要和发布日期。这是最常用的通用搜索工具。")
    public String basicSearchNews(
            @ToolParam(description = "新闻搜索查询关键词") String query,
            @ToolParam(description = "最大返回结果数，默认7") int maxResults) {
        TavilyResponse resp = tavilyService.basicSearchNews(query, maxResults).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }

    @Tool(description = "深度新闻搜索工具。执行深入的新闻分析搜索，使用高级AI摘要，" +
        "返回多达20条结果。适合需要全面深入了解某一新闻主题的场景。")
    public String deepSearchNews(
            @ToolParam(description = "深度搜索查询关键词") String query) {
        TavilyResponse resp = tavilyService.deepSearchNews(query).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }

    @Tool(description = "最近24小时新闻搜索。获取过去24小时内的突发和最新新闻。")
    public String searchNewsLast24Hours(
            @ToolParam(description = "搜索查询关键词") String query) {
        TavilyResponse resp = tavilyService.searchNewsLast24Hours(query).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }

    @Tool(description = "最近一周新闻搜索。获取过去一周内的新闻报道，适合趋势分析。")
    public String searchNewsLastWeek(
            @ToolParam(description = "搜索查询关键词") String query) {
        TavilyResponse resp = tavilyService.searchNewsLastWeek(query).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }

    @Tool(description = "新闻图片搜索工具。搜索与新闻主题相关的图片和视觉信息。")
    public String searchImagesForNews(
            @ToolParam(description = "图片搜索查询关键词") String query) {
        TavilyResponse resp = tavilyService.searchImagesForNews(query).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }

    @Tool(description = "按日期范围搜索历史新闻。需要提供 start_date 和 end_date（YYYY-MM-DD）。")
    public String searchNewsByDate(
            @ToolParam(description = "搜索查询关键词") String query,
            @ToolParam(description = "起始日期，格式 YYYY-MM-DD") String startDate,
            @ToolParam(description = "结束日期，格式 YYYY-MM-DD") String endDate) {
        TavilyResponse resp = tavilyService.searchNewsByDate(query, startDate, endDate).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }
}
```

#### 4.2.3 QueryAgent 主类

```java
package com.bettafish.query;

import com.bettafish.common.agent.AbstractAnalysisAgent;
import com.bettafish.query.prompt.QueryPrompts;
import com.bettafish.query.service.TavilySearchService;
import com.bettafish.query.service.TavilySearchService.TavilyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * QueryEngine Agent — 新闻搜索与深度研究代理。
 * 使用 Tavily API 的 6 种搜索工具执行 Search-Reflect Loop。
 *
 * 对应 Python: QueryEngine/agent.py - DeepSearchAgent
 */
@Component
public class QueryAgent extends AbstractAnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(QueryAgent.class);
    private final TavilySearchService tavilyService;

    public QueryAgent(ChatClient.Builder chatClientBuilder,
                      ReactiveStringRedisTemplate redisTemplate,
                      TavilySearchService tavilyService) {
        super(chatClientBuilder.build(), redisTemplate);
        this.tavilyService = tavilyService;
    }

    @Override
    protected String getAgentName() {
        return "QUERY";
    }

    @Override
    protected String getPromptTemplate(PromptStage stage) {
        return switch (stage) {
            case REPORT_STRUCTURE    -> QueryPrompts.SYSTEM_PROMPT_REPORT_STRUCTURE;
            case FIRST_SEARCH        -> QueryPrompts.SYSTEM_PROMPT_FIRST_SEARCH;
            case FIRST_SUMMARY       -> QueryPrompts.SYSTEM_PROMPT_FIRST_SUMMARY;
            case REFLECTION          -> QueryPrompts.SYSTEM_PROMPT_REFLECTION;
            case REFLECTION_SUMMARY  -> QueryPrompts.SYSTEM_PROMPT_REFLECTION_SUMMARY;
            case REPORT_FORMATTING   -> QueryPrompts.SYSTEM_PROMPT_REPORT_FORMATTING;
        };
    }

    @Override
    protected Mono<String> executeSearch(String toolName, String query,
                                          Map<String, String> params) {
        return switch (toolName) {
            case "basic_search_news" -> {
                int max = 7;
                if (params.containsKey("max_results")) {
                    try { max = Integer.parseInt(params.get("max_results")); }
                    catch (NumberFormatException ignored) {}
                }
                yield tavilyService.basicSearchNews(query, max)
                    .map(TavilyResponse::toReadableText);
            }
            case "deep_search_news" ->
                tavilyService.deepSearchNews(query)
                    .map(TavilyResponse::toReadableText);
            case "search_news_last_24_hours" ->
                tavilyService.searchNewsLast24Hours(query)
                    .map(TavilyResponse::toReadableText);
            case "search_news_last_week" ->
                tavilyService.searchNewsLastWeek(query)
                    .map(TavilyResponse::toReadableText);
            case "search_images_for_news" ->
                tavilyService.searchImagesForNews(query)
                    .map(TavilyResponse::toReadableText);
            case "search_news_by_date" -> {
                String sd = params.getOrDefault("start_date", "");
                String ed = params.getOrDefault("end_date", "");
                yield tavilyService.searchNewsByDate(query, sd, ed)
                    .map(TavilyResponse::toReadableText);
            }
            default -> {
                log.warn("未知工具 {}，回退至 basic_search_news", toolName);
                yield tavilyService.basicSearchNews(query)
                    .map(TavilyResponse::toReadableText);
            }
        };
    }

    @Override
    protected String getToolDescriptions() {
        return """
            可用工具列表：
            1. basic_search_news — 基础新闻搜索，快速标准，适合通用场景
            2. deep_search_news — 深度新闻分析，高级AI摘要，最多20条结果
            3. search_news_last_24_hours — 最近24小时突发/最新新闻
            4. search_news_last_week — 最近一周新闻，适合趋势分析
            5. search_images_for_news — 新闻图片搜索，获取视觉信息
            6. search_news_by_date — 按日期范围搜索历史新闻（需提供 start_date, end_date，YYYY-MM-DD）
            """;
    }
}
```

#### 4.2.4 QueryEngine 完整 Prompt 模板

以下是 QueryEngine 全部 6 个阶段的 System Prompt 常量，从 Python 原版 `QueryEngine/prompts/prompts.py` 翻译适配：

```java
package com.bettafish.query.prompt;

/**
 * QueryEngine Prompt 常量集合。
 * 对应 Python: QueryEngine/prompts/prompts.py
 */
public final class QueryPrompts {

    private QueryPrompts() {}

    // ==========================================================
    // Prompt 1: 报告结构生成
    // ==========================================================

    public static final String SYSTEM_PROMPT_REPORT_STRUCTURE = """
        你是一位深度研究助手（Deep Research Assistant）。

        你的任务是根据用户给出的查询主题，规划一份研究报告的结构。

        要求：
        1. 报告最多包含 5 个有序段落
        2. 每个段落需要有明确的标题（title）和期望内容描述（content）
        3. 段落之间应有逻辑递进关系
        4. 覆盖主题的关键维度：事件概述、多方报道、数据分析、背景深挖、趋势预测

        输出格式：仅返回 JSON 数组，不包含其他文字。
        格式: [{"title": "段落标题", "content": "期望内容描述"}, ...]

        示例:
        [
          {"title": "事件核心概述与时间线", "content": "梳理事件的起因、经过、关键时间节点"},
          {"title": "多方媒体报道对比分析", "content": "对比不同媒体的报道角度和立场差异"},
          {"title": "关键数据与影响评估", "content": "提取关键数据指标、影响范围和程度"},
          {"title": "深层背景与关联分析", "content": "挖掘事件深层背景和历史脉络"},
          {"title": "发展趋势与风险预判", "content": "分析未来走向，评估潜在风险"}
        ]
        """;

    // ==========================================================
    // Prompt 2: 首次搜索决策
    // ==========================================================

    public static final String SYSTEM_PROMPT_FIRST_SEARCH = """
        你是一位深度研究助手。

        任务：根据当前段落的标题和期望内容，选择最合适的搜索工具并构造搜索查询。

        可用搜索工具：
        1. basic_search_news — 通用新闻搜索，速度快，标准搜索，最常用
        2. deep_search_news — 深度新闻分析，高级AI摘要，适合全面深入分析
        3. search_news_last_24_hours — 过去24小时突发/最新新闻
        4. search_news_last_week — 过去一周新闻，适合趋势分析
        5. search_images_for_news — 新闻相关图片和视觉信息
        6. search_news_by_date — 按日期范围搜索历史新闻（必须提供 start_date, end_date，YYYY-MM-DD）

        关键指引：
        - 根据段落需求选择最匹配的工具
        - 搜索查询要精准、具体，覆盖段落核心关注点
        - 选择 search_news_by_date 时必须输出 start_date 和 end_date
        - 对可疑新闻声明设计验证性搜索查询
        - 搜索查询语言应与主题语言一致

        输出：仅返回 JSON 对象。
        必填: search_query, search_tool, reasoning
        可选: start_date, end_date（仅 search_news_by_date 时）
        """;

    // ==========================================================
    // Prompt 3: 首次总结
    // ==========================================================

    public static final String SYSTEM_PROMPT_FIRST_SUMMARY = """
        你是一位专业的新闻分析师和深度内容创作专家。

        任务：基于搜索结果撰写信息密集的结构化分析内容。

        撰写要求：
        1. 字数：800-1200 字以上
        2. 信息密度：每 100 字至少 2-3 个具体数据点
        3. 多源引用：引用不同来源进行交叉验证

        必须包含的分析层次：
        - 核心事件概述：基本面貌、关键参与方、时间节点
        - 多源报道分析：不同媒体报道角度和立场对比
        - 关键数据提取：具体数字、统计数据、量化指标
        - 深层背景分析：深层原因、历史脉络
        - 发展趋势评估：基于现有信息的趋势判断

        质量标准：
        - 事实优先，所有观点基于搜索结果中的事实
        - 关键信息注明来源
        - 引用数据必须准确，不可编造
        - 分析有明确逻辑链条
        - 对可疑信息标注和验证

        输出：仅返回 JSON — {"paragraph_latest_state": "段落完整内容..."}
        """;

    // ==========================================================
    // Prompt 4: 反思搜索决策
    // ==========================================================

    public static final String SYSTEM_PROMPT_REFLECTION = """
        你是一位深度研究助手，正在执行迭代式精炼。

        任务：反思当前段落内容，识别信息缺口，选择搜索工具进行补充搜索。

        反思要点：
        1. 当前段落是否存在重要的信息缺口？
        2. 哪些关键方面的主题内容尚未覆盖？
        3. 现有信息是否需要交叉验证或更新？
        4. 是否有可疑声明需要事实核查？

        可用搜索工具：
        1. basic_search_news — 通用新闻搜索
        2. deep_search_news — 深度新闻分析
        3. search_news_last_24_hours — 最近24小时新闻
        4. search_news_last_week — 最近一周新闻
        5. search_images_for_news — 新闻图片搜索
        6. search_news_by_date — 按日期范围搜索（需 start_date, end_date）

        关键指引：
        - 思考缺少哪些关键信息
        - 选择最能弥补信息缺口的工具
        - 构造精准查询，避免与前次重复
        - 对可疑声明设计验证性搜索

        输出：仅返回 JSON — 必填 search_query, search_tool, reasoning
        """;

    // ==========================================================
    // Prompt 5: 反思总结
    // ==========================================================

    public static final String SYSTEM_PROMPT_REFLECTION_SUMMARY = """
        你是一位深度研究助手。

        任务：基于新搜索结果丰富和扩充当前段落内容。

        核心原则：
        1. 不删除现有关键信息 — 保持原有内容完整性
        2. 增量式扩充 — 将新发现的有价值信息融入
        3. 信息整合 — 新旧信息有机融合，避免简单拼接
        4. 质量提升 — 补充更多数据点、引用来源、具体案例

        扩充策略：
        - 添加新发现的关键事实和数据
        - 补充不同角度的分析和观点
        - 增强论证的证据链
        - 深化背景分析和趋势判断
        - 加入更多具体案例和引用

        输出：仅返回 JSON — {"updated_paragraph_latest_state": "更新后的完整内容..."}
        """;

    // ==========================================================
    // Prompt 6: 最终报告格式化
    // ==========================================================

    public static final String SYSTEM_PROMPT_REPORT_FORMATTING = """
        你是一位资深新闻分析专家和调查报告编辑。

        任务：将各段落研究成果整合为专业的新闻分析报告。

        要求：
        1. 总字数不少于 10,000 字
        2. 使用 Markdown 格式
        3. 专业、严谨、数据翔实

        报告结构模板：

        # [深度调查] 综合新闻分析报告：{主题}

        ## 核心摘要
        ### 关键事实
        - （5-8 个最重要的发现）
        ### 信息来源概览
        - （引用来源数量、类型分布统计）

        ## 第一章 {段落标题}
        ### 事件时间线
        | 时间 | 事件 | 来源 | 可信度 | 影响评级 |
        |------|------|------|--------|----------|

        ### 多源对比
        > 来源A报道："..."
        > 来源B报道："..."
        > 分析：报道差异在于...

        ### 关键数据分析
        ### 事实核查与验证

        ## （后续章节类推）

        ## 综合分析
        ### 完整事件还原
        ### 可信度评估
        | 信息维度 | 可信度评分 | 依据 |
        ### 趋势预测
        ### 影响评估

        ## 专业结论

        ## 信息附录

        写作原则：
        1. 事实至上 — 所有分析基于可查证事实
        2. 多源验证 — 重要信息至少引用两个独立来源
        3. 时间线清晰 — 按时间顺序组织事件
        4. 数据专业 — 精确数字和统计
        5. 辟谣意识 — 对谣言明确标注
        """;
}
```

---

### 4.3 MediaEngine Agent

MediaEngine 是 BettaFish 的多媒体内容分析引擎，基于 Bocha AI Search API 提供 5 种多模态搜索工具。与 QueryEngine 专注于新闻文本不同，MediaEngine 整合了网页、图片、AI 摘要和结构化数据卡片（天气、股票、汇率、百科等），提供全景式的多维度信息融合分析。

#### 4.3.1 BochaSearchService 实现

```java
package com.bettafish.media.service;

import com.bettafish.common.util.RetryHelper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Bocha AI 多模态搜索服务。
 * 封装 Bocha AI Search API，提供 5 种面向 Agent 的多模态搜索工具。
 *
 * 对应 Python: MediaEngine/tools/search.py - BochaMultimodalSearch
 */
@Service
public class BochaSearchService {

    private static final Logger log = LoggerFactory.getLogger(BochaSearchService.class);
    private static final String BOCHA_API_URL = "https://api.bocha.cn/v1/ai-search";

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BochaSearchService(
            @Value("${bettafish.bocha.api-key}") String apiKey,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl(BOCHA_API_URL)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
    }

    // =========================================================
    // 响应数据模型
    // =========================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebpageResult(
        String name,
        String url,
        String snippet,
        @JsonProperty("display_url") String displayUrl,
        @JsonProperty("date_last_crawled") String dateLastCrawled
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageResult(
        String name,
        @JsonProperty("content_url") String contentUrl,
        @JsonProperty("host_page_url") String hostPageUrl,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        int width,
        int height
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModalCardResult(
        @JsonProperty("card_type") String cardType,
        Map<String, Object> content
    ) {}

    public record BochaResponse(
        String query,
        String conversationId,
        String answer,
        List<String> followUps,
        List<WebpageResult> webpages,
        List<ImageResult> images,
        List<ModalCardResult> modalCards
    ) {
        public static BochaResponse fallback(String query) {
            return new BochaResponse(query, null, "搜索失败",
                List.of(), List.of(), List.of(), List.of());
        }

        /** 转换为 Agent 可读的文本格式 */
        public String toReadableText() {
            StringBuilder sb = new StringBuilder();

            if (answer != null && !answer.isBlank()) {
                sb.append("【AI 综合摘要】\n").append(answer).append("\n\n");
            }

            if (webpages != null && !webpages.isEmpty()) {
                sb.append("【网页搜索结果】\n");
                for (int i = 0; i < webpages.size(); i++) {
                    WebpageResult w = webpages.get(i);
                    sb.append(String.format("--- 网页 %d ---\n", i + 1));
                    sb.append("标题: ").append(w.name()).append("\n");
                    sb.append("来源: ").append(w.url()).append("\n");
                    if (w.dateLastCrawled() != null) {
                        sb.append("日期: ").append(w.dateLastCrawled()).append("\n");
                    }
                    sb.append("摘要: ").append(w.snippet()).append("\n\n");
                }
            }

            if (images != null && !images.isEmpty()) {
                sb.append("【图片搜索结果】\n");
                for (ImageResult img : images) {
                    sb.append("- ").append(img.name())
                       .append(" [").append(img.width()).append("x").append(img.height()).append("]")
                       .append(" ").append(img.contentUrl()).append("\n");
                }
                sb.append("\n");
            }

            if (modalCards != null && !modalCards.isEmpty()) {
                sb.append("【结构化数据卡片】\n");
                for (ModalCardResult card : modalCards) {
                    sb.append("类型: ").append(card.cardType()).append("\n");
                    sb.append("数据: ").append(card.content()).append("\n\n");
                }
            }

            if (followUps != null && !followUps.isEmpty()) {
                sb.append("【推荐追问】\n");
                for (String fu : followUps) {
                    sb.append("- ").append(fu).append("\n");
                }
            }

            return sb.toString();
        }
    }

    // =========================================================
    // 内部搜索方法
    // =========================================================

    /**
     * 解析 Bocha API 响应。
     * Bocha API 返回 messages 数组，每条消息有 type 和 content_type 字段，
     * 需要分类解析为 answer、follow_up、webpage、image、modal_card。
     */
    private BochaResponse parseSearchResponse(JsonNode responseDict, String query) {
        String answer = null;
        List<String> followUps = new ArrayList<>();
        List<WebpageResult> webpages = new ArrayList<>();
        List<ImageResult> images = new ArrayList<>();
        List<ModalCardResult> modalCards = new ArrayList<>();
        String conversationId = null;

        try {
            if (responseDict.has("conversation_id")) {
                conversationId = responseDict.get("conversation_id").asText();
            }

            JsonNode messages = responseDict.path("messages");
            if (messages.isArray()) {
                for (JsonNode msg : messages) {
                    String role = msg.path("role").asText();
                    if (!"assistant".equals(role)) continue;

                    String type = msg.path("type").asText();
                    String contentType = msg.path("content_type").asText();

                    switch (type) {
                        case "answer" -> answer = msg.path("content").asText();
                        case "follow_up" -> {
                            JsonNode content = msg.path("content");
                            if (content.isArray()) {
                                for (JsonNode fu : content) {
                                    followUps.add(fu.asText());
                                }
                            }
                        }
                        case "source" -> {
                            switch (contentType) {
                                case "webpage" -> {
                                    JsonNode items = msg.path("content");
                                    if (items.isArray()) {
                                        for (JsonNode item : items) {
                                            webpages.add(objectMapper.treeToValue(
                                                item, WebpageResult.class));
                                        }
                                    }
                                }
                                case "image" -> {
                                    JsonNode items = msg.path("content");
                                    if (items.isArray()) {
                                        for (JsonNode item : items) {
                                            images.add(objectMapper.treeToValue(
                                                item, ImageResult.class));
                                        }
                                    }
                                }
                                default -> {
                                    // modal card (weather, stock, forex, wiki, etc.)
                                    modalCards.add(new ModalCardResult(
                                        contentType,
                                        objectMapper.treeToValue(msg.path("content"), Map.class)
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Bocha 响应解析失败", e);
        }

        return new BochaResponse(query, conversationId, answer,
            followUps, webpages, images, modalCards);
    }

    private Mono<BochaResponse> searchInternal(Map<String, Object> params) {
        return RetryHelper.withGracefulRetry(
            webClient.post()
                .bodyValue(params)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> parseSearchResponse(response,
                    (String) params.getOrDefault("query", "unknown"))),
            3,
            Duration.ofSeconds(2),
            "Bocha Search"
        ).onErrorReturn(BochaResponse.fallback(
            (String) params.getOrDefault("query", "unknown"))
        );
    }

    // =========================================================
    // 5 个公开搜索工具方法
    // =========================================================

    /**
     * 工具 1: comprehensive_search — 全面多模态搜索。
     * 返回网页、图片、AI摘要、追问建议、结构化数据卡片。
     */
    public Mono<BochaResponse> comprehensiveSearch(String query, int maxResults) {
        log.info("comprehensiveSearch: query={}, maxResults={}", query, maxResults);
        return searchInternal(Map.of(
            "query", query,
            "count", maxResults,
            "answer", true,
            "stream", false
        ));
    }

    public Mono<BochaResponse> comprehensiveSearch(String query) {
        return comprehensiveSearch(query, 10);
    }

    /**
     * 工具 2: web_search_only — 纯网页搜索。
     * 不含 AI 摘要，速度更快，成本更低。
     */
    public Mono<BochaResponse> webSearchOnly(String query, int maxResults) {
        log.info("webSearchOnly: query={}, maxResults={}", query, maxResults);
        return searchInternal(Map.of(
            "query", query,
            "count", maxResults,
            "answer", false,
            "stream", false
        ));
    }

    public Mono<BochaResponse> webSearchOnly(String query) {
        return webSearchOnly(query, 15);
    }

    /**
     * 工具 3: search_for_structured_data — 结构化数据搜索。
     * 面向天气、股票、汇率、百科等结构化信息查询。
     */
    public Mono<BochaResponse> searchForStructuredData(String query) {
        log.info("searchForStructuredData: query={}", query);
        return searchInternal(Map.of(
            "query", query,
            "count", 5,
            "answer", true,
            "stream", false
        ));
    }

    /**
     * 工具 4: search_last_24_hours — 最近 24 小时搜索。
     */
    public Mono<BochaResponse> searchLast24Hours(String query) {
        log.info("searchLast24Hours: query={}", query);
        return searchInternal(Map.of(
            "query", query,
            "count", 10,
            "freshness", "oneDay",
            "answer", true,
            "stream", false
        ));
    }

    /**
     * 工具 5: search_last_week — 最近一周搜索。
     */
    public Mono<BochaResponse> searchLastWeek(String query) {
        log.info("searchLastWeek: query={}", query);
        return searchInternal(Map.of(
            "query", query,
            "count", 10,
            "freshness", "oneWeek",
            "answer", true,
            "stream", false
        ));
    }
}
```

#### 4.3.2 MediaAgent @Tool 工具类

```java
package com.bettafish.media.tool;

import com.bettafish.media.service.BochaSearchService;
import com.bettafish.media.service.BochaSearchService.BochaResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MediaEngine 工具集 — 5 个多模态搜索工具。
 * 对应 Python: MediaEngine/tools/search.py - BochaMultimodalSearch
 */
@Component
public class MediaSearchTools {

    private final BochaSearchService bochaService;

    public MediaSearchTools(BochaSearchService bochaService) {
        this.bochaService = bochaService;
    }

    @Tool(description = "全面多模态搜索工具。返回网页内容、图片、AI智能摘要、" +
        "追问建议和结构化数据卡片。这是默认的通用搜索工具，适合大多数分析场景。")
    public String comprehensiveSearch(
            @ToolParam(description = "搜索查询关键词") String query,
            @ToolParam(description = "最大返回结果数，默认10") int maxResults) {
        BochaResponse resp = bochaService.comprehensiveSearch(query, maxResults).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }

    @Tool(description = "纯网页搜索工具。只返回网页结果，不含AI摘要。速度更快、成本更低。" +
        "适合需要大量原始网页信息而非AI总结的场景。")
    public String webSearchOnly(
            @ToolParam(description = "搜索查询关键词") String query,
            @ToolParam(description = "最大返回结果数，默认15") int maxResults) {
        BochaResponse resp = bochaService.webSearchOnly(query, maxResults).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }

    @Tool(description = "结构化数据搜索工具。专门查询天气、股票、汇率、百科定义等结构化信息，" +
        "返回格式化的数据卡片。")
    public String searchForStructuredData(
            @ToolParam(description = "结构化数据查询") String query) {
        BochaResponse resp = bochaService.searchForStructuredData(query).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }

    @Tool(description = "最近24小时内容搜索。获取过去24小时的最新内容，适合追踪实时动态。")
    public String searchLast24Hours(
            @ToolParam(description = "搜索查询关键词") String query) {
        BochaResponse resp = bochaService.searchLast24Hours(query).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }

    @Tool(description = "最近一周内容搜索。获取过去一周的内容，适合分析短期趋势和发展脉络。")
    public String searchLastWeek(
            @ToolParam(description = "搜索查询关键词") String query) {
        BochaResponse resp = bochaService.searchLastWeek(query).block();
        return resp != null ? resp.toReadableText() : "搜索失败";
    }
}
```

#### 4.3.3 MediaAgent 主类

```java
package com.bettafish.media;

import com.bettafish.common.agent.AbstractAnalysisAgent;
import com.bettafish.media.prompt.MediaPrompts;
import com.bettafish.media.service.BochaSearchService;
import com.bettafish.media.service.BochaSearchService.BochaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * MediaEngine Agent — 多媒体内容分析代理。
 * 使用 Bocha AI Search 的 5 种多模态搜索工具。
 *
 * 对应 Python: MediaEngine/agent.py - DeepSearchAgent
 */
@Component
public class MediaAgent extends AbstractAnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(MediaAgent.class);
    private final BochaSearchService bochaService;

    public MediaAgent(ChatClient.Builder chatClientBuilder,
                      ReactiveStringRedisTemplate redisTemplate,
                      BochaSearchService bochaService) {
        super(chatClientBuilder.build(), redisTemplate);
        this.bochaService = bochaService;
    }

    @Override
    protected String getAgentName() {
        return "MEDIA";
    }

    @Override
    protected String getPromptTemplate(PromptStage stage) {
        return switch (stage) {
            case REPORT_STRUCTURE    -> MediaPrompts.SYSTEM_PROMPT_REPORT_STRUCTURE;
            case FIRST_SEARCH        -> MediaPrompts.SYSTEM_PROMPT_FIRST_SEARCH;
            case FIRST_SUMMARY       -> MediaPrompts.SYSTEM_PROMPT_FIRST_SUMMARY;
            case REFLECTION          -> MediaPrompts.SYSTEM_PROMPT_REFLECTION;
            case REFLECTION_SUMMARY  -> MediaPrompts.SYSTEM_PROMPT_REFLECTION_SUMMARY;
            case REPORT_FORMATTING   -> MediaPrompts.SYSTEM_PROMPT_REPORT_FORMATTING;
        };
    }

    @Override
    protected Mono<String> executeSearch(String toolName, String query,
                                          Map<String, String> params) {
        return switch (toolName) {
            case "comprehensive_search" ->
                bochaService.comprehensiveSearch(query)
                    .map(BochaResponse::toReadableText);
            case "web_search_only" ->
                bochaService.webSearchOnly(query)
                    .map(BochaResponse::toReadableText);
            case "search_for_structured_data" ->
                bochaService.searchForStructuredData(query)
                    .map(BochaResponse::toReadableText);
            case "search_last_24_hours" ->
                bochaService.searchLast24Hours(query)
                    .map(BochaResponse::toReadableText);
            case "search_last_week" ->
                bochaService.searchLastWeek(query)
                    .map(BochaResponse::toReadableText);
            default -> {
                log.warn("未知工具 {}，回退至 comprehensive_search", toolName);
                yield bochaService.comprehensiveSearch(query)
                    .map(BochaResponse::toReadableText);
            }
        };
    }

    @Override
    protected String getToolDescriptions() {
        return """
            可用工具列表：
            1. comprehensive_search — 全面多模态搜索，返回网页+图片+AI摘要+结构化数据
            2. web_search_only — 纯网页搜索，速度快成本低
            3. search_for_structured_data — 结构化数据查询（天气/股票/汇率/百科）
            4. search_last_24_hours — 最近24小时内容
            5. search_last_week — 最近一周内容
            """;
    }
}
```

#### 4.3.4 MediaEngine 关键 Prompt 模板

MediaEngine 的 Prompt 与 QueryEngine 共享相同的 6 阶段结构，但在内容分析层面强调多模态融合。以下展示差异最大的 3 个 Prompt（其余与 QueryEngine 类似）：

```java
package com.bettafish.media.prompt;

/**
 * MediaEngine Prompt 常量集合。
 * 对应 Python: MediaEngine/prompts/prompts.py
 */
public final class MediaPrompts {

    private MediaPrompts() {}

    // Prompt 1: 报告结构 — 与 QueryEngine 相同
    public static final String SYSTEM_PROMPT_REPORT_STRUCTURE = """
        你是一位深度研究助手（Deep Research Assistant）。

        你的任务是根据用户给出的查询主题，规划一份研究报告的结构。

        要求：
        1. 报告最多包含 5 个有序段落
        2. 每个段落需要有明确的标题（title）和期望内容描述（content）
        3. 段落之间应有逻辑递进关系
        4. 覆盖多维度信息源：网页内容、视觉信息、结构化数据、AI综合分析

        输出格式：仅返回 JSON 数组
        格式: [{"title": "段落标题", "content": "期望内容描述"}, ...]
        """;

    // Prompt 2: 首次搜索 — 强调多模态工具选择
    public static final String SYSTEM_PROMPT_FIRST_SEARCH = """
        你是一位深度研究助手，专注于多模态信息搜索。

        任务：根据段落需求选择最合适的多模态搜索工具并构造查询。

        可用搜索工具：
        1. comprehensive_search — 全面搜索，返回网页+图片+AI摘要+结构化数据卡片，默认通用工具
        2. web_search_only — 纯网页搜索，速度快成本低，适合只需文本信息的场景
        3. search_for_structured_data — 结构化数据查询，适合天气/股票/汇率/百科等信息
        4. search_last_24_hours — 最近24小时内容，适合追踪实时动态
        5. search_last_week — 最近一周内容，适合短期趋势分析

        工具选择指引：
        - 需要图片和视觉信息时，优先选择 comprehensive_search
        - 需要具体数据（股价/天气/汇率）时，使用 search_for_structured_data
        - 只需要文本网页信息时，使用 web_search_only（更快）
        - 追踪实时事件用 search_last_24_hours
        - 分析短期趋势用 search_last_week

        输出：仅返回 JSON — 必填 search_query, search_tool, reasoning
        """;

    // Prompt 3: 首次总结 — 强调多模态融合分析
    public static final String SYSTEM_PROMPT_FIRST_SUMMARY = """
        你是一位专业的多媒体内容分析师和深度报告撰写专家。

        任务：基于多模态搜索结果撰写多维度综合分析段落。

        撰写要求：
        1. 字数：800-1200 字以上
        2. 多模态整合：融合文字、图片、数据等多种信息源
        3. 交叉验证：不同信息源之间进行交叉对比验证

        多模态整合分析层次：
        - 网页内容分析：提取关键文本信息和观点
        - 图片信息解读：描述和分析视觉内容的信息价值
        - AI摘要整合：利用AI生成的综合摘要进行深度分析
        - 结构化数据应用：引用具体的数据卡片信息

        内容结构：
        ## 综合信息概览
        ## 文本内容深度分析
        ## 视觉信息解读
        ## 数据综合分析
        ## 多维洞察

        特殊要求：
        - 进行不同信息源之间的关联分析
        - 对比来源差异进行比较分析
        - 对图片内容进行视觉描述
        - 从多感官维度进行3D分析

        输出：仅返回 JSON — {"paragraph_latest_state": "段落完整内容..."}
        """;

    // Prompt 4: 反思 — 与 QueryEngine 结构相同但引用 5 个多模态工具
    public static final String SYSTEM_PROMPT_REFLECTION = """
        你是一位深度研究助手，正在执行迭代式精炼。

        任务：反思当前段落，识别多模态信息缺口，选择补充搜索工具。

        反思要点：
        1. 当前段落是否充分利用了多模态信息？
        2. 是否缺少视觉信息、结构化数据或AI分析？
        3. 不同信息源之间是否需要更多交叉验证？
        4. 是否有可以用结构化数据补充的方面？

        可用工具：
        1. comprehensive_search — 全面多模态搜索
        2. web_search_only — 纯网页搜索
        3. search_for_structured_data — 结构化数据查询
        4. search_last_24_hours — 最近24小时
        5. search_last_week — 最近一周

        输出：仅返回 JSON — 必填 search_query, search_tool, reasoning
        """;

    // Prompt 5: 反思总结 — 与 QueryEngine 相同
    public static final String SYSTEM_PROMPT_REFLECTION_SUMMARY = """
        你是一位深度研究助手。

        任务：基于新搜索结果丰富和扩充当前段落。

        核心原则：
        1. 不删除现有关键信息
        2. 增量式扩充新发现的有价值信息
        3. 新旧信息有机融合
        4. 补充更多数据点、来源引用、具体案例

        输出：仅返回 JSON — {"updated_paragraph_latest_state": "更新后内容..."}
        """;

    // Prompt 6: 报告格式化 — 强调多媒体融合报告结构
    public static final String SYSTEM_PROMPT_REPORT_FORMATTING = """
        你是一位资深多媒体内容分析专家和融合报告编辑。

        任务：将各段落研究成果整合为全景式多媒体分析报告。

        要求：
        1. 总字数不少于 10,000 字
        2. 使用 Markdown 格式
        3. 体现多模态信息融合分析的深度

        报告结构模板：

        # [全景分析] 多维融合分析报告：{主题}

        ## 全景概览
        ### 多维信息摘要
        - 网页信息源: X 个
        - 图片资料: X 张
        - 结构化数据: X 条
        - AI分析覆盖率: X%

        ### 信息来源分布

        ## 第一章 {段落标题}
        ### 多模态信息画像
        | 信息类型 | 数量 | 内容概要 | 情感倾向 | 影响力 |
        |----------|------|----------|----------|--------|

        ### 视觉内容深度分析
        - 图片类型分布
        - 代表性图片描述和分析

        ### 文本与视觉融合分析
        - 文字报道与视觉信息的一致性/差异性

        ### 数据与内容交叉验证
        - 结构化数据与文本报道的互证

        ## （后续章节类推）

        ## 跨媒介综合分析
        ### 信息一致性评估
        | 维度 | 文本 | 图片 | 数据 | 一致性评分 |
        ### 多维影响力对比
        ### 融合效应分析

        ## 多维洞察与预测

        ## 多媒体数据附录

        特色要求：
        - 跨媒介对比表格
        - 综合评分体系
        - 视觉内容的电影化构图描述
        - 多媒体组合的协同效应评估
        """;
}
```

---

### 4.4 InsightEngine Agent

InsightEngine 是 BettaFish 最具特色的引擎——它不依赖外部 API，而是查询本地 MySQL 数据库中由社交媒体爬虫采集的真实用户内容。覆盖 Bilibili、微博、抖音、快手、小红书、知乎、贴吧 7 大平台，配合多语言情感分析模型和关键词优化器，挖掘真实的公众情绪和舆论态势。

#### 4.4.1 数据库查询服务

```java
package com.bettafish.insight.service;

import com.bettafish.common.util.RetryHelper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 社交媒体数据库查询服务。
 * 查询由爬虫采集的 7 大平台社交媒体数据。
 *
 * 对应 Python: InsightEngine/tools/search.py - MediaCrawlerDB
 */
@Service
public class MediaCrawlerDbService {

    private static final Logger log = LoggerFactory.getLogger(MediaCrawlerDbService.class);

    // =========================================================
    // 热度权重常量 — 用于计算内容热度分数
    // =========================================================
    /**
     * 热度计算公式（与 Python 版完全一致）：
     *   hotness = W_LIKE * liked_count
     *           + W_COMMENT * comment_count
     *           + W_SHARE * share_count
     *           + W_VIEW * 0.1 * view_count
     *           + W_DANMAKU * 0.5 * danmaku_count
     *
     * 权重设计理念：
     * - 分享(10.0)权重最高：分享是最强的主动传播行为
     * - 评论(5.0)次之：评论代表深度参与和情感投入
     * - 点赞(1.0)基础权重：点赞是最轻量的互动
     * - 观看(0.1)最低权重：观看是被动行为，但量大
     * - 弹幕(0.5)特殊权重：B站特有，介于点赞和评论之间
     */
    public static final double W_LIKE = 1.0;
    public static final double W_COMMENT = 5.0;
    public static final double W_SHARE = 10.0;
    public static final double W_VIEW = 0.1;
    public static final double W_DANMAKU = 0.5;

    // =========================================================
    // 平台与表名映射
    // =========================================================

    /** 平台内容表映射 */
    private static final Map<String, String> PLATFORM_CONTENT_TABLES = Map.of(
        "bilibili",    "bilibili_video",
        "douyin",      "douyin_aweme",
        "kuaishou",    "kuaishou_video",
        "weibo",       "weibo_note",
        "xhs",         "xhs_note",
        "zhihu",       "zhihu_content",
        "tieba",       "tieba_note"
    );

    /** 平台评论表映射 */
    private static final Map<String, String> PLATFORM_COMMENT_TABLES = Map.of(
        "bilibili",    "bilibili_video_comment",
        "douyin",      "douyin_aweme_comment",
        "kuaishou",    "kuaishou_video_comment",
        "weibo",       "weibo_note_comment",
        "xhs",         "xhs_note_comment",
        "zhihu",       "zhihu_comment",
        "tieba",       "tieba_comment"
    );

    /** 所有内容表（含 daily_news） */
    private static final List<String> ALL_CONTENT_TABLES;
    static {
        List<String> tables = new ArrayList<>(PLATFORM_CONTENT_TABLES.values());
        tables.add("daily_news");
        ALL_CONTENT_TABLES = Collections.unmodifiableList(tables);
    }

    private final JdbcTemplate jdbcTemplate;

    public MediaCrawlerDbService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // =========================================================
    // 响应数据模型
    // =========================================================

    public record QueryResult(
        String platform,
        String contentType,      // "content" or "comment"
        String titleOrContent,
        String authorNickname,
        String url,
        LocalDateTime publishTime,
        Map<String, Integer> engagement,  // likes, comments, shares, views, etc.
        double hotnessScore,
        String sourceKeyword,
        String sourceTable
    ) {}

    public record DbResponse(
        String toolName,
        Map<String, Object> parameters,
        List<QueryResult> results,
        int resultsCount,
        String errorMessage
    ) {
        public static DbResponse error(String toolName, String message) {
            return new DbResponse(toolName, Map.of(), List.of(), 0, message);
        }

        /** 转换为 Agent 可读的文本格式 */
        public String toReadableText() {
            if (errorMessage != null && !errorMessage.isBlank()) {
                return "【查询失败】" + errorMessage;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("【%s 查询结果】共 %d 条\n\n", toolName, resultsCount));

            for (int i = 0; i < results.size(); i++) {
                QueryResult r = results.get(i);
                sb.append(String.format("--- 第 %d 条 [%s] ---\n", i + 1, r.platform()));
                if (r.titleOrContent() != null) {
                    String content = r.titleOrContent().length() > 500
                        ? r.titleOrContent().substring(0, 500) + "..."
                        : r.titleOrContent();
                    sb.append("内容: ").append(content).append("\n");
                }
                if (r.authorNickname() != null) {
                    sb.append("作者: ").append(r.authorNickname()).append("\n");
                }
                if (r.publishTime() != null) {
                    sb.append("时间: ").append(r.publishTime()).append("\n");
                }
                if (r.engagement() != null && !r.engagement().isEmpty()) {
                    sb.append("互动: ").append(r.engagement()).append("\n");
                }
                if (r.hotnessScore() > 0) {
                    sb.append(String.format("热度: %.1f\n", r.hotnessScore()));
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    // =========================================================
    // 工具 1: search_hot_content — 热门内容搜索
    // =========================================================

    /**
     * 按加权热度分搜索各平台热门内容。
     * 使用 UNION ALL 查询所有平台的内容表，按热度公式排序。
     *
     * @param timePeriod 时间范围: "24h", "week", "year"
     * @param limit      结果数量上限
     */
    public Mono<DbResponse> searchHotContent(String timePeriod, int limit) {
        return Mono.fromCallable(() -> {
            log.info("searchHotContent: timePeriod={}, limit={}", timePeriod, limit);

            String timeCondition = buildTimeCondition(timePeriod);

            // 构建 UNION ALL 查询
            List<String> unionParts = new ArrayList<>();
            for (Map.Entry<String, String> entry : PLATFORM_CONTENT_TABLES.entrySet()) {
                String platform = entry.getKey();
                String table = entry.getValue();
                unionParts.add(buildHotnessQuery(platform, table, timeCondition));
            }

            String sql = String.join(" UNION ALL ", unionParts) +
                " ORDER BY hotness_score DESC LIMIT ?";

            List<QueryResult> results = jdbcTemplate.query(sql,
                (rs, rowNum) -> mapToQueryResult(rs), limit);

            return new DbResponse("search_hot_content",
                Map.of("time_period", timePeriod, "limit", limit),
                results, results.size(), null);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorReturn(DbResponse.error("search_hot_content", "数据库查询失败"));
    }

    /**
     * 构建单个平台的热度查询 SQL 片段。
     * 热度公式: W_LIKE*liked + W_COMMENT*comments + W_SHARE*shares
     *          + W_VIEW*0.1*views + W_DANMAKU*0.5*danmaku
     */
    private String buildHotnessQuery(String platform, String table,
                                      String timeCondition) {
        // 不同平台的字段名映射
        String likeCol = switch (platform) {
            case "bilibili" -> "COALESCE(liked_count, 0)";
            case "weibo"    -> "COALESCE(liked_count, 0)";
            case "xhs"      -> "COALESCE(liked_count, 0)";
            default         -> "COALESCE(liked_count, 0)";
        };
        String commentCol = "COALESCE(comment_count, 0)";
        String shareCol = "COALESCE(share_count, 0)";
        String viewCol = switch (platform) {
            case "bilibili" -> "COALESCE(video_play_count, 0)";
            default         -> "COALESCE(view_count, 0)";
        };
        String danmakuCol = "bilibili".equals(platform)
            ? "COALESCE(video_danmaku, 0)" : "0";

        return String.format("""
            SELECT '%s' as platform, 'content' as content_type,
                   COALESCE(title, '') as title_or_content,
                   COALESCE(nickname, '') as author_nickname,
                   COALESCE(source_url, '') as url,
                   create_time as publish_time,
                   %s as liked, %s as comments, %s as shares,
                   %s as views, %s as danmaku,
                   (%s * %f + %s * %f + %s * %f + %s * 0.1 * %f + %s * 0.5 * %f)
                       as hotness_score,
                   '%s' as source_table
            FROM %s
            WHERE 1=1 %s
            """,
            platform, likeCol, commentCol, shareCol, viewCol, danmakuCol,
            likeCol, W_LIKE, commentCol, W_COMMENT, shareCol, W_SHARE,
            viewCol, W_VIEW, danmakuCol, W_DANMAKU,
            table, table,
            timeCondition.isBlank() ? "" : "AND " + timeCondition
        );
    }

    // =========================================================
    // 工具 2: search_topic_globally — 全平台主题搜索
    // =========================================================

    /**
     * 跨所有平台（15 张表）搜索主题关键词。
     * 使用 LIKE 模糊匹配搜索内容表和评论表。
     */
    public Mono<DbResponse> searchTopicGlobally(String topic, int limitPerTable) {
        return Mono.fromCallable(() -> {
            log.info("searchTopicGlobally: topic={}, limit={}", topic, limitPerTable);

            List<QueryResult> allResults = new ArrayList<>();
            String likePattern = "%" + topic + "%";

            // 搜索所有内容表
            for (String table : ALL_CONTENT_TABLES) {
                allResults.addAll(searchInTable(table, "content",
                    likePattern, limitPerTable));
            }
            // 搜索所有评论表
            for (String table : PLATFORM_COMMENT_TABLES.values()) {
                allResults.addAll(searchInTable(table, "comment",
                    likePattern, limitPerTable));
            }

            return new DbResponse("search_topic_globally",
                Map.of("topic", topic, "limit_per_table", limitPerTable),
                allResults, allResults.size(), null);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorReturn(DbResponse.error("search_topic_globally", "数据库查询失败"));
    }

    // =========================================================
    // 工具 3: search_topic_by_date — 按日期搜索
    // =========================================================

    /**
     * 在指定日期范围内搜索主题。
     */
    public Mono<DbResponse> searchTopicByDate(String topic, String startDate,
                                                String endDate, int limitPerTable) {
        return Mono.fromCallable(() -> {
            log.info("searchTopicByDate: topic={}, {} ~ {}", topic, startDate, endDate);

            List<QueryResult> allResults = new ArrayList<>();
            String likePattern = "%" + topic + "%";

            for (String table : ALL_CONTENT_TABLES) {
                allResults.addAll(searchInTableWithDateRange(table, "content",
                    likePattern, startDate, endDate, limitPerTable));
            }

            return new DbResponse("search_topic_by_date",
                Map.of("topic", topic, "start_date", startDate, "end_date", endDate),
                allResults, allResults.size(), null);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorReturn(DbResponse.error("search_topic_by_date", "数据库查询失败"));
    }

    // =========================================================
    // 工具 4: get_comments_for_topic — 获取评论
    // =========================================================

    /**
     * 跨 7 个平台搜索特定主题的用户评论。
     * 仅搜索评论表，按时间倒序排列。
     */
    public Mono<DbResponse> getCommentsForTopic(String topic, int limit) {
        return Mono.fromCallable(() -> {
            log.info("getCommentsForTopic: topic={}, limit={}", topic, limit);

            List<QueryResult> allComments = new ArrayList<>();
            String likePattern = "%" + topic + "%";

            for (String table : PLATFORM_COMMENT_TABLES.values()) {
                allComments.addAll(searchInTable(table, "comment", likePattern, limit / 7));
            }

            // 按时间倒序排序
            allComments.sort((a, b) -> {
                if (a.publishTime() == null) return 1;
                if (b.publishTime() == null) return -1;
                return b.publishTime().compareTo(a.publishTime());
            });

            // 截取总限制
            if (allComments.size() > limit) {
                allComments = allComments.subList(0, limit);
            }

            return new DbResponse("get_comments_for_topic",
                Map.of("topic", topic, "limit", limit),
                allComments, allComments.size(), null);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorReturn(DbResponse.error("get_comments_for_topic", "数据库查询失败"));
    }

    // =========================================================
    // 工具 5: search_topic_on_platform — 单平台搜索
    // =========================================================

    /**
     * 在指定平台搜索主题（内容+评论），可选日期过滤。
     */
    public Mono<DbResponse> searchTopicOnPlatform(String platform, String topic,
                                                    String startDate, String endDate,
                                                    int limit) {
        return Mono.fromCallable(() -> {
            log.info("searchTopicOnPlatform: platform={}, topic={}", platform, topic);

            String contentTable = PLATFORM_CONTENT_TABLES.get(platform);
            String commentTable = PLATFORM_COMMENT_TABLES.get(platform);

            if (contentTable == null) {
                return DbResponse.error("search_topic_on_platform",
                    "不支持的平台: " + platform);
            }

            List<QueryResult> results = new ArrayList<>();
            String likePattern = "%" + topic + "%";

            if (startDate != null && endDate != null) {
                results.addAll(searchInTableWithDateRange(contentTable, "content",
                    likePattern, startDate, endDate, limit));
                if (commentTable != null) {
                    results.addAll(searchInTableWithDateRange(commentTable, "comment",
                        likePattern, startDate, endDate, limit));
                }
            } else {
                results.addAll(searchInTable(contentTable, "content", likePattern, limit));
                if (commentTable != null) {
                    results.addAll(searchInTable(commentTable, "comment", likePattern, limit));
                }
            }

            return new DbResponse("search_topic_on_platform",
                Map.of("platform", platform, "topic", topic, "limit", limit),
                results, results.size(), null);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorReturn(DbResponse.error("search_topic_on_platform", "数据库查询失败"));
    }

    // =========================================================
    // 内部查询辅助方法
    // =========================================================

    private List<QueryResult> searchInTable(String table, String contentType,
                                             String likePattern, int limit) {
        try {
            String textCol = getTextColumn(table);
            String sql = String.format(
                "SELECT * FROM %s WHERE %s LIKE ? ORDER BY create_time DESC LIMIT ?",
                table, textCol);
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String platform = inferPlatform(table);
                return new QueryResult(
                    platform, contentType,
                    rs.getString(textCol),
                    rs.getString("nickname"),
                    rs.getString("source_url"),
                    toLocalDateTime(rs.getObject("create_time")),
                    extractEngagement(rs, platform),
                    0.0, likePattern, table
                );
            }, likePattern, limit);
        } catch (Exception e) {
            log.warn("查询表 {} 失败: {}", table, e.getMessage());
            return List.of();
        }
    }

    private List<QueryResult> searchInTableWithDateRange(String table, String contentType,
                                                          String likePattern,
                                                          String startDate, String endDate,
                                                          int limit) {
        try {
            String textCol = getTextColumn(table);
            String sql = String.format(
                "SELECT * FROM %s WHERE %s LIKE ? AND create_time >= ? AND create_time <= ? " +
                "ORDER BY create_time DESC LIMIT ?",
                table, textCol);
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String platform = inferPlatform(table);
                return new QueryResult(
                    platform, contentType,
                    rs.getString(textCol),
                    rs.getString("nickname"),
                    rs.getString("source_url"),
                    toLocalDateTime(rs.getObject("create_time")),
                    extractEngagement(rs, platform),
                    0.0, likePattern, table
                );
            }, likePattern, startDate, endDate, limit);
        } catch (Exception e) {
            log.warn("日期范围查询表 {} 失败: {}", table, e.getMessage());
            return List.of();
        }
    }

    private String buildTimeCondition(String timePeriod) {
        return switch (timePeriod) {
            case "24h"  -> "create_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)";
            case "week"  -> "create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)";
            case "year"  -> "create_time >= DATE_SUB(NOW(), INTERVAL 365 DAY)";
            default      -> "";
        };
    }

    private String getTextColumn(String table) {
        if (table.contains("comment")) return "content";
        if (table.equals("daily_news")) return "title";
        return "title";
    }

    private String inferPlatform(String table) {
        for (var entry : PLATFORM_CONTENT_TABLES.entrySet()) {
            if (table.startsWith(entry.getKey())) return entry.getKey();
        }
        if (table.equals("daily_news")) return "news";
        return "unknown";
    }

    private Map<String, Integer> extractEngagement(java.sql.ResultSet rs, String platform) {
        Map<String, Integer> engagement = new HashMap<>();
        try {
            engagement.put("likes", getIntSafe(rs, "liked_count"));
            engagement.put("comments", getIntSafe(rs, "comment_count"));
            engagement.put("shares", getIntSafe(rs, "share_count"));
            if ("bilibili".equals(platform)) {
                engagement.put("views", getIntSafe(rs, "video_play_count"));
                engagement.put("danmaku", getIntSafe(rs, "video_danmaku"));
                engagement.put("coins", getIntSafe(rs, "video_coin"));
                engagement.put("favorites", getIntSafe(rs, "collect_count"));
            } else {
                engagement.put("views", getIntSafe(rs, "view_count"));
            }
        } catch (Exception ignored) {}
        return engagement;
    }

    private int getIntSafe(java.sql.ResultSet rs, String column) {
        try { return rs.getInt(column); }
        catch (Exception e) { return 0; }
    }

    private LocalDateTime toLocalDateTime(Object ts) {
        if (ts == null) return null;
        if (ts instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (ts instanceof Long l) {
            if (l > 1_000_000_000_000L) l = l / 1000; // 毫秒转秒
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(l), ZoneId.systemDefault());
        }
        if (ts instanceof String s) {
            try { return LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME); }
            catch (Exception e) {
                try { return LocalDate.parse(s).atStartOfDay(); }
                catch (Exception e2) { return null; }
            }
        }
        return null;
    }

    private QueryResult mapToQueryResult(java.sql.ResultSet rs) {
        try {
            return new QueryResult(
                rs.getString("platform"),
                rs.getString("content_type"),
                rs.getString("title_or_content"),
                rs.getString("author_nickname"),
                rs.getString("url"),
                toLocalDateTime(rs.getObject("publish_time")),
                Map.of(
                    "likes", rs.getInt("liked"),
                    "comments", rs.getInt("comments"),
                    "shares", rs.getInt("shares"),
                    "views", rs.getInt("views"),
                    "danmaku", rs.getInt("danmaku")
                ),
                rs.getDouble("hotness_score"),
                "", rs.getString("source_table")
            );
        } catch (Exception e) {
            return null;
        }
    }
}
```

#### 4.4.2 SentimentAnalyzer 情感分析服务

InsightEngine 独有的情感分析能力。在 Java 版中，我们通过 ONNX Runtime 加载 HuggingFace 的多语言情感分析模型，支持 22 种语言、5 级分类。

```java
package com.bettafish.insight.service;

import ai.onnxruntime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.LongBuffer;
import java.util.*;

/**
 * 多语言情感分析服务。
 * 使用 ONNX Runtime 加载 tabularisai/multilingual-sentiment-analysis 模型。
 * 支持 22 种语言，5 级情感分类。
 *
 * 对应 Python: InsightEngine/tools/sentiment_analyzer.py
 *             - WeiboMultilingualSentimentAnalyzer
 */
@Service
public class SentimentAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(SentimentAnalyzerService.class);

    /**
     * 情感标签映射（5 级分类）
     */
    private static final Map<Integer, String> SENTIMENT_LABELS = Map.of(
        0, "非常负面",    // Very Negative
        1, "负面",        // Negative
        2, "中性",        // Neutral
        3, "正面",        // Positive
        4, "非常正面"     // Very Positive
    );

    /**
     * 支持的 22 种语言
     */
    private static final List<String> SUPPORTED_LANGUAGES = List.of(
        "Chinese", "English", "Spanish", "Arabic", "Japanese", "Korean",
        "German", "French", "Italian", "Portuguese", "Russian", "Dutch",
        "Polish", "Turkish", "Danish", "Greek", "Finnish", "Swedish",
        "Norwegian", "Hungarian", "Czech", "Bulgarian"
    );

    @Value("${bettafish.sentiment.model-path:models/sentiment-analysis.onnx}")
    private String modelPath;

    @Value("${bettafish.sentiment.enabled:true}")
    private boolean enabled;

    private OrtEnvironment env;
    private OrtSession session;
    private boolean initialized = false;

    // =========================================================
    // 结果数据模型
    // =========================================================

    public record SentimentResult(
        String text,
        String sentimentLabel,
        double confidence,
        Map<String, Double> probabilityDistribution,
        boolean success,
        String errorMessage
    ) {
        public static SentimentResult error(String text, String error) {
            return new SentimentResult(text, null, 0.0, Map.of(), false, error);
        }
    }

    public record BatchSentimentResult(
        List<SentimentResult> results,
        int totalProcessed,
        int successCount,
        int failedCount,
        double averageConfidence,
        String summaryText
    ) {}

    // =========================================================
    // 生命周期管理
    // =========================================================

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.info("情感分析已禁用");
            return;
        }
        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            // 优先使用 CUDA，否则 CPU
            try {
                OrtSession.SessionOptions.appendCUDAProvider(opts, 0);
                log.info("情感分析使用 CUDA 加速");
            } catch (Exception e) {
                log.info("情感分析使用 CPU 推理");
            }
            session = env.createSession(modelPath, opts);
            initialized = true;
            log.info("情感分析模型加载完成: {}", modelPath);
        } catch (Exception e) {
            log.error("情感分析模型加载失败: {}", e.getMessage());
            initialized = false;
        }
    }

    // =========================================================
    // 单文本分析
    // =========================================================

    public SentimentResult analyzeSingleText(String text) {
        if (!initialized || !enabled) {
            return SentimentResult.error(text, "情感分析未初始化或已禁用");
        }

        try {
            // 文本预处理：截断至 512 token
            String processed = text.length() > 512 ? text.substring(0, 512) : text;

            // 简化的 tokenization（实际应使用 ONNX tokenizer 或预处理器）
            long[] inputIds = tokenize(processed);
            long[] attentionMask = new long[inputIds.length];
            Arrays.fill(attentionMask, 1L);

            // 推理
            Map<String, OnnxTensor> inputs = Map.of(
                "input_ids", OnnxTensor.createTensor(env,
                    LongBuffer.wrap(inputIds), new long[]{1, inputIds.length}),
                "attention_mask", OnnxTensor.createTensor(env,
                    LongBuffer.wrap(attentionMask), new long[]{1, attentionMask.length})
            );

            OrtSession.Result output = session.run(inputs);
            float[][] logits = (float[][]) output.get(0).getValue();

            // Softmax 转概率分布
            double[] probs = softmax(logits[0]);
            int predictedClass = argmax(probs);
            double confidence = probs[predictedClass];

            Map<String, Double> distribution = new LinkedHashMap<>();
            for (int i = 0; i < probs.length && i < SENTIMENT_LABELS.size(); i++) {
                distribution.put(SENTIMENT_LABELS.get(i), probs[i]);
            }

            return new SentimentResult(
                text,
                SENTIMENT_LABELS.get(predictedClass),
                confidence,
                distribution,
                true,
                null
            );

        } catch (Exception e) {
            log.warn("情感分析失败: {}", e.getMessage());
            return SentimentResult.error(text, e.getMessage());
        }
    }

    // =========================================================
    // 批量分析
    // =========================================================

    public BatchSentimentResult analyzeBatch(List<String> texts) {
        List<SentimentResult> results = new ArrayList<>();
        int successCount = 0;
        double totalConfidence = 0;

        for (String text : texts) {
            SentimentResult result = analyzeSingleText(text);
            results.add(result);
            if (result.success()) {
                successCount++;
                totalConfidence += result.confidence();
            }
        }

        double avgConfidence = successCount > 0 ? totalConfidence / successCount : 0;

        // 生成摘要文本
        Map<String, Long> labelCounts = results.stream()
            .filter(SentimentResult::success)
            .collect(Collectors.groupingBy(SentimentResult::sentimentLabel,
                Collectors.counting()));

        StringBuilder summary = new StringBuilder("情感分布: ");
        labelCounts.forEach((label, count) ->
            summary.append(String.format("%s %.1f%%, ",
                label, 100.0 * count / successCount)));

        return new BatchSentimentResult(
            results, texts.size(), successCount,
            texts.size() - successCount, avgConfidence,
            summary.toString().trim()
        );
    }

    // =========================================================
    // 针对数据库查询结果的分析
    // =========================================================

    /**
     * 分析 MediaCrawlerDB 的查询结果。
     * 返回情感分布、主导情感、高置信度结果和摘要。
     */
    public Map<String, Object> analyzeQueryResults(
            List<MediaCrawlerDbService.QueryResult> queryResults,
            double minConfidence) {

        if (!initialized || !enabled || queryResults.isEmpty()) {
            return Map.of("status", "skipped", "reason", "未初始化或无数据");
        }

        List<String> texts = queryResults.stream()
            .map(MediaCrawlerDbService.QueryResult::titleOrContent)
            .filter(Objects::nonNull)
            .filter(t -> !t.isBlank())
            .toList();

        BatchSentimentResult batchResult = analyzeBatch(texts);

        // 高置信度结果
        List<SentimentResult> highConfidence = batchResult.results().stream()
            .filter(r -> r.success() && r.confidence() >= minConfidence)
            .toList();

        // 情感分布统计
        Map<String, Long> distribution = batchResult.results().stream()
            .filter(SentimentResult::success)
            .collect(Collectors.groupingBy(SentimentResult::sentimentLabel,
                Collectors.counting()));

        // 主导情感
        String dominantSentiment = distribution.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("未知");

        return Map.of(
            "sentiment_distribution", distribution,
            "dominant_sentiment", dominantSentiment,
            "high_confidence_results", highConfidence,
            "summary", batchResult.summaryText(),
            "total_analyzed", batchResult.totalProcessed(),
            "average_confidence", batchResult.averageConfidence()
        );
    }

    // =========================================================
    // 工具方法
    // =========================================================

    private long[] tokenize(String text) {
        // 简化版本 — 实际应使用完整的 tokenizer
        char[] chars = text.toCharArray();
        long[] ids = new long[Math.min(chars.length, 512)];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = chars[i];
        }
        return ids;
    }

    private double[] softmax(float[] logits) {
        double max = Float.MIN_VALUE;
        for (float l : logits) if (l > max) max = l;
        double sum = 0;
        double[] probs = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probs[i] = Math.exp(logits[i] - max);
            sum += probs[i];
        }
        for (int i = 0; i < probs.length; i++) probs[i] /= sum;
        return probs;
    }

    private int argmax(double[] arr) {
        int maxIdx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[maxIdx]) maxIdx = i;
        }
        return maxIdx;
    }
}
```

#### 4.4.3 KeywordOptimizer 关键词优化器

InsightEngine 独有的关键词优化中间件。在执行搜索前，将用户查询扩展为多个优化后的搜索关键词，每个关键词独立搜索后合并结果。

```java
package com.bettafish.insight.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 关键词优化服务。
 * 将原始搜索查询扩展为多个更精准、更接地气的搜索关键词。
 *
 * 核心策略：
 * - 避免官方术语，使用真实网民语言
 * - 考虑平台特色用语
 * - 包含情感性词汇以捕获更多真实用户反应
 */
@Service
public class KeywordOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(KeywordOptimizerService.class);

    private final ChatClient chatClient;

    public KeywordOptimizerService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    private static final String OPTIMIZE_PROMPT = """
        你是一位社交媒体舆情搜索专家。

        任务：将用户的搜索查询优化为 3-5 个更精准的搜索关键词，
        以便在社交媒体数据库中检索到更多真实的用户内容。

        关键词优化原则：
        1. 避免官方术语（如"舆情传播"、"公众反应"、"社会影响"）
        2. 使用真实网民语言和平台特色用语
        3. 包含情感性词汇以捕获真实用户反应
        4. 考虑不同表述方式和同义词

        平台用语参考：
        - 微博：热搜词汇、#话题标签#
        - 知乎：问答格式（"如何看待..."、"怎么评价..."）
        - B站：弹幕文化（"yyds"、"绝了"、"太强了"）
        - 贴吧：直接称呼（"xx吧"、"楼主"）
        - 抖音/快手：视频描述（"日常"、"记录"）
        - 小红书：分享体（"真的很..."、"推荐"、"避雷"）

        输出格式：每行一个关键词，不加编号或标点。
        """;

    /**
     * 优化搜索关键词。
     *
     * @param originalQuery 原始查询
     * @param context       上下文信息（段落标题等）
     * @return 优化后的关键词列表
     */
    public List<String> optimizeKeywords(String originalQuery, String context) {
        try {
            String response = chatClient.prompt()
                .system(OPTIMIZE_PROMPT)
                .user("原始查询: " + originalQuery + "\n上下文: " + context)
                .call()
                .content();

            List<String> keywords = Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(5)
                .toList();

            // 确保原始查询也在列表中
            if (!keywords.contains(originalQuery)) {
                List<String> combined = new java.util.ArrayList<>(keywords);
                combined.add(0, originalQuery);
                return combined;
            }

            log.info("关键词优化: {} -> {}", originalQuery, keywords);
            return keywords;

        } catch (Exception e) {
            log.warn("关键词优化失败，使用原始查询: {}", e.getMessage());
            return List.of(originalQuery);
        }
    }
}
```

#### 4.4.4 InsightAgent 主类

```java
package com.bettafish.insight;

import com.bettafish.common.agent.AbstractAnalysisAgent;
import com.bettafish.insight.prompt.InsightPrompts;
import com.bettafish.insight.service.*;
import com.bettafish.insight.service.MediaCrawlerDbService.DbResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * InsightEngine Agent — 社交媒体舆情分析代理。
 * 查询本地数据库中的 7 大平台社交媒体数据，
 * 配合情感分析和关键词优化进行深度舆情挖掘。
 *
 * 对应 Python: InsightEngine/agent.py - DeepSearchAgent
 */
@Component
public class InsightAgent extends AbstractAnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(InsightAgent.class);

    private final MediaCrawlerDbService dbService;
    private final SentimentAnalyzerService sentimentService;
    private final KeywordOptimizerService keywordOptimizer;

    public InsightAgent(ChatClient.Builder chatClientBuilder,
                        ReactiveStringRedisTemplate redisTemplate,
                        MediaCrawlerDbService dbService,
                        SentimentAnalyzerService sentimentService,
                        KeywordOptimizerService keywordOptimizer) {
        super(chatClientBuilder.build(), redisTemplate);
        this.dbService = dbService;
        this.sentimentService = sentimentService;
        this.keywordOptimizer = keywordOptimizer;
    }

    @Override
    protected String getAgentName() {
        return "INSIGHT";
    }

    @Override
    protected String getPromptTemplate(PromptStage stage) {
        return switch (stage) {
            case REPORT_STRUCTURE    -> InsightPrompts.SYSTEM_PROMPT_REPORT_STRUCTURE;
            case FIRST_SEARCH        -> InsightPrompts.SYSTEM_PROMPT_FIRST_SEARCH;
            case FIRST_SUMMARY       -> InsightPrompts.SYSTEM_PROMPT_FIRST_SUMMARY;
            case REFLECTION          -> InsightPrompts.SYSTEM_PROMPT_REFLECTION;
            case REFLECTION_SUMMARY  -> InsightPrompts.SYSTEM_PROMPT_REFLECTION_SUMMARY;
            case REPORT_FORMATTING   -> InsightPrompts.SYSTEM_PROMPT_REPORT_FORMATTING;
        };
    }

    @Override
    protected Mono<String> executeSearch(String toolName, String query,
                                          Map<String, String> params) {
        boolean enableSentiment = !"false".equalsIgnoreCase(
            params.getOrDefault("enable_sentiment", "true"));

        return switch (toolName) {
            case "search_hot_content" -> {
                String timePeriod = params.getOrDefault("time_period", "week");
                yield dbService.searchHotContent(timePeriod, 50)
                    .flatMap(resp -> appendSentiment(resp, enableSentiment));
            }
            case "search_topic_globally" -> {
                // 使用关键词优化器扩展查询
                yield executeWithKeywordOptimization(query, params,
                    kw -> dbService.searchTopicGlobally(kw, 100), enableSentiment);
            }
            case "search_topic_by_date" -> {
                String sd = params.getOrDefault("start_date", "");
                String ed = params.getOrDefault("end_date", "");
                yield executeWithKeywordOptimization(query, params,
                    kw -> dbService.searchTopicByDate(kw, sd, ed, 100), enableSentiment);
            }
            case "get_comments_for_topic" -> {
                yield executeWithKeywordOptimization(query, params,
                    kw -> dbService.getCommentsForTopic(kw, 500), enableSentiment);
            }
            case "search_topic_on_platform" -> {
                String platform = params.getOrDefault("platform", "weibo");
                String sd = params.get("start_date");
                String ed = params.get("end_date");
                yield executeWithKeywordOptimization(query, params,
                    kw -> dbService.searchTopicOnPlatform(platform, kw, sd, ed, 20),
                    enableSentiment);
            }
            case "analyze_sentiment" -> {
                // 独立情感分析工具
                String textsParam = params.getOrDefault("texts", query);
                List<String> texts = Arrays.asList(textsParam.split("\\|\\|"));
                yield Mono.fromCallable(() -> {
                    var result = sentimentService.analyzeBatch(texts);
                    return result.summaryText();
                });
            }
            default -> {
                log.warn("未知工具 {}，回退至 search_topic_globally", toolName);
                yield executeWithKeywordOptimization(query, params,
                    kw -> dbService.searchTopicGlobally(kw, 100), enableSentiment);
            }
        };
    }

    /**
     * 关键词优化 + 多关键词搜索 + 结果合并。
     */
    private Mono<String> executeWithKeywordOptimization(
            String query, Map<String, String> params,
            java.util.function.Function<String, Mono<DbResponse>> searchFn,
            boolean enableSentiment) {

        List<String> keywords = keywordOptimizer.optimizeKeywords(
            query, params.getOrDefault("context", ""));

        return Flux.fromIterable(keywords)
            .flatMap(searchFn::apply)
            .collectList()
            .map(this::mergeResponses)
            .flatMap(resp -> appendSentiment(resp, enableSentiment));
    }

    /**
     * 合并多个搜索响应（去重）。
     */
    private DbResponse mergeResponses(List<DbResponse> responses) {
        List<MediaCrawlerDbService.QueryResult> allResults = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (DbResponse resp : responses) {
            for (var result : resp.results()) {
                String key = result.titleOrContent() != null
                    ? result.titleOrContent().substring(0,
                        Math.min(50, result.titleOrContent().length()))
                    : result.url();
                if (seen.add(key)) {
                    allResults.add(result);
                }
            }
        }

        return new DbResponse("merged", Map.of(), allResults, allResults.size(), null);
    }

    /**
     * 为搜索结果追加情感分析。
     */
    private Mono<String> appendSentiment(DbResponse response, boolean enable) {
        if (!enable || response.results().isEmpty()) {
            return Mono.just(response.toReadableText());
        }

        return Mono.fromCallable(() -> {
            Map<String, Object> sentimentResult =
                sentimentService.analyzeQueryResults(response.results(), 0.5);

            String text = response.toReadableText();
            if (sentimentResult.containsKey("summary")) {
                text += "\n\n【情感分析】\n" + sentimentResult.get("summary");
                text += "\n主导情感: " + sentimentResult.get("dominant_sentiment");
                text += "\n分析样本: " + sentimentResult.get("total_analyzed") + " 条";
            }
            return text;
        });
    }

    @Override
    protected String getToolDescriptions() {
        return """
            可用工具列表：
            1. search_hot_content — 热门内容搜索，按加权热度排序（参数: time_period='24h'/'week'/'year'）
            2. search_topic_globally — 全平台主题搜索（Bilibili/微博/抖音/快手/小红书/知乎/贴吧）
            3. search_topic_by_date — 按日期范围搜索（参数: start_date, end_date，YYYY-MM-DD）
            4. get_comments_for_topic — 获取用户评论，深挖真实态度和情绪
            5. search_topic_on_platform — 单平台搜索（参数: platform=bilibili/weibo/douyin/kuaishou/xhs/zhihu/tieba）
            6. analyze_sentiment — 情感分析工具，多语言5级分类（参数: texts，多文本用||分隔）
            """;
    }
}
```

#### 4.4.5 InsightEngine 完整 Prompt 模板

InsightEngine 的 Prompt 与其他引擎差异最大，特别强调：(1) 使用网民真实语言而非官方术语 (2) 平台特色用语指南 (3) 情感词汇库 (4) 丰富的用户评论引用。

```java
package com.bettafish.insight.prompt;

/**
 * InsightEngine Prompt 常量集合。
 * 对应 Python: InsightEngine/prompts/prompts.py
 *
 * 核心特色：使用真实网民语言，避免官方术语。
 */
public final class InsightPrompts {

    private InsightPrompts() {}

    // ==========================================================
    // Prompt 1: 报告结构 — 舆情分析专用
    // ==========================================================

    public static final String SYSTEM_PROMPT_REPORT_STRUCTURE = """
        你是一位专业的舆情分析师和报告架构师。

        任务：规划一份全面的舆情分析报告结构，包含 5 个核心段落。

        必须覆盖的分析维度：
        1. 背景与事件概述 — 事件来龙去脉、关键时间节点
        2. 舆情热度与传播分析 — 各平台的热度数据、传播路径
        3. 公众情绪与观点分析 — 正面/负面/中性情感分布、代表性观点
        4. 跨群体与跨平台差异 — 不同平台用户的态度差异
        5. 深层原因与社会影响 — 事件背后的深层动因、社会心理分析

        每个段落必须包含：
        - 3-5 个子分析点
        - 需要引用的数据类型
        - 多元化的观点视角

        输出格式：仅返回 JSON 数组
        格式: [{"title": "段落标题", "content": "期望内容描述"}, ...]
        """;

    // ==========================================================
    // Prompt 2: 首次搜索 — 含平台用语指南和情感词汇库
    // ==========================================================

    public static final String SYSTEM_PROMPT_FIRST_SEARCH = """
        你是一位专业的舆情分析师。

        核心使命：挖掘真实的公众意见和人类情感。

        可用搜索工具：
        1. search_hot_content — 按热度搜索热门内容，基于真实的点赞/评论/分享数据
            参数: time_period（'24h'/'week'/'year'），enable_sentiment（默认true）
        2. search_topic_globally — 全平台主题搜索（覆盖B站/微博/抖音/快手/小红书/知乎/贴吧）
            参数: enable_sentiment（默认true）
        3. search_topic_by_date — 按日期范围追踪舆情演变
            参数: start_date, end_date（YYYY-MM-DD），enable_sentiment
        4. get_comments_for_topic — 深挖用户评论，获取真实态度和情绪
            参数: enable_sentiment（默认true）
        5. search_topic_on_platform — 针对特定平台搜索，分析用户群体特征
            参数: platform（必填: bilibili/weibo/douyin/kuaishou/xhs/zhihu/tieba）
        6. analyze_sentiment — 多语言情感分析（22种语言，5级分类）
            参数: texts（文本列表）

        【关键】搜索查询设计规则：
        - 禁止使用官方术语："舆情传播"、"公众反应"、"社会影响"
        - 必须使用真实网民语言模式
        - 包含情感性词汇
        - 考虑平台特色用语

        【平台用语指南】
        - 微博：热搜词汇、#话题标签#（如 "武大又上热搜"、"#武汉大学#"）
        - 知乎：问答格式（如 "如何看待武汉大学"、"怎么评价XX事件"）
        - B站：弹幕文化（如 "武大yyds"、"太强了吧"）
        - 贴吧：直接称呼（如 "武大吧"、"有没有xx吧友"）
        - 抖音/快手：视频描述（如 "武大日常"、"记录xx"）
        - 小红书：分享体（如 "武大真的很美"、"推荐xx"、"避雷"）

        【情感词汇库】
        正面: "太棒了", "牛逼", "绝了", "爱了", "yyds", "666", "点赞", "支持"
        负面: "无语", "离谱", "服了", "麻了", "破防", "翻车", "塌房", "难绷"
        中性: "围观", "吃瓜", "路过", "有一说一", "客观说"

        输出：仅返回 JSON
        必填: search_query, search_tool, reasoning
        可选: platform, time_period, enable_sentiment, start_date, end_date, texts
        """;

    // ==========================================================
    // Prompt 3: 首次总结 — 强调用户评论引用和数据密度
    // ==========================================================

    public static final String SYSTEM_PROMPT_FIRST_SUMMARY = """
        你是一位专业的舆情分析师和深度内容创作专家。

        任务：基于搜索结果撰写数据丰富的舆情分析段落。

        撰写要求：
        1. 字数：800-1200 字以上
        2. 引用至少 5-8 条代表性用户评论
        3. 包含精确统计数据（点赞数、评论数、分享数、用户量）
        4. 详细的情感分布百分比（正面 X%、负面 Y%、中性 Z%）
        5. 平台数据对比

        分析层次：
        - 现象描述：用数据说话，展示舆情热度和传播广度
        - 数据分析：互动数据对比、平台差异、时间趋势
        - 观点挖掘：提取并归类典型用户观点，引用原文
        - 深度洞察：从社会心理学、文化因素角度分析

        信息密度：每 100 字至少 1-2 个数据点或用户评论引用

        用户评论引用格式：
        > "评论原文..." —— @用户昵称（平台，点赞 XXX）

        输出：仅返回 JSON — {"paragraph_latest_state": "段落完整内容..."}
        """;

    // ==========================================================
    // Prompt 4: 反思 — 强调人文性和真实性
    // ==========================================================

    public static final String SYSTEM_PROMPT_REFLECTION = """
        你是一位资深舆情分析师。

        核心目标：让报告更具人文关怀和真实感。

        反思检查清单：
        1. 当前段落是否太过官方化/公式化？
        2. 是否缺少真实的公众声音和情感表达？
        3. 是否遗漏了重要的公众意见和争议焦点？
        4. 是否需要具体的网民评论和真实案例？
        5. 情感分析是否覆盖了多个维度？

        搜索词优化指引：
        - 争议话题用：出事了、怎么回事、翻车、炸了（不要用"争议事件"）
        - 情感话题用：支持、反对、心疼、气死、666（不要用"情感倾向"）
        - 热度话题用：上热搜、火了、刷屏、出圈（不要用"传播效果"）

        可用工具（6个）：
        1. search_hot_content — 热门内容
        2. search_topic_globally — 全平台搜索
        3. search_topic_by_date — 日期范围搜索
        4. get_comments_for_topic — 用户评论
        5. search_topic_on_platform — 单平台搜索
        6. analyze_sentiment — 情感分析

        输出：仅返回 JSON — 必填 search_query, search_tool, reasoning
        """;

    // ==========================================================
    // Prompt 5: 反思总结 — 大幅扩充策略
    // ==========================================================

    public static final String SYSTEM_PROMPT_REFLECTION_SUMMARY = """
        你是一位资深舆情分析师和内容深化专家。

        任务：大幅丰富和扩充当前段落内容（目标 1000-1500 字）。

        扩充策略：
        - 保留 70% 的原有核心内容
        - 新增内容量不少于原内容的 100%
        - 每 200 字至少包含 3-5 个数据点
        - 每段至少 8-12 条用户评论引用

        质量检查清单：
        □ 是否有足够多的具体数据和统计？
        □ 是否有足够多样化的用户声音？
        □ 是否进行了多层次的深度分析？
        □ 是否有跨维度对比和趋势分析？
        □ 情感分析是否有具体的比例数据？

        输出：仅返回 JSON — {"updated_paragraph_latest_state": "更新后内容..."}
        """;

    // ==========================================================
    // Prompt 6: 报告格式化 — 舆情分析专业报告
    // ==========================================================

    public static final String SYSTEM_PROMPT_REPORT_FORMATTING = """
        你是一位资深舆情分析专家和报告撰写大师。

        任务：将各段落研究成果整合为专业的舆情分析报告。

        要求：
        1. 总字数不少于 10,000 字
        2. 使用 Markdown 格式
        3. 充分体现公众情绪和真实用户声音

        报告结构：

        # [舆情洞察] 深度公众意见分析报告：{主题}

        ## 摘要
        ### 核心舆情发现
        - （5-8 个关键发现）
        ### 公众意见热点概览
        - （热度排名 TOP5 话题/事件）

        ## 第一章 {段落标题}
        ### 舆情数据画像
        | 平台 | 参与用户 | 内容数量 | 正面占比 | 负面占比 | 中性占比 |
        |------|----------|----------|----------|----------|----------|

        ### 代表性公众声音
        #### 支持方声音（XX%）
        > "评论原文..." —— @用户A（平台，点赞 XXXX）

        #### 反对方声音（XX%）
        > "评论原文..." —— @用户C（平台，评论 XXXX）

        #### 中立/观望声音（XX%）
        > "评论原文..." —— @用户E（平台）

        ### 深度情感解读
        ### 情绪演变轨迹

        ## （后续章节类推）

        ## 综合舆情分析
        ### 总体公众意见倾向
        ### 跨群体意见对比
        | 群体 | 核心诉求 | 情感倾向 | 影响力 |
        ### 平台差异化分析
        ### 舆情发展预测

        ## 深度洞察与建议
        ### 社会心理分析
        ### 舆情管理建议

        ## 数据附录

        特色元素：
        - 使用 emoji 进行情感可视化（😊 😡 😢 🤔）
        - 用温度隐喻描述情感强度（"沸腾"、"升温"、"降温"）
        - 用颜色概念划分情感区域（"红色警戒区"、"绿色安全区"）
        - 使用引用块突出真实用户声音
        """;
}
```

---

### 4.5 ReportEngine Agent

ReportEngine 与前三个分析引擎截然不同——它不执行搜索-反思循环，而是采用 **六阶段流水线模式**，将三个分析引擎和论坛讨论的产物整合为最终的交互式 HTML 报告。六个阶段分别是：模板选择、文档布局、字数预算、章节生成、IR 拼接、HTML 渲染。

#### 4.5.1 报告模板定义

系统预定义 6 种报告模板，覆盖常见的舆情分析场景：

```java
package com.bettafish.report.model;

/**
 * 报告模板类型枚举。
 * 对应 Python: ReportEngine/prompts/prompts.py 中的 6 种模板。
 */
public enum ReportTemplate {

    BRAND_REPUTATION(
        "brand_reputation",
        "企业品牌声誉分析报告模板",
        "适用于品牌形象和声誉管理分析"
    ),
    MARKET_COMPETITION(
        "market_competition",
        "市场竞争格局舆情分析报告模板",
        "适用于市场竞争态势和竞品分析"
    ),
    ROUTINE_MONITORING(
        "routine_monitoring",
        "日常或定期舆情监测报告模板",
        "适用于日常/定期的舆情监控报告"
    ),
    POLICY_INDUSTRY(
        "policy_industry",
        "特定政策或行业动态舆情分析报告",
        "适用于政策/行业动态分析"
    ),
    SOCIAL_HOT_EVENT(
        "social_hot_event",
        "社会公共热点事件分析报告模板",
        "适用于社会公共热点事件分析（推荐默认）"
    ),
    CRISIS_PR(
        "crisis_pr",
        "突发事件与危机公关舆情报告模板",
        "适用于危机公关和突发事件应对"
    );

    private final String code;
    private final String displayName;
    private final String description;

    ReportTemplate(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public static ReportTemplate fromCode(String code) {
        for (ReportTemplate t : values()) {
            if (t.code.equals(code)) return t;
        }
        return SOCIAL_HOT_EVENT; // 默认模板
    }
}
```

#### 4.5.2 IR (Intermediate Representation) 块类型

ReportEngine 使用中间表示（IR）格式来描述报告的结构化内容，共支持 16 种块类型：

```java
package com.bettafish.report.model;

/**
 * IR 块类型枚举。
 * 定义报告中所有允许使用的结构化块类型。
 */
public enum IrBlockType {
    HEADING,          // 标题（h1-h6）
    PARAGRAPH,        // 段落（含 inlines 和 marks）
    LIST,             // 列表（有序/无序）
    TABLE,            // 表格（rows/cells/align）
    WIDGET,           // 图表（Chart.js 类型）
    CALLOUT,          // 提示框（info/warning/danger/success）
    BLOCKQUOTE,       // 引用块
    HR,               // 分隔线
    KPI_GRID,         // KPI 指标网格
    CODE,             // 代码块
    MATH,             // 数学公式（LaTeX）
    SWOT_TABLE,       // SWOT 分析表（最多 1 个章节使用）
    PEST_TABLE,       // PEST 分析表（最多 1 个章节使用）
    ENGINE_QUOTE;     // 引擎引用块（引用 INSIGHT/MEDIA/QUERY 的原文）

    public String toJsonType() {
        return name().toLowerCase().replace("_", "");
    }
}
```

#### 4.5.3 ReportAgent 编排器

```java
package com.bettafish.report;

import com.bettafish.common.model.AnalysisResult;
import com.bettafish.report.model.ReportTemplate;
import com.bettafish.report.prompt.ReportPrompts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * ReportEngine Agent — 报告生成编排器。
 *
 * 六阶段流水线：
 *   1. 模板选择 (Template Selection)
 *   2. 文档布局 (Document Layout)
 *   3. 字数预算 (Word Budget)
 *   4. 章节生成 (Chapter Generation) — 含修复/救援
 *   5. IR 拼接  (IR Stitching)
 *   6. HTML 渲染 (HTML Rendering)
 *
 * 对应 Python: ReportEngine/prompts/prompts.py + ReportEngine 编排逻辑
 */
@Component
public class ReportAgent {

    private static final Logger log = LoggerFactory.getLogger(ReportAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TARGET_TOTAL_WORDS = 40000;

    private final ChatClient chatClient;

    public ReportAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    // =========================================================
    // 主入口：生成完整报告
    // =========================================================

    /**
     * 生成完整的交互式 HTML 报告。
     *
     * @param query         用户查询主题
     * @param queryReport   QueryEngine 的分析报告
     * @param mediaReport   MediaEngine 的分析报告
     * @param insightReport InsightEngine 的分析报告
     * @param forumLogs     ForumEngine 的讨论日志
     * @return 完整的 HTML 报告字符串
     */
    public Mono<String> generateReport(String query,
                                        AnalysisResult queryReport,
                                        AnalysisResult mediaReport,
                                        AnalysisResult insightReport,
                                        List<String> forumLogs) {
        log.info("ReportEngine: 开始生成报告 - query={}", query);

        return selectTemplate(query, queryReport, mediaReport, insightReport)
            .flatMap(template -> designLayout(query, template, queryReport, mediaReport, insightReport)
                .flatMap(layout -> allocateWordBudget(layout)
                    .flatMap(budget -> generateAllChapters(layout, budget,
                        queryReport, mediaReport, insightReport, forumLogs)
                        .flatMap(chapters -> stitchIrDocument(layout, chapters)
                            .flatMap(this::renderToHtml)))));
    }

    // =========================================================
    // 阶段 1: 模板选择
    // =========================================================

    private Mono<ReportTemplate> selectTemplate(String query,
                                                  AnalysisResult queryReport,
                                                  AnalysisResult mediaReport,
                                                  AnalysisResult insightReport) {
        log.info("ReportEngine 阶段 1: 模板选择");

        String userPrompt = String.format("""
            用户查询主题: %s

            QueryEngine 报告摘要（前 500 字）:
            %s

            MediaEngine 报告摘要（前 500 字）:
            %s

            InsightEngine 报告摘要（前 500 字）:
            %s

            请选择最合适的报告模板。
            """,
            query,
            truncate(queryReport.report(), 500),
            truncate(mediaReport.report(), 500),
            truncate(insightReport.report(), 500)
        );

        return callLlm(ReportPrompts.SYSTEM_PROMPT_TEMPLATE_SELECTION, userPrompt)
            .map(response -> {
                try {
                    JsonNode json = objectMapper.readTree(extractJson(response));
                    String templateName = json.get("template_name").asText();
                    String reason = json.get("selection_reason").asText();
                    log.info("选择模板: {} - 原因: {}", templateName, reason);
                    return ReportTemplate.fromCode(templateName);
                } catch (Exception e) {
                    log.warn("模板选择解析失败，使用默认模板");
                    return ReportTemplate.SOCIAL_HOT_EVENT;
                }
            });
    }

    // =========================================================
    // 阶段 2: 文档布局
    // =========================================================

    private Mono<JsonNode> designLayout(String query, ReportTemplate template,
                                         AnalysisResult queryReport,
                                         AnalysisResult mediaReport,
                                         AnalysisResult insightReport) {
        log.info("ReportEngine 阶段 2: 文档布局设计");

        String userPrompt = ReportPrompts.buildDocumentLayoutPrompt(
            query, template, queryReport, mediaReport, insightReport);

        return callLlm(ReportPrompts.SYSTEM_PROMPT_DOCUMENT_LAYOUT, userPrompt)
            .map(response -> {
                try {
                    return objectMapper.readTree(extractJson(response));
                } catch (Exception e) {
                    throw new RuntimeException("文档布局解析失败", e);
                }
            });
    }

    // =========================================================
    // 阶段 3: 字数预算
    // =========================================================

    private Mono<JsonNode> allocateWordBudget(JsonNode layout) {
        log.info("ReportEngine 阶段 3: 字数预算分配");

        String userPrompt = ReportPrompts.buildWordBudgetPrompt(layout);

        return callLlm(ReportPrompts.SYSTEM_PROMPT_WORD_BUDGET, userPrompt)
            .map(response -> {
                try {
                    return objectMapper.readTree(extractJson(response));
                } catch (Exception e) {
                    throw new RuntimeException("字数预算解析失败", e);
                }
            });
    }

    // =========================================================
    // 阶段 4: 章节生成（含修复和救援）
    // =========================================================

    private Mono<List<JsonNode>> generateAllChapters(JsonNode layout, JsonNode budget,
                                                      AnalysisResult queryReport,
                                                      AnalysisResult mediaReport,
                                                      AnalysisResult insightReport,
                                                      List<String> forumLogs) {
        log.info("ReportEngine 阶段 4: 章节生成");

        JsonNode tocPlan = layout.path("tocPlan");
        if (!tocPlan.isArray()) {
            return Mono.error(new RuntimeException("tocPlan 不是数组"));
        }

        return Flux.range(0, tocPlan.size())
            .concatMap(i -> {
                JsonNode chapter = tocPlan.get(i);
                return generateSingleChapter(chapter, layout, budget,
                    queryReport, mediaReport, insightReport, forumLogs, i)
                    .onErrorResume(e -> {
                        log.warn("章节 {} 生成失败，尝试修复", i);
                        return repairChapter(chapter, e.getMessage(), layout);
                    })
                    .onErrorResume(e -> {
                        log.warn("章节 {} 修复失败，尝试救援", i);
                        return rescueChapter(chapter, layout, queryReport,
                            mediaReport, insightReport);
                    });
            })
            .collectList();
    }

    private Mono<JsonNode> generateSingleChapter(JsonNode chapter, JsonNode layout,
                                                   JsonNode budget,
                                                   AnalysisResult queryReport,
                                                   AnalysisResult mediaReport,
                                                   AnalysisResult insightReport,
                                                   List<String> forumLogs,
                                                   int chapterIndex) {
        String userPrompt = ReportPrompts.buildChapterUserPrompt(
            chapter, layout, budget, queryReport, mediaReport, insightReport,
            forumLogs, chapterIndex);

        return callLlm(ReportPrompts.SYSTEM_PROMPT_CHAPTER_JSON, userPrompt)
            .map(response -> {
                try {
                    JsonNode ir = objectMapper.readTree(extractJson(response));
                    validateIrBlocks(ir);
                    return ir;
                } catch (Exception e) {
                    throw new RuntimeException("章节 IR 验证失败: " + e.getMessage(), e);
                }
            });
    }

    private Mono<JsonNode> repairChapter(JsonNode chapter, String errors,
                                           JsonNode layout) {
        String userPrompt = ReportPrompts.buildChapterRepairPrompt(chapter, errors);
        return callLlm(ReportPrompts.SYSTEM_PROMPT_CHAPTER_JSON_REPAIR, userPrompt)
            .map(response -> {
                try {
                    return objectMapper.readTree(extractJson(response));
                } catch (Exception e) {
                    throw new RuntimeException("章节修复失败", e);
                }
            });
    }

    private Mono<JsonNode> rescueChapter(JsonNode chapter, JsonNode layout,
                                           AnalysisResult queryReport,
                                           AnalysisResult mediaReport,
                                           AnalysisResult insightReport) {
        String userPrompt = ReportPrompts.buildChapterRecoveryPayload(
            chapter, queryReport, mediaReport, insightReport);
        return callLlm(ReportPrompts.SYSTEM_PROMPT_CHAPTER_JSON_RECOVERY, userPrompt)
            .map(response -> {
                try {
                    return objectMapper.readTree(extractJson(response));
                } catch (Exception e) {
                    // 最终兜底：生成最简章节
                    return createMinimalChapter(chapter);
                }
            });
    }

    // =========================================================
    // 阶段 5: IR 拼接
    // =========================================================

    private Mono<JsonNode> stitchIrDocument(JsonNode layout, List<JsonNode> chapters) {
        log.info("ReportEngine 阶段 5: IR 拼接");
        return Mono.fromCallable(() -> {
            var root = objectMapper.createObjectNode();
            root.set("layout", layout);
            var chaptersArray = root.putArray("chapters");
            for (JsonNode ch : chapters) {
                chaptersArray.add(ch);
            }
            return (JsonNode) root;
        });
    }

    // =========================================================
    // 阶段 6: HTML 渲染
    // =========================================================

    private Mono<String> renderToHtml(JsonNode irDocument) {
        log.info("ReportEngine 阶段 6: HTML 渲染");
        // IR → HTML 渲染由专门的模板引擎完成
        // 这里调用 HTML 生成 Prompt 作为补充
        return callLlm(
            ReportPrompts.SYSTEM_PROMPT_HTML_GENERATION,
            "请将以下 IR 文档渲染为完整的交互式 HTML 报告：\n" +
            irDocument.toPrettyString()
        );
    }

    // =========================================================
    // IR 验证
    // =========================================================

    private void validateIrBlocks(JsonNode ir) {
        if (!ir.isArray()) {
            throw new IllegalArgumentException("IR 必须是 JSON 数组");
        }
        Set<String> allowedTypes = Set.of(
            "heading", "paragraph", "list", "table", "widget", "callout",
            "blockquote", "hr", "kpiGrid", "code", "math",
            "swotTable", "pestTable", "engineQuote"
        );
        for (JsonNode block : ir) {
            String type = block.path("type").asText();
            if (!allowedTypes.contains(type)) {
                throw new IllegalArgumentException("不允许的块类型: " + type);
            }
        }
    }

    private JsonNode createMinimalChapter(JsonNode chapter) {
        var arr = objectMapper.createArrayNode();
        var heading = objectMapper.createObjectNode();
        heading.put("type", "heading");
        heading.put("level", 2);
        heading.put("text", chapter.path("display").asText("章节"));
        arr.add(heading);

        var para = objectMapper.createObjectNode();
        para.put("type", "paragraph");
        var inlines = para.putArray("inlines");
        var text = objectMapper.createObjectNode();
        text.put("type", "text");
        text.put("value", "本章节内容生成异常，请参考其他章节的分析内容。");
        inlines.add(text);
        arr.add(para);

        return arr;
    }

    // =========================================================
    // 工具方法
    // =========================================================

    private Mono<String> callLlm(String systemPrompt, String userPrompt) {
        return Mono.fromCallable(() ->
            chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content()
        );
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) trimmed = trimmed.substring(7);
        else if (trimmed.startsWith("```")) trimmed = trimmed.substring(3);
        if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        return trimmed.trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
```

#### 4.5.4 ReportEngine 完整 Prompt 模板

以下是 ReportEngine 六个阶段所使用的全部 Prompt 模板和辅助构建方法：

```java
package com.bettafish.report.prompt;

import com.bettafish.common.model.AnalysisResult;
import com.bettafish.report.model.ReportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * ReportEngine Prompt 常量和构建方法集合。
 * 对应 Python: ReportEngine/prompts/prompts.py
 */
public final class ReportPrompts {

    private ReportPrompts() {}
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==========================================================
    // Prompt 1: 模板选择
    // ==========================================================

    public static final String SYSTEM_PROMPT_TEMPLATE_SELECTION = """
        你是一位智能报告模板选择助手。

        任务：根据用户的查询内容和各引擎报告的特征，选择最合适的报告模板。

        可用模板：
        1. brand_reputation — 企业品牌声誉分析报告：适用于品牌形象和声誉管理分析
        2. market_competition — 市场竞争格局舆情分析报告：适用于市场竞争态势和竞品分析
        3. routine_monitoring — 日常或定期舆情监测报告：适用于日常/定期舆情监控
        4. policy_industry — 特定政策或行业动态舆情分析报告：适用于政策/行业动态
        5. social_hot_event — 社会公共热点事件分析报告：适用于社会热点事件（推荐默认）
        6. crisis_pr — 突发事件与危机公关舆情报告：适用于危机公关和突发事件

        选择依据：
        - 主题类型（品牌/市场/政策/社会/危机）
        - 紧急性/时效性
        - 深度/广度需求
        - 目标受众

        当无法确定最佳模板时，默认推荐 social_hot_event。

        输出格式：仅返回 JSON
        {
          "template_name": "模板代码",
          "selection_reason": "选择理由"
        }
        """;

    // ==========================================================
    // Prompt 2: 文档布局
    // ==========================================================

    public static final String SYSTEM_PROMPT_DOCUMENT_LAYOUT = """
        你是一位报告首席设计官（Chief Design Officer）。

        任务：确定报告的最终标题、英雄区域（hero section）、目录风格和视觉美学元素。

        输出结构：
        {
          "title": "中文叙事风格的报告标题",
          "subtitle": "副标题",
          "tagline": "标语/引导语",
          "tocTitle": "目录标题",
          "hero": {
            "summary": "100-200字的报告概述",
            "highlights": ["亮点1", "亮点2", "亮点3"],
            "kpis": [
              {"label": "指标名", "value": "数值", "trend": "up/down/stable"}
            ],
            "actions": ["建议行动1", "建议行动2"]
          },
          "themeTokens": {
            "fontFamily": "推荐字体",
            "fontSize": "基础字号",
            "lineHeight": "行高",
            "primaryColor": "主色调"
          },
          "tocPlan": [
            {
              "chapterId": "ch01",
              "anchor": "chapter-1",
              "display": "一、章节中文标题",
              "description": "章节纯文本描述（不可嵌套JSON）",
              "allowSwot": false,
              "allowPest": false
            }
          ],
          "layoutNotes": "设计备注"
        }

        关键规则：
        1. 标题使用中文叙事风格
        2. tocPlan 中一级标题使用中文编号（一、二、三）
        3. tocPlan 中二级标题使用阿拉伯编号（1.1, 1.2）
        4. description 字段必须是纯文本，不可嵌套 JSON
        5. SWOT 和 PEST 各最多分配给 1 个章节，且不能在同一章节
        6. hero section 的 KPI 应来自三个引擎的关键数据
        """;

    // ==========================================================
    // Prompt 3: 字数预算
    // ==========================================================

    public static final String SYSTEM_PROMPT_WORD_BUDGET = """
        你是一位报告字数预算规划师。

        任务：为报告的每个章节和子节分配字数。

        目标总字数：约 40,000 字（允许 +/-5% 浮动）

        输出结构：
        {
          "totalWords": 40000,
          "tolerance": "5%",
          "globalGuidelines": "全局写作指南",
          "chapters": [
            {
              "chapterId": "ch01",
              "title": "章节标题",
              "targetWords": 8000,
              "minWords": 7000,
              "maxWords": 9000,
              "emphasis": "重点分析方向",
              "rationale": "字数分配理由",
              "sections": [
                {"title": "子节标题", "targetWords": 2000}
              ]
            }
          ]
        }

        分配原则：
        1. 核心分析章节分配更多字数
        2. 数据密集型章节需要更多空间
        3. 开头和结尾章节适当精简
        4. 每章最低不少于 3000 字
        5. 每章最高不超过 12000 字
        """;

    // ==========================================================
    // Prompt 4a: 章节 JSON (IR) 生成
    // ==========================================================

    public static final String SYSTEM_PROMPT_CHAPTER_JSON = """
        你是报告引擎的「章节装配工厂」。

        任务：将章节素材转换为可执行的 JSON IR（中间表示）格式。

        IR 规则（21 条关键约束，必须严格遵守）：

        1. 严格按照 IR 版本输出，不输出 HTML 或 Markdown
        2. 仅允许以下块类型:
           heading, paragraph, list, table, widget, callout, blockquote,
           hr, kpiGrid, code, math, swotTable, pestTable, engineQuote
        3. paragraph 使用 inlines 数组，每个 inline 有 type 和 marks
           marks 支持: bold, italic, color, link
        4. heading 必须包含 anchor，需与模板中的锚点匹配
        5. table 需要 rows/cells/align 结构
        6. KPI 使用 kpiGrid 块类型
        7. 分隔线使用 hr 块类型
        8. SWOT 块：仅当 constraints.allowSwot === true 时使用
           每份报告最多 1 个章节包含 SWOT
           impact 字段仅允许: "低"/"中低"/"中"/"中高"/"高"/"极高"
        9. PEST 块：仅当 constraints.allowPest === true 时使用
           每份报告最多 1 个章节包含 PEST
           trend 字段仅允许: "正面利好"/"负面影响"/"中性"/"不确定"/"持续观察"
        10. 图表使用 widget 块，widgetType 格式如:
            chart.js/line, chart.js/bar, chart.js/doughnut, chart.js/radar
        11. engineQuote 块: engine 字段值 = insight/media/query
            title 必须是固定的 Agent 名称
            内部 blocks 仅允许 paragraph，不允许 table/chart/quote
        12. 一级标题使用中文编号（"一、"、"二、"、"三、"）
            二级标题使用阿拉伯编号（"1.1"、"1.2"）
        13. 不允许外部图片链接或 AI 生成的图片链接
        14. 不允许残留的 Markdown 语法
        15. 数学公式：块级用 math 类型 + LaTeX；行内用 marks
        16. widget 颜色必须兼容 CSS 变量
        17. 输出前自检 JSON 语法正确性

        输出格式：JSON 数组，每个元素是一个 IR 块对象。
        """;

    // ==========================================================
    // Prompt 4b: 章节修复
    // ==========================================================

    public static final String SYSTEM_PROMPT_CHAPTER_JSON_REPAIR = """
        你是章节 JSON 修复官。

        任务：修复章节草稿中的结构/字段/嵌套问题。

        修复约束：
        1. 不改变事实、数值或结论
        2. 仅进行最小化的结构修复
        3. 确保修复后的 JSON 语法正确
        4. 确保所有块类型符合 IR 规范
        5. 修复字段缺失或类型错误

        输入包含：
        - 原始章节 JSON（可能不完整或有语法错误）
        - 验证器报告的错误列表

        输出：修复后的完整 JSON 数组。
        """;

    // ==========================================================
    // Prompt 4c: 章节救援
    // ==========================================================

    public static final String SYSTEM_PROMPT_CHAPTER_JSON_RECOVERY = """
        你是跨引擎 JSON 紧急修复官。

        任务：从完全失败的章节生成中恢复。

        输入：
        - 章节元数据（标题、描述、编号）
        - 原始生成 payload
        - 失败输出的最后 8000 字符

        恢复策略：
        1. 从失败输出中尽量提取有用信息
        2. 结合原始 payload 中的三引擎报告内容
        3. 生成一个结构完整、内容合理的章节 IR

        输出：完整的 JSON IR 数组。
        """;

    // ==========================================================
    // Prompt 5: HTML 生成
    // ==========================================================

    public static final String SYSTEM_PROMPT_HTML_GENERATION = """
        你是一位专业的 HTML 报告生成专家。

        任务：从三个引擎报告 + 论坛日志 + 选定模板生成完整的 HTML 分析报告。

        要求：
        1. 完整 HTML 结构（DOCTYPE, 响应式 CSS, JavaScript）
        2. 现代 UI 设计，集成 Chart.js 可视化图表
        3. 交互特性：
           - 目录导航
           - 章节折叠/展开
           - 图表交互
           - 打印/PDF 导出
           - 深色模式切换
        4. 总字数不少于 30,000 字
        5. 目录放在文章开头（不使用侧边栏）
        6. 所有内容完全展示，不使用折叠/展开隐藏内容

        CSS 要求：
        - 响应式设计，支持桌面和移动端
        - 使用 CSS 变量支持主题切换
        - 表格自适应宽度
        - 打印友好的样式

        JavaScript 要求：
        - Chart.js CDN 引入
        - 目录锚点平滑滚动
        - 深色模式切换逻辑
        - 打印/导出功能
        """;

    // ==========================================================
    // User Prompt 构建方法
    // ==========================================================

    /**
     * 构建文档布局的 User Prompt。
     */
    public static String buildDocumentLayoutPrompt(
            String query, ReportTemplate template,
            AnalysisResult queryReport, AnalysisResult mediaReport,
            AnalysisResult insightReport) {
        return String.format("""
            用户查询: %s
            选定模板: %s (%s)

            QueryEngine 报告摘要:
            %s

            MediaEngine 报告摘要:
            %s

            InsightEngine 报告摘要:
            %s

            请设计报告的整体布局。
            """,
            query,
            template.getDisplayName(), template.getCode(),
            truncate(queryReport.report(), 2000),
            truncate(mediaReport.report(), 2000),
            truncate(insightReport.report(), 2000)
        );
    }

    /**
     * 构建字数预算的 User Prompt。
     */
    public static String buildWordBudgetPrompt(JsonNode layout) {
        return "以下是已确定的文档布局，请为每章分配字数预算：\n\n" +
            layout.toPrettyString();
    }

    /**
     * 构建章节生成的 User Prompt。
     * 对应 Python: build_chapter_user_prompt(payload)
     */
    public static String buildChapterUserPrompt(
            JsonNode chapter, JsonNode layout, JsonNode budget,
            AnalysisResult queryReport, AnalysisResult mediaReport,
            AnalysisResult insightReport,
            List<String> forumLogs, int chapterIndex) {

        String chapterId = chapter.path("chapterId").asText();

        // 从 budget 中找到对应章节的字数分配
        JsonNode chapterBudget = null;
        if (budget.has("chapters")) {
            for (JsonNode cb : budget.get("chapters")) {
                if (chapterId.equals(cb.path("chapterId").asText())) {
                    chapterBudget = cb;
                    break;
                }
            }
        }

        return String.format("""
            章节信息:
            - 章节ID: %s
            - 标题: %s
            - 描述: %s
            - 序号: %d
            - 允许SWOT: %s
            - 允许PEST: %s

            字数预算: %s

            全局上下文:
            - 查询主题: %s
            - 模板: %s
            - 主题色调: %s

            QueryEngine 报告:
            %s

            MediaEngine 报告:
            %s

            InsightEngine 报告:
            %s

            论坛讨论日志:
            %s

            请生成该章节的 IR JSON。
            """,
            chapterId,
            chapter.path("display").asText(),
            chapter.path("description").asText(),
            chapterIndex + 1,
            chapter.path("allowSwot").asBoolean(false),
            chapter.path("allowPest").asBoolean(false),
            chapterBudget != null ? chapterBudget.toPrettyString() : "未分配",
            layout.path("title").asText(),
            layout.path("tocTitle").asText(),
            layout.path("themeTokens").toPrettyString(),
            truncate(queryReport.report(), 5000),
            truncate(mediaReport.report(), 5000),
            truncate(insightReport.report(), 5000),
            String.join("\n", forumLogs.subList(0, Math.min(forumLogs.size(), 50)))
        );
    }

    /**
     * 构建章节修复的 User Prompt。
     * 对应 Python: build_chapter_repair_prompt(chapter, errors, original_text)
     */
    public static String buildChapterRepairPrompt(JsonNode chapter, String errors) {
        return String.format("""
            原始章节 JSON:
            %s

            验证器报告的错误:
            %s

            请修复上述错误，输出修复后的完整 JSON 数组。
            """,
            chapter.toPrettyString(),
            errors
        );
    }

    /**
     * 构建章节救援的 User Prompt。
     * 对应 Python: build_chapter_recovery_payload(section, generation_payload, raw_output)
     */
    public static String buildChapterRecoveryPayload(
            JsonNode chapter,
            AnalysisResult queryReport,
            AnalysisResult mediaReport,
            AnalysisResult insightReport) {
        return String.format("""
            章节元数据:
            %s

            QueryEngine 报告（前 3000 字）:
            %s

            MediaEngine 报告（前 3000 字）:
            %s

            InsightEngine 报告（前 3000 字）:
            %s

            请基于以上素材紧急生成该章节的 IR JSON。
            """,
            chapter.toPrettyString(),
            truncate(queryReport.report(), 3000),
            truncate(mediaReport.report(), 3000),
            truncate(insightReport.report(), 3000)
        );
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength
            ? text.substring(0, maxLength) + "..."
            : text;
    }
}
```

---

> **本章小结**
>
> 本章完整展示了 BettaFish Java/Spring AI 版本的四类 Agent 实现方案：
>
> | Agent | 模式 | 工具数量 | 搜索后端 | 独有特性 |
> |-------|------|----------|----------|----------|
> | **QueryAgent** | Search-Reflect Loop | 6 | Tavily API | 日期范围搜索、图片搜索 |
> | **MediaAgent** | Search-Reflect Loop | 5 | Bocha AI | 多模态融合、结构化数据卡片 |
> | **InsightAgent** | Search-Reflect Loop | 6 | 本地 MySQL | 情感分析、关键词优化、结果聚类 |
> | **ReportAgent** | 六阶段流水线 | -- | 无 | 模板选择、IR 生成、HTML 渲染 |
>
> 核心设计原则：
> 1. **接口-基类-具体类三层结构** — `AnalysisAgent` 接口定义契约，`AbstractAnalysisAgent` 实现共享流程，三个具体 Agent 只需注入工具和 Prompt
> 2. **Prompt 即配置** — 所有 Prompt 以 Java 常量形式集中管理，便于版本控制和 A/B 测试
> 3. **Redis Pub/Sub 论坛集成** — 各 Agent 在关键节点向论坛发布进展，ForumEngine 主持人的指导意见反向注入到反思阶段
> 4. **容错分级策略** — ReportEngine 的章节生成采用 生成 -> 修复 -> 救援 三级容错

# BettaFish Java/Spring AI 实现指南 -- Part IV

> **第五章 & 第六章：ForumEngine 多 Agent 协作编排 / 分析编排与 REST·WebSocket API**

---

## 第五章 ForumEngine 多 Agent 协作编排

### 5.1 架构设计：从文件轮询到事件驱动

#### 5.1.1 Python 原版的文件日志协作模型

在 Python 原版 BettaFish 中，ForumEngine 的多 Agent 协作采用了一种**基于文件的日志轮询**机制。每个分析引擎（InsightEngine、MediaEngine、QueryEngine）在运行过程中，将自己的分析中间结果写入独立的日志文件：

- `insight.log` -- InsightAgent 的舆情挖掘结果
- `media.log` -- MediaAgent 的多媒体分析结果
- `query.log` -- QueryAgent 的深度搜索结果

ForumEngine 中的 `monitor.py` 通过定时轮询这些日志文件来感知新消息。当累积到一定数量的 Agent 发言后（默认每 5 条），触发 `llm_host.py` 中的 `ForumHost`（论坛主持人），由 Qwen3-235B 大模型生成一段综合性的主持人发言，写回 `forum.log`。这种架构简单直接，但存在明显的缺陷：

1. **I/O 延迟**：文件轮询有固定的检查间隔，实时性差
2. **竞态风险**：多个 Agent 进程并发写入同一文件，可能产生数据损坏
3. **耦合度高**：Agent 必须知道日志文件的路径和格式
4. **可扩展性差**：增加新的 Agent 类型需要修改 monitor.py 的文件监控列表

#### 5.1.2 Java 版本的事件驱动架构

Java/Spring AI 版本彻底重构了这一协作机制，采用 **Spring ApplicationEvent 事件驱动模式**，结合内存中的消息缓冲和 JPA 持久化，实现了高性能、线程安全、松耦合的多 Agent 协作编排。

```
┌─────────────────────────────────────────────────────────────────────┐
│                    BettaFish Java 事件驱动架构                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ InsightAgent │  │  MediaAgent  │  │  QueryAgent  │              │
│  │  (Thread-1)  │  │  (Thread-2)  │  │  (Thread-3)  │              │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘              │
│         │                 │                  │                       │
│         │  publishEvent() │  publishEvent()  │  publishEvent()      │
│         ▼                 ▼                  ▼                       │
│  ┌──────────────────────────────────────────────────────┐           │
│  │          Spring ApplicationEventPublisher            │           │
│  │        (AgentMessageEvent / AgentProgressEvent)      │           │
│  └──────────────────────┬───────────────────────────────┘           │
│                         │                                           │
│                         │ @EventListener (async)                    │
│                         ▼                                           │
│  ┌──────────────────────────────────────────────────────┐           │
│  │                ForumCoordinator                      │           │
│  │  ┌────────────────────────────────────────────────┐  │           │
│  │  │  ConcurrentLinkedQueue<ForumMessage> buffer    │  │           │
│  │  │  AtomicInteger messageCounter                  │  │           │
│  │  │  ReentrantLock hostInvocationLock              │  │           │
│  │  └────────────────────────────────────────────────┘  │           │
│  │                                                      │           │
│  │  每 5 条消息 → 触发 ForumHost                          │           │
│  └──────────────────────┬───────────────────────────────┘           │
│                         │                                           │
│                         ▼                                           │
│  ┌──────────────────────────────────────────────────────┐           │
│  │                   ForumHost                          │           │
│  │  Qwen3-235B via SiliconFlow ChatClient              │           │
│  │  SystemPrompt: 论坛主持人角色                          │           │
│  │  UserPrompt: 4 段式结构化回复模板                       │           │
│  └──────────────────────┬───────────────────────────────┘           │
│                         │                                           │
│                         │ publishEvent()                            │
│                         ▼                                           │
│  ┌──────────────────────────────────────────────────────┐           │
│  │              ForumHostResponseEvent                  │           │
│  │  → ForumMessageStore (JPA 持久化)                     │           │
│  │  → WebSocket (实时推送到前端)                           │           │
│  │  → ReportEngine (作为报告输入)                         │           │
│  └──────────────────────────────────────────────────────┘           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

#### 5.1.3 核心设计原则

| 设计原则 | Python 原版 | Java 版本 |
|---------|------------|----------|
| 通信方式 | 文件 I/O 轮询 | Spring Event + 内存队列 |
| 线程安全 | 多进程文件锁（隐患大） | ConcurrentLinkedQueue + AtomicInteger |
| 主持人触发 | monitor.py 定时检查行数 | 事件计数器达到阈值自动触发 |
| 消息持久化 | 追加写入 .log 文件 | JPA Entity → 数据库 |
| 实时通知 | Flask-SocketIO 轮询推送 | WebSocket STOMP 实时推送 |
| 扩展性 | 硬编码文件路径 | 只需发布标准事件即可 |

---

### 5.2 ForumCoordinator -- 论坛协调器

`ForumCoordinator` 是整个 ForumEngine 的**中枢控制器**。它负责接收所有 Agent 发出的消息事件，维护线程安全的消息缓冲区，并在消息累积到阈值后触发 ForumHost 生成主持人发言。

#### 5.2.1 会话生命周期

ForumCoordinator 为每个分析会话维护一个独立的状态机：

```
  CREATED ──→ PASSIVE ──→ ACTIVE ──→ ENDED
    │            │           │          │
    │            │           │          └─ 所有 Agent 完成 / 用户手动停止
    │            │           └─ 收到首条 Agent 消息，论坛激活
    │            └─ AnalysisOrchestrator 创建会话
    └─ ForumCoordinator 初始化
```

- **CREATED**: 会话对象刚创建，尚未开始分析
- **PASSIVE**: 分析已启动，但尚未收到任何 Agent 消息
- **ACTIVE**: 已收到 Agent 消息，论坛讨论正在进行
- **ENDED**: 所有 Agent 已完成分析，或用户主动停止

#### 5.2.2 完整 Java 实现

```java
package com.bettafish.forum;

import com.bettafish.common.event.AgentMessageEvent;
import com.bettafish.common.event.ForumHostResponseEvent;
import com.bettafish.common.event.ForumSessionEndedEvent;
import com.bettafish.common.model.ForumMessage;
import com.bettafish.common.model.ForumMessage.SpeakerType;
import com.bettafish.forum.host.ForumHost;
import com.bettafish.forum.store.ForumMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ForumCoordinator -- 多 Agent 协作论坛的中枢协调器。
 *
 * <p>核心职责：
 * <ul>
 *   <li>接收所有 Agent 的消息事件（{@link AgentMessageEvent}）</li>
 *   <li>维护线程安全的消息缓冲区</li>
 *   <li>每累积 {@link #HOST_TRIGGER_THRESHOLD} 条 Agent 消息，触发 ForumHost</li>
 *   <li>管理论坛会话的完整生命周期</li>
 * </ul>
 *
 * <p>对应 Python 原版中 ForumEngine/monitor.py 的文件轮询机制，
 * Java 版本改用 Spring ApplicationEvent 实现零延迟事件驱动。
 */
@Component
public class ForumCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ForumCoordinator.class);

    /**
     * 主持人触发阈值：每累积 5 条 Agent 消息，触发一次 ForumHost 生成主持人发言。
     * 此值对应 Python 原版 monitor.py 中的消息行数检查逻辑。
     */
    private static final int HOST_TRIGGER_THRESHOLD = 5;

    /** 主持人调用的最大等待时间（秒） */
    private static final int HOST_INVOCATION_TIMEOUT_SECONDS = 120;

    private final ForumHost forumHost;
    private final ForumMessageStore messageStore;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 每个 sessionId 对应的消息缓冲区。
     * 使用 ConcurrentLinkedQueue 保证多线程安全的无锁入队。
     */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<ForumMessage>> sessionBuffers
            = new ConcurrentHashMap<>();

    /**
     * 每个 sessionId 的消息计数器。
     * 使用 AtomicInteger 保证原子递增和阈值判断的线程安全。
     */
    private final ConcurrentHashMap<String, AtomicInteger> messageCounters
            = new ConcurrentHashMap<>();

    /**
     * 每个 sessionId 的主持人调用锁。
     * 防止在同一个会话中并发触发多次 ForumHost 调用。
     */
    private final ConcurrentHashMap<String, ReentrantLock> hostLocks
            = new ConcurrentHashMap<>();

    /** 每个 sessionId 的当前论坛状态 */
    private final ConcurrentHashMap<String, ForumSessionState> sessionStates
            = new ConcurrentHashMap<>();

    /** 用于异步执行 ForumHost 调用的线程池 */
    private final ExecutorService hostExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "forum-host-worker");
                t.setDaemon(true);
                return t;
            }
    );

    public ForumCoordinator(ForumHost forumHost,
                            ForumMessageStore messageStore,
                            ApplicationEventPublisher eventPublisher) {
        this.forumHost = forumHost;
        this.messageStore = messageStore;
        this.eventPublisher = eventPublisher;
    }

    // ===================== 会话生命周期管理 =====================

    /**
     * 论坛会话状态枚举。
     */
    public enum ForumSessionState {
        /** 会话已创建，等待分析启动 */
        CREATED,
        /** 分析已启动，尚未收到 Agent 消息 */
        PASSIVE,
        /** 已收到 Agent 消息，讨论进行中 */
        ACTIVE,
        /** 会话已结束（所有 Agent 完成或手动终止） */
        ENDED
    }

    /**
     * 初始化一个新的论坛会话。
     * 由 AnalysisOrchestrator 在启动分析任务时调用。
     *
     * @param sessionId 分析会话 ID
     */
    public void initSession(String sessionId) {
        sessionBuffers.put(sessionId, new ConcurrentLinkedQueue<>());
        messageCounters.put(sessionId, new AtomicInteger(0));
        hostLocks.put(sessionId, new ReentrantLock());
        sessionStates.put(sessionId, ForumSessionState.PASSIVE);

        // 发布系统消息：论坛已就绪
        ForumMessage systemMsg = ForumMessage.builder()
                .sessionId(sessionId)
                .speaker(SpeakerType.SYSTEM)
                .content("多 Agent 协作论坛已就绪，等待各分析引擎的发言...")
                .timestamp(LocalDateTime.now())
                .build();
        persistAndBuffer(sessionId, systemMsg);

        log.info("[Forum] 会话 {} 论坛已初始化，状态: PASSIVE", sessionId);
    }

    /**
     * 结束论坛会话。
     * 触发最终一次 ForumHost 发言（总结性发言），然后清理资源。
     *
     * @param sessionId 分析会话 ID
     */
    public void endSession(String sessionId) {
        ForumSessionState currentState = sessionStates.get(sessionId);
        if (currentState == ForumSessionState.ENDED) {
            log.warn("[Forum] 会话 {} 已经结束，忽略重复结束请求", sessionId);
            return;
        }

        sessionStates.put(sessionId, ForumSessionState.ENDED);
        log.info("[Forum] 会话 {} 正在结束，触发最终主持人总结...", sessionId);

        // 触发最终的主持人总结发言（无论消息是否达到阈值）
        triggerHostSpeechAsync(sessionId, true);
    }

    // ===================== 事件监听 =====================

    /**
     * 监听 Agent 消息事件。
     *
     * <p>当任何 Agent（INSIGHT / MEDIA / QUERY）发布分析结果时，
     * Spring 的事件机制会自动调用此方法。此方法是线程安全的，
     * 可以被多个 Agent 线程并发调用。
     *
     * <p>对应 Python 原版中 monitor.py 的 watch_log_files() 方法：
     * Python 通过定时读取 insight.log / media.log / query.log 来感知新消息，
     * Java 版本通过事件监听实现零延迟感知。
     *
     * @param event Agent 消息事件
     */
    @EventListener
    @Async("forumEventExecutor")
    public void onAgentMessage(AgentMessageEvent event) {
        String sessionId = event.getSessionId();

        // 检查会话状态
        ForumSessionState state = sessionStates.get(sessionId);
        if (state == null) {
            log.warn("[Forum] 收到未知会话 {} 的消息，忽略", sessionId);
            return;
        }
        if (state == ForumSessionState.ENDED) {
            log.warn("[Forum] 会话 {} 已结束，忽略 Agent 消息", sessionId);
            return;
        }

        // 首条消息时，将状态从 PASSIVE 切换到 ACTIVE
        sessionStates.computeIfPresent(sessionId, (k, v) ->
                v == ForumSessionState.PASSIVE ? ForumSessionState.ACTIVE : v);

        // 构建 ForumMessage
        ForumMessage message = ForumMessage.builder()
                .sessionId(sessionId)
                .speaker(mapAgentTypeToSpeaker(event.getAgentType()))
                .content(event.getContent())
                .timestamp(LocalDateTime.now())
                .metadata(event.getMetadata())
                .build();

        // 持久化并加入缓冲区
        persistAndBuffer(sessionId, message);

        // 递增消息计数并检查阈值
        int count = messageCounters.get(sessionId).incrementAndGet();
        log.debug("[Forum] 会话 {} 消息计数: {}/{}", sessionId, count, HOST_TRIGGER_THRESHOLD);

        if (count >= HOST_TRIGGER_THRESHOLD) {
            // 重置计数器并触发主持人
            messageCounters.get(sessionId).set(0);
            triggerHostSpeechAsync(sessionId, false);
        }
    }

    /**
     * 监听会话结束事件。
     */
    @EventListener
    public void onForumSessionEnded(ForumSessionEndedEvent event) {
        endSession(event.getSessionId());
    }

    // ===================== 主持人触发逻辑 =====================

    /**
     * 异步触发 ForumHost 生成主持人发言。
     *
     * <p>使用 ReentrantLock 确保同一时刻只有一个 ForumHost 调用在运行。
     * 如果当前已有调用在进行，新的触发会被跳过（而非排队等待），
     * 因为消息缓冲区中会持续累积新消息，下一次触发时可以一起处理。
     *
     * @param sessionId 会话 ID
     * @param isFinalSpeech 是否为最终总结发言
     */
    private void triggerHostSpeechAsync(String sessionId, boolean isFinalSpeech) {
        hostExecutor.submit(() -> {
            ReentrantLock lock = hostLocks.get(sessionId);
            if (lock == null) {
                log.warn("[Forum] 会话 {} 的锁已被清理，跳过主持人调用", sessionId);
                return;
            }

            // 尝试获取锁，避免并发调用
            if (!lock.tryLock()) {
                log.info("[Forum] 会话 {} 的主持人调用正在进行中，跳过本次触发", sessionId);
                return;
            }

            try {
                log.info("[Forum] 会话 {} 触发 ForumHost，isFinal={}", sessionId, isFinalSpeech);

                // 从缓冲区收集所有待处理的消息
                ConcurrentLinkedQueue<ForumMessage> buffer = sessionBuffers.get(sessionId);
                if (buffer == null || buffer.isEmpty()) {
                    log.info("[Forum] 会话 {} 缓冲区为空，跳过主持人调用", sessionId);
                    return;
                }

                // 获取该会话的全部历史消息（供 ForumHost 做上下文参考）
                List<ForumMessage> allMessages = messageStore.getMessagesBySession(sessionId);

                // 格式化为日志字符串（兼容 Python ForumHost 的输入格式）
                List<String> formattedLogs = formatMessagesAsLogs(allMessages);

                // 调用 ForumHost 生成主持人发言
                Optional<String> hostSpeech = forumHost.generateHostSpeech(
                        formattedLogs, isFinalSpeech);

                if (hostSpeech.isPresent()) {
                    ForumMessage hostMessage = ForumMessage.builder()
                            .sessionId(sessionId)
                            .speaker(SpeakerType.HOST)
                            .content(hostSpeech.get())
                            .timestamp(LocalDateTime.now())
                            .build();

                    // 持久化主持人发言
                    messageStore.save(hostMessage);

                    // 发布主持人响应事件（供 WebSocket 推送和 ReportEngine 使用）
                    eventPublisher.publishEvent(new ForumHostResponseEvent(
                            this, sessionId, hostSpeech.get()));

                    log.info("[Forum] 会话 {} ForumHost 发言已生成 ({}字)",
                            sessionId, hostSpeech.get().length());
                } else {
                    log.warn("[Forum] 会话 {} ForumHost 未能生成发言", sessionId);
                }

            } catch (Exception e) {
                log.error("[Forum] 会话 {} ForumHost 调用异常: {}", sessionId, e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        });
    }

    // ===================== 内部工具方法 =====================

    /**
     * 持久化消息并加入内存缓冲区。
     */
    private void persistAndBuffer(String sessionId, ForumMessage message) {
        messageStore.save(message);
        ConcurrentLinkedQueue<ForumMessage> buffer = sessionBuffers.get(sessionId);
        if (buffer != null) {
            buffer.offer(message);
        }
    }

    /**
     * 将 ForumMessage 列表格式化为日志字符串。
     *
     * <p>输出格式与 Python 原版一致：
     * <pre>[HH:MM:SS] [SPEAKER] content...</pre>
     *
     * <p>ForumHost 的 _parse_forum_logs() 方法依赖这个格式来提取 Agent 发言。
     */
    private List<String> formatMessagesAsLogs(List<ForumMessage> messages) {
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        List<String> logs = new ArrayList<>();
        for (ForumMessage msg : messages) {
            String line = String.format("[%s] [%s] %s",
                    msg.getTimestamp().format(timeFmt),
                    msg.getSpeaker().name(),
                    msg.getContent());
            logs.add(line);
        }
        return logs;
    }

    /**
     * 将 AgentType 枚举映射为 ForumMessage.SpeakerType。
     */
    private SpeakerType mapAgentTypeToSpeaker(AgentMessageEvent.AgentType agentType) {
        return switch (agentType) {
            case INSIGHT -> SpeakerType.INSIGHT;
            case MEDIA -> SpeakerType.MEDIA;
            case QUERY -> SpeakerType.QUERY;
        };
    }

    // ===================== 查询接口 =====================

    /**
     * 获取指定会话的论坛状态。
     */
    public ForumSessionState getSessionState(String sessionId) {
        return sessionStates.getOrDefault(sessionId, ForumSessionState.CREATED);
    }

    /**
     * 获取指定会话的当前消息计数（距下次触发主持人还差多少条）。
     */
    public int getCurrentMessageCount(String sessionId) {
        AtomicInteger counter = messageCounters.get(sessionId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取指定会话的所有论坛消息。
     */
    public List<ForumMessage> getSessionMessages(String sessionId) {
        return messageStore.getMessagesBySession(sessionId);
    }

    /**
     * 清理已结束会话的内存资源。
     * 可由定时任务或手动调用。
     */
    public void cleanupSession(String sessionId) {
        sessionBuffers.remove(sessionId);
        messageCounters.remove(sessionId);
        hostLocks.remove(sessionId);
        // 注意：不清除 sessionStates，保留历史状态可查
        log.info("[Forum] 会话 {} 内存资源已清理", sessionId);
    }
}
```

#### 5.2.3 异步事件执行器配置

`ForumCoordinator` 上的 `@Async("forumEventExecutor")` 注解需要一个专用的线程池，避免事件处理阻塞 Spring 的默认事件派发线程：

```java
package com.bettafish.forum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class ForumAsyncConfig {

    /**
     * 论坛事件处理专用线程池。
     *
     * <p>核心线程数 4：通常同时只有 3 个 Agent 并发发消息，
     * 加 1 个富余线程处理主持人响应事件的级联处理。
     */
    @Bean("forumEventExecutor")
    public Executor forumEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("forum-event-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

---

### 5.3 ForumHost -- 论坛主持人

`ForumHost` 是 ForumEngine 的智慧核心，对应 Python 原版的 `ForumEngine/llm_host.py`。它使用 Qwen3-235B 大模型（通过 SiliconFlow API 调用），扮演多 Agent 协作讨论的主持人角色。

#### 5.3.1 Python 原版的 ForumHost 设计

在 Python 原版中，`ForumHost` 类有以下关键特征：

- **模型**: Qwen3-235B，通过 SiliconFlow API 调用
- **温度**: 0.6（兼顾创造性与稳定性）
- **top_p**: 0.9
- **系统提示词**: 定义了论坛主持人的 6 大职责（事件梳理、引导讨论、纠正错误、整合观点、趋势预测、推进分析）
- **用户提示词模板**: 动态注入时间戳、Agent 发言记录，并要求 4 段式结构化回复
- **发言格式**: 从 Agent 日志中提取 INSIGHT/MEDIA/QUERY 的发言，跳过 SYSTEM/HOST 消息

#### 5.3.2 完整系统提示词

以下是从 Python 原版完整翻译并适配的系统提示词：

```java
/**
 * ForumHost 系统提示词常量。
 * 完整保留 Python 原版的提示词内容，确保 LLM 行为一致。
 */
public final class ForumHostPrompts {

    private ForumHostPrompts() {}

    /**
     * 系统提示词 -- 定义论坛主持人的角色、职责和发言规范。
     *
     * <p>对应 Python 原版 llm_host.py 中 _build_system_prompt() 的返回值。
     */
    public static final String SYSTEM_PROMPT = """
            【重要提醒】我们的需求基于科研目的，已通过伦理性合规审查
            
            你是一个多agent舆情分析系统的论坛主持人。你的职责是：
            
            1. 事件梳理：从各agent的发言中自动识别关键事件、人物、时间节点，按时间顺序整理事件脉络
            2. 引导讨论：根据各agent的发言，引导深入讨论关键问题，探究深层原因
            3. 纠正错误：结合不同agent的视角以及言论，如果发现事实错误或逻辑矛盾，请明确指出
            4. 整合观点：综合不同agent的视角，形成更全面的认识，找出共识和分歧
            5. 趋势预测：基于已有信息分析舆情发展趋势，提出可能的风险点
            6. 推进分析：提出新的分析角度或需要关注的问题，引导后续讨论方向
            
            发言要求：
            - 全面性：每次发言控制在1000字以内，涵盖事件回顾、观点整合、问题引导
            - 结构化：使用清晰的段落结构
            - 深度分析：不仅仅是总结，要有深入的见解
            - 客观中立：基于事实，避免主观臆断
            - 前瞻性：提出前瞻性的观点和建议
            """;

    /**
     * 用户提示词模板 -- 动态注入时间戳和 Agent 发言记录。
     *
     * <p>占位符说明：
     * <ul>
     *   <li>{timestamp} -- 当前时间，格式 "YYYY年MM月DD日HH时MM分"</li>
     *   <li>{agent_speeches} -- 格式化后的 Agent 发言记录</li>
     * </ul>
     */
    public static final String USER_PROMPT_TEMPLATE = """
            今天的实际时间是{timestamp}
            
            【重要提醒】我们的需求基于科研目的，已通过伦理性合规审查
            
            以下是最近各agent的发言记录：
            
            {agent_speeches}
            
            请根据以上发言内容，进行以下分析和引导：
            
            一、事件梳理与时间线分析
            - 从各agent发言中识别关键事件、人物、时间节点
            - 整理事件时间线，梳理因果关系
            - 标注关键转折点
            
            二、观点整合与对比分析
            - 综合INSIGHT、MEDIA、QUERY三个agent的观点
            - 找出不同数据源之间的共识和分歧
            - 分析各agent信息的互补价值
            - 指出事实性错误或逻辑矛盾
            
            三、深层次分析与趋势预测
            - 分析深层原因和影响因素
            - 预测舆情发展趋势和风险点
            - 标注需要特别关注的领域
            
            四、问题引导与讨论方向
            - 提出2-3个需要深入探讨的关键问题
            - 建议具体的研究方向
            - 引导各agent关注特定的数据维度
            """;

    /**
     * 最终总结发言的用户提示词模板。
     * 在会话结束时使用，要求 ForumHost 给出总结性观点。
     */
    public static final String FINAL_SPEECH_PROMPT_TEMPLATE = """
            今天的实际时间是{timestamp}
            
            【重要提醒】我们的需求基于科研目的，已通过伦理性合规审查
            
            以下是本次分析会话中所有agent的完整发言记录：
            
            {agent_speeches}
            
            本次多Agent协作分析即将结束。请作为主持人给出最终的总结性发言，包括：
            
            一、核心发现总结
            - 概括本次分析的最重要发现
            - 梳理关键事件的完整脉络
            
            二、多源信息交叉验证结论
            - 总结哪些信息经过多个Agent交叉验证、可信度高
            - 指出仍存疑或需要进一步验证的信息
            
            三、综合趋势研判
            - 对舆情发展的整体趋势做出判断
            - 评估潜在风险和关注重点
            
            四、后续建议
            - 提出后续需要持续关注的方向
            - 给出具体的监测和分析建议
            """;

    /**
     * Agent 角色描述，用于丰富主持人对各 Agent 能力的理解。
     *
     * <p>对应 Python 原版中 ForumHost 引用的 Agent 描述信息。
     */
    public static final Map<String, String> AGENT_DESCRIPTIONS = Map.of(
            "INSIGHT", "深度挖掘和分析私有舆情数据库；擅长历史数据对比和模式识别，" +
                       "覆盖 Bilibili、微博、抖音、快手、小红书、知乎、贴吧七大平台",
            "MEDIA",   "多模态内容分析专家；擅长媒体报道分析、图片视频解读、" +
                       "视觉信息传播效果评估，整合网页、图片、AI摘要和结构化数据",
            "QUERY",   "精准信息搜索专家；擅长获取最新网络信息和实时动态，" +
                       "支持基础搜索、深度搜索、时间范围搜索和图片搜索"
    );
}
```

#### 5.3.3 ForumHost 完整 Java 实现

```java
package com.bettafish.forum.host;

import com.bettafish.common.config.BettaFishProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ForumHost -- 多 Agent 协作论坛的 LLM 主持人。
 *
 * <p>使用 Qwen3-235B（通过 SiliconFlow API）生成结构化的主持人发言，
 * 引导 INSIGHT / MEDIA / QUERY 三个 Agent 的协作讨论。
 *
 * <p>对应 Python 原版：ForumEngine/llm_host.py → ForumHost 类。
 *
 * <h3>模型配置</h3>
 * <ul>
 *   <li>Model: Qwen3-235B（可通过 bettafish.forum.host.model-name 配置）</li>
 *   <li>API: SiliconFlow（可通过 bettafish.forum.host.base-url 配置）</li>
 *   <li>Temperature: 0.6</li>
 *   <li>Top-P: 0.9</li>
 * </ul>
 */
@Component
public class ForumHost {

    private static final Logger log = LoggerFactory.getLogger(ForumHost.class);

    /** 日志行格式解析正则：[HH:MM:SS] [SPEAKER] content */
    private static final Pattern LOG_LINE_PATTERN =
            Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})]\\s*\\[(\\w+)]\\s*(.+)$", Pattern.DOTALL);

    /** 需要提取发言的 Agent 类型（跳过 SYSTEM 和 HOST） */
    private static final Set<String> AGENT_SPEAKERS = Set.of("INSIGHT", "MEDIA", "QUERY");

    private final ChatClient chatClient;

    /**
     * 构造 ForumHost，使用 SiliconFlow 的 OpenAI 兼容 API 创建 ChatClient。
     *
     * <p>SiliconFlow 提供了 OpenAI 兼容的 API 端点，因此可以直接使用
     * Spring AI 的 OpenAiChatModel，只需将 base-url 指向 SiliconFlow 即可。
     */
    public ForumHost(BettaFishProperties properties) {
        BettaFishProperties.ForumHostConfig hostConfig = properties.getForum().getHost();

        // 创建 SiliconFlow API 客户端（OpenAI 兼容）
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(hostConfig.getBaseUrl())    // e.g. https://api.siliconflow.cn/v1
                .apiKey(hostConfig.getApiKey())
                .build();

        // 配置模型参数：temperature=0.6, top_p=0.9
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(hostConfig.getModelName())    // e.g. Qwen/Qwen3-235B-A22B
                .temperature(0.6)
                .topP(0.9)
                .maxTokens(2048)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();

        this.chatClient = ChatClient.builder(chatModel).build();

        log.info("[ForumHost] 初始化完成 - Model: {}, API: {}",
                hostConfig.getModelName(), hostConfig.getBaseUrl());
    }

    /**
     * 生成主持人发言。
     *
     * <p>主入口方法，对应 Python 原版的 generate_host_speech()。
     * 流程：解析日志 → 构建提示词 → 调用 LLM → 格式化输出。
     *
     * @param forumLogs    格式化的论坛日志列表（[HH:MM:SS] [SPEAKER] content）
     * @param isFinalSpeech 是否为最终总结发言
     * @return 主持人发言内容，如果生成失败返回 empty
     */
    public Optional<String> generateHostSpeech(List<String> forumLogs, boolean isFinalSpeech) {
        if (forumLogs == null || forumLogs.isEmpty()) {
            log.warn("[ForumHost] 输入日志为空，跳过发言生成");
            return Optional.empty();
        }

        try {
            // Step 1: 解析论坛日志，提取 Agent 发言
            List<ParsedSpeech> parsedSpeeches = parseForumLogs(forumLogs);

            if (parsedSpeeches.isEmpty()) {
                log.warn("[ForumHost] 未找到有效的 Agent 发言，跳过");
                return Optional.empty();
            }

            log.info("[ForumHost] 解析到 {} 条 Agent 发言，开始生成主持人回复",
                    parsedSpeeches.size());

            // Step 2: 构建提示词
            String systemPrompt = ForumHostPrompts.SYSTEM_PROMPT;
            String userPrompt = buildUserPrompt(parsedSpeeches, isFinalSpeech);

            // Step 3: 调用 Qwen API
            String rawSpeech = callLlm(systemPrompt, userPrompt);

            // Step 4: 格式化输出
            String formattedSpeech = formatHostSpeech(rawSpeech);

            return Optional.of(formattedSpeech);

        } catch (Exception e) {
            log.error("[ForumHost] 生成发言异常: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ===================== 日志解析 =====================

    /**
     * 解析论坛日志，提取 Agent 发言。
     *
     * <p>对应 Python 原版 _parse_forum_logs() 方法。
     * 只提取 INSIGHT / MEDIA / QUERY 的发言，跳过 SYSTEM 和 HOST。
     */
    List<ParsedSpeech> parseForumLogs(List<String> forumLogs) {
        List<ParsedSpeech> speeches = new ArrayList<>();

        for (String line : forumLogs) {
            if (line == null || line.isBlank()) continue;

            Matcher matcher = LOG_LINE_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                String timestamp = matcher.group(1);
                String speaker = matcher.group(2).toUpperCase();
                String content = matcher.group(3).trim();

                if (AGENT_SPEAKERS.contains(speaker) && !content.isEmpty()) {
                    speeches.add(new ParsedSpeech(timestamp, speaker, content));
                }
            }
        }

        return speeches;
    }

    /**
     * 构建用户提示词。
     *
     * <p>对应 Python 原版 _build_user_prompt() 方法。
     * 动态注入当前时间戳和 Agent 发言记录。
     */
    private String buildUserPrompt(List<ParsedSpeech> speeches, boolean isFinalSpeech) {
        // 格式化当前时间
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日HH时mm分"));

        // 格式化 Agent 发言
        String agentSpeeches = speeches.stream()
                .map(s -> String.format("[%s] %s:\n%s", s.timestamp(), s.speaker(), s.content()))
                .collect(Collectors.joining("\n\n"));

        // 选择模板
        String template = isFinalSpeech
                ? ForumHostPrompts.FINAL_SPEECH_PROMPT_TEMPLATE
                : ForumHostPrompts.USER_PROMPT_TEMPLATE;

        return template
                .replace("{timestamp}", timestamp)
                .replace("{agent_speeches}", agentSpeeches);
    }

    // ===================== LLM 调用 =====================

    /**
     * 调用 Qwen3-235B 生成主持人发言。
     *
     * <p>使用 Spring Retry 实现重试逻辑，对应 Python 原版的 _call_qwen_api()。
     * 最多重试 3 次，指数退避（初始 2 秒，倍数 2）。
     */
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2.0),
            retryFor = {Exception.class}
    )
    String callLlm(String systemPrompt, String userPrompt) {
        log.debug("[ForumHost] 调用 LLM，systemPrompt 长度: {}, userPrompt 长度: {}",
                systemPrompt.length(), userPrompt.length());

        ChatResponse response = chatClient
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();

        String content = response.getResult().getOutput().getText();

        if (content == null || content.isBlank()) {
            throw new RuntimeException("ForumHost LLM 返回空内容");
        }

        log.debug("[ForumHost] LLM 返回内容长度: {}", content.length());
        return content;
    }

    // ===================== 输出格式化 =====================

    /**
     * 格式化主持人发言。
     *
     * <p>对应 Python 原版 _format_host_speech() 方法。
     * 清理多余的空行、引号、首尾空白。
     */
    private String formatHostSpeech(String rawSpeech) {
        if (rawSpeech == null) return "";

        String formatted = rawSpeech.trim();

        // 移除可能的首尾引号
        if (formatted.startsWith("\"") && formatted.endsWith("\"")) {
            formatted = formatted.substring(1, formatted.length() - 1);
        }

        // 压缩连续空行（最多保留 2 个换行）
        formatted = formatted.replaceAll("\n{3,}", "\n\n");

        return formatted.trim();
    }

    // ===================== 内部记录类 =====================

    /**
     * 解析后的 Agent 发言记录。
     */
    record ParsedSpeech(String timestamp, String speaker, String content) {}
}
```

#### 5.3.4 ForumHost 配置属性

```java
package com.bettafish.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "bettafish")
public class BettaFishProperties {

    private Forum forum = new Forum();

    @Data
    public static class Forum {
        private ForumHostConfig host = new ForumHostConfig();
    }

    @Data
    public static class ForumHostConfig {
        /** SiliconFlow API Key */
        @NotBlank(message = "Forum Host API Key 不能为空")
        private String apiKey;

        /** SiliconFlow API Base URL */
        private String baseUrl = "https://api.siliconflow.cn/v1";

        /** 模型名称 */
        private String modelName = "Qwen/Qwen3-235B-A22B";

        /** 温度参数 */
        private double temperature = 0.6;

        /** Top-P 参数 */
        private double topP = 0.9;

        /** 最大输出 Token 数 */
        private int maxTokens = 2048;
    }
}
```

对应的 `application.yml` 配置：

```yaml
bettafish:
  forum:
    host:
      api-key: ${FORUM_HOST_API_KEY}
      base-url: https://api.siliconflow.cn/v1
      model-name: Qwen/Qwen3-235B-A22B
      temperature: 0.6
      top-p: 0.9
      max-tokens: 2048
```

---

### 5.4 ForumReader、MessageStore 与事件类

#### 5.4.1 ForumMessage 实体

```java
package com.bettafish.common.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 论坛消息实体。
 * 每条消息代表一个 Agent 或主持人的一次发言。
 */
@Entity
@Table(name = "forum_messages", indexes = {
    @Index(name = "idx_forum_msg_session", columnList = "sessionId"),
    @Index(name = "idx_forum_msg_session_time", columnList = "sessionId, timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForumMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属分析会话 ID */
    @Column(nullable = false, length = 64)
    private String sessionId;

    /** 发言者类型 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SpeakerType speaker;

    /** 发言内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 发言时间 */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /** 元数据（JSON 存储），如段落编号、搜索关键词等 */
    @Convert(converter = MapToJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metadata;

    /**
     * 发言者类型枚举。
     * 对应 Python 原版日志中的 [SPEAKER] 标识。
     */
    public enum SpeakerType {
        /** 系统消息 */
        SYSTEM,
        /** 论坛主持人 */
        HOST,
        /** InsightAgent -- 舆情挖掘 */
        INSIGHT,
        /** MediaAgent -- 多媒体分析 */
        MEDIA,
        /** QueryAgent -- 深度搜索 */
        QUERY
    }
}
```

#### 5.4.2 JPA Map 转 JSON 转换器

```java
package com.bettafish.common.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;

/**
 * JPA 属性转换器：Map<String, Object> ↔ JSON 字符串。
 */
@Converter
public class MapToJsonConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Map → JSON 转换失败", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON → Map 转换失败", e);
        }
    }
}
```

#### 5.4.3 ForumMessageStore -- JPA 持久化存储

```java
package com.bettafish.forum.store;

import com.bettafish.common.model.ForumMessage;
import com.bettafish.common.model.ForumMessage.SpeakerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 论坛消息的 JPA Repository。
 * 提供按会话、发言者类型、时间范围的查询能力。
 */
@Repository
public interface ForumMessageRepository extends JpaRepository<ForumMessage, Long> {

    /**
     * 获取指定会话的所有消息，按时间正序排列。
     */
    List<ForumMessage> findBySessionIdOrderByTimestampAsc(String sessionId);

    /**
     * 获取指定会话中特定类型发言者的消息。
     */
    List<ForumMessage> findBySessionIdAndSpeakerOrderByTimestampAsc(
            String sessionId, SpeakerType speaker);

    /**
     * 获取指定会话中所有 Agent 发言（排除 SYSTEM 和 HOST）。
     */
    @Query("SELECT m FROM ForumMessage m WHERE m.sessionId = :sessionId " +
           "AND m.speaker IN ('INSIGHT', 'MEDIA', 'QUERY') " +
           "ORDER BY m.timestamp ASC")
    List<ForumMessage> findAgentMessagesBySession(@Param("sessionId") String sessionId);

    /**
     * 获取指定会话中所有 HOST 发言。
     */
    List<ForumMessage> findBySessionIdAndSpeaker(String sessionId, SpeakerType speaker);

    /**
     * 统计指定会话的消息总数。
     */
    long countBySessionId(String sessionId);

    /**
     * 删除指定会话的所有消息（用于会话清理）。
     */
    void deleteBySessionId(String sessionId);
}
```

```java
package com.bettafish.forum.store;

import com.bettafish.common.model.ForumMessage;
import com.bettafish.common.model.ForumMessage.SpeakerType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ForumMessageStore -- 论坛消息存储服务。
 *
 * <p>封装 ForumMessageRepository，提供更高层的业务接口。
 * 同时维护一个内存缓存以减少频繁数据库查询。
 */
@Service
public class ForumMessageStore {

    private final ForumMessageRepository repository;

    public ForumMessageStore(ForumMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * 保存一条论坛消息。
     */
    @Transactional
    public ForumMessage save(ForumMessage message) {
        return repository.save(message);
    }

    /**
     * 获取指定会话的所有消息（时间正序）。
     */
    @Transactional(readOnly = true)
    public List<ForumMessage> getMessagesBySession(String sessionId) {
        return repository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    /**
     * 获取指定会话中仅 Agent 发言（INSIGHT / MEDIA / QUERY）。
     */
    @Transactional(readOnly = true)
    public List<ForumMessage> getAgentMessages(String sessionId) {
        return repository.findAgentMessagesBySession(sessionId);
    }

    /**
     * 获取指定会话中仅主持人发言。
     */
    @Transactional(readOnly = true)
    public List<ForumMessage> getHostMessages(String sessionId) {
        return repository.findBySessionIdAndSpeaker(sessionId, SpeakerType.HOST);
    }

    /**
     * 获取会话消息总数。
     */
    @Transactional(readOnly = true)
    public long getMessageCount(String sessionId) {
        return repository.countBySessionId(sessionId);
    }

    /**
     * 清理会话数据。
     */
    @Transactional
    public void deleteSession(String sessionId) {
        repository.deleteBySessionId(sessionId);
    }
}
```

#### 5.4.4 ForumReader -- 论坛历史读取工具

```java
package com.bettafish.forum.reader;

import com.bettafish.common.model.ForumMessage;
import com.bettafish.forum.store.ForumMessageStore;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ForumReader -- 论坛历史记录读取工具。
 *
 * <p>对应 Python 原版 utils/forum_reader.py。
 * 提供多种格式的论坛历史输出，供 ReportEngine 和其他组件使用。
 */
@Component
public class ForumReader {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FULL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ForumMessageStore messageStore;

    public ForumReader(ForumMessageStore messageStore) {
        this.messageStore = messageStore;
    }

    /**
     * 读取指定会话的所有论坛消息，以日志格式返回。
     *
     * <p>输出格式：[HH:MM:SS] [SPEAKER] content
     * 此格式与 Python 原版 ForumHost 的日志格式一致。
     *
     * @param sessionId 会话 ID
     * @return 格式化的日志行列表
     */
    public List<String> readAsLogLines(String sessionId) {
        List<ForumMessage> messages = messageStore.getMessagesBySession(sessionId);
        return messages.stream()
                .map(msg -> String.format("[%s] [%s] %s",
                        msg.getTimestamp().format(TIME_FMT),
                        msg.getSpeaker().name(),
                        msg.getContent()))
                .collect(Collectors.toList());
    }

    /**
     * 读取论坛讨论的完整 Markdown 格式内容。
     * 供 ReportEngine 作为报告输入使用。
     *
     * @param sessionId 会话 ID
     * @return Markdown 格式的论坛讨论记录
     */
    public String readAsMarkdown(String sessionId) {
        List<ForumMessage> messages = messageStore.getMessagesBySession(sessionId);
        if (messages.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 多 Agent 协作讨论记录\n\n");

        for (ForumMessage msg : messages) {
            String speakerLabel = switch (msg.getSpeaker()) {
                case SYSTEM  -> "**[系统]**";
                case HOST    -> "**[主持人]**";
                case INSIGHT -> "**[舆情分析 Agent]**";
                case MEDIA   -> "**[多媒体分析 Agent]**";
                case QUERY   -> "**[深度搜索 Agent]**";
            };

            sb.append(String.format("### %s (%s)\n\n",
                    speakerLabel, msg.getTimestamp().format(FULL_FMT)));
            sb.append(msg.getContent()).append("\n\n---\n\n");
        }

        return sb.toString();
    }

    /**
     * 读取论坛讨论的纯文本摘要。
     * 用于快速预览或状态查询。
     *
     * @param sessionId 会话 ID
     * @param maxLength 最大字符数
     * @return 摘要文本
     */
    public String readSummary(String sessionId, int maxLength) {
        List<ForumMessage> messages = messageStore.getMessagesBySession(sessionId);
        StringBuilder sb = new StringBuilder();

        for (ForumMessage msg : messages) {
            sb.append("[").append(msg.getSpeaker().name()).append("] ");
            String content = msg.getContent();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            sb.append(content).append("\n");

            if (sb.length() >= maxLength) break;
        }

        return sb.length() > maxLength ? sb.substring(0, maxLength) + "..." : sb.toString();
    }
}
```

#### 5.4.5 事件类定义

```java
package com.bettafish.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.util.Map;

/**
 * AgentMessageEvent -- Agent 发言事件。
 *
 * <p>当任何分析 Agent（INSIGHT / MEDIA / QUERY）产生新的分析结果时，
 * 通过 ApplicationEventPublisher 发布此事件。
 * ForumCoordinator 通过 @EventListener 接收并处理。
 */
@Getter
public class AgentMessageEvent extends ApplicationEvent {

    private final String sessionId;
    private final AgentType agentType;
    private final String content;
    private final Map<String, Object> metadata;

    public AgentMessageEvent(Object source,
                              String sessionId,
                              AgentType agentType,
                              String content,
                              Map<String, Object> metadata) {
        super(source);
        this.sessionId = sessionId;
        this.agentType = agentType;
        this.content = content;
        this.metadata = metadata;
    }

    /**
     * Agent 类型枚举。
     */
    public enum AgentType {
        /** InsightAgent -- 舆情数据库挖掘 */
        INSIGHT,
        /** MediaAgent -- 多媒体内容分析 */
        MEDIA,
        /** QueryAgent -- 深度搜索 */
        QUERY
    }
}
```

```java
package com.bettafish.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * ForumHostResponseEvent -- 论坛主持人响应事件。
 *
 * <p>当 ForumHost 生成主持人发言后，发布此事件。
 * 下游消费者包括：
 * <ul>
 *   <li>WebSocket 推送器 -- 将主持人发言实时推送到前端</li>
 *   <li>ReportEngine -- 将论坛讨论记录纳入最终报告</li>
 * </ul>
 */
@Getter
public class ForumHostResponseEvent extends ApplicationEvent {

    private final String sessionId;
    private final String hostSpeech;

    public ForumHostResponseEvent(Object source, String sessionId, String hostSpeech) {
        super(source);
        this.sessionId = sessionId;
        this.hostSpeech = hostSpeech;
    }
}
```

```java
package com.bettafish.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * ForumSessionEndedEvent -- 论坛会话结束事件。
 *
 * <p>当所有分析 Agent 完成工作后，AnalysisOrchestrator 发布此事件，
 * 通知 ForumCoordinator 进行最终总结并清理资源。
 */
@Getter
public class ForumSessionEndedEvent extends ApplicationEvent {

    private final String sessionId;

    public ForumSessionEndedEvent(Object source, String sessionId) {
        super(source);
        this.sessionId = sessionId;
    }
}
```

---

## 第六章 分析编排与 REST/WebSocket API

### 6.1 AnalysisOrchestrator -- 分析编排器

`AnalysisOrchestrator` 是整个 BettaFish 系统的**顶层编排器**，负责协调三个分析引擎（InsightAgent、MediaAgent、QueryAgent）的并行执行，以及 ForumEngine 和 ReportEngine 的顺序协作。

#### 6.1.1 编排流程

```
用户查询 (query)
    │
    ├─→ [1] ForumCoordinator.initSession()     ← 初始化论坛
    │
    ├─→ [2] 并行启动三个分析引擎（Mono.zip）
    │       ├─ InsightAgent.research(query)     ← 舆情数据库挖掘
    │       ├─ MediaAgent.research(query)       ← 多媒体内容分析
    │       └─ QueryAgent.research(query)       ← 深度搜索分析
    │       │
    │       │  （Agent 在分析过程中持续发布 AgentMessageEvent）
    │       │  （ForumCoordinator 自动触发 ForumHost 生成主持人发言）
    │       │
    │       └─→ 三个引擎全部完成
    │
    ├─→ [3] ForumCoordinator.endSession()       ← 触发最终主持人总结
    │
    ├─→ [4] ReportEngine.generate()             ← 生成最终报告
    │       输入：三个引擎报告 + 论坛讨论记录
    │
    └─→ [5] 返回最终结果
```

#### 6.1.2 完整 Java 实现

```java
package com.bettafish.orchestrator;

import com.bettafish.common.config.BettaFishProperties;
import com.bettafish.common.event.ForumSessionEndedEvent;
import com.bettafish.common.model.AnalysisResult;
import com.bettafish.common.model.AnalysisSession;
import com.bettafish.common.model.AnalysisSession.SessionStatus;
import com.bettafish.forum.ForumCoordinator;
import com.bettafish.forum.reader.ForumReader;
import com.bettafish.insight.InsightAgent;
import com.bettafish.media.MediaAgent;
import com.bettafish.query.QueryAgent;
import com.bettafish.report.ReportEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AnalysisOrchestrator -- 分析流程的顶层编排器。
 *
 * <p>使用 Project Reactor 的 Mono.zip() 实现三个分析引擎的并行执行，
 * 并在所有引擎完成后触发 ForumEngine 的最终总结和 ReportEngine 的报告生成。
 *
 * <p>对应 Python 原版的 app.py 中的 Flask 路由处理逻辑，
 * Python 版本使用 subprocess 启动子进程并行运行各引擎，
 * Java 版本使用 Reactor + 虚拟线程实现更高效的并发。
 */
@Service
public class AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);

    /** 单个引擎的最大执行时间 */
    private static final Duration ENGINE_TIMEOUT = Duration.ofMinutes(30);

    private final InsightAgent insightAgent;
    private final MediaAgent mediaAgent;
    private final QueryAgent queryAgent;
    private final ReportEngine reportEngine;
    private final ForumCoordinator forumCoordinator;
    private final ForumReader forumReader;
    private final ApplicationEventPublisher eventPublisher;

    /** 活跃会话缓存 */
    private final ConcurrentHashMap<String, AnalysisSession> activeSessions =
            new ConcurrentHashMap<>();

    public AnalysisOrchestrator(InsightAgent insightAgent,
                                 MediaAgent mediaAgent,
                                 QueryAgent queryAgent,
                                 ReportEngine reportEngine,
                                 ForumCoordinator forumCoordinator,
                                 ForumReader forumReader,
                                 ApplicationEventPublisher eventPublisher) {
        this.insightAgent = insightAgent;
        this.mediaAgent = mediaAgent;
        this.queryAgent = queryAgent;
        this.reportEngine = reportEngine;
        this.forumCoordinator = forumCoordinator;
        this.forumReader = forumReader;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 启动一次完整的舆情分析。
     *
     * <p>这是整个系统的主入口方法。流程：
     * <ol>
     *   <li>创建分析会话</li>
     *   <li>初始化论坛</li>
     *   <li>并行启动三个分析引擎</li>
     *   <li>等待所有引擎完成</li>
     *   <li>触发论坛最终总结</li>
     *   <li>生成最终报告</li>
     * </ol>
     *
     * @param query 用户的舆情分析查询
     * @return 包含会话 ID 的 Mono（立即返回，后台执行）
     */
    public Mono<String> startAnalysis(String query) {
        // Step 0: 创建会话
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        AnalysisSession session = AnalysisSession.builder()
                .sessionId(sessionId)
                .query(query)
                .status(SessionStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .build();
        activeSessions.put(sessionId, session);

        log.info("[Orchestrator] 开始分析会话 {} | 查询: {}", sessionId, query);

        // Step 1: 初始化论坛
        forumCoordinator.initSession(sessionId);

        // Step 2: 并行启动三个分析引擎（在后台异步执行）
        Mono<String> insightMono = Mono.fromCallable(() -> {
                    log.info("[Orchestrator] [{}] InsightAgent 开始执行", sessionId);
                    return insightAgent.research(sessionId, query);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(ENGINE_TIMEOUT)
                .doOnSuccess(r -> {
                    log.info("[Orchestrator] [{}] InsightAgent 完成", sessionId);
                    session.setInsightReport(r);
                })
                .onErrorResume(e -> {
                    log.error("[Orchestrator] [{}] InsightAgent 失败: {}",
                            sessionId, e.getMessage());
                    session.setInsightReport("InsightAgent 执行失败: " + e.getMessage());
                    return Mono.just("ERROR: " + e.getMessage());
                });

        Mono<String> mediaMono = Mono.fromCallable(() -> {
                    log.info("[Orchestrator] [{}] MediaAgent 开始执行", sessionId);
                    return mediaAgent.research(sessionId, query);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(ENGINE_TIMEOUT)
                .doOnSuccess(r -> {
                    log.info("[Orchestrator] [{}] MediaAgent 完成", sessionId);
                    session.setMediaReport(r);
                })
                .onErrorResume(e -> {
                    log.error("[Orchestrator] [{}] MediaAgent 失败: {}",
                            sessionId, e.getMessage());
                    session.setMediaReport("MediaAgent 执行失败: " + e.getMessage());
                    return Mono.just("ERROR: " + e.getMessage());
                });

        Mono<String> queryMono = Mono.fromCallable(() -> {
                    log.info("[Orchestrator] [{}] QueryAgent 开始执行", sessionId);
                    return queryAgent.research(sessionId, query);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(ENGINE_TIMEOUT)
                .doOnSuccess(r -> {
                    log.info("[Orchestrator] [{}] QueryAgent 完成", sessionId);
                    session.setQueryReport(r);
                })
                .onErrorResume(e -> {
                    log.error("[Orchestrator] [{}] QueryAgent 失败: {}",
                            sessionId, e.getMessage());
                    session.setQueryReport("QueryAgent 执行失败: " + e.getMessage());
                    return Mono.just("ERROR: " + e.getMessage());
                });

        // Step 3: 使用 Mono.zip 等待三个引擎全部完成
        Mono.zip(insightMono, mediaMono, queryMono)
                .flatMap(tuple -> {
                    String insightReport = tuple.getT1();
                    String mediaReport = tuple.getT2();
                    String queryReport = tuple.getT3();

                    log.info("[Orchestrator] [{}] 三个引擎全部完成，触发后续流程", sessionId);

                    // Step 4: 结束论坛，触发最终主持人总结
                    eventPublisher.publishEvent(
                            new ForumSessionEndedEvent(this, sessionId));

                    // 等待 ForumHost 生成最终总结（给一个短暂的缓冲时间）
                    return Mono.delay(Duration.ofSeconds(5))
                            .then(Mono.fromCallable(() -> {
                                // Step 5: 获取论坛讨论记录
                                String forumLogs = forumReader.readAsMarkdown(sessionId);

                                // Step 6: 调用 ReportEngine 生成最终报告
                                log.info("[Orchestrator] [{}] 开始生成最终报告", sessionId);
                                session.setStatus(SessionStatus.GENERATING_REPORT);

                                String finalReport = reportEngine.generateReport(
                                        query, insightReport, mediaReport,
                                        queryReport, forumLogs);

                                session.setFinalReport(finalReport);
                                session.setStatus(SessionStatus.COMPLETED);
                                session.setCompletedAt(LocalDateTime.now());

                                log.info("[Orchestrator] [{}] 分析完成，报告长度: {} 字符",
                                        sessionId, finalReport.length());

                                return finalReport;
                            }))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .onErrorResume(e -> {
                    log.error("[Orchestrator] [{}] 分析流程异常: {}", sessionId, e.getMessage(), e);
                    session.setStatus(SessionStatus.FAILED);
                    session.setErrorMessage(e.getMessage());
                    return Mono.empty();
                })
                .subscribe();  // 启动后台执行

        // 立即返回 sessionId（不阻塞）
        return Mono.just(sessionId);
    }

    /**
     * 获取分析会话状态。
     */
    public AnalysisSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    /**
     * 停止分析会话。
     */
    public boolean stopAnalysis(String sessionId) {
        AnalysisSession session = activeSessions.get(sessionId);
        if (session == null) return false;

        session.setStatus(SessionStatus.CANCELLED);
        forumCoordinator.endSession(sessionId);

        log.info("[Orchestrator] 会话 {} 已取消", sessionId);
        return true;
    }

    /**
     * 获取所有活跃会话。
     */
    public Map<String, AnalysisSession> getActiveSessions() {
        return Map.copyOf(activeSessions);
    }
}
```

#### 6.1.3 AnalysisSession 模型

```java
package com.bettafish.common.model;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 分析会话模型，跟踪一次完整分析的全部状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisSession {

    private String sessionId;
    private String query;
    private SessionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    /** 三个引擎的独立报告 */
    private String insightReport;
    private String mediaReport;
    private String queryReport;

    /** 最终综合报告 */
    private String finalReport;

    /** 错误信息（如果分析失败） */
    private String errorMessage;

    /**
     * 会话状态枚举。
     */
    public enum SessionStatus {
        /** 分析进行中 */
        RUNNING,
        /** 正在生成最终报告 */
        GENERATING_REPORT,
        /** 分析完成 */
        COMPLETED,
        /** 分析失败 */
        FAILED,
        /** 用户取消 */
        CANCELLED
    }
}
```

---

### 6.2 REST API Controller

#### 6.2.1 路由映射关系

Python 原版使用 Flask 提供 REST API，Java 版本使用 Spring MVC 控制器。以下是完整的路由映射：

| HTTP 方法 | Python Flask 路由 | Java Spring 路由 | 功能描述 |
|----------|------------------|-----------------|---------|
| POST | `/api/start_analysis` | `/api/analysis/start` | 启动舆情分析 |
| GET | `/api/status/<session_id>` | `/api/analysis/{sessionId}/status` | 获取分析状态 |
| GET | `/api/report/<session_id>` | `/api/analysis/{sessionId}/report` | 获取最终报告 |
| GET | `/api/forum/<session_id>` | `/api/analysis/{sessionId}/forum` | 获取论坛消息 |
| POST | `/api/stop/<session_id>` | `/api/analysis/{sessionId}/stop` | 停止分析 |

#### 6.2.2 完整 Controller 代码

```java
package com.bettafish.web.controller;

import com.bettafish.common.model.AnalysisSession;
import com.bettafish.common.model.ForumMessage;
import com.bettafish.forum.ForumCoordinator;
import com.bettafish.orchestrator.AnalysisOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AnalysisController -- 舆情分析 REST API 控制器。
 *
 * <p>对应 Python 原版 app.py 中的 Flask 路由处理器。
 * 提供分析任务的创建、状态查询、报告获取、论坛查看和停止操作。
 */
@RestController
@RequestMapping("/api/analysis")
@Tag(name = "舆情分析 API", description = "多 Agent 舆情分析系统的 REST 接口")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final AnalysisOrchestrator orchestrator;
    private final ForumCoordinator forumCoordinator;

    public AnalysisController(AnalysisOrchestrator orchestrator,
                               ForumCoordinator forumCoordinator) {
        this.orchestrator = orchestrator;
        this.forumCoordinator = forumCoordinator;
    }

    // ===================== DTO 定义 =====================

    /**
     * 分析启动请求 DTO。
     */
    public record StartAnalysisRequest(
            @NotBlank(message = "查询内容不能为空")
            String query
    ) {}

    /**
     * 统一 API 响应包装。
     */
    public record ApiResponse<T>(
            boolean success,
            String message,
            T data,
            LocalDateTime timestamp
    ) {
        public static <T> ApiResponse<T> ok(T data) {
            return new ApiResponse<>(true, "操作成功", data, LocalDateTime.now());
        }

        public static <T> ApiResponse<T> ok(String message, T data) {
            return new ApiResponse<>(true, message, data, LocalDateTime.now());
        }

        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, message, null, LocalDateTime.now());
        }
    }

    /**
     * 分析状态响应 DTO。
     */
    public record AnalysisStatusResponse(
            String sessionId,
            String query,
            String status,
            String forumState,
            int forumMessageCount,
            boolean hasInsightReport,
            boolean hasMediaReport,
            boolean hasQueryReport,
            boolean hasFinalReport,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {}

    /**
     * 论坛消息 DTO。
     */
    public record ForumMessageDto(
            String speaker,
            String content,
            LocalDateTime timestamp,
            Map<String, Object> metadata
    ) {
        public static ForumMessageDto from(ForumMessage msg) {
            return new ForumMessageDto(
                    msg.getSpeaker().name(),
                    msg.getContent(),
                    msg.getTimestamp(),
                    msg.getMetadata()
            );
        }
    }

    // ===================== API 端点 =====================

    /**
     * POST /api/analysis/start -- 启动舆情分析。
     *
     * <p>对应 Python 原版 Flask 路由：POST /api/start_analysis
     *
     * <p>接收用户的查询内容，创建分析会话，并行启动三个分析引擎。
     * 立即返回会话 ID，后台异步执行分析流程。
     */
    @PostMapping("/start")
    @Operation(summary = "启动舆情分析",
               description = "提交查询内容，系统将并行启动舆情分析、多媒体分析、深度搜索三个引擎")
    public ResponseEntity<ApiResponse<Map<String, String>>> startAnalysis(
            @Valid @RequestBody StartAnalysisRequest request) {

        log.info("[API] 收到分析请求: {}", request.query());

        try {
            String sessionId = orchestrator.startAnalysis(request.query()).block();

            return ResponseEntity.ok(ApiResponse.ok(
                    "分析任务已启动",
                    Map.of(
                            "sessionId", sessionId,
                            "query", request.query(),
                            "status", "RUNNING",
                            "message", "三个分析引擎已并行启动，请通过 /status 接口查询进度"
                    )
            ));
        } catch (Exception e) {
            log.error("[API] 启动分析失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("分析启动失败: " + e.getMessage()));
        }
    }

    /**
     * GET /api/analysis/{sessionId}/status -- 获取分析状态。
     *
     * <p>对应 Python 原版 Flask 路由：GET /api/status/<session_id>
     *
     * <p>返回指定会话的当前状态，包括各引擎完成情况、论坛状态等。
     */
    @GetMapping("/{sessionId}/status")
    @Operation(summary = "获取分析状态",
               description = "返回指定会话的当前进度、论坛状态和各引擎完成情况")
    public ResponseEntity<ApiResponse<AnalysisStatusResponse>> getStatus(
            @PathVariable @Parameter(description = "分析会话 ID") String sessionId) {

        AnalysisSession session = orchestrator.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("会话不存在: " + sessionId));
        }

        ForumCoordinator.ForumSessionState forumState =
                forumCoordinator.getSessionState(sessionId);

        AnalysisStatusResponse status = new AnalysisStatusResponse(
                session.getSessionId(),
                session.getQuery(),
                session.getStatus().name(),
                forumState.name(),
                forumCoordinator.getCurrentMessageCount(sessionId),
                session.getInsightReport() != null,
                session.getMediaReport() != null,
                session.getQueryReport() != null,
                session.getFinalReport() != null,
                session.getCreatedAt(),
                session.getCompletedAt()
        );

        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    /**
     * GET /api/analysis/{sessionId}/report -- 获取最终报告。
     *
     * <p>对应 Python 原版 Flask 路由：GET /api/report/<session_id>
     *
     * <p>如果分析尚未完成，返回 202 Accepted 和当前状态。
     * 如果分析已完成，返回最终的 HTML 格式报告。
     */
    @GetMapping("/{sessionId}/report")
    @Operation(summary = "获取最终报告",
               description = "返回完整的舆情分析报告（HTML 格式）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReport(
            @PathVariable String sessionId) {

        AnalysisSession session = orchestrator.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("会话不存在: " + sessionId));
        }

        if (session.getFinalReport() == null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.ok("报告尚在生成中", Map.of(
                            "status", session.getStatus().name(),
                            "message", "请稍后重试"
                    )));
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "sessionId", sessionId,
                "query", session.getQuery(),
                "report", session.getFinalReport(),
                "generatedAt", session.getCompletedAt() != null
                        ? session.getCompletedAt().toString() : ""
        )));
    }

    /**
     * GET /api/analysis/{sessionId}/forum -- 获取论坛讨论消息。
     *
     * <p>对应 Python 原版 Flask 路由：GET /api/forum/<session_id>
     *
     * <p>返回指定会话的所有论坛消息（包括 Agent 发言和主持人发言）。
     * 支持分页查询。
     */
    @GetMapping("/{sessionId}/forum")
    @Operation(summary = "获取论坛讨论消息",
               description = "返回指定会话的所有论坛消息，包括 Agent 发言和主持人回复")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getForumMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        AnalysisSession session = orchestrator.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("会话不存在: " + sessionId));
        }

        List<ForumMessage> messages = forumCoordinator.getSessionMessages(sessionId);
        ForumCoordinator.ForumSessionState forumState =
                forumCoordinator.getSessionState(sessionId);

        // 分页处理
        int startIdx = page * size;
        int endIdx = Math.min(startIdx + size, messages.size());
        List<ForumMessageDto> pagedMessages = messages.subList(
                        Math.min(startIdx, messages.size()),
                        endIdx)
                .stream()
                .map(ForumMessageDto::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "sessionId", sessionId,
                "forumState", forumState.name(),
                "totalMessages", messages.size(),
                "page", page,
                "size", size,
                "messages", pagedMessages
        )));
    }

    /**
     * POST /api/analysis/{sessionId}/stop -- 停止分析。
     *
     * <p>对应 Python 原版 Flask 路由：POST /api/stop/<session_id>
     *
     * <p>取消正在运行的分析任务，终止论坛会话。
     */
    @PostMapping("/{sessionId}/stop")
    @Operation(summary = "停止分析",
               description = "取消正在运行的分析任务")
    public ResponseEntity<ApiResponse<Map<String, String>>> stopAnalysis(
            @PathVariable String sessionId) {

        boolean stopped = orchestrator.stopAnalysis(sessionId);

        if (!stopped) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("会话不存在或已结束: " + sessionId));
        }

        return ResponseEntity.ok(ApiResponse.ok("分析已停止", Map.of(
                "sessionId", sessionId,
                "status", "CANCELLED"
        )));
    }
}
```

---

### 6.3 WebSocket 实时推送

Python 原版使用 Flask-SocketIO 实现实时推送，Java 版本使用 Spring WebSocket + STOMP 协议 + SockJS 降级支持。

#### 6.3.1 WebSocket 配置

```java
package com.bettafish.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置类。
 *
 * <p>使用 STOMP 协议 + SockJS 降级方案，提供：
 * <ul>
 *   <li>/ws -- WebSocket 连接端点（SockJS 兼容）</li>
 *   <li>/topic/forum/{sessionId} -- 论坛消息实时推送</li>
 *   <li>/topic/progress/{sessionId} -- Agent 进度实时推送</li>
 *   <li>/topic/report/{sessionId} -- 报告生成进度推送</li>
 * </ul>
 *
 * <p>对应 Python 原版使用的 Flask-SocketIO 实时通信。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的内存消息代理
        // /topic -- 广播消息（一对多）
        // /queue -- 点对点消息（一对一）
        config.enableSimpleBroker("/topic", "/queue");

        // 客户端发送消息时的前缀
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 WebSocket 端点，支持 SockJS 降级
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setStreamBytesLimit(512 * 1024)      // 512KB
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000);        // 30 秒断开延迟
    }
}
```

#### 6.3.2 实时推送处理器

```java
package com.bettafish.web.websocket;

import com.bettafish.common.event.AgentMessageEvent;
import com.bettafish.common.event.ForumHostResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket 实时推送处理器。
 *
 * <p>监听 Spring 应用事件，将 Agent 消息和论坛讨论实时推送到前端 WebSocket 客户端。
 *
 * <h3>推送频道</h3>
 * <ul>
 *   <li>/topic/forum/{sessionId} -- 论坛消息（Agent 发言 + 主持人回复）</li>
 *   <li>/topic/progress/{sessionId} -- Agent 分析进度更新</li>
 *   <li>/topic/report/{sessionId} -- 报告生成进度</li>
 * </ul>
 */
@Component
public class AnalysisWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWebSocketHandler.class);

    private final SimpMessagingTemplate messagingTemplate;

    public AnalysisWebSocketHandler(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // ===================== WebSocket 消息 DTO =====================

    /**
     * 论坛消息推送体。
     */
    public record ForumWebSocketMessage(
            String type,         // "AGENT_MESSAGE" | "HOST_RESPONSE" | "SYSTEM"
            String speaker,      // "INSIGHT" | "MEDIA" | "QUERY" | "HOST" | "SYSTEM"
            String content,
            String sessionId,
            LocalDateTime timestamp
    ) {}

    /**
     * 进度更新推送体。
     */
    public record ProgressWebSocketMessage(
            String type,         // "AGENT_PROGRESS" | "REPORT_PROGRESS"
            String agent,        // "INSIGHT" | "MEDIA" | "QUERY" | "REPORT"
            String phase,        // "STRUCTURE" | "SEARCH" | "SUMMARY" | "REFLECTION" | "FORMATTING"
            int progress,        // 0-100
            String message,
            String sessionId,
            LocalDateTime timestamp
    ) {}

    // ===================== 事件监听 & 推送 =====================

    /**
     * 监听 Agent 消息事件，推送到 WebSocket。
     *
     * <p>当 Agent 发布新的分析结果时，同时推送到：
     * <ul>
     *   <li>/topic/forum/{sessionId} -- 论坛频道（完整消息）</li>
     *   <li>/topic/progress/{sessionId} -- 进度频道（简要更新）</li>
     * </ul>
     */
    @EventListener
    @Async("forumEventExecutor")
    public void onAgentMessage(AgentMessageEvent event) {
        String sessionId = event.getSessionId();

        // 推送论坛消息
        ForumWebSocketMessage forumMsg = new ForumWebSocketMessage(
                "AGENT_MESSAGE",
                event.getAgentType().name(),
                event.getContent(),
                sessionId,
                LocalDateTime.now()
        );
        messagingTemplate.convertAndSend(
                "/topic/forum/" + sessionId, forumMsg);

        // 推送进度更新
        String phase = extractPhaseFromMetadata(event.getMetadata());
        int progress = extractProgressFromMetadata(event.getMetadata());

        ProgressWebSocketMessage progressMsg = new ProgressWebSocketMessage(
                "AGENT_PROGRESS",
                event.getAgentType().name(),
                phase,
                progress,
                String.format("%s Agent 已发布新的分析结果", event.getAgentType().name()),
                sessionId,
                LocalDateTime.now()
        );
        messagingTemplate.convertAndSend(
                "/topic/progress/" + sessionId, progressMsg);

        log.debug("[WebSocket] 已推送 Agent 消息到 /topic/forum/{} 和 /topic/progress/{}",
                sessionId, sessionId);
    }

    /**
     * 监听 ForumHost 响应事件，推送到 WebSocket。
     */
    @EventListener
    @Async("forumEventExecutor")
    public void onForumHostResponse(ForumHostResponseEvent event) {
        String sessionId = event.getSessionId();

        ForumWebSocketMessage msg = new ForumWebSocketMessage(
                "HOST_RESPONSE",
                "HOST",
                event.getHostSpeech(),
                sessionId,
                LocalDateTime.now()
        );
        messagingTemplate.convertAndSend("/topic/forum/" + sessionId, msg);

        log.debug("[WebSocket] 已推送主持人发言到 /topic/forum/{}", sessionId);
    }

    /**
     * 发送报告生成进度更新。
     * 由 ReportEngine 直接调用。
     */
    public void sendReportProgress(String sessionId, int progress, String message) {
        ProgressWebSocketMessage msg = new ProgressWebSocketMessage(
                "REPORT_PROGRESS",
                "REPORT",
                "GENERATING",
                progress,
                message,
                sessionId,
                LocalDateTime.now()
        );
        messagingTemplate.convertAndSend("/topic/report/" + sessionId, msg);
    }

    // ===================== 辅助方法 =====================

    private String extractPhaseFromMetadata(Map<String, Object> metadata) {
        if (metadata == null) return "UNKNOWN";
        Object phase = metadata.get("phase");
        return phase != null ? phase.toString() : "ANALYSIS";
    }

    private int extractProgressFromMetadata(Map<String, Object> metadata) {
        if (metadata == null) return -1;
        Object progress = metadata.get("progress");
        if (progress instanceof Number num) {
            return num.intValue();
        }
        return -1;
    }
}
```

#### 6.3.3 前端 JavaScript 连接示例

为了完整性，提供与 Java 后端配合的前端 WebSocket 连接示例：

```javascript
/**
 * BettaFish WebSocket 客户端。
 * 使用 STOMP over SockJS 连接到 Spring WebSocket 端点。
 */
class BettaFishWebSocket {
    constructor(baseUrl) {
        this.baseUrl = baseUrl || window.location.origin;
        this.stompClient = null;
        this.subscriptions = {};
    }

    /**
     * 连接到 WebSocket 服务器。
     */
    connect() {
        const socket = new SockJS(`${this.baseUrl}/ws`);
        this.stompClient = Stomp.over(socket);

        // 减少控制台日志
        this.stompClient.debug = null;

        return new Promise((resolve, reject) => {
            this.stompClient.connect({}, () => {
                console.log('[BettaFish WS] 已连接');
                resolve();
            }, (error) => {
                console.error('[BettaFish WS] 连接失败:', error);
                reject(error);
            });
        });
    }

    /**
     * 订阅指定会话的论坛消息。
     */
    subscribeToForum(sessionId, callback) {
        const dest = `/topic/forum/${sessionId}`;
        this.subscriptions[dest] = this.stompClient.subscribe(dest, (message) => {
            const data = JSON.parse(message.body);
            callback(data);
        });
    }

    /**
     * 订阅指定会话的进度更新。
     */
    subscribeToProgress(sessionId, callback) {
        const dest = `/topic/progress/${sessionId}`;
        this.subscriptions[dest] = this.stompClient.subscribe(dest, (message) => {
            const data = JSON.parse(message.body);
            callback(data);
        });
    }

    /**
     * 订阅指定会话的报告进度。
     */
    subscribeToReport(sessionId, callback) {
        const dest = `/topic/report/${sessionId}`;
        this.subscriptions[dest] = this.stompClient.subscribe(dest, (message) => {
            const data = JSON.parse(message.body);
            callback(data);
        });
    }

    /**
     * 断开连接。
     */
    disconnect() {
        if (this.stompClient) {
            Object.values(this.subscriptions).forEach(sub => sub.unsubscribe());
            this.stompClient.disconnect();
            console.log('[BettaFish WS] 已断开');
        }
    }
}

// 使用示例
async function startAnalysisWithLiveUpdates(query) {
    // 1. 启动分析
    const response = await fetch('/api/analysis/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query })
    });
    const result = await response.json();
    const sessionId = result.data.sessionId;

    // 2. 建立 WebSocket 连接
    const ws = new BettaFishWebSocket();
    await ws.connect();

    // 3. 订阅实时更新
    ws.subscribeToForum(sessionId, (msg) => {
        console.log(`[论坛] [${msg.speaker}] ${msg.content.substring(0, 100)}...`);
        // 更新前端 UI...
    });

    ws.subscribeToProgress(sessionId, (msg) => {
        console.log(`[进度] ${msg.agent}: ${msg.progress}% - ${msg.message}`);
        // 更新进度条...
    });

    ws.subscribeToReport(sessionId, (msg) => {
        console.log(`[报告] ${msg.progress}% - ${msg.message}`);
        // 更新报告生成进度...
    });

    return { sessionId, ws };
}
```

---

### 6.4 异常处理与重试

#### 6.4.1 全局异常处理器

```java
package com.bettafish.web.handler;

import com.bettafish.web.controller.AnalysisController.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>统一处理各类异常，返回标准化的 API 错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理参数校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationError(
            MethodArgumentNotValidException ex) {

        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("[API] 参数校验失败: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("参数校验失败: " + errors));
    }

    /**
     * 处理约束违规异常。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {

        log.warn("[API] 约束违规: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("请求参数不合法: " + ex.getMessage()));
    }

    /**
     * 处理 Agent 超时异常。
     *
     * <p>当分析引擎在指定时间内未完成时抛出。
     * 对应 AnalysisOrchestrator 中配置的 ENGINE_TIMEOUT。
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleTimeout(TimeoutException ex) {
        log.error("[API] Agent 执行超时: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error("分析引擎执行超时，请稍后重试"));
    }

    /**
     * 处理 LLM 调用异常。
     */
    @ExceptionHandler(LlmInvocationException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmError(LlmInvocationException ex) {
        log.error("[API] LLM 调用失败: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("AI 模型调用失败: " + ex.getMessage()));
    }

    /**
     * 处理所有未捕获的异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericError(Exception ex) {
        log.error("[API] 未预期的异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("服务器内部错误，请联系管理员"));
    }
}
```

```java
package com.bettafish.web.handler;

/**
 * LLM 调用异常。
 * 当 LLM API 调用失败且重试耗尽后抛出。
 */
public class LlmInvocationException extends RuntimeException {

    private final String model;
    private final int httpStatus;

    public LlmInvocationException(String message, String model, int httpStatus) {
        super(message);
        this.model = model;
        this.httpStatus = httpStatus;
    }

    public LlmInvocationException(String message, String model, int httpStatus, Throwable cause) {
        super(message, cause);
        this.model = model;
        this.httpStatus = httpStatus;
    }

    public String getModel() { return model; }
    public int getHttpStatus() { return httpStatus; }
}
```

#### 6.4.2 LLM 调用重试策略

```java
package com.bettafish.common.retry;

import com.bettafish.web.handler.LlmInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

/**
 * LLM 调用重试策略配置。
 *
 * <p>对应 Python 原版中各引擎使用的 @with_graceful_retry 装饰器。
 * Java 版本使用 Spring Retry 实现相同的指数退避重试逻辑。
 *
 * <h3>重试策略</h3>
 * <ul>
 *   <li>最大重试次数：3 次</li>
 *   <li>初始退避时间：2 秒</li>
 *   <li>退避倍数：2.0（2s → 4s → 8s）</li>
 *   <li>最大退避时间：30 秒</li>
 *   <li>可重试异常：RuntimeException 及其子类（排除参数错误等不可恢复异常）</li>
 * </ul>
 */
@Configuration
@EnableRetry
public class RetryConfig {

    private static final Logger log = LoggerFactory.getLogger(RetryConfig.class);

    /**
     * LLM 调用专用 RetryTemplate。
     *
     * <p>用于所有 LLM API 调用（ChatClient.call()），提供统一的重试和退避行为。
     * Agent 中的每个 LLM 调用节点（SearchNode、SummaryNode、ReflectionNode 等）
     * 都通过此模板执行。
     */
    @Bean("llmRetryTemplate")
    public RetryTemplate llmRetryTemplate() {
        // 重试策略：最多 3 次（含首次尝试）
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                3,
                Map.of(
                        RuntimeException.class, true,              // 一般运行时异常可重试
                        LlmInvocationException.class, true,        // LLM 调用异常可重试
                        IllegalArgumentException.class, false       // 参数错误不重试
                ),
                true  // traverseCauses = true，检查嵌套异常链
        );

        // 退避策略：指数退避
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(2000);    // 初始等待 2 秒
        backOffPolicy.setMultiplier(2.0);          // 每次翻倍
        backOffPolicy.setMaxInterval(30000);       // 最大等待 30 秒

        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOffPolicy);

        // 添加重试监听器用于日志记录
        template.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> boolean open(RetryContext context,
                                                          RetryCallback<T, E> callback) {
                return true;  // 允许重试
            }

            @Override
            public <T, E extends Throwable> void onError(RetryContext context,
                                                          RetryCallback<T, E> callback,
                                                          Throwable throwable) {
                int retryCount = context.getRetryCount();
                log.warn("[LLM Retry] 第 {} 次重试失败: {}",
                        retryCount, throwable.getMessage());
            }

            @Override
            public <T, E extends Throwable> void close(RetryContext context,
                                                        RetryCallback<T, E> callback,
                                                        Throwable throwable) {
                if (throwable != null) {
                    log.error("[LLM Retry] 所有重试已耗尽，最终异常: {}",
                            throwable.getMessage());
                } else if (context.getRetryCount() > 0) {
                    log.info("[LLM Retry] 在第 {} 次重试后成功",
                            context.getRetryCount());
                }
            }
        });

        return template;
    }

    /**
     * 搜索工具调用专用 RetryTemplate。
     *
     * <p>对应 Python 原版中 search.py 的 @with_graceful_retry(SEARCH_API_RETRY_CONFIG)。
     * 搜索工具的重试策略更宽松（5 次重试），因为外部搜索 API 不稳定性更高。
     */
    @Bean("searchRetryTemplate")
    public RetryTemplate searchRetryTemplate() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(5);

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);    // 初始等待 1 秒
        backOffPolicy.setMultiplier(1.5);          // 每次 1.5 倍
        backOffPolicy.setMaxInterval(15000);       // 最大等待 15 秒

        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOffPolicy);
        return template;
    }
}
```

#### 6.4.3 Agent 超时处理

在 `AnalysisOrchestrator` 中，每个引擎的 Mono 都配置了 `.timeout(ENGINE_TIMEOUT)`。当 Agent 在 30 分钟内未完成时，Reactor 会抛出 `TimeoutException`，然后通过 `.onErrorResume()` 被优雅捕获。关键的超时处理层次如下：

| 层级 | 超时目标 | 超时时间 | 处理方式 |
|------|---------|---------|---------|
| L1 -- 单次 LLM 调用 | ChatClient.call() | 120 秒 | Spring AI 的 connectTimeout / readTimeout |
| L2 -- LLM 重试序列 | RetryTemplate 执行 | ~46 秒 (2+4+8 + 调用时间) | 3 次重试后抛出异常 |
| L3 -- 单个搜索工具 | searchRetryTemplate | ~30 秒 (1+1.5+2.25+...) | 5 次重试后返回空结果 |
| L4 -- 单个 Agent | Mono.timeout() | 30 分钟 | 返回错误报告，不影响其他 Agent |
| L5 -- 整体分析 | 无硬超时 | 取决于最慢 Agent | 所有 Agent 完成后继续 |

```java
/**
 * Spring AI ChatClient 的超时配置。
 * 在各引擎的 ChatModel 构建时通过 RestClient 配置。
 */
@Bean
public RestClient.Builder llmRestClientBuilder() {
    return RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory() {{
                setConnectTimeout(Duration.ofSeconds(30));  // 连接超时 30 秒
                setReadTimeout(Duration.ofSeconds(120));    // 读取超时 120 秒
            }});
}
```

#### 6.4.4 优雅降级策略

当某个 Agent 失败或超时时，系统不会中断整个分析流程，而是执行优雅降级：

```java
/**
 * Agent 降级策略示例（在 AnalysisOrchestrator 中）。
 *
 * 当 InsightAgent 失败时，生成一个降级报告，
 * 而非让整个分析流程失败。
 */
.onErrorResume(e -> {
    log.error("[Orchestrator] [{}] InsightAgent 失败: {}", sessionId, e.getMessage());

    // 构建降级报告
    String degradedReport = String.format("""
            ## 舆情分析报告（降级模式）
            
            > **注意**: InsightAgent 在执行过程中遇到异常，以下为有限信息。
            > 错误原因: %s
            
            ### 可用信息
            由于舆情数据库分析引擎未能正常完成，本部分数据暂缺。
            请参考 MediaAgent 和 QueryAgent 的分析结果获取相关信息。
            """, e.getMessage());

    session.setInsightReport(degradedReport);
    return Mono.just(degradedReport);
})
```

---

> **本章小结**
>
> 第五章详细介绍了 ForumEngine 从 Python 文件轮询模式到 Java 事件驱动模式的架构重构，包括 ForumCoordinator 的线程安全设计、ForumHost 的 LLM 主持人实现（完整保留了 Python 原版的提示词语义），以及 JPA 持久化的消息存储方案。
>
> 第六章展示了 AnalysisOrchestrator 使用 Project Reactor 实现的并行 Agent 编排、完整的 REST API 控制器（5 个端点对应 Python Flask 路由）、基于 STOMP 协议的 WebSocket 实时推送，以及多层次的异常处理和重试策略。
>
> 这两章共同构成了 BettaFish Java 版本的**运行时骨架** -- 从用户提交查询到最终报告输出的完整数据流路径。

## Chapter 7: Document IR 中间表示层

Document IR（Intermediate Representation，中间表示）是 BettaFish 报告引擎的核心数据层。它定义了一套与渲染无关的结构化文档模型，使得内容生成（LLM 输出）与最终渲染（HTML / PDF）彻底解耦。本章将详细阐述 IR 在 Java 中的完整实现，包括 16 种 Block 类型、12 种 InlineMark 类型、章节 / 文档级 IR 容器、校验器、装订器与渲染器。

---

### 7.1 IR 设计理念

#### 7.1.1 为什么需要 IR

在 BettaFish 的报告生成管线中，LLM 负责逐章节生成内容，最终由渲染器将所有章节拼装为交互式 HTML 或可打印 PDF。如果让 LLM 直接输出 HTML，会面临以下问题：

1. **不可控的标签嵌套**：LLM 经常生成不闭合或错误嵌套的 HTML 标签，导致页面渲染崩溃。
2. **样式耦合**：HTML 中混入内联样式后，全局主题切换（深色模式、打印模式）变得极其困难。
3. **无法校验**：纯 HTML 字符串缺乏结构化语义，难以进行内容完整性校验（如"是否包含 SWOT 分析"、"KPI 数据是否完整"）。
4. **多格式输出困难**：同一份报告需要输出 HTML、PDF、甚至未来的 DOCX，直接操作 HTML 字符串无法复用。

IR 层的引入解决了所有这些问题：

```
LLM → JSON (ChapterIR) → IRValidator → DocumentStitcher → HtmlRenderer / PdfRenderer
                                                         → DocxRenderer (未来)
```

每个 LLM 调用输出的是一个符合严格 Schema 的 JSON 对象（`ChapterIR`），经过校验后由装订器合并为完整的 `DocumentIR`，最终交给不同的渲染器输出目标格式。

#### 7.1.2 Python JSON Schema → Java Sealed Interface

原版 Python 实现使用 JSON Schema 进行运行时校验：

```python
# Python 原版：运行时 JSON Schema 校验
from jsonschema import validate
validate(instance=chapter_json, schema=CHAPTER_JSON_SCHEMA)
```

这种方式的缺点是：校验发生在运行时，类型错误只能在执行时才能发现。Java 版利用 **sealed interface + record** 实现编译时类型安全：

```java
// Java 版：编译时类型安全
public sealed interface Block permits
    HeadingBlock, ParagraphBlock, ListBlock, TableBlock,
    SwotTableBlock, PestTableBlock, BlockquoteBlock, EngineQuoteBlock,
    HrBlock, CodeBlock, MathBlock, FigureBlock,
    CalloutBlock, KpiGridBlock, WidgetBlock, TocBlock { }
```

这样做的优势：

| 特性 | Python JSON Schema | Java Sealed Interface |
|------|-------------------|----------------------|
| 类型检查时机 | 运行时 | 编译时 |
| 穷举检查 | 无 | switch 表达式强制穷举 |
| IDE 支持 | 有限 | 完整的自动补全与重构 |
| 序列化 / 反序列化 | 手动解析 dict | Jackson 自动映射 |
| 扩展新类型 | 修改 Schema 文件 | 编译器提示所有未处理的 case |

当我们在 `IRValidator` 中使用 `switch` 表达式对 `Block` 进行模式匹配时，编译器会强制要求处理所有 16 种类型——任何遗漏都会导致编译错误，从根本上杜绝了"漏处理某种 Block"的 bug。

---

### 7.2 Block 类型定义（16 types）

#### 7.2.0 顶层密封接口

所有 Block 类型构成一个密封类型层级。`type()` 方法返回与 Python 原版 IR JSON 中 `"type"` 字段一致的字符串，确保序列化兼容性：

```java
package com.bettafish.report.ir;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * Document IR Block 顶层密封接口。
 *
 * 对应原版 ReportEngine/ir/schema.py 中定义的所有 block 类型。
 * 使用 sealed interface 确保编译时穷举，Jackson 注解确保 JSON 序列化兼容。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = HeadingBlock.class, name = "heading"),
    @JsonSubTypes.Type(value = ParagraphBlock.class, name = "paragraph"),
    @JsonSubTypes.Type(value = ListBlock.class, name = "list"),
    @JsonSubTypes.Type(value = TableBlock.class, name = "table"),
    @JsonSubTypes.Type(value = SwotTableBlock.class, name = "swotTable"),
    @JsonSubTypes.Type(value = PestTableBlock.class, name = "pestTable"),
    @JsonSubTypes.Type(value = BlockquoteBlock.class, name = "blockquote"),
    @JsonSubTypes.Type(value = EngineQuoteBlock.class, name = "engineQuote"),
    @JsonSubTypes.Type(value = HrBlock.class, name = "hr"),
    @JsonSubTypes.Type(value = CodeBlock.class, name = "code"),
    @JsonSubTypes.Type(value = MathBlock.class, name = "math"),
    @JsonSubTypes.Type(value = FigureBlock.class, name = "figure"),
    @JsonSubTypes.Type(value = CalloutBlock.class, name = "callout"),
    @JsonSubTypes.Type(value = KpiGridBlock.class, name = "kpiGrid"),
    @JsonSubTypes.Type(value = WidgetBlock.class, name = "widget"),
    @JsonSubTypes.Type(value = TocBlock.class, name = "toc")
})
public sealed interface Block permits
        HeadingBlock, ParagraphBlock, ListBlock, TableBlock,
        SwotTableBlock, PestTableBlock, BlockquoteBlock, EngineQuoteBlock,
        HrBlock, CodeBlock, MathBlock, FigureBlock,
        CalloutBlock, KpiGridBlock, WidgetBlock, TocBlock {

    /** 返回与 JSON "type" 字段一致的类型标识符 */
    String type();
}
```

#### 7.2.1 HeadingBlock — 标题块

对应原版 `_render_heading`，支持 1-6 级标题、锚点定位和副标题：

```java
package com.bettafish.report.ir;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

/**
 * 标题块 —— 对应原版 heading block。
 *
 * 渲染器会将 level 1-2 统一渲染为 h2（章节标题），level 3 渲染为 h3，
 * level 4-6 保持原级。anchor 用于目录跳转和交叉引用。
 *
 * @param level    标题级别 1-6
 * @param text     标题文本（纯文本，不含 Markdown）
 * @param anchor   锚点 ID，用于 TOC 跳转，如 "ch1-overview"
 * @param numbering 可选，中文编号如 "一、"、"1.1"
 * @param subtitle 可选，副标题文本
 */
public record HeadingBlock(
        int level,
        String text,
        String anchor,
        @Nullable String numbering,
        @Nullable String subtitle
) implements Block {

    @Override
    public String type() { return "heading"; }

    /** 便捷构造：仅标题级别、文本和锚点 */
    public HeadingBlock(int level, String text, String anchor) {
        this(level, text, anchor, null, null);
    }
}
```

#### 7.2.2 ParagraphBlock — 段落块

段落是最常用的 Block 类型，内部由一组 `InlineRun` 组成，每个 run 携带文本和零到多个 `InlineMark` 样式标记：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * 段落块 —— 对应原版 paragraph block。
 *
 * 段落内部不直接存储纯文本，而是通过 InlineRun 列表实现富文本混排：
 * 粗体、斜体、链接、高亮、代码、数学公式等均通过 marks 叠加。
 *
 * @param inlines 内联文本运行列表，不可为空
 * @param align   可选对齐方式：left / center / right / justify
 */
public record ParagraphBlock(
        List<InlineRun> inlines,
        @Nullable String align
) implements Block {

    @Override
    public String type() { return "paragraph"; }

    /** 便捷构造：纯文本段落 */
    public static ParagraphBlock ofPlainText(String text) {
        return new ParagraphBlock(
                List.of(new InlineRun(text, List.of())),
                null
        );
    }
}
```

#### 7.2.3 ListBlock — 列表块

支持有序列表、无序列表和任务列表，列表项内部可嵌套任意 Block：

```java
package com.bettafish.report.ir;

import java.util.List;

/**
 * 列表块 —— 对应原版 list block。
 *
 * 每个 item 是一个 Block 列表，允许嵌套段落、子列表、代码块等。
 * listType 决定渲染时使用 ol/ul/task-list。
 *
 * @param listType 列表类型："ordered" | "bullet" | "task"
 * @param items    列表项，每项为 List&lt;Block&gt;（支持嵌套）
 */
public record ListBlock(
        String listType,
        List<List<Block>> items
) implements Block {

    @Override
    public String type() { return "list"; }

    /** 便捷判断：是否有序列表 */
    public boolean isOrdered() {
        return "ordered".equals(listType);
    }
}
```

#### 7.2.4 TableBlock — 通用表格块

表格是报告中的高频组件，支持表头、合并单元格、列对齐和行高亮：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * 通用表格块 —— 对应原版 table block。
 *
 * 单元格内部通过嵌套 Block 列表实现富文本内容（段落、列表等）。
 * 支持 rowspan/colspan 合并、列对齐、行级高亮。
 *
 * @param caption       可选，表格标题
 * @param rows          行列表，每行包含一组 TableCell
 * @param columnAligns  可选，每列对齐方式：left / center / right
 * @param highlights    可选，需要高亮的行索引列表
 */
public record TableBlock(
        @Nullable String caption,
        List<TableRow> rows,
        @Nullable List<String> columnAligns,
        @Nullable List<Integer> highlights
) implements Block {

    @Override
    public String type() { return "table"; }
}

/** 表格行 */
public record TableRow(List<TableCell> cells) {}

/** 表格单元格 */
public record TableCell(
        List<Block> blocks,
        boolean isHeader,
        @Nullable Integer rowspan,
        @Nullable Integer colspan,
        @Nullable String align
) {
    /** 便捷构造：纯文本单元格 */
    public static TableCell ofText(String text, boolean isHeader) {
        return new TableCell(
                List.of(ParagraphBlock.ofPlainText(text)),
                isHeader, null, null, null
        );
    }
}
```

#### 7.2.5 SwotTableBlock — SWOT 分析表

SWOT 分析是舆情报告的核心组件之一。原版渲染器会生成两种布局：HTML 卡片布局和 PDF 表格布局。每个维度（优势/劣势/机会/威胁）包含多个条目，每条目带有影响等级评定：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * SWOT 分析四象限表 —— 对应原版 swotTable block。
 *
 * 约束（来自 SYSTEM_PROMPT_CHAPTER_JSON 第 6 条）：
 * - 仅当 constraints.allowSwot === true 时才允许生成
 * - 全报告最多出现在 1 个章节中
 * - impact 字段仅允许以下评级值："低"/"中低"/"中"/"中高"/"高"/"极高"
 *
 * @param title         可选，分析标题，默认 "SWOT 分析"
 * @param summary       可选，分析概述
 * @param strengths     优势条目列表
 * @param weaknesses    劣势条目列表
 * @param opportunities 机会条目列表
 * @param threats       威胁条目列表
 */
public record SwotTableBlock(
        @Nullable String title,
        @Nullable String summary,
        @Nullable List<SwotItem> strengths,
        @Nullable List<SwotItem> weaknesses,
        @Nullable List<SwotItem> opportunities,
        @Nullable List<SwotItem> threats
) implements Block {

    @Override
    public String type() { return "swotTable"; }
}

/**
 * SWOT 单条分析条目。
 *
 * @param text   条目描述文本
 * @param impact 影响等级，限定值："低"/"中低"/"中"/"中高"/"高"/"极高"
 */
public record SwotItem(
        String text,
        @Nullable String impact
) {
    /** 允许的 impact 值集合 */
    public static final List<String> ALLOWED_IMPACTS =
            List.of("低", "中低", "中", "中高", "高", "极高");

    public boolean hasValidImpact() {
        return impact == null || ALLOWED_IMPACTS.contains(impact);
    }
}
```

#### 7.2.6 PestTableBlock — PEST 分析表

PEST 分析从政治（Political）、经济（Economic）、社会（Social）、技术（Technological）四个维度分析宏观环境：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * PEST 宏观环境分析表 —— 对应原版 pestTable block。
 *
 * 约束（来自 SYSTEM_PROMPT_CHAPTER_JSON 第 7 条）：
 * - 仅当 constraints.allowPest === true 时才允许生成
 * - 全报告最多出现在 1 个章节中
 * - trend 字段仅允许以下值："正面利好"/"负面影响"/"中性"/"不确定"/"持续观察"
 * - 不得与 SWOT 出现在同一章节
 *
 * @param title          可选，分析标题，默认 "PEST 分析"
 * @param summary        可选，分析概述
 * @param political      政治因素条目列表
 * @param economic       经济因素条目列表
 * @param social         社会因素条目列表
 * @param technological  技术因素条目列表
 */
public record PestTableBlock(
        @Nullable String title,
        @Nullable String summary,
        @Nullable List<PestItem> political,
        @Nullable List<PestItem> economic,
        @Nullable List<PestItem> social,
        @Nullable List<PestItem> technological
) implements Block {

    @Override
    public String type() { return "pestTable"; }
}

/**
 * PEST 单条分析条目。
 *
 * @param factor      因素名称
 * @param description 详细描述
 * @param trend       趋势评估，限定值："正面利好"/"负面影响"/"中性"/"不确定"/"持续观察"
 * @param impact      影响程度描述
 */
public record PestItem(
        String factor,
        @Nullable String description,
        @Nullable String trend,
        @Nullable String impact
) {
    public static final List<String> ALLOWED_TRENDS =
            List.of("正面利好", "负面影响", "中性", "不确定", "持续观察");

    public boolean hasValidTrend() {
        return trend == null || ALLOWED_TRENDS.contains(trend);
    }
}
```

#### 7.2.7 BlockquoteBlock — 引用块

通用引用块，内部可嵌套任意 Block（段落、列表等）：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * 引用块 —— 对应原版 blockquote block。
 *
 * 渲染为 HTML &lt;blockquote&gt;，内部支持嵌套 block。
 *
 * @param blocks      引用内部的 Block 列表
 * @param attribution 可选，引用来源/作者
 */
public record BlockquoteBlock(
        List<Block> blocks,
        @Nullable String attribution
) implements Block {

    @Override
    public String type() { return "blockquote"; }
}
```

#### 7.2.8 EngineQuoteBlock — 引擎引用块

这是 BettaFish 的特色 Block 类型，用于标注内容来自哪个分析引擎（Insight / Media / Query）。原版有严格的 `ENGINE_AGENT_TITLES` 映射限制：

```java
package com.bettafish.report.ir;

import java.util.List;
import java.util.Map;

/**
 * 引擎引用块 —— 对应原版 engineQuote block。
 *
 * 用于在报告中标注某段分析来自哪个 Agent 引擎。
 * 渲染器为不同引擎分配独立配色和图标。
 *
 * 约束（来自 SYSTEM_PROMPT_CHAPTER_JSON 第 9 条）：
 * - engine 仅允许 "insight" / "media" / "query"
 * - title 必须使用 ENGINE_AGENT_TITLES 中的固定标题
 * - 内部 blocks 仅允许 paragraph 类型，不可嵌套表格/图表/引用
 *
 * @param engine 引擎标识："insight" | "media" | "query"
 * @param title  引擎固定标题（由 ENGINE_AGENT_TITLES 决定）
 * @param blocks 引用内部的 Block 列表（仅限 paragraph）
 */
public record EngineQuoteBlock(
        String engine,
        String title,
        List<Block> blocks
) implements Block {

    /** 引擎标识 → 固定显示标题的映射（对应原版 schema.py 的 ENGINE_AGENT_TITLES） */
    public static final Map<String, String> ENGINE_AGENT_TITLES = Map.of(
            "insight", "深度洞察引擎 · 数据库深度挖掘与情感分析",
            "media",   "多媒体分析引擎 · 跨平台多模态内容解读",
            "query",   "精准检索引擎 · 实时信息追踪与事实核查"
    );

    /** 允许的 engine 值集合 */
    public static final List<String> ALLOWED_ENGINES =
            List.of("insight", "media", "query");

    @Override
    public String type() { return "engineQuote"; }

    /** 根据 engine 获取正确的标题，非法值回退到 insight */
    public String resolvedTitle() {
        return ENGINE_AGENT_TITLES.getOrDefault(
                engine, ENGINE_AGENT_TITLES.get("insight"));
    }
}
```

#### 7.2.9 HrBlock — 水平分隔线

最简单的 Block 类型，无任何字段：

```java
package com.bettafish.report.ir;

/**
 * 水平分隔线 —— 对应原版 hr block。
 * 渲染为 HTML &lt;hr /&gt;，无附加属性。
 */
public record HrBlock() implements Block {

    @Override
    public String type() { return "hr"; }
}
```

#### 7.2.10 CodeBlock — 代码块

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;

/**
 * 代码块 —— 对应原版 code block。
 *
 * 渲染为 &lt;pre&gt;&lt;code&gt; 结构，附带语言标识供语法高亮使用。
 *
 * @param language 编程语言标识，如 "java"、"python"、"sql"
 * @param code     代码内容（原始文本，渲染时 HTML 转义）
 */
public record CodeBlock(
        @Nullable String language,
        String code
) implements Block {

    @Override
    public String type() { return "code"; }
}
```

#### 7.2.11 MathBlock — 数学公式块

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;

/**
 * 数学公式块（display 模式） —— 对应原版 math block。
 *
 * 渲染为 MathJax display 公式：$$ latex $$。
 * 行内数学公式通过 InlineMark.MATH 实现，不使用此 Block。
 *
 * @param latex  LaTeX 公式字符串
 * @param mathId 可选，公式唯一 ID，用于交叉引用
 */
public record MathBlock(
        String latex,
        @Nullable String mathId
) implements Block {

    @Override
    public String type() { return "math"; }
}
```

#### 7.2.12 FigureBlock — 图片块

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;

/**
 * 图片/图示块 —— 对应原版 figure block。
 *
 * 注意（来自 SYSTEM_PROMPT_CHAPTER_JSON 第 11 条）：
 * 报告中不允许外部图片或 AI 生成的图片链接。
 * 渲染器会将 figure 替换为友好的占位提示。
 *
 * @param src     图片 URL
 * @param alt     替代文本
 * @param caption 可选，图片说明
 * @param width   可选，显示宽度，如 "80%" 或 "400px"
 */
public record FigureBlock(
        String src,
        @Nullable String alt,
        @Nullable String caption,
        @Nullable String width
) implements Block {

    @Override
    public String type() { return "figure"; }
}
```

#### 7.2.13 CalloutBlock — 提示框块

提示框用于高亮重要信息，支持四种语气（info / warning / success / error），内部可嵌套有限的 Block 类型：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * 高亮提示框 —— 对应原版 callout block。
 *
 * 渲染器限制 callout 内部仅允许以下 block 类型：
 * paragraph, list, table, blockquote, code, math, figure,
 * kpiGrid, swotTable, pestTable, engineQuote。
 * 不允许的类型（如 widget、heading）会被自动剥离到外层。
 *
 * @param tone   提示类型："info" | "warning" | "success" | "error"
 * @param title  可选，提示框标题
 * @param blocks 内部 Block 列表
 */
public record CalloutBlock(
        String tone,
        @Nullable String title,
        List<Block> blocks
) implements Block {

    /** callout 内部允许的 block 类型集合 */
    public static final Set<String> ALLOWED_INNER_TYPES = Set.of(
            "paragraph", "list", "table", "blockquote", "code", "math",
            "figure", "kpiGrid", "swotTable", "pestTable", "engineQuote"
    );

    @Override
    public String type() { return "callout"; }
}
```

#### 7.2.14 KpiGridBlock — KPI 指标网格

用于在报告顶部或章节中展示关键指标卡片，支持数值、单位、变化量和趋势色调：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * KPI 指标网格 —— 对应原版 kpiGrid block。
 *
 * 渲染为一行卡片，每张卡片展示一个关键指标。
 * 常用于报告 hero 区域或章节摘要。
 *
 * @param items KPI 条目列表
 * @param cols  可选，每行显示列数（响应式自动调整）
 */
public record KpiGridBlock(
        List<KpiItem> items,
        @Nullable Integer cols
) implements Block {

    @Override
    public String type() { return "kpiGrid"; }
}

/**
 * 单个 KPI 指标条目。
 *
 * @param label     指标名称，如 "舆情热度"
 * @param value     指标值，如 "89.3"
 * @param unit      可选，单位，如 "万次"、"%"
 * @param delta     可选，变化量，如 "+12.5%"、"-3.2"
 * @param deltaTone 可选，变化色调："positive" | "negative" | "neutral"
 */
public record KpiItem(
        String label,
        String value,
        @Nullable String unit,
        @Nullable String delta,
        @Nullable String deltaTone
) {}
```

#### 7.2.15 WidgetBlock — 交互组件块

Widget 是 IR 中最复杂的 Block 类型，用于承载 Chart.js 图表、词云等交互式可视化组件。渲染器会对 Chart.js 配置进行校验和修复（本地 + LLM 兜底）：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * 交互式组件块 —— 对应原版 widget block。
 *
 * widgetType 格式示例：
 * - "chart.js/bar"     → Chart.js 柱状图
 * - "chart.js/line"    → Chart.js 折线图
 * - "chart.js/pie"     → Chart.js 饼图
 * - "chart.js/doughnut"→ Chart.js 环形图
 * - "chart.js/radar"   → Chart.js 雷达图
 * - "wordcloud"        → 词云组件
 * - "timeline"         → 时间线组件
 *
 * 渲染器处理流程：
 * 1. ChartValidator 校验 data 结构（labels/datasets 必须存在且合法）
 * 2. 校验失败 → ChartRepairer 本地修复 → 仍失败 → LLM API 修复
 * 3. 修复仍失败 → 输出错误占位符而非崩溃
 *
 * @param widgetType  组件类型标识
 * @param widgetId    组件唯一 ID，用于 canvas 绑定
 * @param title       可选，图表标题
 * @param data        图表数据：{labels: [...], datasets: [...]}
 * @param props       可选，Chart.js options 透传
 * @param dataRef     可选，外部数据引用
 */
public record WidgetBlock(
        String widgetType,
        @Nullable String widgetId,
        @Nullable String title,
        @Nullable Map<String, Object> data,
        @Nullable Map<String, Object> props,
        @Nullable String dataRef
) implements Block {

    @Override
    public String type() { return "widget"; }

    /** 判断是否为 Chart.js 类型 */
    public boolean isChart() {
        return widgetType != null && widgetType.startsWith("chart.js");
    }

    /** 判断是否为词云类型 */
    public boolean isWordcloud() {
        return widgetType != null && widgetType.toLowerCase().contains("wordcloud");
    }

    /** 提取 Chart.js 图表子类型，如 "bar"、"line" */
    public String chartSubType() {
        if (!isChart() || !widgetType.contains("/")) return "bar";
        return widgetType.substring(widgetType.indexOf('/') + 1);
    }
}
```

#### 7.2.16 TocBlock — 目录块

目录块作为一个占位符嵌入 IR，渲染器在最终输出时根据所有章节标题动态生成目录内容：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;

/**
 * 目录占位块 —— 对应原版 toc block。
 *
 * 嵌入 IR 后，渲染器会扫描所有 HeadingBlock 生成目录。
 * maxDepth 控制目录展示的最大标题级别。
 *
 * @param maxDepth 可选，目录最大深度，默认 3（h1-h3）
 */
public record TocBlock(
        @Nullable Integer maxDepth
) implements Block {

    @Override
    public String type() { return "toc"; }

    public int effectiveMaxDepth() {
        return maxDepth != null ? maxDepth : 3;
    }
}
```

#### 7.2.17 InlineRun 与 InlineMark（12 种内联标记）

段落内部的富文本通过 `InlineRun` 承载，每个 run 包含一段文本和一组叠加的样式标记：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * 内联文本运行 —— 对应原版 inline run。
 *
 * 一个段落由多个 InlineRun 顺序拼接而成。
 * 每个 run 的文本共享同一组 marks（样式标记）。
 *
 * @param text  文本内容
 * @param marks 样式标记列表，可叠加（如同时粗体+链接）
 */
public record InlineRun(
        String text,
        List<InlineMark> marks
) {
    /** 便捷构造：无标记的纯文本 */
    public static InlineRun plain(String text) {
        return new InlineRun(text, List.of());
    }
}

/**
 * 内联标记 —— 对应原版渲染器 _render_inline 中处理的 mark 类型。
 *
 * 支持 12 种标记类型，可任意叠加组合。
 *
 * @param type  标记类型（见 InlineMarkType 枚举）
 * @param value 标记值，含义因 type 而异
 * @param href  链接地址（仅 type=link 时使用）
 * @param title 链接标题（仅 type=link 时使用）
 */
public record InlineMark(
        InlineMarkType type,
        @Nullable String value,
        @Nullable String href,
        @Nullable String title
) {
    /** 便捷构造：简单标记（如 bold、italic） */
    public static InlineMark of(InlineMarkType type) {
        return new InlineMark(type, null, null, null);
    }

    /** 便捷构造：链接标记 */
    public static InlineMark link(String href, String title) {
        return new InlineMark(InlineMarkType.LINK, null, href, title);
    }

    /** 便捷构造：高亮标记（带颜色） */
    public static InlineMark highlight(String color) {
        return new InlineMark(InlineMarkType.HIGHLIGHT, color, null, null);
    }
}

/**
 * 内联标记类型枚举 —— 12 种。
 *
 * 对应原版渲染器中 mark_type 的完整集合：
 * bold, italic, underline, strike(through), code, link,
 * highlight, superscript(sup), subscript(sub), color, math, footnote
 */
public enum InlineMarkType {
    /** 粗体 → &lt;strong&gt; */
    BOLD("bold"),
    /** 斜体 → &lt;em&gt; */
    ITALIC("italic"),
    /** 下划线 → text-decoration: underline */
    UNDERLINE("underline"),
    /** 删除线 → text-decoration: line-through */
    STRIKETHROUGH("strike"),
    /** 行内代码 → &lt;code&gt; */
    CODE("code"),
    /** 超链接 → &lt;a href&gt; */
    LINK("link"),
    /** 高亮/标记 → &lt;mark&gt; */
    HIGHLIGHT("highlight"),
    /** 上标 → &lt;sup&gt; */
    SUP("superscript"),
    /** 下标 → &lt;sub&gt; */
    SUB("subscript"),
    /** 行内数学公式 → \( latex \) */
    MATH("math"),
    /** Emoji 表情 → 直接输出 Unicode */
    EMOJI("emoji"),
    /** 脚注引用 → 上标数字链接 */
    FOOTNOTE("footnote");

    private final String jsonValue;

    InlineMarkType(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    /** 返回与 JSON 中 mark.type 一致的字符串 */
    public String jsonValue() { return jsonValue; }

    /** 从 JSON 字符串解析 */
    public static InlineMarkType fromJson(String value) {
        for (InlineMarkType t : values()) {
            if (t.jsonValue.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown InlineMark type: " + value);
    }
}
```

---

### 7.3 ChapterIR 与 DocumentIR

#### 7.3.1 ChapterIR — 章节中间表示

每次 LLM 调用生成一个章节的 IR，对应原版 `CHAPTER_JSON_SCHEMA`。ChapterIR 是 IR 体系的核心交换单元：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 章节 IR —— 对应原版 CHAPTER_JSON_SCHEMA。
 *
 * 由 LLM 逐章节生成，经 IRValidator 校验后交给 DocumentStitcher 装订。
 * 每个章节独立生成、独立校验，支持失败重试和跨引擎修复。
 *
 * @param chapterId     章节唯一标识，如 "ch1-overview"
 * @param anchor        锚点 ID，用于 TOC 跳转
 * @param chapterTitle  章节标题
 * @param order         章节排序序号（从 1 开始）
 * @param blocks        章节内 Block 列表
 * @param wordCount     章节字数统计（由生成器或校验器计算）
 * @param engineSources 引用的引擎来源列表，如 ["insight", "media"]
 * @param summary       可选，章节摘要
 * @param metadata      可选，扩展元数据（调试信息、生成参数等）
 */
public record ChapterIR(
        String chapterId,
        String anchor,
        String chapterTitle,
        int order,
        List<Block> blocks,
        int wordCount,
        @Nullable List<String> engineSources,
        @Nullable String summary,
        @Nullable Map<String, Object> metadata
) {

    /** 便捷构造：最小必需字段 */
    public static ChapterIR of(String chapterId, String title, int order, List<Block> blocks) {
        int wc = estimateWordCount(blocks);
        return new ChapterIR(chapterId, chapterId, title, order, blocks, wc,
                List.of(), null, Map.of());
    }

    /** 递归估算 Block 列表的总字数 */
    public static int estimateWordCount(List<Block> blocks) {
        int count = 0;
        for (Block block : blocks) {
            count += switch (block) {
                case HeadingBlock h -> h.text().length();
                case ParagraphBlock p -> p.inlines().stream()
                        .mapToInt(r -> r.text().length()).sum();
                case ListBlock l -> l.items().stream()
                        .mapToInt(items -> estimateWordCount(items)).sum();
                case TableBlock t -> t.rows().stream()
                        .mapToInt(row -> row.cells().stream()
                                .mapToInt(cell -> estimateWordCount(cell.blocks())).sum())
                        .sum();
                case BlockquoteBlock bq -> estimateWordCount(bq.blocks());
                case EngineQuoteBlock eq -> estimateWordCount(eq.blocks());
                case CalloutBlock c -> estimateWordCount(c.blocks());
                case CodeBlock cb -> cb.code().length();
                case MathBlock m -> m.latex().length();
                case SwotTableBlock s -> estimateSwotWords(s);
                case PestTableBlock p -> estimatePestWords(p);
                default -> 0;
            };
        }
        return count;
    }

    private static int estimateSwotWords(SwotTableBlock s) {
        int count = 0;
        if (s.strengths() != null) count += s.strengths().stream()
                .mapToInt(i -> i.text().length()).sum();
        if (s.weaknesses() != null) count += s.weaknesses().stream()
                .mapToInt(i -> i.text().length()).sum();
        if (s.opportunities() != null) count += s.opportunities().stream()
                .mapToInt(i -> i.text().length()).sum();
        if (s.threats() != null) count += s.threats().stream()
                .mapToInt(i -> i.text().length()).sum();
        return count;
    }

    private static int estimatePestWords(PestTableBlock p) {
        int count = 0;
        if (p.political() != null) count += p.political().stream()
                .mapToInt(i -> (i.factor() + " " + (i.description() != null ? i.description() : "")).length())
                .sum();
        if (p.economic() != null) count += p.economic().stream()
                .mapToInt(i -> (i.factor() + " " + (i.description() != null ? i.description() : "")).length())
                .sum();
        if (p.social() != null) count += p.social().stream()
                .mapToInt(i -> (i.factor() + " " + (i.description() != null ? i.description() : "")).length())
                .sum();
        if (p.technological() != null) count += p.technological().stream()
                .mapToInt(i -> (i.factor() + " " + (i.description() != null ? i.description() : "")).length())
                .sum();
        return count;
    }
}
```

#### 7.3.2 DocumentIR — 文档中间表示

DocumentIR 是最终渲染器的输入，由 DocumentStitcher 将所有 ChapterIR 装订而成：

```java
package com.bettafish.report.ir;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 完整文档 IR —— 渲染器的最终输入。
 *
 * 由 DocumentStitcher 将所有 ChapterIR 装订而成。
 * 包含报告元数据（标题、主题、hero 配置）和有序的章节列表。
 *
 * @param title       报告主标题
 * @param subtitle    可选，副标题
 * @param tagline     可选，标语/摘要行
 * @param heroConfig  可选，hero 区域配置（摘要、KPI、亮点、操作按钮）
 * @param tocConfig   可选，目录配置
 * @param themeTokens 可选，主题变量（字体、间距、配色等）
 * @param chapters    有序章节列表
 * @param metadata    扩展元数据
 */
public record DocumentIR(
        String title,
        @Nullable String subtitle,
        @Nullable String tagline,
        @Nullable HeroConfig heroConfig,
        @Nullable TocConfig tocConfig,
        @Nullable Map<String, String> themeTokens,
        List<ChapterIR> chapters,
        Map<String, Object> metadata
) {

    /** 获取报告总字数 */
    public int totalWordCount() {
        return chapters.stream().mapToInt(ChapterIR::wordCount).sum();
    }
}

/**
 * Hero 区域配置 —— 对应原版 document_layout 中的 hero 字段。
 *
 * @param summary    报告概述
 * @param highlights 要点列表
 * @param kpis       KPI 指标列表
 * @param actions    操作按钮列表
 */
public record HeroConfig(
        @Nullable String summary,
        @Nullable List<String> highlights,
        @Nullable List<KpiItem> kpis,
        @Nullable List<HeroAction> actions
) {}

public record HeroAction(
        String label,
        String anchor
) {}

/**
 * 目录配置 —— 对应原版 tocPlan。
 *
 * @param title    目录标题，如 "目录"
 * @param maxDepth 最大显示深度
 * @param entries  目录条目列表
 */
public record TocConfig(
        String title,
        int maxDepth,
        List<TocEntry> entries
) {}

public record TocEntry(
        String chapterId,
        String anchor,
        String display,
        @Nullable String description,
        int level
) {}
```

---

### 7.4 IRValidator — IR 校验器

IRValidator 利用 Java 模式匹配（switch 表达式对 sealed 类型穷举）实现结构化校验。每条校验错误都携带完整的路径信息（如 `chapters[2].blocks[5].items[0].impact`），便于 LLM 修复器精确定位问题。

```java
package com.bettafish.report.ir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.IntStream;

/**
 * IR 校验器 —— 对应原版 ReportEngine/ir/validator.py。
 *
 * 基于 Java sealed interface 的模式匹配实现编译时穷举校验。
 * 每条错误携带 JSON-Path 风格的路径，便于 LLM 修复器精确定位。
 */
@Component
public class IRValidator {

    private static final Logger log = LoggerFactory.getLogger(IRValidator.class);

    /** 章节最小字数阈值（低于此值视为"内容稀疏"警告） */
    private static final int SPARSE_CHAPTER_THRESHOLD = 200;

    /** 校验结果 */
    public record ValidationResult(
            boolean valid,
            List<ValidationError> errors,
            List<ValidationWarning> warnings
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of());
        }
    }

    public record ValidationError(String path, String message) {
        @Override
        public String toString() {
            return "[%s] %s".formatted(path, message);
        }
    }

    public record ValidationWarning(String path, String message) {}

    // ========== 文档级校验 ==========

    /**
     * 校验完整文档 IR。
     */
    public ValidationResult validateDocument(DocumentIR doc) {
        var errors = new ArrayList<ValidationError>();
        var warnings = new ArrayList<ValidationWarning>();

        if (doc.title() == null || doc.title().isBlank()) {
            errors.add(new ValidationError("title", "文档标题不能为空"));
        }
        if (doc.chapters() == null || doc.chapters().isEmpty()) {
            errors.add(new ValidationError("chapters", "文档必须包含至少一个章节"));
        } else {
            // 校验章节排序
            validateChapterOrdering(doc.chapters(), errors, warnings);

            // 校验锚点唯一性
            validateAnchorUniqueness(doc.chapters(), errors);

            // 全局约束：SWOT / PEST 各最多出现一个章节
            validateSwotPestConstraints(doc.chapters(), errors);

            // 逐章节校验
            for (int i = 0; i < doc.chapters().size(); i++) {
                var chapterErrors = validateChapter(doc.chapters().get(i),
                        "chapters[%d]".formatted(i));
                errors.addAll(chapterErrors.errors());
                warnings.addAll(chapterErrors.warnings());
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    // ========== 章节级校验 ==========

    /**
     * 校验单个章节 IR。
     */
    public ValidationResult validateChapter(ChapterIR chapter) {
        return validateChapter(chapter, "chapter");
    }

    private ValidationResult validateChapter(ChapterIR chapter, String basePath) {
        var errors = new ArrayList<ValidationError>();
        var warnings = new ArrayList<ValidationWarning>();

        // 必需字段检查
        if (chapter.chapterId() == null || chapter.chapterId().isBlank()) {
            errors.add(new ValidationError(basePath + ".chapterId",
                    "章节 ID 不能为空"));
        }
        if (chapter.chapterTitle() == null || chapter.chapterTitle().isBlank()) {
            errors.add(new ValidationError(basePath + ".chapterTitle",
                    "章节标题不能为空"));
        }
        if (chapter.anchor() == null || chapter.anchor().isBlank()) {
            errors.add(new ValidationError(basePath + ".anchor",
                    "章节锚点不能为空"));
        }

        // Block 列表校验
        if (chapter.blocks() == null || chapter.blocks().isEmpty()) {
            errors.add(new ValidationError(basePath + ".blocks",
                    "章节必须包含至少一个 Block"));
        } else {
            for (int i = 0; i < chapter.blocks().size(); i++) {
                String blockPath = "%s.blocks[%d]".formatted(basePath, i);
                errors.addAll(validateBlock(chapter.blocks().get(i), blockPath));
            }
        }

        // 内容稀疏警告
        if (chapter.wordCount() < SPARSE_CHAPTER_THRESHOLD) {
            warnings.add(new ValidationWarning(basePath + ".wordCount",
                    "章节字数 (%d) 低于阈值 (%d)，内容可能过于稀疏"
                            .formatted(chapter.wordCount(), SPARSE_CHAPTER_THRESHOLD)));
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    // ========== Block 级校验（核心：sealed 类型穷举） ==========

    /**
     * 校验单个 Block —— 利用模式匹配穷举所有 16 种类型。
     * 编译器保证不会遗漏任何类型。
     */
    private List<ValidationError> validateBlock(Block block, String path) {
        return switch (block) {

            case HeadingBlock h -> validateHeading(h, path);

            case ParagraphBlock p -> validateParagraph(p, path);

            case ListBlock l -> validateList(l, path);

            case TableBlock t -> validateTable(t, path);

            case SwotTableBlock s -> validateSwotTable(s, path);

            case PestTableBlock p -> validatePestTable(p, path);

            case BlockquoteBlock bq -> validateBlockquote(bq, path);

            case EngineQuoteBlock eq -> validateEngineQuote(eq, path);

            case HrBlock hr -> List.of(); // 无需校验

            case CodeBlock cb -> validateCode(cb, path);

            case MathBlock m -> validateMath(m, path);

            case FigureBlock f -> validateFigure(f, path);

            case CalloutBlock c -> validateCallout(c, path);

            case KpiGridBlock k -> validateKpiGrid(k, path);

            case WidgetBlock w -> validateWidget(w, path);

            case TocBlock toc -> List.of(); // 目录块无需校验
        };
    }

    // ========== 各类型的具体校验方法 ==========

    private List<ValidationError> validateHeading(HeadingBlock h, String path) {
        var errors = new ArrayList<ValidationError>();
        if (h.level() < 1 || h.level() > 6) {
            errors.add(new ValidationError(path + ".level",
                    "标题级别必须在 1-6 之间，当前: " + h.level()));
        }
        if (h.text() == null || h.text().isBlank()) {
            errors.add(new ValidationError(path + ".text",
                    "标题文本不能为空"));
        }
        if (h.anchor() == null || h.anchor().isBlank()) {
            errors.add(new ValidationError(path + ".anchor",
                    "标题锚点不能为空"));
        }
        // 检查锚点格式：仅允许字母、数字、连字符
        if (h.anchor() != null && !h.anchor().matches("[a-zA-Z0-9\\-_]+")) {
            errors.add(new ValidationError(path + ".anchor",
                    "锚点格式非法，仅允许字母、数字、连字符和下划线: " + h.anchor()));
        }
        return errors;
    }

    private List<ValidationError> validateParagraph(ParagraphBlock p, String path) {
        var errors = new ArrayList<ValidationError>();
        if (p.inlines() == null || p.inlines().isEmpty()) {
            errors.add(new ValidationError(path + ".inlines",
                    "段落必须包含至少一个 InlineRun"));
        } else {
            for (int i = 0; i < p.inlines().size(); i++) {
                InlineRun run = p.inlines().get(i);
                if (run.text() == null) {
                    errors.add(new ValidationError(
                            "%s.inlines[%d].text".formatted(path, i),
                            "InlineRun 的 text 不能为 null"));
                }
                // 校验 marks
                if (run.marks() != null) {
                    for (int j = 0; j < run.marks().size(); j++) {
                        errors.addAll(validateInlineMark(run.marks().get(j),
                                "%s.inlines[%d].marks[%d]".formatted(path, i, j)));
                    }
                }
            }
        }
        return errors;
    }

    private List<ValidationError> validateInlineMark(InlineMark mark, String path) {
        var errors = new ArrayList<ValidationError>();
        if (mark.type() == null) {
            errors.add(new ValidationError(path + ".type",
                    "InlineMark 类型不能为 null"));
        }
        // 链接类型必须有 href
        if (mark.type() == InlineMarkType.LINK) {
            if (mark.href() == null || mark.href().isBlank()) {
                errors.add(new ValidationError(path + ".href",
                        "link 标记必须提供 href"));
            }
        }
        // 数学公式类型必须有 value
        if (mark.type() == InlineMarkType.MATH) {
            if (mark.value() == null || mark.value().isBlank()) {
                errors.add(new ValidationError(path + ".value",
                        "math 标记必须提供 LaTeX 值"));
            }
        }
        return errors;
    }

    private List<ValidationError> validateList(ListBlock l, String path) {
        var errors = new ArrayList<ValidationError>();
        if (l.listType() == null || l.listType().isBlank()) {
            errors.add(new ValidationError(path + ".listType",
                    "列表类型不能为空"));
        }
        if (l.items() == null || l.items().isEmpty()) {
            errors.add(new ValidationError(path + ".items",
                    "列表必须包含至少一个条目"));
        } else {
            for (int i = 0; i < l.items().size(); i++) {
                List<Block> itemBlocks = l.items().get(i);
                for (int j = 0; j < itemBlocks.size(); j++) {
                    errors.addAll(validateBlock(itemBlocks.get(j),
                            "%s.items[%d][%d]".formatted(path, i, j)));
                }
            }
        }
        return errors;
    }

    private List<ValidationError> validateTable(TableBlock t, String path) {
        var errors = new ArrayList<ValidationError>();
        if (t.rows() == null || t.rows().isEmpty()) {
            errors.add(new ValidationError(path + ".rows",
                    "表格必须包含至少一行"));
        } else {
            for (int i = 0; i < t.rows().size(); i++) {
                TableRow row = t.rows().get(i);
                if (row.cells() == null || row.cells().isEmpty()) {
                    errors.add(new ValidationError(
                            "%s.rows[%d].cells".formatted(path, i),
                            "表格行必须包含至少一个单元格"));
                } else {
                    for (int j = 0; j < row.cells().size(); j++) {
                        TableCell cell = row.cells().get(j);
                        if (cell.blocks() != null) {
                            for (int k = 0; k < cell.blocks().size(); k++) {
                                errors.addAll(validateBlock(cell.blocks().get(k),
                                        "%s.rows[%d].cells[%d].blocks[%d]"
                                                .formatted(path, i, j, k)));
                            }
                        }
                    }
                }
            }
        }
        return errors;
    }

    private List<ValidationError> validateSwotTable(SwotTableBlock s, String path) {
        var errors = new ArrayList<ValidationError>();
        boolean hasAny = (s.strengths() != null && !s.strengths().isEmpty())
                || (s.weaknesses() != null && !s.weaknesses().isEmpty())
                || (s.opportunities() != null && !s.opportunities().isEmpty())
                || (s.threats() != null && !s.threats().isEmpty());
        if (!hasAny) {
            errors.add(new ValidationError(path,
                    "SWOT 分析至少需要填入一个维度的条目"));
        }
        // 校验 impact 值合法性
        validateSwotItems(s.strengths(), path + ".strengths", errors);
        validateSwotItems(s.weaknesses(), path + ".weaknesses", errors);
        validateSwotItems(s.opportunities(), path + ".opportunities", errors);
        validateSwotItems(s.threats(), path + ".threats", errors);
        return errors;
    }

    private void validateSwotItems(List<SwotItem> items, String path,
                                   List<ValidationError> errors) {
        if (items == null) return;
        for (int i = 0; i < items.size(); i++) {
            SwotItem item = items.get(i);
            if (item.text() == null || item.text().isBlank()) {
                errors.add(new ValidationError(
                        "%s[%d].text".formatted(path, i),
                        "SWOT 条目文本不能为空"));
            }
            if (!item.hasValidImpact()) {
                errors.add(new ValidationError(
                        "%s[%d].impact".formatted(path, i),
                        "SWOT 影响等级非法: '%s'，允许值: %s"
                                .formatted(item.impact(),
                                        String.join(", ", SwotItem.ALLOWED_IMPACTS))));
            }
        }
    }

    private List<ValidationError> validatePestTable(PestTableBlock p, String path) {
        var errors = new ArrayList<ValidationError>();
        boolean hasAny = (p.political() != null && !p.political().isEmpty())
                || (p.economic() != null && !p.economic().isEmpty())
                || (p.social() != null && !p.social().isEmpty())
                || (p.technological() != null && !p.technological().isEmpty());
        if (!hasAny) {
            errors.add(new ValidationError(path,
                    "PEST 分析至少需要填入一个维度的条目"));
        }
        validatePestItems(p.political(), path + ".political", errors);
        validatePestItems(p.economic(), path + ".economic", errors);
        validatePestItems(p.social(), path + ".social", errors);
        validatePestItems(p.technological(), path + ".technological", errors);
        return errors;
    }

    private void validatePestItems(List<PestItem> items, String path,
                                   List<ValidationError> errors) {
        if (items == null) return;
        for (int i = 0; i < items.size(); i++) {
            PestItem item = items.get(i);
            if (item.factor() == null || item.factor().isBlank()) {
                errors.add(new ValidationError(
                        "%s[%d].factor".formatted(path, i),
                        "PEST 因素名称不能为空"));
            }
            if (!item.hasValidTrend()) {
                errors.add(new ValidationError(
                        "%s[%d].trend".formatted(path, i),
                        "PEST 趋势评估非法: '%s'，允许值: %s"
                                .formatted(item.trend(),
                                        String.join(", ", PestItem.ALLOWED_TRENDS))));
            }
        }
    }

    private List<ValidationError> validateBlockquote(BlockquoteBlock bq, String path) {
        var errors = new ArrayList<ValidationError>();
        if (bq.blocks() == null || bq.blocks().isEmpty()) {
            errors.add(new ValidationError(path + ".blocks",
                    "引用块内容不能为空"));
        } else {
            for (int i = 0; i < bq.blocks().size(); i++) {
                errors.addAll(validateBlock(bq.blocks().get(i),
                        "%s.blocks[%d]".formatted(path, i)));
            }
        }
        return errors;
    }

    private List<ValidationError> validateEngineQuote(EngineQuoteBlock eq, String path) {
        var errors = new ArrayList<ValidationError>();
        if (!EngineQuoteBlock.ALLOWED_ENGINES.contains(eq.engine())) {
            errors.add(new ValidationError(path + ".engine",
                    "引擎标识非法: '%s'，允许值: %s"
                            .formatted(eq.engine(),
                                    String.join(", ", EngineQuoteBlock.ALLOWED_ENGINES))));
        }
        // 校验 title 是否匹配固定标题
        String expectedTitle = EngineQuoteBlock.ENGINE_AGENT_TITLES.get(eq.engine());
        if (expectedTitle != null && !expectedTitle.equals(eq.title())) {
            errors.add(new ValidationError(path + ".title",
                    "引擎标题必须为固定值: '%s'".formatted(expectedTitle)));
        }
        // 校验内部 blocks 只允许 paragraph
        if (eq.blocks() != null) {
            for (int i = 0; i < eq.blocks().size(); i++) {
                Block inner = eq.blocks().get(i);
                if (!(inner instanceof ParagraphBlock)) {
                    errors.add(new ValidationError(
                            "%s.blocks[%d]".formatted(path, i),
                            "engineQuote 内部仅允许 paragraph，当前: " + inner.type()));
                } else {
                    errors.addAll(validateBlock(inner,
                            "%s.blocks[%d]".formatted(path, i)));
                }
            }
        }
        return errors;
    }

    private List<ValidationError> validateCode(CodeBlock cb, String path) {
        var errors = new ArrayList<ValidationError>();
        if (cb.code() == null || cb.code().isBlank()) {
            errors.add(new ValidationError(path + ".code",
                    "代码内容不能为空"));
        }
        return errors;
    }

    private List<ValidationError> validateMath(MathBlock m, String path) {
        var errors = new ArrayList<ValidationError>();
        if (m.latex() == null || m.latex().isBlank()) {
            errors.add(new ValidationError(path + ".latex",
                    "LaTeX 公式不能为空"));
        }
        return errors;
    }

    private List<ValidationError> validateFigure(FigureBlock f, String path) {
        var errors = new ArrayList<ValidationError>();
        if (f.src() == null || f.src().isBlank()) {
            errors.add(new ValidationError(path + ".src",
                    "图片 src 不能为空"));
        }
        return errors;
    }

    private List<ValidationError> validateCallout(CalloutBlock c, String path) {
        var errors = new ArrayList<ValidationError>();
        String tone = c.tone();
        if (tone == null || !Set.of("info", "warning", "success", "error").contains(tone)) {
            errors.add(new ValidationError(path + ".tone",
                    "callout 类型非法: '%s'，允许值: info, warning, success, error"
                            .formatted(tone)));
        }
        if (c.blocks() != null) {
            for (int i = 0; i < c.blocks().size(); i++) {
                Block inner = c.blocks().get(i);
                // 校验内部 block 类型是否允许
                if (!CalloutBlock.ALLOWED_INNER_TYPES.contains(inner.type())) {
                    errors.add(new ValidationError(
                            "%s.blocks[%d]".formatted(path, i),
                            "callout 内部不允许 '%s' 类型".formatted(inner.type())));
                }
                errors.addAll(validateBlock(inner,
                        "%s.blocks[%d]".formatted(path, i)));
            }
        }
        return errors;
    }

    private List<ValidationError> validateKpiGrid(KpiGridBlock k, String path) {
        var errors = new ArrayList<ValidationError>();
        if (k.items() == null || k.items().isEmpty()) {
            errors.add(new ValidationError(path + ".items",
                    "KPI 网格必须包含至少一个指标"));
        } else {
            for (int i = 0; i < k.items().size(); i++) {
                KpiItem item = k.items().get(i);
                if (item.label() == null || item.label().isBlank()) {
                    errors.add(new ValidationError(
                            "%s.items[%d].label".formatted(path, i),
                            "KPI 指标名称不能为空"));
                }
                if (item.value() == null || item.value().isBlank()) {
                    errors.add(new ValidationError(
                            "%s.items[%d].value".formatted(path, i),
                            "KPI 指标值不能为空"));
                }
            }
        }
        return errors;
    }

    private List<ValidationError> validateWidget(WidgetBlock w, String path) {
        var errors = new ArrayList<ValidationError>();
        if (w.widgetType() == null || w.widgetType().isBlank()) {
            errors.add(new ValidationError(path + ".widgetType",
                    "Widget 类型不能为空"));
        }
        // Chart.js 类型必须有 data
        if (w.isChart()) {
            if (w.data() == null) {
                errors.add(new ValidationError(path + ".data",
                        "Chart.js 组件必须提供 data 字段"));
            } else {
                if (!w.data().containsKey("labels")) {
                    errors.add(new ValidationError(path + ".data.labels",
                            "Chart.js data 必须包含 labels 数组"));
                }
                if (!w.data().containsKey("datasets")) {
                    errors.add(new ValidationError(path + ".data.datasets",
                            "Chart.js data 必须包含 datasets 数组"));
                }
            }
        }
        return errors;
    }

    // ========== 全局约束校验 ==========

    private void validateChapterOrdering(List<ChapterIR> chapters,
                                         List<ValidationError> errors,
                                         List<ValidationWarning> warnings) {
        for (int i = 0; i < chapters.size() - 1; i++) {
            if (chapters.get(i).order() >= chapters.get(i + 1).order()) {
                warnings.add(new ValidationWarning(
                        "chapters[%d].order".formatted(i),
                        "章节排序非递增: chapters[%d].order=%d >= chapters[%d].order=%d"
                                .formatted(i, chapters.get(i).order(),
                                        i + 1, chapters.get(i + 1).order())));
            }
        }
    }

    private void validateAnchorUniqueness(List<ChapterIR> chapters,
                                          List<ValidationError> errors) {
        Set<String> seenAnchors = new HashSet<>();
        for (int i = 0; i < chapters.size(); i++) {
            String anchor = chapters.get(i).anchor();
            if (anchor != null && !seenAnchors.add(anchor)) {
                errors.add(new ValidationError(
                        "chapters[%d].anchor".formatted(i),
                        "锚点重复: '%s'".formatted(anchor)));
            }
        }
    }

    private void validateSwotPestConstraints(List<ChapterIR> chapters,
                                             List<ValidationError> errors) {
        List<Integer> swotChapters = new ArrayList<>();
        List<Integer> pestChapters = new ArrayList<>();

        for (int i = 0; i < chapters.size(); i++) {
            boolean hasSwot = false, hasPest = false;
            for (Block block : chapters.get(i).blocks()) {
                if (block instanceof SwotTableBlock) hasSwot = true;
                if (block instanceof PestTableBlock) hasPest = true;
            }
            if (hasSwot) swotChapters.add(i);
            if (hasPest) pestChapters.add(i);
            // SWOT 和 PEST 不得出现在同一章节
            if (hasSwot && hasPest) {
                errors.add(new ValidationError(
                        "chapters[%d]".formatted(i),
                        "SWOT 和 PEST 分析不得出现在同一章节"));
            }
        }
        // 全报告各最多一个
        if (swotChapters.size() > 1) {
            errors.add(new ValidationError("global.swot",
                    "SWOT 分析最多出现在 1 个章节，当前出现在: " + swotChapters));
        }
        if (pestChapters.size() > 1) {
            errors.add(new ValidationError("global.pest",
                    "PEST 分析最多出现在 1 个章节，当前出现在: " + pestChapters));
        }
    }
}
```

---

### 7.5 DocumentStitcher — 文档装订器

DocumentStitcher 负责将多个独立生成的 ChapterIR 合并为一个完整的 DocumentIR，处理锚点去重、章节排序和目录生成：

```java
package com.bettafish.report.ir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档装订器 —— 对应原版 ReportEngine 中的文档组装逻辑。
 *
 * 职责：
 * 1. 将多个 ChapterIR 按 order 排序合并
 * 2. 处理锚点冲突（自动去重）
 * 3. 从 DocumentLayout 提取元数据
 * 4. 生成目录（TOC）配置
 * 5. 注入 hero 区域和主题变量
 */
@Component
public class DocumentStitcher {

    private static final Logger log = LoggerFactory.getLogger(DocumentStitcher.class);

    private final IRValidator validator;

    public DocumentStitcher(IRValidator validator) {
        this.validator = validator;
    }

    /**
     * 将章节列表装订为完整文档 IR。
     *
     * @param layout   文档布局配置（来自 DocumentLayoutNode 的 LLM 输出）
     * @param chapters 各章节 IR 列表（可能乱序）
     * @return 完整的 DocumentIR
     */
    public DocumentIR stitch(DocumentLayout layout, List<ChapterIR> chapters) {
        log.info("开始装订文档: {} 个章节", chapters.size());

        // Step 1: 按 order 排序
        List<ChapterIR> sorted = chapters.stream()
                .sorted(Comparator.comparingInt(ChapterIR::order))
                .toList();
        log.debug("章节排序完成: {}",
                sorted.stream().map(c -> c.chapterId() + "(" + c.order() + ")")
                        .collect(Collectors.joining(", ")));

        // Step 2: 锚点去重
        List<ChapterIR> deduped = deduplicateAnchors(sorted);

        // Step 3: 收集所有标题，生成 heading label map
        Map<String, HeadingLabelMapping> headingMap = buildHeadingLabelMap(deduped);

        // Step 4: 生成 TOC 配置
        TocConfig tocConfig = buildTocConfig(layout, deduped);

        // Step 5: 组装 HeroConfig
        HeroConfig heroConfig = buildHeroConfig(layout);

        // Step 6: 提取主题变量
        Map<String, String> themeTokens = layout.themeTokens() != null
                ? layout.themeTokens() : Map.of();

        // Step 7: 构建最终 DocumentIR
        DocumentIR doc = new DocumentIR(
                layout.title(),
                layout.subtitle(),
                layout.tagline(),
                heroConfig,
                tocConfig,
                themeTokens,
                deduped,
                Map.of(
                        "totalWordCount", deduped.stream()
                                .mapToInt(ChapterIR::wordCount).sum(),
                        "chapterCount", deduped.size(),
                        "headingMap", headingMap,
                        "generatedAt", System.currentTimeMillis()
                )
        );

        // Step 8: 最终校验
        var result = validator.validateDocument(doc);
        if (!result.valid()) {
            log.warn("文档 IR 校验发现 {} 条错误:", result.errors().size());
            result.errors().forEach(e -> log.warn("  {}", e));
        }
        if (!result.warnings().isEmpty()) {
            log.info("文档 IR 校验发现 {} 条警告:", result.warnings().size());
            result.warnings().forEach(w -> log.info("  [{}] {}", w.path(), w.message()));
        }

        log.info("文档装订完成: 标题='{}', 章节数={}, 总字数={}",
                doc.title(), doc.chapters().size(), doc.totalWordCount());
        return doc;
    }

    /**
     * 锚点去重：当多个 Block 使用相同锚点时，自动追加序号后缀。
     */
    private List<ChapterIR> deduplicateAnchors(List<ChapterIR> chapters) {
        Set<String> usedAnchors = new HashSet<>();
        List<ChapterIR> result = new ArrayList<>();

        for (ChapterIR chapter : chapters) {
            // 章节级锚点去重
            String chapterAnchor = ensureUnique(chapter.anchor(), usedAnchors);

            // Block 级锚点去重
            List<Block> dedupedBlocks = new ArrayList<>();
            for (Block block : chapter.blocks()) {
                if (block instanceof HeadingBlock h) {
                    String newAnchor = ensureUnique(h.anchor(), usedAnchors);
                    if (!newAnchor.equals(h.anchor())) {
                        log.debug("锚点去重: '{}' → '{}'", h.anchor(), newAnchor);
                        dedupedBlocks.add(new HeadingBlock(
                                h.level(), h.text(), newAnchor,
                                h.numbering(), h.subtitle()));
                    } else {
                        dedupedBlocks.add(h);
                    }
                } else {
                    dedupedBlocks.add(block);
                }
            }

            result.add(new ChapterIR(
                    chapter.chapterId(), chapterAnchor, chapter.chapterTitle(),
                    chapter.order(), dedupedBlocks, chapter.wordCount(),
                    chapter.engineSources(), chapter.summary(), chapter.metadata()));
        }

        return result;
    }

    private String ensureUnique(String anchor, Set<String> used) {
        if (anchor == null) anchor = "section";
        String candidate = anchor;
        int suffix = 1;
        while (!used.add(candidate)) {
            candidate = anchor + "-" + suffix++;
        }
        return candidate;
    }

    /**
     * 构建标题编号映射（中文一级编号 + 阿拉伯数字二级编号）。
     */
    private Map<String, HeadingLabelMapping> buildHeadingLabelMap(
            List<ChapterIR> chapters) {
        String[] chineseNumbers = {"一", "二", "三", "四", "五", "六",
                "七", "八", "九", "十", "十一", "十二"};
        Map<String, HeadingLabelMapping> map = new LinkedHashMap<>();
        int primaryIndex = 0;
        int secondaryIndex = 0;

        for (ChapterIR chapter : chapters) {
            for (Block block : chapter.blocks()) {
                if (block instanceof HeadingBlock h) {
                    if (h.level() <= 2) {
                        // 一级标题：中文编号
                        String numbering = primaryIndex < chineseNumbers.length
                                ? chineseNumbers[primaryIndex] + "、"
                                : (primaryIndex + 1) + "、";
                        String display = numbering + h.text();
                        map.put(h.anchor(), new HeadingLabelMapping(
                                display, numbering, h.level()));
                        primaryIndex++;
                        secondaryIndex = 0;
                    } else if (h.level() == 3) {
                        // 二级标题：阿拉伯数字编号
                        secondaryIndex++;
                        String numbering = primaryIndex + "." + secondaryIndex;
                        String display = numbering + " " + h.text();
                        map.put(h.anchor(), new HeadingLabelMapping(
                                display, numbering, h.level()));
                    }
                }
            }
        }

        return map;
    }

    /**
     * 从章节列表构建目录配置。
     */
    private TocConfig buildTocConfig(DocumentLayout layout, List<ChapterIR> chapters) {
        List<TocEntry> entries = new ArrayList<>();

        for (ChapterIR chapter : chapters) {
            // 章节顶层标题
            entries.add(new TocEntry(
                    chapter.chapterId(), chapter.anchor(),
                    chapter.chapterTitle(), chapter.summary(), 1));

            // 章节内 h3 级标题
            for (Block block : chapter.blocks()) {
                if (block instanceof HeadingBlock h && h.level() == 3) {
                    entries.add(new TocEntry(
                            chapter.chapterId(), h.anchor(),
                            h.text(), null, 2));
                }
            }
        }

        String tocTitle = "目 录";
        int maxDepth = 3;
        if (layout.tocPlan() != null) {
            tocTitle = layout.tocPlan().title() != null
                    ? layout.tocPlan().title() : tocTitle;
            maxDepth = layout.tocPlan().maxDepth() > 0
                    ? layout.tocPlan().maxDepth() : maxDepth;
        }

        return new TocConfig(tocTitle, maxDepth, entries);
    }

    /**
     * 从 DocumentLayout 构建 hero 区域配置。
     */
    private HeroConfig buildHeroConfig(DocumentLayout layout) {
        if (layout.hero() == null) return null;
        var hero = layout.hero();
        return new HeroConfig(
                hero.summary(),
                hero.highlights(),
                hero.kpis() != null ? hero.kpis().stream()
                        .map(k -> new KpiItem(k.label(), k.value(), k.unit(),
                                k.delta(), k.deltaTone()))
                        .toList() : null,
                hero.actions() != null ? hero.actions().stream()
                        .map(a -> new HeroAction(a.label(), a.anchor()))
                        .toList() : null
        );
    }

    /** 标题编号映射 */
    public record HeadingLabelMapping(String display, String numbering, int level) {}
}

/**
 * 文档布局配置 —— 对应原版 document_layout_output_schema 的 LLM 输出。
 */
public record DocumentLayout(
        String title,
        @Nullable String subtitle,
        @Nullable String tagline,
        @Nullable DocumentLayout.Hero hero,
        @Nullable DocumentLayout.TocPlan tocPlan,
        @Nullable Map<String, String> themeTokens
) {
    public record Hero(
            @Nullable String summary,
            @Nullable List<String> highlights,
            @Nullable List<KpiItemInput> kpis,
            @Nullable List<ActionInput> actions
    ) {}

    public record KpiItemInput(String label, String value,
                               @Nullable String unit, @Nullable String delta,
                               @Nullable String deltaTone) {}
    public record ActionInput(String label, String anchor) {}
    public record TocPlan(@Nullable String title, int maxDepth) {}
}
```

---

### 7.6 HtmlRenderer — HTML 渲染器

HtmlRenderer 是 DocumentIR 到最终交互式 HTML 的转换层，对应原版 `renderers/html_renderer.py`。Java 版使用 Thymeleaf 模板引擎结合手动 Block 渲染，确保对每种 Block 类型的精确控制。

```java
package com.bettafish.report.renderer;

import com.bettafish.report.ir.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HTML 渲染器 —— 对应原版 HTMLRenderer。
 *
 * 渲染流程：
 * 1. 重置内部状态
 * 2. 渲染 &lt;head&gt;：CSS 变量、内联库、CDN fallback
 * 3. 渲染 &lt;body&gt;：页眉、hero、TOC、章节 blocks、脚本注水
 * 4. 写入文件并返回路径
 *
 * 关键特性：
 * - Chart.js 图表数据校验 + 修复（本地 + LLM 兜底）
 * - MathJax 数学公式渲染
 * - 深色模式切换
 * - PDF 导出（html2canvas + jsPDF）
 * - 内联字体（思源宋体子集）
 */
@Component
public class HtmlRenderer {

    private static final Logger log = LoggerFactory.getLogger(HtmlRenderer.class);

    private final TemplateEngine thymeleafEngine;

    /** Widget 配置 JSON 收集器，渲染结束后注入 body 尾部 */
    private final List<String> widgetScripts = new ArrayList<>();
    /** TOC 条目收集器 */
    private final List<TocRenderEntry> tocEntries = new ArrayList<>();
    /** 标题计数器 */
    private int headingCounter = 0;
    /** 图表计数器 */
    private int chartCounter = 0;
    /** 标题编号映射 */
    private Map<String, Object> headingLabelMap = Map.of();

    public HtmlRenderer(TemplateEngine thymeleafEngine) {
        this.thymeleafEngine = thymeleafEngine;
    }

    /**
     * 渲染 DocumentIR 为完整 HTML 文件。
     *
     * @param document 完整文档 IR
     * @return 输出文件路径
     */
    public String render(DocumentIR document) throws IOException {
        log.info("开始渲染 HTML: '{}'", document.title());

        // 重置状态
        resetState();

        // 提取元数据
        if (document.metadata() != null && document.metadata().containsKey("headingMap")) {
            headingLabelMap = (Map<String, Object>) document.metadata().get("headingMap");
        }

        // 构建 HTML
        StringBuilder html = new StringBuilder();
        html.append(renderHead(document));
        html.append(renderBody(document));

        // 写入文件
        String filename = "final_report_%s_%d.html".formatted(
                sanitizeFilename(document.title()),
                System.currentTimeMillis());
        Path outputDir = Path.of("final_reports");
        Files.createDirectories(outputDir);
        Path outputPath = outputDir.resolve(filename);
        Files.writeString(outputPath, html.toString(), StandardCharsets.UTF_8);

        log.info("HTML 渲染完成: {}", outputPath);
        return outputPath.toString();
    }

    private void resetState() {
        widgetScripts.clear();
        tocEntries.clear();
        headingCounter = 0;
        chartCounter = 0;
        headingLabelMap = Map.of();
    }

    // ========== HEAD 渲染 ==========

    private String renderHead(DocumentIR doc) {
        StringBuilder head = new StringBuilder();
        head.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        head.append("  <meta charset=\"UTF-8\">\n");
        head.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        head.append("  <title>").append(escapeHtml(doc.title())).append("</title>\n");

        // CSS 变量（主题 tokens）
        head.append("  <style>:root {\n");
        if (doc.themeTokens() != null) {
            doc.themeTokens().forEach((key, value) ->
                    head.append("    --").append(key).append(": ").append(value).append(";\n"));
        }
        head.append("  }</style>\n");

        // 内联 CSS（响应式布局、组件样式、打印样式）
        head.append("  <style>\n").append(getCoreStyles()).append("\n  </style>\n");

        // MathJax（带 CDN fallback）
        head.append(buildScriptWithFallback(
                "mathjax", "https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js",
                "typeof MathJax !== 'undefined'"));

        head.append("</head>\n");
        return head.toString();
    }

    // ========== BODY 渲染 ==========

    private String renderBody(DocumentIR doc) {
        StringBuilder body = new StringBuilder();
        body.append("<body>\n");

        // 页眉（操作按钮区）
        body.append(renderHeader(doc));

        // Hero 区域
        if (doc.heroConfig() != null) {
            body.append(renderHero(doc));
        }

        // 目录
        if (doc.tocConfig() != null) {
            body.append(renderToc(doc.tocConfig()));
        }

        // 章节内容
        body.append("<main class=\"report-content\">\n");
        for (ChapterIR chapter : doc.chapters()) {
            body.append(renderChapter(chapter));
        }
        body.append("</main>\n");

        // 注入 Widget 配置脚本
        widgetScripts.forEach(script -> body.append(script).append("\n"));

        // Chart.js 库（带 CDN fallback）
        body.append(buildScriptWithFallback(
                "chart.js", "https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.js",
                "typeof Chart !== 'undefined'"));

        // 注水脚本（按钮交互、图表实例化、主题切换等）
        body.append("<script>\n").append(getHydrationScript()).append("\n</script>\n");

        body.append("</body>\n</html>");
        return body.toString();
    }

    // ========== 章节渲染 ==========

    private String renderChapter(ChapterIR chapter) {
        StringBuilder sb = new StringBuilder();
        sb.append("<section class=\"chapter\" id=\"")
                .append(escapeAttr(chapter.anchor())).append("\">\n");
        for (Block block : chapter.blocks()) {
            sb.append(renderBlock(block));
        }
        sb.append("</section>\n");
        return sb.toString();
    }

    // ========== Block 分发渲染（核心） ==========

    /**
     * 根据 Block 类型分发到对应的渲染方法。
     * 利用 sealed interface 的模式匹配实现穷举。
     */
    private String renderBlock(Block block) {
        return switch (block) {
            case HeadingBlock h     -> renderHeading(h);
            case ParagraphBlock p   -> renderParagraph(p);
            case ListBlock l        -> renderList(l);
            case TableBlock t       -> renderTable(t);
            case SwotTableBlock s   -> renderSwotTable(s);
            case PestTableBlock p   -> renderPestTable(p);
            case BlockquoteBlock bq -> renderBlockquote(bq);
            case EngineQuoteBlock eq -> renderEngineQuote(eq);
            case HrBlock hr         -> "<hr />\n";
            case CodeBlock cb       -> renderCode(cb);
            case MathBlock m        -> renderMath(m);
            case FigureBlock f      -> renderFigure(f);
            case CalloutBlock c     -> renderCallout(c);
            case KpiGridBlock k     -> renderKpiGrid(k);
            case WidgetBlock w      -> renderWidget(w);
            case TocBlock toc       -> ""; // TOC 在 body 层级独立渲染
        };
    }

    // ========== 各 Block 类型渲染实现 ==========

    private String renderHeading(HeadingBlock h) {
        int level = Math.max(2, Math.min(6, h.level()));
        headingCounter++;
        String anchor = h.anchor() != null ? h.anchor() : "heading-" + headingCounter;
        String displayText = h.text();

        // 收集 TOC 条目
        if (level <= 3) {
            tocEntries.add(new TocRenderEntry(anchor, displayText, level));
        }

        String subtitleHtml = h.subtitle() != null
                ? "<small>" + escapeHtml(h.subtitle()) + "</small>" : "";
        return "<h%d id=\"%s\">%s%s</h%d>\n".formatted(
                level, escapeAttr(anchor), escapeHtml(displayText), subtitleHtml, level);
    }

    private String renderParagraph(ParagraphBlock p) {
        StringBuilder sb = new StringBuilder("<p>");
        for (InlineRun run : p.inlines()) {
            sb.append(renderInline(run));
        }
        sb.append("</p>\n");
        return sb.toString();
    }

    /**
     * 渲染内联文本，支持多种 marks 叠加。
     * 对应原版 _render_inline 方法。
     */
    private String renderInline(InlineRun run) {
        String text = escapeHtml(run.text());
        if (run.marks() == null || run.marks().isEmpty()) {
            return text;
        }

        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();
        List<String> styles = new ArrayList<>();

        for (InlineMark mark : run.marks()) {
            switch (mark.type()) {
                case BOLD -> { prefix.append("<strong>"); suffix.insert(0, "</strong>"); }
                case ITALIC -> { prefix.append("<em>"); suffix.insert(0, "</em>"); }
                case CODE -> { prefix.append("<code>"); suffix.insert(0, "</code>"); }
                case HIGHLIGHT -> { prefix.append("<mark>"); suffix.insert(0, "</mark>"); }
                case LINK -> {
                    if (mark.href() != null && !"#".equals(mark.href())) {
                        String title = mark.title() != null ? mark.title() : "";
                        prefix.append("<a href=\"").append(escapeAttr(mark.href()))
                                .append("\" title=\"").append(escapeAttr(title))
                                .append("\" target=\"_blank\" rel=\"noopener\">");
                        suffix.insert(0, "</a>");
                    }
                }
                case UNDERLINE -> styles.add("text-decoration: underline");
                case STRIKETHROUGH -> styles.add("text-decoration: line-through");
                case SUP -> { prefix.append("<sup>"); suffix.insert(0, "</sup>"); }
                case SUB -> { prefix.append("<sub>"); suffix.insert(0, "</sub>"); }
                case MATH -> {
                    String latex = mark.value() != null ? mark.value() : run.text();
                    return "<span class=\"math-inline\">\\( "
                            + escapeHtml(latex) + " \\)</span>";
                }
                case EMOJI -> { /* Unicode emoji，直接输出 */ }
                case FOOTNOTE -> {
                    String ref = mark.value() != null ? mark.value() : "?";
                    return "<sup class=\"footnote-ref\"><a href=\"#fn-"
                            + escapeAttr(ref) + "\">" + escapeHtml(ref) + "</a></sup>";
                }
            }
        }

        if (!styles.isEmpty()) {
            prefix.insert(0, "<span style=\"" + String.join("; ", styles) + "\">");
            suffix.append("</span>");
        }

        return prefix.toString() + text + suffix.toString();
    }

    private String renderList(ListBlock l) {
        String tag = l.isOrdered() ? "ol" : "ul";
        StringBuilder sb = new StringBuilder("<" + tag + ">\n");
        for (List<Block> item : l.items()) {
            sb.append("<li>");
            for (Block b : item) sb.append(renderBlock(b));
            sb.append("</li>\n");
        }
        sb.append("</" + tag + ">\n");
        return sb.toString();
    }

    private String renderTable(TableBlock t) {
        StringBuilder sb = new StringBuilder("<div class=\"table-wrap\"><table>\n");
        if (t.caption() != null) {
            sb.append("<caption>").append(escapeHtml(t.caption())).append("</caption>\n");
        }
        sb.append("<tbody>\n");
        for (TableRow row : t.rows()) {
            sb.append("<tr>");
            for (TableCell cell : row.cells()) {
                String tag = cell.isHeader() ? "th" : "td";
                StringBuilder attrs = new StringBuilder();
                if (cell.rowspan() != null) attrs.append(" rowspan=\"").append(cell.rowspan()).append("\"");
                if (cell.colspan() != null) attrs.append(" colspan=\"").append(cell.colspan()).append("\"");
                if (cell.align() != null) attrs.append(" class=\"align-").append(cell.align()).append("\"");
                sb.append("<").append(tag).append(attrs).append(">");
                for (Block b : cell.blocks()) sb.append(renderBlock(b));
                sb.append("</").append(tag).append(">");
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table></div>\n");
        return sb.toString();
    }

    private String renderSwotTable(SwotTableBlock s) {
        String title = s.title() != null ? s.title() : "SWOT 分析";
        String[][] quadrants = {
                {"strengths", "优势 Strengths", "S", "strength"},
                {"weaknesses", "劣势 Weaknesses", "W", "weakness"},
                {"opportunities", "机会 Opportunities", "O", "opportunity"},
                {"threats", "威胁 Threats", "T", "threat"}
        };

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"swot-container\">\n");
        sb.append("<div class=\"swot-card__title\">").append(escapeHtml(title)).append("</div>\n");
        if (s.summary() != null) {
            sb.append("<p class=\"swot-card__summary\">").append(escapeHtml(s.summary())).append("</p>\n");
        }
        sb.append("<div class=\"swot-grid\">\n");

        List<?>[] itemLists = {s.strengths(), s.weaknesses(), s.opportunities(), s.threats()};
        for (int i = 0; i < 4; i++) {
            List<SwotItem> items = (List<SwotItem>) itemLists[i];
            sb.append("<div class=\"swot-cell ").append(quadrants[i][3]).append("\">\n");
            sb.append("  <div class=\"swot-cell__meta\"><span class=\"swot-pill\">")
                    .append(quadrants[i][2]).append("</span> ")
                    .append(escapeHtml(quadrants[i][1])).append("</div>\n");
            sb.append("  <ul class=\"swot-list\">\n");
            if (items != null) {
                for (SwotItem item : items) {
                    sb.append("    <li>").append(escapeHtml(item.text()));
                    if (item.impact() != null) {
                        sb.append(" <span class=\"impact-badge\">").append(escapeHtml(item.impact())).append("</span>");
                    }
                    sb.append("</li>\n");
                }
            }
            sb.append("  </ul>\n</div>\n");
        }
        sb.append("</div></div>\n");
        return sb.toString();
    }

    private String renderPestTable(PestTableBlock p) {
        String title = p.title() != null ? p.title() : "PEST 分析";
        String[][] dims = {
                {"political", "政治因素 Political", "P"},
                {"economic", "经济因素 Economic", "E"},
                {"social", "社会因素 Social", "S"},
                {"technological", "技术因素 Technological", "T"}
        };

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"pest-container\">\n");
        sb.append("<div class=\"pest-card__title\">").append(escapeHtml(title)).append("</div>\n");
        if (p.summary() != null) {
            sb.append("<p class=\"pest-card__summary\">").append(escapeHtml(p.summary())).append("</p>\n");
        }
        sb.append("<div class=\"pest-strips\">\n");

        List<?>[] itemLists = {p.political(), p.economic(), p.social(), p.technological()};
        for (int i = 0; i < 4; i++) {
            List<PestItem> items = (List<PestItem>) itemLists[i];
            sb.append("<div class=\"pest-strip ").append(dims[i][0]).append("\">\n");
            sb.append("  <div class=\"pest-strip__indicator\"><span class=\"pest-code\">")
                    .append(dims[i][2]).append("</span></div>\n");
            sb.append("  <div class=\"pest-strip__content\">\n");
            sb.append("    <div class=\"pest-strip__title\">").append(escapeHtml(dims[i][1])).append("</div>\n");
            sb.append("    <ul class=\"pest-list\">\n");
            if (items != null) {
                for (PestItem item : items) {
                    sb.append("      <li><strong>").append(escapeHtml(item.factor())).append("</strong>");
                    if (item.description() != null) {
                        sb.append("：").append(escapeHtml(item.description()));
                    }
                    if (item.trend() != null) {
                        sb.append(" <span class=\"trend-badge\">").append(escapeHtml(item.trend())).append("</span>");
                    }
                    sb.append("</li>\n");
                }
            }
            sb.append("    </ul>\n  </div>\n</div>\n");
        }
        sb.append("</div></div>\n");
        return sb.toString();
    }

    private String renderBlockquote(BlockquoteBlock bq) {
        StringBuilder sb = new StringBuilder("<blockquote>\n");
        for (Block b : bq.blocks()) sb.append(renderBlock(b));
        if (bq.attribution() != null) {
            sb.append("<footer>— ").append(escapeHtml(bq.attribution())).append("</footer>\n");
        }
        sb.append("</blockquote>\n");
        return sb.toString();
    }

    private String renderEngineQuote(EngineQuoteBlock eq) {
        String engine = EngineQuoteBlock.ALLOWED_ENGINES.contains(eq.engine())
                ? eq.engine() : "insight";
        String title = eq.resolvedTitle();

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"engine-quote engine-").append(escapeAttr(engine)).append("\">\n");
        sb.append("  <div class=\"engine-quote__header\">\n");
        sb.append("    <span class=\"engine-quote__dot\"></span>\n");
        sb.append("    <span class=\"engine-quote__title\">").append(escapeHtml(title)).append("</span>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"engine-quote__body\">\n");
        for (Block b : eq.blocks()) sb.append(renderBlock(b));
        sb.append("  </div>\n</div>\n");
        return sb.toString();
    }

    private String renderCode(CodeBlock cb) {
        String lang = cb.language() != null ? cb.language() : "";
        return "<pre class=\"code-block\" data-lang=\"%s\"><code>%s</code></pre>\n"
                .formatted(escapeAttr(lang), escapeHtml(cb.code()));
    }

    private String renderMath(MathBlock m) {
        return "<div class=\"math-block\">$$ %s $$</div>\n"
                .formatted(escapeHtml(m.latex()));
    }

    private String renderFigure(FigureBlock f) {
        // 按原版规范，不渲染外部图片，改为友好提示
        String caption = f.caption() != null ? f.caption()
                : "图像内容已省略（仅允许 HTML 原生图表与表格）";
        return "<div class=\"figure-placeholder\">%s</div>\n"
                .formatted(escapeHtml(caption));
    }

    private String renderCallout(CalloutBlock c) {
        String tone = c.tone() != null ? c.tone() : "info";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"callout tone-").append(escapeAttr(tone)).append("\">\n");
        if (c.title() != null) {
            sb.append("  <strong>").append(escapeHtml(c.title())).append("</strong>\n");
        }
        for (Block b : c.blocks()) sb.append(renderBlock(b));
        sb.append("</div>\n");
        return sb.toString();
    }

    private String renderKpiGrid(KpiGridBlock k) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"kpi-grid\" data-kpi-count=\"")
                .append(k.items().size()).append("\">\n");
        for (KpiItem item : k.items()) {
            String deltaTone = item.deltaTone() != null ? item.deltaTone() : "neutral";
            String deltaHtml = item.delta() != null
                    ? "<span class=\"delta %s\">%s</span>".formatted(deltaTone, escapeHtml(item.delta()))
                    : "";
            String unitHtml = item.unit() != null
                    ? "<small>" + escapeHtml(item.unit()) + "</small>" : "";
            sb.append("  <div class=\"kpi-card\">\n");
            sb.append("    <div class=\"kpi-value\">").append(escapeHtml(item.value())).append(unitHtml).append("</div>\n");
            sb.append("    <div class=\"kpi-label\">").append(escapeHtml(item.label())).append("</div>\n");
            sb.append("    ").append(deltaHtml).append("\n");
            sb.append("  </div>\n");
        }
        sb.append("</div>\n");
        return sb.toString();
    }

    private String renderWidget(WidgetBlock w) {
        chartCounter++;
        String canvasId = "chart-" + chartCounter;
        String configId = "chart-config-" + chartCounter;

        // 序列化 widget 配置为 JSON
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("widgetId", w.widgetId());
        payload.put("widgetType", w.widgetType());
        payload.put("props", w.props());
        payload.put("data", w.data());
        payload.put("dataRef", w.dataRef());

        String configJson;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            configJson = mapper.writeValueAsString(payload).replace("</", "<\\/");
        } catch (Exception e) {
            configJson = "{}";
        }
        widgetScripts.add("<script type=\"application/json\" id=\"%s\">%s</script>"
                .formatted(configId, configJson));

        String titleHtml = w.title() != null
                ? "<div class=\"chart-title\">" + escapeHtml(w.title()) + "</div>" : "";

        return """
                <div class="chart-card">
                  %s
                  <div class="chart-container">
                    <canvas id="%s" data-config-id="%s"></canvas>
                  </div>
                </div>
                """.formatted(titleHtml, canvasId, configId);
    }

    // ========== 辅助方法 ==========

    private String renderHeader(DocumentIR doc) {
        return """
                <header class="report-header">
                  <h1>%s</h1>
                  %s
                  <div class="header-actions">
                    <button id="btn-theme" title="切换深色模式">🌓</button>
                    <button id="btn-print" title="打印">🖨</button>
                    <button id="btn-pdf" title="导出 PDF">📄</button>
                  </div>
                </header>
                """.formatted(
                escapeHtml(doc.title()),
                doc.subtitle() != null ? "<p class=\"subtitle\">" + escapeHtml(doc.subtitle()) + "</p>" : "");
    }

    private String renderHero(DocumentIR doc) {
        HeroConfig hero = doc.heroConfig();
        StringBuilder sb = new StringBuilder("<section class=\"hero\">\n");
        if (hero.summary() != null) {
            sb.append("<p class=\"hero-summary\">").append(escapeHtml(hero.summary())).append("</p>\n");
        }
        if (hero.kpis() != null && !hero.kpis().isEmpty()) {
            sb.append(renderKpiGrid(new KpiGridBlock(hero.kpis(), null)));
        }
        sb.append("</section>\n");
        return sb.toString();
    }

    private String renderToc(TocConfig toc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<nav class=\"toc\">\n<h2>").append(escapeHtml(toc.title())).append("</h2>\n<ul>\n");
        for (TocEntry entry : toc.entries()) {
            if (entry.level() <= toc.maxDepth()) {
                sb.append("  <li class=\"toc-level-").append(entry.level()).append("\">");
                sb.append("<a href=\"#").append(escapeAttr(entry.anchor())).append("\">");
                sb.append(escapeHtml(entry.display()));
                sb.append("</a></li>\n");
            }
        }
        sb.append("</ul>\n</nav>\n");
        return sb.toString();
    }

    private record TocRenderEntry(String anchor, String text, int level) {}

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String escapeAttr(String s) {
        if (s == null) return "";
        return escapeHtml(s).replace("'", "&#39;");
    }

    private String sanitizeFilename(String s) {
        if (s == null) return "report";
        return s.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5\\-_]", "_")
                .substring(0, Math.min(s.length(), 50));
    }

    // CSS 和 JS 方法省略，参见完整实现
    private String getCoreStyles() { return "/* 核心样式 — 参见原版 html_renderer.py */"; }
    private String getHydrationScript() { return "/* 注水脚本 — 参见原版 _hydration_script */"; }
    private String buildScriptWithFallback(String name, String cdn, String check) {
        return "<script src=\"%s\"></script>\n".formatted(cdn);
    }
}
```

#### 7.6.2 PDF 导出支持

PDF 导出采用两种策略：服务端 OpenPDF 渲染（适合批量处理）和客户端 html2canvas + jsPDF（适合交互式导出）：

```java
package com.bettafish.report.renderer;

import com.bettafish.report.ir.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.BaseFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;

/**
 * PDF 渲染器 —— 使用 OpenPDF 实现服务端 PDF 导出。
 *
 * 对应原版中通过 html2canvas + jsPDF 在客户端导出的功能。
 * Java 版提供服务端直接渲染 PDF 的能力，适合批量生成和 CI/CD 集成。
 */
@Component
public class PdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderer.class);

    /**
     * 将 DocumentIR 渲染为 PDF 文件。
     */
    public String render(DocumentIR document) throws IOException, DocumentException {
        log.info("开始渲染 PDF: '{}'", document.title());

        String filename = "final_report_%s_%d.pdf".formatted(
                document.title().replaceAll("[^\\w\\u4e00-\\u9fa5]", "_"),
                System.currentTimeMillis());
        Path outputPath = Path.of("final_reports", filename);
        Files.createDirectories(outputPath.getParent());

        Document pdf = new Document(PageSize.A4, 72, 72, 72, 72);
        PdfWriter.getInstance(pdf, new FileOutputStream(outputPath.toFile()));
        pdf.open();

        // 加载中文字体
        BaseFont bfChinese = BaseFont.createFont(
                "fonts/SourceHanSerifSC-Medium.ttf",
                BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font titleFont = new Font(bfChinese, 24, Font.BOLD);
        Font headingFont = new Font(bfChinese, 16, Font.BOLD);
        Font bodyFont = new Font(bfChinese, 11, Font.NORMAL);

        // 标题
        Paragraph title = new Paragraph(document.title(), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        pdf.add(title);

        if (document.subtitle() != null) {
            Paragraph subtitle = new Paragraph(document.subtitle(), bodyFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(30);
            pdf.add(subtitle);
        }

        // 逐章节渲染
        for (ChapterIR chapter : document.chapters()) {
            renderChapterToPdf(pdf, chapter, headingFont, bodyFont);
        }

        pdf.close();
        log.info("PDF 渲染完成: {}", outputPath);
        return outputPath.toString();
    }

    private void renderChapterToPdf(Document pdf, ChapterIR chapter,
                                     Font headingFont, Font bodyFont)
            throws DocumentException {
        for (Block block : chapter.blocks()) {
            switch (block) {
                case HeadingBlock h -> {
                    Paragraph p = new Paragraph(h.text(), headingFont);
                    p.setSpacingBefore(15);
                    p.setSpacingAfter(10);
                    pdf.add(p);
                }
                case ParagraphBlock para -> {
                    Paragraph p = new Paragraph();
                    for (InlineRun run : para.inlines()) {
                        Font font = bodyFont;
                        if (run.marks() != null) {
                            for (InlineMark mark : run.marks()) {
                                if (mark.type() == InlineMarkType.BOLD) {
                                    font = new Font(font.getBaseFont(),
                                            font.getSize(), Font.BOLD);
                                }
                            }
                        }
                        p.add(new Chunk(run.text(), font));
                    }
                    p.setSpacingAfter(8);
                    p.setFirstLineIndent(22);
                    pdf.add(p);
                }
                case HrBlock hr -> {
                    pdf.add(new Paragraph(" "));
                    pdf.add(new LineSeparator());
                    pdf.add(new Paragraph(" "));
                }
                default -> {
                    // 其他类型暂以文本形式呈现
                }
            }
        }
    }
}
```

---

## Chapter 8: MCP Server 情感分析微服务

情感分析是 BettaFish InsightEngine 的核心能力之一。原版使用 PyTorch + Hugging Face Transformers 直接加载预训练模型进行推理。Java 版将其重构为独立的 MCP（Model Context Protocol）Server 微服务，通过 ONNX Runtime 实现高性能推理，并借助 Spring AI MCP Client 实现无缝集成。

---

### 8.1 为什么用 MCP

#### 8.1.1 原版架构的局限性

原版 Python 实现中，情感分析直接耦合在 InsightEngine 内部：

```python
# 原版 InsightEngine/tools/sentiment_analyzer.py
from transformers import pipeline

class SentimentAnalyzer:
    def __init__(self):
        self.model = pipeline(
            "sentiment-analysis",
            model="tabularisai/multilingual-sentiment-analysis",
            device=0  # GPU
        )
    
    def analyze(self, text: str) -> dict:
        result = self.model(text, truncation=True, max_length=512)
        return {"label": result[0]["label"], "score": result[0]["score"]}
```

这种方式存在以下问题：

| 问题 | 描述 |
|------|------|
| **耦合性高** | 模型加载、推理、后处理全部嵌入 Agent 内部 |
| **资源浪费** | 每个 Agent 实例都会加载一份模型到 GPU |
| **扩展困难** | 更换模型需要修改 Agent 代码 |
| **语言绑定** | PyTorch 模型只能在 Python 环境运行 |

#### 8.1.2 MCP 架构的优势

MCP（Model Context Protocol）是 Anthropic 提出的开放标准协议，Spring AI 1.1+ 已原生支持。将情感分析封装为 MCP Server 后：

```
┌──────────────────┐    MCP Protocol    ┌────────────────────┐
│  InsightAgent    │  ◄──────────────►  │  Sentiment MCP     │
│  (MCP Client)    │   JSON-RPC/HTTP    │  Server             │
│                  │                    │  (ONNX Runtime)    │
│  Spring AI 自动   │                    │  独立部署/扩缩容    │
│  工具注入         │                    │  多语言支持         │
└──────────────────┘                    └────────────────────┘
```

优势：

1. **解耦部署**：情感分析服务独立部署，可以在 GPU 机器上运行，主应用无需 GPU。
2. **协议标准化**：MCP 协议让 LLM 可以自动发现和调用情感分析工具，无需硬编码。
3. **模型热切换**：更换 ONNX 模型文件即可切换模型，无需重启主应用。
4. **多实例扩缩容**：高负载时可水平扩展多个 MCP Server 实例。

---

### 8.2 OnnxSentimentAnalyzer

#### 8.2.1 模型导出：PyTorch → ONNX

首先需要将原版使用的 `tabularisai/multilingual-sentiment-analysis` 模型导出为 ONNX 格式：

```python
# export_to_onnx.py — 一次性运行的导出脚本
from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

model_name = "tabularisai/multilingual-sentiment-analysis"
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForSequenceClassification.from_pretrained(model_name)
model.eval()

# 构造示例输入
dummy_input = tokenizer(
    "这个产品非常好用", return_tensors="pt",
    padding="max_length", truncation=True, max_length=512
)

# 导出 ONNX
torch.onnx.export(
    model,
    (dummy_input["input_ids"], dummy_input["attention_mask"]),
    "sentiment_model.onnx",
    input_names=["input_ids", "attention_mask"],
    output_names=["logits"],
    dynamic_axes={
        "input_ids": {0: "batch", 1: "seq"},
        "attention_mask": {0: "batch", 1: "seq"},
        "logits": {0: "batch"}
    },
    opset_version=14
)
print("ONNX 模型导出成功: sentiment_model.onnx")
```

#### 8.2.2 Java ONNX Runtime 推理实现

```java
package com.bettafish.sentiment;

import ai.onnxruntime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

/**
 * ONNX Runtime 情感分析推理器。
 *
 * 对应原版 InsightEngine/tools/sentiment_analyzer.py。
 * 使用导出的 ONNX 模型，支持 22 种语言的情感分析。
 *
 * 模型信息：
 * - 基础模型: tabularisai/multilingual-sentiment-analysis
 * - 输入: input_ids, attention_mask (最大长度 512)
 * - 输出: logits (5 个类别)
 * - 类别: Very Negative, Negative, Neutral, Positive, Very Positive
 */
@Component
public class OnnxSentimentAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(OnnxSentimentAnalyzer.class);

    /** 5 种情感类别 */
    public static final String[] SENTIMENT_LABELS = {
            "Very Negative", "Negative", "Neutral", "Positive", "Very Positive"
    };

    /** 支持的 22 种语言 */
    public static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "zh", "en", "es", "fr", "de", "it", "pt", "nl", "ru", "ja",
            "ko", "ar", "hi", "tr", "pl", "sv", "da", "no", "fi", "cs",
            "ro", "hu"
    );

    private static final int MAX_SEQ_LENGTH = 512;

    @Value("${sentiment.model.path:models/sentiment_model.onnx}")
    private String modelPath;

    @Value("${sentiment.tokenizer.vocab:models/vocab.txt}")
    private String vocabPath;

    private OrtEnvironment env;
    private OrtSession session;
    private SimpleTokenizer tokenizer;

    @PostConstruct
    public void init() throws OrtException {
        log.info("加载 ONNX 情感分析模型: {}", modelPath);
        env = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        // 如果有 GPU，启用 CUDA
        try {
            opts.addCUDA(0);
            log.info("CUDA GPU 加速已启用");
        } catch (OrtException e) {
            log.info("CUDA 不可用，使用 CPU 推理");
        }
        opts.setInterOpNumThreads(4);
        opts.setIntraOpNumThreads(4);

        session = env.createSession(modelPath, opts);
        tokenizer = new SimpleTokenizer(vocabPath);

        log.info("ONNX 模型加载完成，输入: {}, 输出: {}",
                session.getInputNames(), session.getOutputNames());
    }

    @PreDestroy
    public void destroy() throws OrtException {
        if (session != null) session.close();
        if (env != null) env.close();
        log.info("ONNX 会话已关闭");
    }

    /**
     * 分析单条文本的情感。
     *
     * @param text 待分析文本
     * @param lang 文本语言（可选，仅用于日志记录）
     * @return 情感分析结果
     */
    public SentimentResult analyze(String text, String lang) {
        if (text == null || text.isBlank()) {
            return SentimentResult.neutral();
        }

        try {
            // Step 1: 分词
            TokenizerOutput tokens = tokenizer.encode(text, MAX_SEQ_LENGTH);

            // Step 2: 构建 ONNX 输入张量
            long[] inputIds = tokens.inputIds();
            long[] attentionMask = tokens.attentionMask();
            long[] shape = {1, inputIds.length};

            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(
                    env, LongBuffer.wrap(inputIds), shape);
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(
                    env, LongBuffer.wrap(attentionMask), shape);

            Map<String, OnnxTensor> inputs = Map.of(
                    "input_ids", inputIdsTensor,
                    "attention_mask", attentionMaskTensor
            );

            // Step 3: 推理
            OrtSession.Result result = session.run(inputs);
            float[][] logits = (float[][]) result.get(0).getValue();

            // Step 4: Softmax 转概率
            float[] probabilities = softmax(logits[0]);

            // Step 5: 取最大概率的类别
            int maxIndex = 0;
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > probabilities[maxIndex]) {
                    maxIndex = i;
                }
            }

            // 清理资源
            inputIdsTensor.close();
            attentionMaskTensor.close();
            result.close();

            return new SentimentResult(
                    SENTIMENT_LABELS[maxIndex],
                    probabilities[maxIndex],
                    toMap(probabilities),
                    text.length() > 100 ? text.substring(0, 100) + "..." : text,
                    lang
            );

        } catch (OrtException e) {
            log.error("ONNX 推理失败: {}", e.getMessage(), e);
            return SentimentResult.error(e.getMessage());
        }
    }

    /**
     * 批量分析多条文本的情感。
     * 利用 ONNX Runtime 的批处理能力提升吞吐量。
     *
     * @param texts 待分析的文本列表
     * @return 情感分析结果列表
     */
    public List<SentimentResult> batchAnalyze(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        log.debug("批量情感分析: {} 条文本", texts.size());

        try {
            int batchSize = texts.size();

            // 批量分词
            List<TokenizerOutput> tokensList = texts.stream()
                    .map(t -> tokenizer.encode(t != null ? t : "", MAX_SEQ_LENGTH))
                    .toList();

            // 找到最长序列，统一 padding
            int maxLen = tokensList.stream()
                    .mapToInt(t -> t.inputIds().length)
                    .max().orElse(MAX_SEQ_LENGTH);

            // 构建批量输入张量
            long[] batchInputIds = new long[batchSize * maxLen];
            long[] batchAttentionMask = new long[batchSize * maxLen];

            for (int i = 0; i < batchSize; i++) {
                TokenizerOutput tokens = tokensList.get(i);
                int seqLen = tokens.inputIds().length;
                System.arraycopy(tokens.inputIds(), 0, batchInputIds,
                        i * maxLen, seqLen);
                System.arraycopy(tokens.attentionMask(), 0, batchAttentionMask,
                        i * maxLen, seqLen);
                // 剩余位置自动为 0（padding）
            }

            long[] shape = {batchSize, maxLen};

            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(
                    env, LongBuffer.wrap(batchInputIds), shape);
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(
                    env, LongBuffer.wrap(batchAttentionMask), shape);

            Map<String, OnnxTensor> inputs = Map.of(
                    "input_ids", inputIdsTensor,
                    "attention_mask", attentionMaskTensor
            );

            // 批量推理
            OrtSession.Result result = session.run(inputs);
            float[][] batchLogits = (float[][]) result.get(0).getValue();

            // 解析结果
            List<SentimentResult> results = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                float[] probs = softmax(batchLogits[i]);
                int maxIndex = IntStream.range(0, probs.length)
                        .reduce((a, b) -> probs[a] >= probs[b] ? a : b)
                        .orElse(2); // 默认 Neutral

                String preview = texts.get(i);
                if (preview != null && preview.length() > 100) {
                    preview = preview.substring(0, 100) + "...";
                }

                results.add(new SentimentResult(
                        SENTIMENT_LABELS[maxIndex], probs[maxIndex],
                        toMap(probs), preview, null));
            }

            // 清理
            inputIdsTensor.close();
            attentionMaskTensor.close();
            result.close();

            return results;

        } catch (OrtException e) {
            log.error("批量 ONNX 推理失败: {}", e.getMessage(), e);
            return texts.stream()
                    .map(t -> SentimentResult.error(e.getMessage()))
                    .toList();
        }
    }

    // ========== 辅助方法 ==========

    private float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) if (v > max) max = v;

        float sum = 0;
        float[] exp = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = (float) Math.exp(logits[i] - max);
            sum += exp[i];
        }
        for (int i = 0; i < exp.length; i++) exp[i] /= sum;
        return exp;
    }

    private Map<String, Float> toMap(float[] probabilities) {
        Map<String, Float> map = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(probabilities.length, SENTIMENT_LABELS.length); i++) {
            map.put(SENTIMENT_LABELS[i], probabilities[i]);
        }
        return map;
    }
}

/**
 * 情感分析结果。
 */
public record SentimentResult(
        String label,
        float confidence,
        Map<String, Float> probabilities,
        String textPreview,
        String language
) {
    public static SentimentResult neutral() {
        return new SentimentResult("Neutral", 1.0f,
                Map.of("Neutral", 1.0f), "", null);
    }

    public static SentimentResult error(String message) {
        return new SentimentResult("Error", 0.0f,
                Map.of(), "分析失败: " + message, null);
    }
}

/**
 * 分词器输出。
 */
public record TokenizerOutput(long[] inputIds, long[] attentionMask) {}
```

#### 8.2.3 简易分词器

由于 Java 没有直接等价于 Hugging Face Tokenizers 的库，我们实现一个基于 WordPiece 算法的简易分词器，或者使用 DJL（Deep Java Library）提供的分词器：

```java
package com.bettafish.sentiment;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 简易 WordPiece 分词器。
 *
 * 生产环境中建议使用 DJL (ai.djl.huggingface:tokenizers) 替代。
 * 此实现仅供演示和轻量级部署使用。
 */
public class SimpleTokenizer {

    private static final String UNK_TOKEN = "[UNK]";
    private static final String CLS_TOKEN = "[CLS]";
    private static final String SEP_TOKEN = "[SEP]";
    private static final String PAD_TOKEN = "[PAD]";

    private final Map<String, Integer> vocab;
    private final int unkId;
    private final int clsId;
    private final int sepId;
    private final int padId;

    public SimpleTokenizer(String vocabPath) {
        this.vocab = loadVocab(vocabPath);
        this.unkId = vocab.getOrDefault(UNK_TOKEN, 0);
        this.clsId = vocab.getOrDefault(CLS_TOKEN, 101);
        this.sepId = vocab.getOrDefault(SEP_TOKEN, 102);
        this.padId = vocab.getOrDefault(PAD_TOKEN, 0);
    }

    /**
     * 对文本编码为 token IDs。
     */
    public TokenizerOutput encode(String text, int maxLength) {
        // 基本清洗
        text = text.toLowerCase().strip();

        // 按字符 / 单词拆分
        List<String> tokens = tokenize(text);

        // WordPiece 编码
        List<Integer> ids = new ArrayList<>();
        ids.add(clsId); // [CLS]

        for (String token : tokens) {
            List<Integer> wordPieceIds = wordPieceEncode(token);
            if (ids.size() + wordPieceIds.size() >= maxLength - 1) break;
            ids.addAll(wordPieceIds);
        }

        ids.add(sepId); // [SEP]

        // 构建 attention mask
        int seqLen = ids.size();
        long[] inputIds = new long[seqLen];
        long[] attentionMask = new long[seqLen];
        for (int i = 0; i < seqLen; i++) {
            inputIds[i] = ids.get(i);
            attentionMask[i] = 1;
        }

        return new TokenizerOutput(inputIds, attentionMask);
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
                if (isPunctuation(c)) {
                    tokens.add(String.valueOf(c));
                }
            } else {
                // 中文字符单独切分
                if (isChinese(c)) {
                    if (!current.isEmpty()) {
                        tokens.add(current.toString());
                        current = new StringBuilder();
                    }
                    tokens.add(String.valueOf(c));
                } else {
                    current.append(c);
                }
            }
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    private List<Integer> wordPieceEncode(String token) {
        List<Integer> ids = new ArrayList<>();
        int start = 0;
        while (start < token.length()) {
            int end = token.length();
            boolean found = false;
            while (start < end) {
                String subword = (start > 0 ? "##" : "") + token.substring(start, end);
                Integer id = vocab.get(subword);
                if (id != null) {
                    ids.add(id);
                    start = end;
                    found = true;
                    break;
                }
                end--;
            }
            if (!found) {
                ids.add(unkId);
                start++;
            }
        }
        return ids;
    }

    private boolean isPunctuation(char c) {
        return "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".indexOf(c) >= 0
                || "，。！？；：""''、…—".indexOf(c) >= 0;
    }

    private boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private Map<String, Integer> loadVocab(String path) {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(Path.of(path))) {
            String line;
            int idx = 0;
            while ((line = reader.readLine()) != null) {
                map.put(line.strip(), idx++);
            }
        } catch (IOException e) {
            throw new RuntimeException("加载词表失败: " + path, e);
        }
        return map;
    }
}
```

---

### 8.3 MCP Server 配置

#### 8.3.1 MCP Server 端（独立 Spring Boot 应用）

```java
package com.bettafish.sentiment;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 情感分析 MCP Server —— 独立部署的微服务。
 *
 * 启动后自动注册为 MCP Server，暴露情感分析工具。
 * InsightAgent 通过 MCP Client 自动发现并调用。
 */
@SpringBootApplication
public class SentimentMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentimentMcpServerApplication.class, args);
    }
}

@Service
class SentimentToolService {

    private final OnnxSentimentAnalyzer analyzer;

    SentimentToolService(OnnxSentimentAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * 分析文本的情感倾向 —— 暴露为 MCP 工具。
     * 对应原版 InsightEngine/tools/sentiment_analyzer.py 的 analyze 方法。
     */
    @Tool(description = "分析文本的情感倾向，返回五级情感分类（非常消极/消极/中性/积极/非常积极）"
            + "及概率分布。支持中文、英文等 22 种语言。"
            + "适用于分析社交媒体帖子、新闻评论、用户反馈等文本的情感极性。")
    public SentimentResult analyzeSentiment(
            @ToolParam(description = "待分析的文本内容（不超过 512 个 token）")
            String text,
            @ToolParam(description = "文本语言代码，如 zh（中文）、en（英文）。可选参数。",
                    required = false)
            String lang
    ) {
        return analyzer.analyze(text, lang);
    }

    /**
     * 批量分析多条文本的情感倾向。
     */
    @Tool(description = "批量分析多条文本的情感倾向，适用于一次性分析大量评论或帖子。"
            + "输入文本列表，返回对应的情感分析结果列表。")
    public List<SentimentResult> batchAnalyzeSentiment(
            @ToolParam(description = "待分析的文本列表，每条不超过 512 个 token")
            List<String> texts
    ) {
        return analyzer.batchAnalyze(texts);
    }

    /**
     * 获取支持的语言列表。
     */
    @Tool(description = "获取情感分析模型支持的语言列表")
    public List<String> getSupportedLanguages() {
        return OnnxSentimentAnalyzer.SUPPORTED_LANGUAGES.stream()
                .sorted().toList();
    }
}
```

#### 8.3.2 MCP Server 端配置文件

```yaml
# bettafish-sentiment-mcp/src/main/resources/application.yml
server:
  port: 8090

spring:
  application:
    name: bettafish-sentiment-mcp
  ai:
    mcp:
      server:
        name: bettafish-sentiment-server
        version: 1.0.0
        type: SYNC
        # MCP Server 通过 Streamable HTTP 传输
        streamable-http:
          enabled: true

sentiment:
  model:
    path: ${SENTIMENT_MODEL_PATH:models/sentiment_model.onnx}
  tokenizer:
    vocab: ${SENTIMENT_VOCAB_PATH:models/vocab.txt}

# 健康检查
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

#### 8.3.3 MCP Server Maven 依赖

```xml
<!-- bettafish-sentiment-mcp/pom.xml -->
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring AI MCP Server（Streamable HTTP 传输） -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>

    <!-- ONNX Runtime -->
    <dependency>
        <groupId>com.microsoft.onnxruntime</groupId>
        <artifactId>onnxruntime</artifactId>
        <version>1.19.0</version>
    </dependency>
    <!-- 如果需要 GPU 加速 -->
    <dependency>
        <groupId>com.microsoft.onnxruntime</groupId>
        <artifactId>onnxruntime_gpu</artifactId>
        <version>1.19.0</version>
        <optional>true</optional>
    </dependency>

    <!-- Actuator 健康检查 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

#### 8.3.4 MCP Client 端配置（在主应用中）

在 BettaFish 主应用的配置中声明 MCP Client 连接：

```yaml
# bettafish-app/src/main/resources/application.yml
spring:
  ai:
    mcp:
      client:
        # 通过 Streamable HTTP 连接 Sentiment MCP Server
        streamable-http:
          connections:
            sentiment-server:
              url: ${SENTIMENT_MCP_URL:http://localhost:8090/mcp}
        # 也可以通过 SSE 传输连接
        # sse:
        #   connections:
        #     sentiment-server:
        #       url: http://localhost:8090/sse
```

#### 8.3.5 InsightAgent 集成 MCP 工具

Spring AI 会自动将 MCP Server 的工具注入到 ChatClient 的工具列表中。InsightAgent 无需显式调用情感分析 API，LLM 会根据上下文自动决定何时调用：

```java
package com.bettafish.insight;

import com.bettafish.common.agent.AbstractAnalysisAgent;
import com.bettafish.common.model.AgentState;
import com.bettafish.insight.tool.InsightDatabaseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Insight Agent —— 对应原版 InsightEngine/agent.py。
 *
 * MCP 工具（情感分析）通过 Spring AI 自动注入，LLM 会自动决定何时调用。
 * 无需在代码中显式编排情感分析调用。
 */
@Service
public class InsightAgent extends AbstractAnalysisAgent {

    private final InsightDatabaseTools databaseTools;
    private final SyncMcpToolCallbackProvider mcpToolProvider;

    public InsightAgent(
            @Qualifier("insightChatClient") ChatClient chatClient,
            InsightDatabaseTools databaseTools,
            SyncMcpToolCallbackProvider mcpToolProvider
    ) {
        super(chatClient);
        this.databaseTools = databaseTools;
        this.mcpToolProvider = mcpToolProvider;
    }

    @Override
    public String getName() { return "insight"; }

    @Override
    protected AgentState executeFirstSearch(AgentState state) {
        // Spring AI 自动将以下工具注册到 ChatClient：
        // 1. InsightDatabaseTools 的 @Tool 方法（数据库搜索）
        // 2. MCP Server 暴露的 analyzeSentiment / batchAnalyzeSentiment 工具
        //
        // LLM 会根据 prompt 和上下文自动决定使用哪些工具。
        // 例如：当 LLM 需要分析评论情感时，会自动调用 analyzeSentiment。
        String response = chatClient.prompt()
                .system(InsightPrompts.FIRST_SEARCH_SYSTEM)
                .user(InsightPrompts.buildFirstSearchPrompt(state))
                .tools(databaseTools)           // 数据库搜索工具
                .toolCallbacks(mcpToolProvider)  // MCP 工具（含情感分析）
                .call()
                .content();

        state.addSearchResults(response);
        return state;
    }

    @Override
    protected AgentState executeReflection(AgentState state, int round) {
        String hostGuidance = state.getHostGuidance();
        String prompt = InsightPrompts.buildReflectionPrompt(state, round, hostGuidance);

        String response = chatClient.prompt()
                .system(InsightPrompts.REFLECTION_SYSTEM)
                .user(prompt)
                .tools(databaseTools)
                .toolCallbacks(mcpToolProvider)
                .call()
                .content();

        state.addReflectionResults(round, response);
        return state;
    }
}
```

这种架构使得情感分析的调用完全由 LLM 自主决策——当分析上下文需要情感数据时，LLM 会自动调用 MCP Server 的 `analyzeSentiment` 工具；当不需要时则跳过。这比原版硬编码的调用方式更加灵活和智能。

---

## Chapter 9: 实施路线图

本章将 BettaFish Java/Spring AI 重构项目分解为 11 周的分阶段实施计划，明确每周的交付物、关键依赖和验收标准。同时识别关键风险并提供应对策略，最后制定完整的测试方案。

---

### 9.1 分阶段实施计划（11 weeks）

#### Week 1-2: Foundation — 基础骨架搭建

**目标**：搭建完整的项目骨架，所有模块可编译通过，核心配置体系就绪。

| 任务 | 详细内容 | 交付物 |
|------|---------|--------|
| Maven 多模块项目初始化 | 创建 parent POM + 9 个子模块，配置 BOM 版本管理 | `pom.xml` × 10 |
| Spring Boot 应用启动 | bettafish-app 主应用，确保 Spring Boot 3.x 正常启动 | `BettaFishApplication.java` |
| 配置体系 | `BettaFishProperties` + `application.yml`，涵盖数据库、LLM、搜索等全部配置项 | 配置类 + YAML |
| 多 LLM 自动配置 | `LlmAutoConfiguration`：为每个 Agent 创建独立的 `ChatClient` Bean | 5 个 ChatClient Bean |
| 数据库层 | JPA 实体类映射原版所有表（daily_news, daily_topics, crawling_tasks 等） | Entity + Repository |
| WebSocket 基础设施 | STOMP 配置 + 基本的消息推送框架 | `WebSocketConfig.java` |
| CI/CD 基础 | Dockerfile + GitHub Actions / GitLab CI 配置 | 构建流水线 |

**验收标准**：
- `mvn clean compile` 全模块通过
- Spring Boot 应用启动成功，Actuator 健康检查返回 UP
- 数据库连接正常，Flyway/Liquibase 迁移成功
- WebSocket 端点可连接

```java
// Week 1 核心交付物示例：BettaFishApplication.java
@SpringBootApplication
@EnableConfigurationProperties(BettaFishProperties.class)
public class BettaFishApplication {
    public static void main(String[] args) {
        SpringApplication.run(BettaFishApplication.class, args);
    }
}
```

#### Week 3-4: Tool Services — 工具服务层

**目标**：实现所有外部 API 工具的封装，Agent 可调用的工具集就绪。

| 任务 | 详细内容 | 交付物 |
|------|---------|--------|
| Tavily 搜索工具 | 封装 6 个搜索方法为 `@Tool` 注解的 Bean | `TavilySearchTools.java` |
| Bocha 多模态搜索 | 封装 5 个搜索方法 + 结构化数据解析 | `BochaSearchTools.java` |
| Anspire 搜索工具 | 封装搜索 API + 重试机制 | `AnspireSearchTools.java` |
| 数据库搜索工具 | InsightEngine 的数据库查询工具（热点内容、话题、评论） | `InsightDatabaseTools.java` |
| 情感分析 MCP Server | ONNX 模型加载 + MCP Server 独立应用 | `bettafish-sentiment-mcp` 模块 |
| 重试工具 | 统一的 API 重试框架（对应原版 `retry_helper.py`） | `RetryHelper.java` |

**验收标准**：
- 每个 Tool 有独立的单元测试，mock 外部 API
- 情感分析 MCP Server 独立启动，MCP 协议可联通
- 重试机制在 API 超时时正确触发

```java
// Week 3 核心交付物示例：统一重试框架
@Component
public class RetryHelper {

    /**
     * 带指数退避的重试执行器。
     * 对应原版 utils/retry_helper.py 的 @with_graceful_retry 装饰器。
     */
    public <T> T executeWithRetry(
            RetryConfig config,
            Supplier<T> action,
            T defaultReturn
    ) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < config.maxRetries()) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt < config.maxRetries()) {
                    long delay = (long) (config.baseDelay()
                            * Math.pow(config.backoffMultiplier(), attempt - 1));
                    try {
                        Thread.sleep(Math.min(delay, config.maxDelay()));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("重试 {} 次后仍失败: {}", config.maxRetries(),
                lastException != null ? lastException.getMessage() : "未知错误");
        return defaultReturn;
    }
}

public record RetryConfig(
        int maxRetries,
        long baseDelay,
        double backoffMultiplier,
        long maxDelay
) {
    public static final RetryConfig SEARCH_API =
            new RetryConfig(3, 1000, 2.0, 10000);
    public static final RetryConfig LLM_API =
            new RetryConfig(3, 2000, 2.0, 30000);
}
```

#### Week 5-6: Agent Core — Agent 核心框架

**目标**：实现 Agent 基类和前两个 Agent（QueryAgent、MediaAgent）的完整流程。

| 任务 | 详细内容 | 交付物 |
|------|---------|--------|
| Agent 基类 | `AbstractAnalysisAgent`：搜索-反思循环通用框架 | 基类 + 接口 |
| AgentState 模型 | 状态管理：搜索结果、摘要、反思轮次、HOST 引导 | `AgentState.java` |
| QueryAgent | 6 节点完整实现：结构规划 → 首次搜索 → 首次总结 → 反思 → 反思总结 → 格式化 | `QueryAgent.java` |
| MediaAgent | 5 节点完整实现：与 QueryAgent 类似但使用 Bocha/Anspire 工具 | `MediaAgent.java` |
| Prompt 模板 | 移植所有 System Prompt 和 JSON Schema 定义 | `*Prompts.java` × 3 |
| 结构化输出 | Spring AI Structured Output 配置，LLM 输出自动映射为 Java 对象 | JSON Schema + Record |

**验收标准**：
- QueryAgent 端到端执行通过（给定查询 → 输出报告文本）
- MediaAgent 端到端执行通过
- 反思循环按配置执行 N 轮
- Agent 发言事件正确发布

```java
// Week 5 核心交付物示例：AgentState
public class AgentState {

    private final String query;
    private List<ReportSection> reportStructure;
    private final List<SearchRound> searchRounds = new ArrayList<>();
    private String latestSummary;
    private String hostGuidance;
    private final Map<String, Object> metadata = new HashMap<>();

    public AgentState(String query) {
        this.query = query;
    }

    /** 添加搜索轮次结果 */
    public void addSearchRound(int round, String searchQuery,
                                String toolUsed, String rawResults, String summary) {
        searchRounds.add(new SearchRound(round, searchQuery, toolUsed, rawResults, summary));
        this.latestSummary = summary;
    }

    /** 获取所有搜索结果的文本汇总（供反思 prompt 使用） */
    public String getAllSearchResultsText() {
        return searchRounds.stream()
                .map(r -> "=== 第 %d 轮搜索 (工具: %s, 查询: %s) ===\n%s"
                        .formatted(r.round(), r.toolUsed(), r.searchQuery(), r.rawResults()))
                .collect(Collectors.joining("\n\n"));
    }

    /** 获取所有段落最新状态（供最终格式化使用） */
    public String getAllParagraphStates() {
        return searchRounds.stream()
                .map(SearchRound::summary)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    public record SearchRound(int round, String searchQuery,
                               String toolUsed, String rawResults, String summary) {}
    public record ReportSection(String title, String content) {}

    // getters, setters...
}
```

#### Week 7-8: Insight + Forum — InsightAgent 与论坛协作

**目标**：实现 InsightAgent 和 ForumEngine 论坛协作机制。

| 任务 | 详细内容 | 交付物 |
|------|---------|--------|
| InsightAgent | 完整实现，集成数据库搜索 + MCP 情感分析 | `InsightAgent.java` |
| ForumCoordinator | 事件驱动的论坛协调器，替代原版文件轮询 | `ForumCoordinator.java` |
| ForumHost | LLM 主持人，生成引导发言 | `ForumHost.java` |
| ForumReader | Agent 读取最新 HOST 发言 | `ForumReader.java` |
| ForumMessageStore | 论坛消息存储（内存 + 可选持久化） | `ForumMessageStore.java` |
| WebSocket 推送集成 | Forum 消息实时推送到前端 | WebSocket 集成 |

**验收标准**：
- 三个 Agent 并行运行时，Forum 消息按序收集
- 缓冲区满 5 条时正确触发 HOST 发言
- HOST 发言通过 WebSocket 推送到前端
- Agent 反思轮可读取到最新 HOST 引导

#### Week 9: Report Pipeline — 报告管线

**目标**：实现完整的报告生成管线，从三个 Agent 的输出到最终 HTML/PDF。

| 任务 | 详细内容 | 交付物 |
|------|---------|--------|
| IR 类型定义 | 16 种 Block + 12 种 InlineMark 的完整 record 定义 | `com.bettafish.report.ir` 包 |
| IRValidator | 基于模式匹配的校验器 | `IRValidator.java` |
| DocumentStitcher | 章节装订：排序、锚点去重、TOC 生成 | `DocumentStitcher.java` |
| HtmlRenderer | IR → HTML 渲染（16 种 Block 全部支持） | `HtmlRenderer.java` |
| PdfRenderer | IR → PDF 渲染（OpenPDF） | `PdfRenderer.java` |
| ReportAgent | 6 步管线：模板选择 → 布局 → 篇幅规划 → 章节生成 → 装订 → 渲染 | `ReportAgent.java` |
| JSON 修复 | 跨引擎 JSON 修复机制（对应原版 json_rescue_clients） | JSON 修复逻辑 |
| 模板系统 | 6 种报告模板的定义和选择 | 模板配置 |

**验收标准**：
- 给定 mock 的 Agent 输出，能生成完整的 HTML 报告
- HTML 报告包含目录、hero 区域、图表、SWOT/PEST 分析
- IR 校验器能检出非法 Block 并报告路径
- JSON 修复在解析失败时正确触发

#### Week 10: Integration — 全流程集成

**目标**：将所有模块串联为完整的端到端流程。

| 任务 | 详细内容 | 交付物 |
|------|---------|--------|
| AnalysisOrchestrator | 编排器：并行启动 3 Agent → Forum 协作 → Report 生成 | `AnalysisOrchestrator.java` |
| REST API | 全部 API 端点实现（/api/search, /api/status, /api/forum/log 等） | `AnalysisController.java` |
| WebSocket 完整集成 | 论坛消息、控制台输出、状态更新的实时推送 | WebSocket 控制器 |
| 系统生命周期管理 | 启动、关闭、状态监控 | `SystemLifecycleService.java` |
| 错误处理 | 全局异常处理、Agent 超时处理、优雅降级 | 异常处理器 |
| 前端适配 | API 接口对齐原版前端 | API 文档 |

**验收标准**：
- POST `/api/search` → 异步启动分析 → WebSocket 推送进度 → 报告生成完成
- 系统状态可通过 API 和 WebSocket 实时查看
- Agent 异常不会导致整个系统崩溃

```java
// Week 10 核心交付物示例：完整的分析编排器
@Service
@Slf4j
public class AnalysisOrchestrator {

    private final QueryAgent queryAgent;
    private final MediaAgent mediaAgent;
    private final InsightAgent insightAgent;
    private final ReportAgent reportAgent;
    private final ForumReader forumReader;
    private final SimpMessagingTemplate messagingTemplate;

    public Mono<String> executeAnalysis(String query) {
        log.info("开始分析: {}", query);
        messagingTemplate.convertAndSend("/topic/status",
                Map.of("phase", "STARTED", "query", query));

        // 并行启动三个 Agent
        Mono<AnalysisResult> queryResult = queryAgent.analyze(query, forumReader)
                .doOnSuccess(r -> log.info("QueryAgent 完成"))
                .doOnError(e -> log.error("QueryAgent 失败", e))
                .onErrorReturn(AnalysisResult.empty("query"));

        Mono<AnalysisResult> mediaResult = mediaAgent.analyze(query, forumReader)
                .doOnSuccess(r -> log.info("MediaAgent 完成"))
                .doOnError(e -> log.error("MediaAgent 失败", e))
                .onErrorReturn(AnalysisResult.empty("media"));

        Mono<AnalysisResult> insightResult = insightAgent.analyze(query, forumReader)
                .doOnSuccess(r -> log.info("InsightAgent 完成"))
                .doOnError(e -> log.error("InsightAgent 失败", e))
                .onErrorReturn(AnalysisResult.empty("insight"));

        return Mono.zip(queryResult, mediaResult, insightResult)
                .doOnSuccess(t -> messagingTemplate.convertAndSend("/topic/status",
                        Map.of("phase", "AGENTS_COMPLETE")))
                .flatMap(tuple -> {
                    messagingTemplate.convertAndSend("/topic/status",
                            Map.of("phase", "REPORT_GENERATING"));
                    return reportAgent.generateReport(
                            query, tuple.getT1(), tuple.getT2(), tuple.getT3());
                })
                .doOnSuccess(url -> {
                    messagingTemplate.convertAndSend("/topic/report-complete",
                            Map.of("url", url, "query", query));
                    log.info("分析完成: {}", url);
                })
                .timeout(Duration.ofMinutes(30))
                .doOnError(e -> {
                    messagingTemplate.convertAndSend("/topic/status",
                            Map.of("phase", "FAILED", "error", e.getMessage()));
                    log.error("分析失败", e);
                });
    }
}
```

#### Week 11: Testing & Deployment — 测试与部署

**目标**：完善测试覆盖率，完成容器化部署。

| 任务 | 详细内容 | 交付物 |
|------|---------|--------|
| 单元测试 | 每个 Agent、工具、IR 组件的单元测试 | 测试类 × 30+ |
| 集成测试 | Forum 协作流程、Report 管线的集成测试 | 集成测试类 × 10+ |
| E2E 测试 | 完整分析流程的端到端测试 | E2E 测试 × 3 |
| 性能测试 | 并发分析、大报告生成的性能基准 | JMeter / Gatling 脚本 |
| Docker 部署 | 多服务 Docker Compose 编排 | `docker-compose.yml` |
| 文档 | API 文档、部署指南、运维手册 | Markdown 文档 |

---

### 9.2 关键风险与对策

#### 风险 1: LLM Provider 限流

**风险描述**：BettaFish 同时使用 5 个不同的 LLM Provider（Kimi、Gemini、DeepSeek、AiHubMix、SiliconFlow），每个都有 API 速率限制。三个 Agent 并行运行时，瞬时请求量可能触发限流。

**对策：断路器 + 速率限制器**

```java
@Configuration
public class ResilienceConfig {

    /**
     * 为每个 LLM Provider 配置独立的断路器。
     * 当连续失败超过阈值时，断路器打开，避免级联故障。
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)        // 失败率 50% 时打开
                .waitDurationInOpenState(Duration.ofSeconds(30))  // 打开 30s 后半开
                .slidingWindowSize(10)            // 滑动窗口 10 个请求
                .permittedNumberOfCallsInHalfOpenState(3)  // 半开时允许 3 个探测
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    /**
     * 为每个 Provider 配置速率限制器。
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)              // 每周期 10 个请求
                .limitRefreshPeriod(Duration.ofSeconds(1))  // 1 秒刷新
                .timeoutDuration(Duration.ofSeconds(30))    // 等待超时 30s
                .build();
        return RateLimiterRegistry.of(config);
    }
}
```

#### 风险 2: 大报告生成超时

**风险描述**：原版报告目标 30,000-40,000 字，需要逐章节调用 LLM 生成。8-12 个章节 × 每章节 30-60 秒 = 总计可能超过 10 分钟。

**对策：流式生成 + 进度推送**

```java
// 流式章节生成 + WebSocket 进度推送
private Flux<ChapterIR> generateChaptersStreaming(
        List<ChapterInstruction> instructions,
        AnalysisResult... results) {

    return Flux.fromIterable(instructions)
            .concatMap(instruction -> {
                // 推送当前章节进度
                messagingTemplate.convertAndSend("/topic/report-progress",
                        Map.of(
                                "phase", "CHAPTER_GENERATING",
                                "chapterId", instruction.chapterId(),
                                "chapterTitle", instruction.title(),
                                "progress", instruction.order() + "/" + instructions.size()
                        ));

                return Mono.fromCallable(() ->
                        generateChapterWithRetry(instruction, results))
                        .subscribeOn(Schedulers.boundedElastic());
            })
            .doOnNext(chapter -> {
                messagingTemplate.convertAndSend("/topic/report-progress",
                        Map.of(
                                "phase", "CHAPTER_COMPLETE",
                                "chapterId", chapter.chapterId(),
                                "wordCount", chapter.wordCount()
                        ));
            });
}
```

#### 风险 3: 多 Agent 协调故障

**风险描述**：三个 Agent 并行运行时，任一 Agent 崩溃可能影响论坛协作和报告生成。

**对策：事件溯源 + 优雅降级**

```java
/**
 * Agent 执行容错包装器。
 * 任一 Agent 失败时，使用空结果继续流程，确保报告仍可生成。
 */
private Mono<AnalysisResult> executeAgentWithFallback(
        AnalysisAgent agent, String query) {

    return agent.analyze(query, forumReader)
            .timeout(Duration.ofMinutes(15))
            .doOnError(e -> {
                log.error("{} Agent 执行失败: {}", agent.getName(), e.getMessage());
                // 发布失败事件，Forum 协调器会记录
                eventPublisher.publishEvent(new AgentFailureEvent(
                        this, agent.getName(), e.getMessage()));
            })
            .onErrorReturn(AnalysisResult.empty(agent.getName()));
}
```

#### 风险 4: IR 校验失败

**风险描述**：LLM 生成的章节 JSON 可能不符合 IR Schema，导致渲染失败。

**对策：三级修复管线**

```
LLM 生成 → IR 校验
              ├── 通过 → 直接使用
              └── 失败 → 第一级修复（同引擎重试，最多 3 次）
                           ├── 成功 → 使用
                           └── 失败 → 第二级修复（跨引擎 JSON 修复）
                                        ├── 成功 → 使用
                                        └── 失败 → 第三级兜底（占位章节）
```

```java
/**
 * 三级修复管线实现。
 */
private ChapterIR repairPipeline(ChapterInstruction instruction,
                                  String rawJson,
                                  List<IRValidator.ValidationError> errors) {
    // 第一级：请求同引擎修复
    log.info("第一级修复: 请求主引擎修复 {} 条错误", errors.size());
    try {
        String repairPrompt = ReportPrompts.buildChapterRepairPrompt(
                rawJson, errors.stream().map(Object::toString).toList());
        String repaired = chatClient.prompt()
                .system(ReportPrompts.CHAPTER_JSON_REPAIR_SYSTEM)
                .user(repairPrompt)
                .call()
                .content();
        ChapterIR chapter = parseChapterJson(repaired);
        var result = validator.validateChapter(chapter);
        if (result.valid()) {
            log.info("第一级修复成功");
            return chapter;
        }
    } catch (Exception e) {
        log.warn("第一级修复失败: {}", e.getMessage());
    }

    // 第二级：跨引擎 JSON 修复
    log.info("第二级修复: 尝试跨引擎 JSON 修复");
    for (ChatClient rescueClient : rescueClients) {
        try {
            String rescued = rescueClient.prompt()
                    .system(ReportPrompts.CHAPTER_JSON_RECOVERY_SYSTEM)
                    .user(rawJson.substring(Math.max(0, rawJson.length() - 8000)))
                    .call()
                    .content();
            ChapterIR chapter = parseChapterJson(rescued);
            var result = validator.validateChapter(chapter);
            if (result.valid()) {
                log.info("第二级修复成功");
                return chapter;
            }
        } catch (Exception ignored) {}
    }

    // 第三级：生成占位章节
    log.warn("所有修复尝试失败，生成占位章节: {}", instruction.chapterId());
    return createPlaceholderChapter(instruction);
}
```

---

### 9.3 测试策略

#### 9.3.1 单元测试

每个组件都需要独立的单元测试，使用 Mock 隔离外部依赖：

```java
/**
 * IRValidator 单元测试示例。
 */
@ExtendWith(MockitoExtension.class)
class IRValidatorTest {

    private final IRValidator validator = new IRValidator();

    @Test
    @DisplayName("合法的 HeadingBlock 应通过校验")
    void validHeadingBlock_shouldPass() {
        var chapter = ChapterIR.of("ch1", "测试章节", 1, List.of(
                new HeadingBlock(2, "概述", "ch1-overview"),
                ParagraphBlock.ofPlainText("这是测试内容。")
        ));

        var result = validator.validateChapter(chapter);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("HeadingBlock level 超出范围应报错")
    void invalidHeadingLevel_shouldFail() {
        var chapter = ChapterIR.of("ch1", "测试章节", 1, List.of(
                new HeadingBlock(7, "非法级别", "ch1-invalid")
        ));

        var result = validator.validateChapter(chapter);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e ->
                e.path().contains("level") && e.message().contains("1-6"));
    }

    @Test
    @DisplayName("SWOT impact 非法值应报错")
    void invalidSwotImpact_shouldFail() {
        var swot = new SwotTableBlock("SWOT", null,
                List.of(new SwotItem("测试优势", "非法值")),
                null, null, null);
        var chapter = ChapterIR.of("ch1", "测试章节", 1, List.of(swot));

        var result = validator.validateChapter(chapter);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e ->
                e.path().contains("impact") && e.message().contains("非法"));
    }

    @Test
    @DisplayName("EngineQuote 内部包含非 paragraph 类型应报错")
    void engineQuoteWithNonParagraph_shouldFail() {
        var eq = new EngineQuoteBlock("insight",
                EngineQuoteBlock.ENGINE_AGENT_TITLES.get("insight"),
                List.of(new HeadingBlock(3, "非法嵌套标题", "nested")));
        var chapter = ChapterIR.of("ch1", "测试", 1, List.of(eq));

        var result = validator.validateChapter(chapter);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e ->
                e.message().contains("仅允许 paragraph"));
    }

    @Test
    @DisplayName("全局约束：SWOT 和 PEST 不得在同一章节")
    void swotAndPestInSameChapter_shouldFail() {
        var swot = new SwotTableBlock("SWOT", null,
                List.of(new SwotItem("测试", "高")),
                null, null, null);
        var pest = new PestTableBlock("PEST", null,
                List.of(new PestItem("政策", "描述", "正面利好", "高")),
                null, null, null);
        var doc = new DocumentIR("测试报告", null, null, null, null, null,
                List.of(ChapterIR.of("ch1", "章节一", 1, List.of(swot, pest))),
                Map.of());

        var result = validator.validateDocument(doc);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e ->
                e.message().contains("不得出现在同一章节"));
    }
}
```

**Agent 单元测试**：

```java
/**
 * QueryAgent 单元测试 —— Mock LLM 和搜索工具。
 */
@ExtendWith(MockitoExtension.class)
class QueryAgentTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatClient.CallResponseSpec callResponse;
    @Mock private ChatClient.PromptRequestSpec promptSpec;
    @Mock private WebSearchTools searchTools;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private QueryAgent queryAgent;

    @Test
    @DisplayName("QueryAgent 应执行完整的搜索-反思循环")
    void analyze_shouldExecuteFullCycle() {
        // 配置 mock
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.tools(any())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callResponse);
        when(callResponse.content()).thenReturn(
                "{\"search_query\":\"测试\",\"search_tool\":\"basic_search_news\",\"reasoning\":\"测试\"}");

        // 执行
        var result = queryAgent.analyze("中美贸易战", mock(ForumReader.class)).block();

        // 验证
        assertThat(result).isNotNull();
        assertThat(result.agentName()).isEqualTo("query");
        // 验证反思轮次执行了正确的次数
        verify(chatClient, atLeast(3)).prompt();
    }
}
```

#### 9.3.2 集成测试

Forum 协作和 Report 管线需要多组件联动的集成测试：

```java
/**
 * Forum 协作集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class ForumCoordinatorIntegrationTest {

    @Autowired private ForumCoordinator coordinator;
    @Autowired private ForumMessageStore messageStore;
    @Autowired private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("5 条 Agent 发言应触发 HOST 发言")
    void fiveAgentSpeeches_shouldTriggerHostSpeech() throws InterruptedException {
        // 发布 5 条 Agent 发言事件
        for (int i = 0; i < 5; i++) {
            eventPublisher.publishEvent(new AgentSpeechEvent(
                    this, i % 3 == 0 ? "query" : i % 3 == 1 ? "media" : "insight",
                    "第 %d 轮分析结果：发现了重要的舆情趋势变化...".formatted(i + 1)));
        }

        // 等待异步处理
        Thread.sleep(3000);

        // 验证 HOST 消息已生成
        var hostMessages = messageStore.findBySource("HOST");
        assertThat(hostMessages).isNotEmpty();
        assertThat(hostMessages.get(0).getContent()).isNotBlank();
    }
}

/**
 * Report 管线集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportPipelineIntegrationTest {

    @Autowired private DocumentStitcher stitcher;
    @Autowired private HtmlRenderer htmlRenderer;
    @Autowired private IRValidator validator;

    @Test
    @DisplayName("完整的 IR → 装订 → HTML 渲染流程")
    void fullRenderPipeline_shouldProduceValidHtml() throws Exception {
        // 构造测试用 ChapterIR
        var chapters = List.of(
                ChapterIR.of("ch1", "一、事件概述", 1, List.of(
                        new HeadingBlock(2, "事件概述", "ch1-overview"),
                        ParagraphBlock.ofPlainText("这是事件概述的详细内容..."),
                        new KpiGridBlock(List.of(
                                new KpiItem("舆情热度", "89.3", "万次", "+12.5%", "positive"),
                                new KpiItem("情感倾向", "偏负面", null, "-5.2%", "negative")
                        ), null)
                )),
                ChapterIR.of("ch2", "二、深度分析", 2, List.of(
                        new HeadingBlock(2, "深度分析", "ch2-analysis"),
                        ParagraphBlock.ofPlainText("深度分析内容..."),
                        new SwotTableBlock("SWOT 分析", "总体评估",
                                List.of(new SwotItem("品牌知名度高", "高")),
                                List.of(new SwotItem("公关响应迟缓", "中高")),
                                List.of(new SwotItem("社交媒体传播", "中")),
                                List.of(new SwotItem("竞争对手借势", "高")))
                ))
        );

        var layout = new DocumentLayout(
                "舆情分析报告：测试事件",
                "基于多源数据的综合分析",
                null, null, null, Map.of());

        // 装订
        DocumentIR doc = stitcher.stitch(layout, chapters);

        // 校验
        var validationResult = validator.validateDocument(doc);
        assertThat(validationResult.valid()).isTrue();

        // 渲染
        String htmlPath = htmlRenderer.render(doc);
        assertThat(htmlPath).isNotBlank();
        String htmlContent = Files.readString(Path.of(htmlPath));
        assertThat(htmlContent).contains("舆情分析报告");
        assertThat(htmlContent).contains("SWOT");
        assertThat(htmlContent).contains("kpi-grid");
    }
}
```

#### 9.3.3 端到端测试

E2E 测试模拟完整的用户交互流程：

```java
/**
 * 端到端分析流程测试。
 *
 * 使用 WireMock 模拟外部 LLM API，验证从用户输入到报告输出的完整流程。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-test")
class FullAnalysisPipelineE2ETest {

    @Autowired private TestRestTemplate restTemplate;
    @LocalServerPort private int port;

    @Test
    @DisplayName("完整分析流程：搜索请求 → Agent 分析 → 报告生成")
    void fullPipeline_shouldGenerateReport() throws Exception {
        // Step 1: 提交分析请求
        var response = restTemplate.postForEntity(
                "/api/search",
                Map.of("query", "某某事件舆情分析"),
                Map.class
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("success", true);

        // Step 2: 轮询状态直到完成（最多等待 5 分钟）
        boolean completed = false;
        for (int i = 0; i < 60; i++) {
            Thread.sleep(5000);
            var status = restTemplate.getForObject("/api/status", Map.class);
            if ("COMPLETED".equals(status.get("phase"))) {
                completed = true;
                break;
            }
            if ("FAILED".equals(status.get("phase"))) {
                fail("分析失败: " + status.get("error"));
            }
        }
        assertThat(completed).isTrue();

        // Step 3: 验证论坛日志
        var forumLog = restTemplate.getForObject("/api/forum/log", Map.class);
        assertThat((List<?>) forumLog.get("parsed_messages")).isNotEmpty();

        // Step 4: 验证报告文件已生成
        var reports = Files.list(Path.of("final_reports"))
                .filter(p -> p.toString().endsWith(".html"))
                .toList();
        assertThat(reports).isNotEmpty();
    }
}
```

#### 9.3.4 测试覆盖率目标

| 模块 | 目标覆盖率 | 关键测试点 |
|------|-----------|-----------|
| `bettafish-common` | 90%+ | 配置解析、重试逻辑、事件模型 |
| `bettafish-report-engine` (IR) | 95%+ | 所有 16 种 Block 校验、装订逻辑、锚点去重 |
| `bettafish-report-engine` (渲染) | 85%+ | 每种 Block 的 HTML 输出、特殊字符转义 |
| `bettafish-query-engine` | 80%+ | 搜索-反思循环、工具调用、Prompt 构建 |
| `bettafish-media-engine` | 80%+ | 多模态搜索工具、结构化数据解析 |
| `bettafish-insight-engine` | 80%+ | 数据库查询工具、MCP 集成 |
| `bettafish-forum-engine` | 90%+ | 事件驱动协调、缓冲区逻辑、HOST 生成 |
| `bettafish-sentiment-mcp` | 90%+ | ONNX 推理、分词器、MCP 协议 |
| 集成测试 | - | Forum 多 Agent 协作、Report 全管线 |
| E2E 测试 | - | 3 个典型场景（品牌危机、政策分析、热点事件） |
