# 推荐系统商业化升级详细方案

## 1. 结论先行

当前系统已经不是一个简单的“前端随机刷图”项目，而是具备了推荐系统的核心骨架：行为事件、Redis 实时兴趣、离线特征、Milvus/深度召回、首页多源召回、深度精排、线上指标、诊断接口和实验保护雏形。

Claude 的评价方向基本正确，但需要结合当前代码做一次校准：系统并非只有单路 ANN 向量召回。`FeedRecallService` 里已经存在热门、关注作者、内容兴趣、在线兴趣、显式兴趣、近期正反馈、向量、探索等召回方式。真正的差距不是“有没有多路召回”，而是这些能力还没有完全沉淀为可配置、可观测、可评估、可灰度的商业级推荐链路。

本方案的核心目标是：

- 把已有多路召回升级成标准候选生成框架。
- 补上 i2i 协同过滤、实时 CTR、曝光去重、内容向量更新。
- 建立离线回放评估和线上 A/B 实验闭环。
- 解决首页重复、冷启动、加载慢、相似内容不稳定等体验问题。
- 在数据还不大的情况下，先做“可评价、可解释、可迭代”，再考虑扩大数据集。

## 2. 当前系统现状判断

### 2.1 已经具备的能力

后端当前已经有以下基础：

- 行为事件：曝光、点击、详情浏览、点赞、收藏、评论、分享、负反馈、关注等。
- Kafka 事件入口：事件可以异步进入后续推荐特征链路。
- Redis 实时行为：`FeatureService` 和 `feature_engineering.py` 已经维护近期行为序列和在线兴趣。
- 离线特征表：`user_features`、`post_features` 已经由脚本批量生成。
- 深度召回/精排服务：Java 后端通过 `DeepRankingService`、`VectorRecallService` 调用深度模型服务。
- 多源召回雏形：热门、关注、内容、在线兴趣、显式兴趣、近期正反馈、向量、探索。
- 线上观测入口：已有 `metrics`、`diagnostics`、`workbench`、`source health` 一类接口雏形。
- 实验保护雏形：`RecommendationProperties.FeedQuota` 已经有 `abEnabled`、`treatmentRatio`、`guardEnabled` 等配置。

这些能力说明系统已经越过了“demo 阶段”，下一步不应盲目堆模型，而应补足工程闭环。

### 2.2 主要问题

当前最影响效果和体验的问题是：

- 首页重复：候选合并、曝光去重、分页去重和兜底热门混入策略还不够严格。
- 首页加载慢：召回源串行、深度候选过多、特征拼接在线成本偏高、图片加载也会放大体感延迟。
- 相似内容加载不稳定：详情页相似推荐的候选量、分页策略、布局填充和接口延迟需要统一预算。
- 推荐效果难评估：目前主要靠切换用户人工看感受，缺少离线 replay 和线上实验对照。
- 数据集有限：Unsplash 数据适合做视觉内容原型，但标题、标签、作者、行为真实性都不足。
- 标签体系不稳：哈希 tag 可以快速起步，但不利于长期模型学习和可解释性。

## 3. 目标架构

商业级链路建议拆成 7 层：

```text
用户请求
  ↓
用户上下文解析
  - user_id / anonymous_id
  - surface / device / page_no / scene
  - experiment_id / model_version
  ↓
召回层 Candidate Generation
  - hot 热门兜底
  - vector 向量召回
  - i2i 协同过滤召回
  - follow 关注作者召回
  - realtime 最近行为召回
  - content/taxonomy 内容兴趣召回
  - explore 探索召回
  ↓
过滤层 Filtering
  - 已曝光过滤
  - 已删除/不可见过滤
  - 作者屏蔽/拉黑过滤
  - 重复图片/近重复内容过滤
  - 安全审核过滤
  ↓
粗排/预排 Pre-rank
  - source score
  - hot score
  - realtime CTR
  - freshness
  - user-term match
  ↓
精排 Rank
  - MTL CTR/CVR/Quality
  - 用户序列特征
  - 内容特征
  - 交叉特征
  ↓
重排 Re-rank
  - 多样性
  - 作者打散
  - 主题打散
  - 新内容探索配额
  - 负反馈降权
  ↓
响应与日志
  - feed items
  - reason/source
  - request trace
  - exposure event
```

这条链路的重点不是每层都复杂，而是每层都有明确输入、输出、耗时预算、降级策略和可观测字段。

## 4. 分阶段升级路线

## 阶段 0：基线梳理与指标定义

周期：3 到 5 天。

目标：先让系统能被评价，否则后续所有模型升级都只能靠感觉判断。

### 任务 0.1 固定核心指标

首页和详情页至少要统计：

| 指标 | 含义 | 用途 |
| --- | --- | --- |
| exposure | 曝光量 | 作为所有比例指标分母 |
| CTR | 点击率 | 首页推荐吸引力 |
| DTR | 详情浏览率 | 卡片到详情转化 |
| Like Rate | 点赞率 | 正反馈质量 |
| Favorite Rate | 收藏率 | 高意图正反馈 |
| Comment Rate | 评论率 | 强互动 |
| Share Rate | 分享率 | 高价值传播 |
| Negative Rate | 不感兴趣/隐藏率 | 质量红线 |
| Duplicate Rate | 首屏/前 100 条重复率 | 首页重复问题 |
| Source Coverage | 各召回源贡献占比 | 判断召回源是否有效 |
| p95/p99 Latency | 接口延迟 | 判断加载慢来源 |

### 任务 0.2 固定事件字段

所有推荐相关事件必须包含：

```text
event_id
user_id
anonymous_id
post_id
author_id
event_type
surface
scene
page_no
rank_position
request_id
recall_source
experiment_id
bucket
model_version
created_at
payload_json
```

如果当前事件没有全部字段，可以先允许为空，但新链路必须逐步补齐。

### 任务 0.3 建立问题看板

建议新增或完善 4 个视图：

- 首页总览：CTR、DTR、负反馈率、重复率、延迟。
- 召回源视图：每个 source 的返回量、命中量、最终曝光量、点击量。
- 用户分群视图：匿名用户、新用户、低行为用户、活跃用户分别看效果。
- 详情页相似推荐视图：similar 请求延迟、相似候选数、点击率、滚动加载成功率。

验收标准：

- 能回答“某个用户为什么看到这些内容”。
- 能回答“哪个召回源贡献了点击，哪个源只是凑数”。
- 能回答“首页慢是召回慢、精排慢、数据库慢还是前端图片慢”。

## 阶段 1：正式化多路召回框架

周期：1 到 2 周。

目标：把现有多源召回从“方法堆叠”升级成可配置、可追踪、可降级的候选生成框架，并补上 i2i 协同过滤。

### 任务 1.1 定义统一召回接口

建议抽象：

```java
public interface RecallSource {
    String name();
    RecallResult recall(RecallContext context);
}
```

`RecallResult` 建议包含：

```text
source
items
raw_count
unique_count
latency_ms
timeout
fallback
error_message
debug_reason
```

每个候选 item 需要保留：

```text
post_id
source
source_score
source_rank
reason_terms
seed_post_id
seed_event_type
```

这样后续无论是排查重复、解释推荐原因，还是做 A/B，都有原始依据。

### 任务 1.2 梳理当前召回源

当前可直接纳入框架的召回源：

| source | 当前能力 | 升级动作 |
| --- | --- | --- |
| hot | 已有热门召回 | 增加分时段热门、平滑 CTR、冷启动配额 |
| social | 已有关注作者召回 | 增加作者更新频率和社交强度权重 |
| content | 已有内容兴趣召回 | 接入 taxonomy ID，减少字符串模糊匹配 |
| online_interest | 已有 Redis 在线兴趣 | 统一实时兴趣 key 和衰减策略 |
| explicit_interest | 已有显式兴趣 | 降低强度，避免用户手动标签过度束缚 |
| recent_positive | 已有近期正反馈 | 改造成 realtime seed recall |
| vector | 已有深度向量召回 | 增加超时降级和候选去重日志 |
| explore | 已有探索召回 | 保留 10% 到 20% 新鲜内容探索 |
| i2i_cf | 缺失 | 新增协同过滤召回 |

### 任务 1.3 新增 i2i 协同过滤召回

i2i 是当前最值得补的召回。它能解决两个问题：

- 用户点过 A 后，推荐“看过 A 的人也喜欢 B”。
- 当用户画像向量不稳定时，仍能通过行为共现得到相似内容。

建议新增表：

```sql
CREATE TABLE post_i2i_neighbors (
  post_id BIGINT NOT NULL,
  neighbor_post_id BIGINT NOT NULL,
  score DOUBLE NOT NULL,
  co_view_count BIGINT NOT NULL DEFAULT 0,
  co_click_count BIGINT NOT NULL DEFAULT 0,
  co_like_count BIGINT NOT NULL DEFAULT 0,
  co_favorite_count BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (post_id, neighbor_post_id),
  INDEX idx_neighbor_score (post_id, score DESC)
);
```

离线构建逻辑：

```text
按 user_id 聚合最近 7 到 30 天行为
  ↓
取每个用户的正向序列：detail/click/like/favorite/comment/share
  ↓
在同一用户短窗口内生成 item pair
  ↓
按事件强度和时间衰减累计共现分数
  ↓
用 item 热度做惩罚，避免热门内容统治所有相似结果
  ↓
每个 post 保留 top 100 neighbor
```

推荐打分公式可以先简单一点：

```text
i2i_score =
  1.0 * co_detail
  + 1.5 * co_click
  + 2.0 * co_like
  + 2.5 * co_favorite
  + 2.0 * co_comment
  + 2.0 * co_share

final_score = i2i_score * recency_decay / log(2 + neighbor_hotness)
```

在线召回：

```text
读取用户最近正反馈 post_id
  ↓
查 post_i2i_neighbors
  ↓
聚合多个 seed 的 neighbor 分数
  ↓
过滤已曝光、已互动、不可见内容
  ↓
返回 i2i_cf 候选
```

### 任务 1.4 候选融合与去重

候选合并不能只按列表 append。建议：

```text
候选池 Map<post_id, Candidate>
  ↓
同 post 多源命中时合并 source list
  ↓
source_score 做归一化
  ↓
按 source weight 计算 merge_score
  ↓
应用作者配额、主题配额、近期曝光过滤
  ↓
进入粗排
```

推荐初始 source 权重：

| source | 登录活跃用户 | 新用户/少行为用户 |
| --- | ---: | ---: |
| vector | 0.18 | 0.10 |
| i2i_cf | 0.20 | 0.05 |
| online_interest | 0.18 | 0.05 |
| recent_positive | 0.12 | 0.00 |
| content | 0.10 | 0.12 |
| social | 0.08 | 0.08 |
| hot | 0.08 | 0.45 |
| explore | 0.06 | 0.15 |

验收标准：

- 首页前 100 条 `post_id` 重复率为 0。
- 每个请求 diagnostics 能看到各 source 返回量、去重后贡献量、最终曝光量。
- 冷启动用户不依赖向量召回也能稳定返回内容。
- i2i_cf 在活跃用户请求里有稳定贡献。

## 阶段 2：实时特征、曝光去重与加载提速

周期：1 到 2 周。

目标：解决“每次刷新混入同样热门内容”“首页慢”“交互后推荐反馈不够即时”的问题。

### 任务 2.1 Redis 实时 CTR 特征

新增 Redis key：

```text
post:metrics:1h:{post_id}
post:metrics:24h:{post_id}
post:metrics:7d:{post_id}
```

Hash 字段：

```text
exposure
click
detail_view
like
favorite
comment
share
negative
hide
```

平滑 CTR：

```text
ctr_1h = (click_1h + alpha) / (exposure_1h + alpha + beta)
dtr_1h = (detail_1h + alpha) / (exposure_1h + alpha + beta)
negative_rate_24h = (negative_24h + alpha) / (exposure_24h + alpha + beta)
```

初始建议：

```text
alpha = 1
beta = 20
```

避免小样本内容因为 1 次点击就异常冲高。

### 任务 2.2 用户近期曝光过滤

新增 Redis key：

```text
user:recent_exposure:{user_id}
```

建议用 ZSET：

```text
score = timestamp
member = post_id
ttl = 1 到 3 天
max_size = 1000 到 3000
```

过滤规则：

- 首页前几页强过滤近期曝光。
- 如果候选不足，可以允许 24 小时外内容重新出现。
- 同一个 request 内必须强去重。
- 同一个 page cursor 内必须强去重。

这会直接改善“热门内容每次刷新都混进来”的体感。

### 任务 2.3 请求内并行召回

首页慢的常见原因是召回源串行执行。建议把召回分成可并行组：

```text
并行组 A：hot / social / content / explore / i2i
并行组 B：vector / deep recall
并行组 C：online interest / recent positive
```

每个 source 设置超时预算：

| source | 预算 |
| --- | ---: |
| Redis online | 50ms |
| DB recall | 150ms |
| i2i DB | 120ms |
| vector recall | 300ms |
| deep rank | 300 到 600ms |

如果 source 超时，记录 diagnostics，然后跳过，不阻塞整页。

### 任务 2.4 精排候选数量控制

建议：

```text
首页每页 size = 24 到 36
召回候选 = size * 6 到 size * 10
粗排后送精排 = 120 到 200
精排后重排输出 = size
```

详情页相似推荐：

```text
首批候选 = 80 到 120
每次滚动补充 = 24 到 36
预取下一页 = 当前页渲染完成后异步触发
```

验收标准：

- 首页 p95 后端接口小于 800ms。
- 详情页相似推荐 p95 小于 600ms。
- 首页刷新不再反复出现同一批热门内容。
- 用户点击/详情浏览后，Redis 在线兴趣和近期行为在下一次请求可见。

## 阶段 3：Tag 哈希升级为 Taxonomy ID

周期：1 到 2 周。

目标：解决哈希碰撞和标签不可治理问题。前端可以先不展示标签，但后端必须有稳定内容语义体系。

### 任务 3.1 新增 taxonomy 表

建议：

```sql
CREATE TABLE taxonomy_terms (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(32) NOT NULL,
  term_key VARCHAR(128) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  source VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  doc_freq BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_type_key (type, term_key)
);

CREATE TABLE post_taxonomy_terms (
  post_id BIGINT NOT NULL,
  term_id BIGINT NOT NULL,
  weight DOUBLE NOT NULL DEFAULT 1.0,
  source VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (post_id, term_id),
  INDEX idx_term_weight (term_id, weight DESC)
);

CREATE TABLE user_taxonomy_terms (
  user_id BIGINT NOT NULL,
  term_id BIGINT NOT NULL,
  window_name VARCHAR(32) NOT NULL,
  weight DOUBLE NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (user_id, term_id, window_name)
);
```

### 任务 3.2 迁移现有标签

数据来源：

- post 标题。
- post 内容。
- topic cluster。
- semantic tags。
- style tags。
- 用户正向行为反推兴趣词。

迁移原则：

- 高频词进入稳定词表。
- 低频词进入 OOV/long-tail 处理。
- 同义词归一化，例如 `city`, `urban`, `street` 可以建立 alias。
- 词表版本化：`taxonomy_version` 写入训练样本和模型元数据。

### 任务 3.3 模型侧替换哈希向量

当前 `hashed_tag_vector` 可以保留作 fallback，但新模型应使用：

```text
term_id -> embedding lookup -> pooling -> post/user taxonomy embedding
```

好处：

- 避免不同 tag 撞到同一个桶。
- 模型可以学习每个 tag 的真实语义权重。
- 推荐原因可以解释为“因为你最近喜欢城市夜景/极简建筑”。

验收标准：

- 训练样本中 95% 以上 post 有至少 1 个 taxonomy term。
- 线上候选 payload 中包含 taxonomy term id 或稳定 key。
- 旧哈希特征仍可回退，新 embedding 特征可灰度。

## 阶段 4：离线回放评估体系

周期：1 到 2 周。

目标：停止只靠前端肉眼判断推荐效果，建立离线可重复评估。

### 任务 4.1 构建 replay 数据集

按时间切分：

```text
训练窗口：T - 30 天 到 T - 1 天
验证窗口：T 到 T + 1 天
测试窗口：T + 1 天 到 T + 2 天
```

对每个用户：

```text
使用验证窗口前的行为构建用户画像
  ↓
在验证窗口第一个曝光/点击时间点模拟一次推荐请求
  ↓
用真实后续点击/点赞/收藏作为 label
  ↓
计算模型是否把真实喜欢的内容排到前 K
```

### 任务 4.2 指标

离线推荐指标：

| 指标 | 用途 |
| --- | --- |
| Recall@K | 候选生成有没有把用户会喜欢的内容召回来 |
| HitRate@K | 前 K 是否命中正样本 |
| NDCG@K | 排序位置是否合理 |
| MRR | 第一个命中位置是否靠前 |
| Coverage | 内容覆盖率 |
| Novelty | 新鲜度 |
| Diversity | 多样性 |
| Duplicate Rate | 重复率 |
| Author Gini | 作者分布是否过度集中 |

分群评估必须包含：

- 匿名/未登录用户。
- 新注册用户。
- 行为少于 5 次用户。
- 行为 5 到 50 次用户。
- 行为超过 50 次活跃用户。
- 新发布内容。
- 长尾低热内容。

### 任务 4.3 评估报告

输出格式建议：

```text
reports/recommendation_eval/{date}/summary.json
reports/recommendation_eval/{date}/source_breakdown.csv
reports/recommendation_eval/{date}/segment_metrics.csv
reports/recommendation_eval/{date}/top_failure_cases.json
```

验收标准：

- 任意一次推荐策略修改，都能离线对比上一版。
- 能看到每个召回源的 Recall@K 和最终点击贡献。
- 能发现“总体变好但新用户变差”的情况。

## 阶段 5：A/B 实验框架正式化

周期：1 到 2 周。

目标：让推荐策略、召回配额、模型版本可以安全上线，而不是直接全量替换。

### 任务 5.1 实验注册表

建议新增：

```sql
CREATE TABLE recommendation_experiments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  experiment_key VARCHAR(128) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  traffic_ratio DOUBLE NOT NULL,
  start_at DATETIME,
  end_at DATETIME,
  config_json JSON NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);
```

`config_json` 示例：

```json
{
  "buckets": {
    "control": {
      "ratio": 0.5,
      "recallPolicy": "home_v1",
      "rankModel": "rank_v1"
    },
    "treatment": {
      "ratio": 0.5,
      "recallPolicy": "home_i2i_v1",
      "rankModel": "rank_v1"
    }
  },
  "guard": {
    "minExposure": 1200,
    "maxCtrDrop": 0.12,
    "maxNegativeLift": 0.25
  }
}
```

### 任务 5.2 稳定分桶

分桶逻辑：

```text
bucket = hash(experiment_key + user_id) % 10000
```

匿名用户可以用 `anonymous_id` 或 session id。

所有曝光事件写入：

```text
experiment_id
bucket
recall_policy_version
rank_model_version
feature_version
```

### 任务 5.3 守护和自动回退

守护指标：

- CTR 相对下降超过阈值。
- 负反馈相对上升超过阈值。
- p95 延迟超过阈值。
- 空结果率超过阈值。
- 错误率超过阈值。

回退方式：

```text
实验桶继续存在
  ↓
请求路由强制使用 control 策略
  ↓
事件仍记录 rollback_control
  ↓
方便复盘回退前后的效果
```

验收标准：

- 能同时运行多个推荐实验。
- 能按实验、桶、用户分群查看指标。
- 实验异常时不需要发版即可回退。

## 阶段 6：训练与 Embedding 更新闭环

周期：2 到 4 周。

目标：解决模型更新慢、新内容进入推荐慢的问题。

### 任务 6.1 增量样本管道

从事件表或 Kafka 消费：

```text
FEED_EXPOSURE
POST_CLICK
POST_DETAIL_VIEW
POST_LIKE
POST_FAVORITE
POST_COMMENT
POST_SHARE
NOT_INTERESTED
POST_HIDE
```

每天或每小时生成增量训练样本：

```text
user context
post context
scene context
candidate source
rank position
label ctr/cvr/quality
sample weight
event time
```

建议训练窗口：

- 全量模型：最近 30 到 180 天。
- 增量 fine-tune：最近 7 到 14 天。
- 实时特征：最近 1 小时、6 小时、24 小时。

### 任务 6.2 新内容 post embedding 更新

新内容发布时：

```text
post created
  ↓
抽取标题、正文、图片 URL、作者、taxonomy
  ↓
异步生成 image/text embedding
  ↓
写 post_embeddings / post_features
  ↓
upsert Milvus
  ↓
更新召回可见状态
```

如果 embedding 未生成：

- 首页可以走 hot/explore/content 兜底。
- vector 召回暂时不使用该 post。
- diagnostics 记录 `embedding_missing`。

### 任务 6.3 模型注册与版本

每次训练产物需要有：

```text
model_name
model_version
feature_version
taxonomy_version
train_data_window
eval_report_path
created_at
status
```

上线顺序：

```text
offline eval 通过
  ↓
小流量 A/B
  ↓
guard 指标稳定
  ↓
扩大流量
  ↓
设为默认版本
```

验收标准：

- 新发布内容在 5 到 15 分钟内拥有 embedding 并可进入召回。
- 增量模型能每天自动产出离线评估报告。
- 线上请求记录模型版本，方便回溯。

## 阶段 7：模型结构升级

周期：A/B 和离线评估稳定后再做，预计 3 到 6 周。

目标：在已有评估闭环下升级模型，而不是盲目重构。

### 任务 7.1 GRU 升级为 SASRec

适用原因：

- 用户行为序列有短期兴趣漂移。
- Self-Attention 比 GRU 更容易捕捉远距离行为关系。
- 训练可以并行，效率更好。

升级策略：

- 先离线实现 SASRec user sequence encoder。
- 与 GRU 使用同一份样本和指标对比。
- 仅当 Recall@K/NDCG@K 和线上 A/B 都更好时替换。

### 任务 7.2 CrossLayer 升级为 DCNv2

适用原因：

- 当前 CrossLayer × 2 能做基础交叉，但表达能力有限。
- DCNv2 对显式高阶特征交叉更稳定。

重点交叉：

- 用户兴趣 term × post taxonomy term。
- 用户最近行为 × 作者。
- surface × post age。
- device × image aspect/style。
- rank position × item popularity。

### 任务 7.3 MTL 权重自动学习

当前固定 CTR/CVR/Quality 权重可用，但后期手调成本高。

建议引入 Uncertainty Weighting：

```text
loss = Σ (1 / (2 * sigma_i^2)) * loss_i + log(sigma_i)
```

这样模型能自动学习不同任务的相对权重。

### 任务 7.4 动态温度

当前固定 temperature 简单可靠，但不同用户需要不同探索强度：

- 新用户：温度高一点，增加探索。
- 兴趣稳定用户：温度低一点，提高相关性。
- 负反馈多的用户：提高多样性，避免困在错误方向。

验收标准：

- 新结构必须通过离线评估和线上 A/B。
- 不允许只因为模型更复杂就上线。
- 每次模型升级都记录 feature/model/taxonomy 版本。

## 阶段 8：模型服务拆分与部署

周期：系统流量或模型复杂度上来后再做，预计 2 到 4 周。

目标：让 Java 业务服务和深度模型服务解耦，方便独立扩容、GPU 推理、热切换模型版本。

### 推荐演进顺序

第一阶段：

```text
Java 后端 -> Python FastAPI deep-rank service
```

保留当前 HTTP 调用方式，先规范接口和日志。

第二阶段：

```text
Java 后端 -> TorchServe / Triton Inference Server
```

适合模型数量变多、推理耗时增加、需要 GPU 和版本热切换时。

### 模型服务接口

建议固定：

```text
POST /infer/recall
POST /infer/recall/similar
POST /infer/rank
GET  /health
GET  /models
```

每个响应包含：

```text
model_version
feature_version
latency_ms
degraded
items
```

验收标准：

- Java 服务模型超时后能自动降级。
- 模型服务支持多版本并存。
- 单独扩容模型服务不影响业务服务发布。

## 5. 关于数据集和爬取问题

不建议现在立刻把重点放在“爬更多图片”上。原因是：

- 没有评估体系时，数据变多不一定能证明推荐变好。
- 爬取其他网站图片会带来版权、反爬、数据清洗和内容安全成本。
- 当前更缺的是行为真实性、元数据质量、评估闭环，而不是单纯图片数量。

更推荐的顺序：

1. 先用现有 Unsplash 数据把评估体系建起来。
2. 给现有图片补充标题、描述、taxonomy、视觉 embedding、作者信息。
3. 通过前端真实交互产生更干净的行为样本。
4. 如果数据量仍不够，再考虑合法开放数据集，而不是直接爬站。

可选数据增强方向：

- 使用 Unsplash 原始 metadata：title、description、alt_description、tags、author、location。
- 使用 CLIP/BLIP 给图片生成 caption。
- 使用视觉聚类生成 topic/style。
- 生成合成用户画像用于离线 replay 压测，但不能替代真实用户行为。

## 6. 推荐效果怎么评估

当前你用“切换几个用户在前端交互测试”的方法是有价值的，但只能发现表面问题。正式评估建议分三层。

### 6.1 人工验收

保留人工测试，但标准化：

```text
用户 A：城市建筑偏好
用户 B：自然风景偏好
用户 C：人像/生活方式偏好
用户 D：新用户无行为
用户 E：连续负反馈某类内容
```

每个用户测试：

- 首页前 30 条是否符合偏好。
- 是否重复。
- 是否有足够多样性。
- 交互 3 到 5 次后是否明显变化。
- 负反馈后类似内容是否下降。

### 6.2 离线 replay

这是后续模型迭代的主依据。

每次修改召回、特征或模型，都跑：

- Recall@50。
- NDCG@20。
- Coverage。
- Diversity。
- Duplicate Rate。
- 新用户/活跃用户分群指标。

### 6.3 线上 A/B

只有 A/B 能回答“用户真实行为是否变好”。

线上主指标：

- CTR。
- DTR。
- Like/Favorite Rate。
- Negative Rate。
- 次日留存或会话时长，如果后续有数据。

守护指标：

- 接口延迟。
- 空结果率。
- 重复率。
- 负反馈率。

## 7. 近期最小可行落地清单

建议下一轮不要一次性上所有内容，而是按下面顺序做。

### Sprint 1：重复与召回框架

- 把所有召回源统一成 `RecallSource` 结构。
- diagnostics 记录每个 source 的 raw/unique/final 数量。
- 新增 request 内、page 内、近期曝光去重。
- 热门召回不再无条件混入，每次混入必须有 quota 和去重。

验收：

- 首页前 100 条无重复。
- 刷新首页不会持续看到同一批热门内容。

### Sprint 2：i2i 协同过滤

- 新增 `post_i2i_neighbors` 表。
- 编写离线构建脚本。
- 新增 `i2i_cf` 召回源。
- 最近正反馈用户优先使用 i2i。

验收：

- 用户点击/详情浏览某类内容后，i2i 能贡献相似候选。
- diagnostics 能看到 i2i 的贡献量和点击表现。

### Sprint 3：实时 CTR 和曝光过滤

- Redis 维护 post 1h/24h 指标。
- rank features 加入实时 CTR/DTR/negative rate。
- Redis 维护 `user:recent_exposure`。
- 负反馈内容和类似内容降权。

验收：

- 交互后的推荐变化更快。
- 热门内容混入更自然，不再像固定列表。

### Sprint 4：离线评估

- 生成 replay 数据集。
- 输出 Recall@K、NDCG@K、Coverage、Duplicate Rate。
- 按用户分群输出报告。

验收：

- 每次改推荐策略都能和上一版对比。
- 不再只靠前端肉眼判断。

### Sprint 5：A/B 正式化

- 实验注册表。
- 稳定分桶。
- 曝光事件写入 bucket/model/feature 版本。
- guard 自动回退。

验收：

- 新召回配额或模型版本可以 10% 小流量上线。
- 指标异常时自动回到 control。

## 8. 优先级表

| 优先级 | 模块 | 原因 |
| --- | --- | --- |
| P0 | 曝光去重 | 直接解决首页重复和热门混入问题 |
| P0 | 召回诊断 | 没有诊断就无法判断哪个源有效 |
| P0 | i2i 召回 | 投入产出比高，能提升相似推荐和个性化 |
| P0 | 实时 CTR | 提升新鲜内容和热门内容排序质量 |
| P0 | 离线 replay | 解决推荐效果无法评价 |
| P1 | A/B 实验 | 让后续上线可控 |
| P1 | Taxonomy ID | 替代 tag hash，支撑长期模型 |
| P1 | 新内容 embedding 更新 | 解决新内容冷启动 |
| P2 | SASRec/DCNv2 | 有评估闭环后再升级模型结构 |
| P2 | Triton/TorchServe | 流量和模型复杂度上来后再拆服务 |

## 9. 最终验收标准

当以下条件满足时，可以认为系统达到“小规模商业级推荐系统”的状态：

- 首页前 100 条重复率为 0。
- 首页 p95 接口延迟小于 800ms。
- 详情页相似推荐 p95 小于 600ms。
- 每个推荐 item 能解释来源：hot/vector/i2i/online_interest/social/explore。
- 每个召回源能看到返回量、去重后数量、最终曝光量、点击量。
- 任意推荐策略修改都能离线 replay 对比。
- 任意新模型都能通过 A/B 小流量验证。
- 实验异常时能自动回退。
- 新内容 5 到 15 分钟内进入可召回状态。
- 负反馈后，同类内容在后续推荐中明显降权。

## 10. 推荐的下一步

下一步最建议直接实现：

1. 首页曝光去重和候选合并去重。
2. `RecallSource` 统一召回框架。
3. `post_i2i_neighbors` 表和 i2i 构建脚本。
4. Redis 实时 CTR/DTR/negative 特征。
5. 离线 replay 评估脚本。

这五项完成后，再讨论 SASRec、DCNv2、动态温度、Triton 这类模型/部署升级才更稳。现在最急的不是换更复杂的模型，而是让系统能稳定回答三个问题：

- 为什么推荐了它？
- 推荐得比上一版好吗？
- 出问题时能不能快速定位和回退？
