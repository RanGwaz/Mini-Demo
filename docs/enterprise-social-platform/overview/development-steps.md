# 企业级中文内容社区分阶段开发步骤

状态：执行规划稿  
日期：2026-05-08  
依据：[design-plan.md](./design-plan.md)  
目标：把当前设计方案拆成可按阶段开发、测试、验收、回滚的工程任务。

## 0. 执行铁律

这些规则每个阶段都必须遵守：

1. 不再使用 Unsplash 数据作为项目内容来源，现有 Unsplash/模拟内容按清理任务处理，冷启动内容从零开始重建。
2. 频道不再依赖 `ContentChannel` 枚举兜底，频道真相来源迁移到数据库。
3. 对外统一叫“话题”，后端直接从历史 `tags` 迁移到话题模型。
4. 不修改已经执行过的 Liquibase changeset，只追加新 changeset。
5. 推荐链路不删除，只增强；任何召回、排序、重排改动都要保留当前可用兜底。
6. 频道页只展示频道内容，不保留“推荐 / 关注 / 朋友动态”这类频道内部子 tab。
7. 发布、收藏、评论、关注、详情、Feed 展示这些当前正常的链路不能被破坏。
8. 每个阶段完成后必须能独立运行、独立验收，不留下半成品主链路。
9. 将yaml形式的liquibase改成.sql文件形式，每次改动数据库都要新建一个sql文件，而不是写yaml文件，新建一个包，liquibase扫描这个包下所有sql文件即可（不太了解具体细节，工作中大概是这样的）

## 1. 总体阶段顺序

| 阶段 | 名称 | 主要目标 | 是否改数据库 | 是否改前端 | 是否改推荐 |
|---|---|---|---:|---:|---:|
| P0 | 基线保护与清理准备 | 明确现状、备份、禁用 Unsplash 运行入口 | 否 | 否 | 否 |
| P1 | 动态频道与话题数据模型 | 建立 `channels/topics/post_topics` 等核心表 | 是 | 少量 | 否 |
| P2 | 频道/话题后端主链路 | 后端接口切到数据库频道和话题 | 是 | 否 | 轻微 |
| P3 | 发布与前端话题体验 | 发布页、频道 tab、话题选择接入真实接口 | 否 | 是 | 否 |
| P4 | 频道页、话题页、搜索发现 | 完成内容发现主入口 | 可能 | 是 | 轻微 |
| P5 | 运营后台第一版 | 管理频道、话题、内容、导入批次 | 是 | 是 | 否 |
| P6 | 中文内容冷启动与数据重建 | 清空模拟内容，导入中文高质量内容 | 是 | 否 | 是 |
| P7 | 搜索、MinIO、Milvus 重建流水线 | 建立内容资产生命周期 | 是 | 否 | 是 |
| P8 | 推荐观测与策略增强 | 增加日志、分层、话题/频道召回 | 是 | 少量 | 是 |
| P9 | 训练数据与模型升级 | 离线评估、模型版本、深排/双塔训练 | 是 | 否 | 是 |
| P10 | 审核治理与内容安全 | 色情/低质/举报/下架/账号治理 | 是 | 是 | 是 |
| P11 | 创作者与商业化预留 | 创作者成长、品牌合作、推广标识 | 是 | 是 | 是 |
| P12 | 生产化与运维 | CI、配置、监控、备份、环境隔离 | 可能 | 可能 | 可能 |

## 2. P0：基线保护与清理准备

### 目标

先把当前能跑的系统固定住，明确哪些脚本和数据是历史模拟数据，避免后续重构误伤主链路。

### 开发任务

1. 检查当前工作区：
   - `git status --short --branch`
   - 确认用户已有改动，不回退用户删除或修改的文件。

2. 梳理现有数据库和迁移：
   - 列出 `db.changelog-master.yaml` 最新 changeset。
   - 标记禁止修改的历史 changeset。
   - 规划下一批 changeset 编号。

3. 梳理 Unsplash/模拟数据入口：
   - 找出所有导入、生成、迁移、缩略图、随机头像、推荐训练脚本。
   - 区分三类：保留推荐任务、迁移为中文内容导入任务、删除或停用。
   - 不直接删除用户还可能需要审查的脚本，先生成清单。

4. 建立数据清理策略：
   - MySQL：清理帖子、图片、互动、特征、I2I、话题、搜索相关模拟数据。
   - MinIO：清理 migrated/Unsplash 类对象。
   - Milvus：清空 post/user embedding collection 或重建 collection。
   - Redis：清理用户行为、曝光、实时兴趣缓存。

5. 记录当前推荐主链路：
   - 热度召回
   - 社交召回
   - 内容语义召回
   - 在线兴趣召回
   - 显式兴趣召回
   - 最近正反馈召回
   - I2I 召回
   - 向量召回
   - 探索召回
   - Deep Rank 降级逻辑

### 产出

- `docs/enterprise-social-platform/phases/p0-p1/data-cleanup-and-script-inventory.md`
- `docs/enterprise-social-platform/phases/p0-p1/recommendation-current-baseline.md`
- 不改业务代码，除非发现明确启动错误。

### 验收

- 项目仍能启动。
- 当前发布、收藏、评论、关注、Feed 展示仍正常。
- 清楚知道哪些脚本后续删，哪些保留。

## 3. P1：动态频道与话题数据模型

### 目标

创建频道和话题的数据库模型，把后续开发的地基搭好。

### 数据库任务

新增 Liquibase changeset，只追加不修改历史文件：

1. `channels`
   - `id`
   - `code`
   - `name`
   - `description`
   - `icon_url`
   - `cover_url`
   - `sort_order`
   - `status`
   - `nav_group`
   - `default_post_type`
   - `waterfall_enabled`
   - `publish_enabled`
   - `recommend_enabled`
   - `config_json`
   - `created_at`
   - `updated_at`

2. `topics`
   - `id`
   - `name`
   - `slug`
   - `description`
   - `cover_url`
   - `status`
   - `risk_level`
   - `topic_type`
   - `source`
   - `parent_topic_id`
   - `post_count`
   - `follower_count`
   - `hot_score`
   - `last_trended_at`
   - `created_by`
   - `created_at`
   - `updated_at`

3. `topic_aliases`
   - `topic_id`
   - `alias`
   - `normalized_alias`
   - 唯一索引：`normalized_alias`

4. `post_topics`
   - `post_id`
   - `topic_id`
   - `source`
   - `confidence`
   - 唯一索引：`post_id + topic_id`

5. `user_topic_follows`
   - `user_id`
   - `topic_id`
   - `status`
   - 唯一索引：`user_id + topic_id`

6. `topic_channel_bindings`
   - `topic_id`
   - `channel_code`
   - `weight`
   - `status`

7. `topic_trend_snapshots`
   - `topic_id`
   - `window_type`
   - `post_count`
   - `view_count`
   - `interaction_count`
   - `hot_score`
   - `snapshot_at`

8. `topic_merge_logs`
   - `from_topic_id`
   - `to_topic_id`
   - `operator_id`
   - `reason`

### 初始化任务

1. 初始化第一批频道：
   - `campus`
   - `photography`
   - `pet`
   - `anime_outfit`
   - `overseas_life`
   - `ai_tools`
   - `food_explore`
   - `weekend_trip`
   - `home_life`

2. 初始化每个频道的推荐话题：
   - 每个频道先放 20-50 个中文话题。
   - 所有话题必须中文可读。
   - 话题不能等同于频道名。

### 后端任务

1. 新增实体、Mapper、DTO：
   - `Channel`
   - `Topic`
   - `TopicAlias`
   - `PostTopic`
   - `UserTopicFollow`
   - `TopicChannelBinding`

2. 新增基础 Service：
   - `ChannelService`
   - `TopicService`
   - `PostTopicService`

### 验收

- Liquibase 空库可执行。
- 已有数据库可执行。
- 频道和话题初始化成功。
- 不出现 Liquibase checksum 错误。

## 4. P2：频道/话题后端主链路

### 目标

让后端真正使用数据库频道和话题，不再依赖枚举。

### 后端任务

1. 改造 `GET /api/channels`
   - 只读 `channels` 表。
   - 只返回 `status=ACTIVE` 且可展示的频道。
   - 按 `sort_order` 排序。
   - 不再走枚举 fallback。

2. 新增话题接口：
   - `GET /api/topics/search?keyword=`
   - `GET /api/topics/trending`
   - `GET /api/topics/{slug}`
   - `GET /api/topics/{slug}/posts`
   - `POST /api/topics/{id}/follow`
   - `DELETE /api/topics/{id}/follow`

3. 改造发布接口：
   - 请求仍兼容 `tags`，但内部统一映射为话题。
   - 新增 `topicIds` 或 `topics` 入参。
   - 写 `post_topics`。
   - 同步维护 `posts.tags`，只为旧推荐/旧展示兼容。

4. 历史数据迁移：
   - 读取 `posts.tags`。
   - 创建或匹配 `topics`。
   - 写入 `post_topics`。
   - 每条帖子迁移结果记录日志。

5. Feed 过滤：
   - 支持 `channelCode` 从 `posts.channel_code` 过滤。
   - 支持 `topicId/topicSlug` 从 `post_topics` 过滤。
   - 频道不存在时返回空结果或明确错误，不默默 fallback。

6. 推荐兼容：
   - `FeatureService` 和 `FeedRecallService` 暂时继续读取 `posts.tags`、`topic_path`、`semantic_tags`。
   - 同时准备读取 `post_topics` 的辅助方法。
   - 不删除当前召回源。

### 测试任务

- ChannelController 测试。
- TopicController 测试。
- CreatePostRequest 发布话题测试。
- 历史 tags 迁移测试。
- Feed channel/topic 过滤测试。

### 验收

- 频道接口来自数据库。
- 发布可不选话题。
- 发布选话题时 `post_topics` 正常写入。
- 老帖子能被迁移到话题。
- Feed 不因频道/话题改造断裂。

## 5. P3：发布与前端话题体验

### 目标

前端全面把“标签”改成“话题”，发布页和频道 tab 接真实接口。

### 前端任务

1. 文案替换：
   - 用户可见的“标签”改为“话题”。
   - 内部类型可暂时保留 `tags` 字段，但新增 `topics` 类型定义。

2. 发布页：
   - 中间发布区域保留当前简化方向。
   - 标题、正文可选。
   - 可上传图片。
   - 默认校园生活频道。
   - 可不选话题。
   - 话题选择区接入 `GET /api/topics/search` 和 `GET /api/topics/trending`。
   - 支持输入 `#话题` 后创建或选择。

3. 频道选择：
   - 首页频道 tab 来自 `GET /api/channels`。
   - 发布页频道列表来自同一接口。
   - 删除前端硬编码频道兜底。

4. API 封装：
   - 增加 topic 相关 API。
   - `createPost` 支持 `topicIds/topics`。
   - 保留 `tags` 兼容字段直到后端完全迁移。

5. 视觉要求：
   - 话题选择区参考你给的发布图，但更适合当前系统。
   - 频道页不出现推荐/关注/朋友动态子 tab。

### 验收

- 发布页显示“话题”。
- 输入话题可搜索、可选择、可移除。
- 不选话题也能发布。
- 频道列表来自数据库。
- 前端构建通过。

## 6. P4：频道页、话题页、搜索发现

### 目标

频道和话题从发布附属能力升级为内容发现入口。

### 后端任务

1. 频道详情接口：
   - `GET /api/channels/{code}`
   - `GET /api/channels/{code}/topics`
   - `GET /api/channels/{code}/posts`

2. 话题详情接口：
   - 话题基础信息。
   - 热门帖子。
   - 最新帖子。
   - 相关话题。

3. 搜索接口扩展：
   - 综合搜索返回 posts/users/topics/channels。
   - 话题搜索按别名、拼音预留、热度排序。
   - 频道搜索按名称、描述、code。

4. 热榜任务：
   - 根据 `user_events`、`post_topics`、`posts` 生成 `topic_trend_snapshots`。
   - 先用 SQL/定时任务，后续再升级流式。

### 前端任务

1. 新增频道页：
   - 顶部频道信息。
   - 频道话题横向列表。
   - 内容瀑布流/列表。
   - 无推荐/关注/朋友动态子 tab。

2. 新增话题页：
   - 话题头部信息。
   - 关注话题按钮。
   - 热门/最新切换。
   - 参与发布入口。

3. 搜索页：
   - 综合搜索布局。
   - 结果分组：笔记、用户、话题、频道。

### 验收

- 点击频道可进入频道页。
- 点击话题可进入话题页。
- 搜索能搜到话题和频道。
- 频道/话题内容列表可分页。

## 7. P5：运营后台第一版

### 目标

让频道、话题、内容导入和审核可通过后台操作，不靠手改数据库。

### 后端任务

1. 增加后台权限：
   - 管理员角色。
   - 后台接口鉴权。

2. 频道管理：
   - 新增频道。
   - 编辑频道。
   - 上下架频道。
   - 调整排序。
   - 配置默认发布类型。

3. 话题管理：
   - 新增话题。
   - 编辑话题。
   - 合并话题。
   - 别名管理。
   - 风险等级。
   - 绑定频道。

4. 内容管理：
   - 帖子列表。
   - 审核状态。
   - 加精。
   - 下架。
   - 限流。
   - 删除。

5. 导入批次：
   - 新增批次。
   - 上传图片。
   - 创建草稿。
   - 批次上架。
   - 批次回滚。

### 前端任务

1. 新增后台入口。
2. 新增后台布局。
3. 完成频道管理页。
4. 完成话题管理页。
5. 完成内容管理页。
6. 完成导入批次页。

### 验收

- 不改代码即可新增频道。
- 不改代码即可新增话题。
- 可下架某个频道且前端不展示。
- 可下架某篇内容且 Feed 不展示。

## 8. P6：中文内容冷启动与数据重建

### 目标

清掉模拟数据，从零建立中文内容池。

### 清理任务

1. MySQL：
   - 清理 Unsplash/模拟帖子。
   - 清理关联 post_assets。
   - 清理关联互动、评论、收藏、点赞。
   - 清理 post_features、topic_clusters、post_i2i_neighbors。
   - 清理旧标签词典中明显英文/Unsplash 残留。

2. MinIO：
   - 删除 Unsplash/migrated 历史对象。
   - 保留用户真实上传对象，除非确认也属于模拟数据。

3. Milvus：
   - 清空 post_embeddings。
   - 清空 user_embeddings。
   - 重建 collection 或按 collection delete。

4. Redis：
   - 清理 recent_*。
   - 清理 online interest。
   - 清理 exposure。

### 内容生产任务

1. 每个核心频道准备 300-800 篇中文内容。
2. 每个频道准备至少 50 个话题。
3. 每篇内容至少 1 张图片。
4. 图文内容必须中文可读，不要机器翻译腔。
5. 不要色情、擦边、暴力、仇恨、低俗内容。
6. 官方内容使用官方/编辑部/频道主理人账号，不伪装成自然用户。

### 导入任务

1. 通过后台导入批次创建内容。
2. 图片上传 MinIO。
3. 内容写入 `posts/post_assets/post_topics`。
4. 初始审核状态为 `APPROVED` 或 `PENDING_REVIEW`，按导入策略决定。
5. 生成初始互动数时要合理，不要所有数据为 0，也不要过度夸张。

### 验收

- 生产候选数据中无 Unsplash 内容。
- 每个频道打开都有内容。
- 每个话题页不是空壳。
- 首屏连续刷新 20 次重复感可接受。

## 9. P7：搜索、MinIO、Milvus 重建流水线

### 目标

让 MySQL、MinIO、搜索、Milvus、特征表之间形成可重跑流水线。

### 后端任务

1. 内容资产状态：
   - 图片上传成功。
   - 缩略图生成成功。
   - 搜索索引成功。
   - embedding 成功。
   - 特征重算成功。
   - I2I 重算成功。

2. 新增重建队列：
   - `content_rebuild_tasks`
   - 类型：SEARCH_INDEX、EMBEDDING、FEATURE、I2I、THUMBNAIL
   - 状态：PENDING、RUNNING、SUCCESS、FAILED、RETRY

3. 后台提供重建按钮：
   - 重建单篇。
   - 重建批次。
   - 重建全部。

### 脚本任务

1. 改造 `extract_image_embeddings.py`：
   - 只处理公开、审核通过、非模拟内容。
   - 支持按批次处理。

2. 改造 `build_post_semantics.py`：
   - 中文话题优先。
   - 保持 `topic_path/semantic_tags/style_tags` 与新话题模型一致。

3. 改造 `feature_engineering.py`：
   - 读取 `post_topics`。
   - 输出频道/话题特征。

4. 改造 `build_i2i_neighbors.py`：
   - 排除下架/审核未通过内容。
   - 支持新内容批次重算。

### 验收

- 导入内容后可以一键重建搜索、特征、向量。
- Milvus 里只存在有效内容向量。
- 搜索结果和 Feed 内容一致。

## 10. P8：推荐观测与策略增强

### 目标

让推荐系统可解释、可监控、可调参。

### 数据库任务

新增：

- `feed_request_logs`
- `feed_impression_logs`
- `recommendation_experiments`
- `recommendation_source_snapshots`

### 后端任务

1. Feed 请求日志：
   - requestId
   - userId
   - page/size/seed
   - user segment
   - experiment bucket
   - latency

2. 曝光日志：
   - requestId
   - postId
   - rank position
   - recall source
   - rank score
   - channel
   - topics

3. 用户分层：
   - anonymous
   - new_user
   - sparse_user
   - active_user
   - negative_heavy_user
   - creator_user

4. 新增召回源：
   - `channel_quality`
   - `topic_follow`
   - `topic_trending`
   - `creator_quality`
   - `fresh_seed`
   - `editorial_pool`

5. 重排增强：
   - 作者去重。
   - 话题去重。
   - 新内容探索位。
   - 已曝光抑制。
   - 低质内容降权。

6. 推荐工作台：
   - 展示召回源贡献。
   - 展示每条内容原因。
   - 展示降级情况。

### 验收

- Feed 中每条内容能解释来源。
- 任意召回源失败不导致 Feed 白屏。
- 话题/频道召回能贡献候选。
- 低质和已曝光内容被明显抑制。

## 11. P9：训练数据与模型升级

### 目标

在真实中文内容和真实/模拟行为基础上，形成训练、评估、上线闭环。

### 数据任务

1. 构建训练样本表或离线文件：
   - 曝光
   - 点击
   - 详情
   - 长停留
   - 点赞
   - 收藏
   - 评论
   - 分享
   - 负反馈

2. 时间切分：
   - train
   - validation
   - test

3. 负样本采样：
   - 曝光未点弱负。
   - 不感兴趣/隐藏强负。

### 模型任务

1. 轻量排序模型：
   - 先用可解释模型验证特征有效。
   - 输出离线 AUC、NDCG、Recall。

2. 深度排序模型：
   - 使用 `deep-rank-service`。
   - 增加频道、话题、质量、作者特征。
   - 输出模型版本。

3. 双塔召回模型：
   - 用户塔：历史行为、话题兴趣、频道偏好、关注关系。
   - 内容塔：文本/图片 embedding、话题、频道、作者质量。
   - 用户向量和内容向量写 Milvus。

4. 模型注册：
   - `model_versions`
   - `training_datasets`
   - `offline_eval_reports`

### 上线任务

1. Shadow 推理。
2. A/B 实验。
3. Guardrail 自动回滚：
   - CTR 大幅下降。
   - 负反馈上升。
   - 延迟超标。
   - 空结果率上升。

### 验收

- 每个模型有训练数据、评估报告和版本。
- 模型失败时自动降级到现有推荐。
- 新模型只通过实验进入线上。

## 12. P10：审核治理与内容安全

### 目标

确保公开内容健康，先重点防色情、低俗、垃圾和违规内容。

### 后端任务

1. 上传审核：
   - 图片格式。
   - 图片大小。
   - 基础 NSFW 检测接口预留。
   - 文件 hash 去重。

2. 发布审核：
   - 敏感词。
   - 外链风险。
   - 重复内容。
   - 低质内容。

3. 举报处理：
   - 举报原因。
   - 举报状态。
   - 处理记录。
   - 内容下架。

4. 账号治理：
   - 禁言。
   - 封禁。
   - 限流。
   - 申诉预留。

5. 推荐治理：
   - 审核未通过不进 Feed。
   - 下架内容不进搜索。
   - 高风险话题降权。

### 前端任务

1. 举报入口完善。
2. 不感兴趣原因选择。
3. 后台审核队列。
4. 后台举报处理页。

### 验收

- 色情/低俗内容不能直接公开上架。
- 举报后后台可处理。
- 下架后 Feed、搜索、话题页都不展示。

## 13. P11：创作者与商业化预留

### 目标

先搭结构，不急着做完整商业投放。

### 创作者任务

1. `creator_profiles`
   - 用户领域。
   - 创作者等级。
   - 质量分。
   - 违规状态。

2. 创作者主页：
   - 内容列表。
   - 领域标签。
   - 数据概览。

3. 创作者质量：
   - 发布频率。
   - 收藏率。
   - 负反馈率。
   - 优质内容占比。

### 商业化任务

1. 品牌账号标识。
2. 推广内容标识。
3. 商业内容频控字段。
4. 曝光/点击归因字段。
5. 后台商业内容审核入口。

### 验收

- 创作者有独立 profile。
- 商业内容可以被标识。
- 推荐可以识别商业候选但默认不强推。

## 14. P12：生产化与运维

### 目标

把项目从本地可运行升级到可长期运行。

### 工程任务

1. CI：
   - 后端测试。
   - 前端构建。
   - Liquibase 校验。
   - Python 脚本基础 lint。

2. 环境配置：
   - local
   - dev
   - staging
   - prod

3. 密钥：
   - JWT secret 不写死。
   - 数据库密码不写死。
   - MinIO key 不写死。

4. 监控：
   - API 延迟。
   - 错误率。
   - Feed 空结果率。
   - 召回源失败率。
   - Deep Rank 延迟。
   - Milvus/Redis/ES 健康。

5. 备份：
   - MySQL。
   - MinIO。
   - Milvus。
   - ES。

6. 日志：
   - requestId。
   - userId 脱敏。
   - feed trace。
   - 导入批次日志。

### 验收

- staging 可完整跑通。
- 配置不含生产明文密钥。
- 服务异常能告警。
- 数据可恢复。

## 15. 推荐的实际开发节奏

每次我执行时建议按下面粒度推进，不要一次跨太多阶段：

1. 第一轮：P0 + P1  
   只做清单、changeset、实体、初始化频道话题。

2. 第二轮：P2  
   后端频道/话题接口、发布写话题、历史 tags 迁移。

3. 第三轮：P3  
   前端发布页、频道 tab、话题选择。

4. 第四轮：P4  
   频道页、话题页、搜索发现。

5. 第五轮：P5  
   运营后台第一版。

6. 第六轮：P6 + P7  
   清理模拟数据，导入中文内容，重建 MinIO/Milvus/搜索/特征。

7. 第七轮：P8  
   推荐观测和召回增强。

8. 第八轮：P9  
   训练数据和模型升级。

9. 第九轮：P10  
   审核治理和内容安全。

10. 第十轮：P11 + P12  
    创作者、商业预留、生产化。

## 16. 第一轮开发任务明细

第一轮可以直接开始，范围锁定 P0 + P1。

### 16.1 文件与代码范围

预计修改：

- `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- `backend/src/main/java/com/rangwaz/imagesocial/domain/entity/*`
- `backend/src/main/java/com/rangwaz/imagesocial/domain/mapper/*`
- `backend/src/main/java/com/rangwaz/imagesocial/channel/*`
- `backend/src/main/java/com/rangwaz/imagesocial/topic/*`
- `backend/src/test/java/com/rangwaz/imagesocial/*`
- `docs/enterprise-social-platform/phases/p0-p1/data-cleanup-and-script-inventory.md`
- `docs/enterprise-social-platform/phases/p0-p1/recommendation-current-baseline.md`

第一轮不改：

- Feed 排序主逻辑。
- Deep Rank。
- 前端大页面。
- 数据库历史 changeset。
- MinIO/Milvus 真实数据。

### 16.2 第一轮验收命令

后端：

```powershell
cd backend
mvn test
```

工作区：

```powershell
git status --short
```

数据库：

```powershell
cd backend
mvn spring-boot:run
```

启动后检查：

- Liquibase 无 checksum 错误。
- `channels` 表存在。
- `topics` 表存在。
- 初始化频道存在。
- 初始化话题存在。

## 17. 阶段完成定义

任何阶段都必须满足：

1. 代码能编译。
2. 测试能通过，或明确说明无法运行原因。
3. 关键链路可手工验证。
4. 新增表有索引。
5. 新增接口有错误处理。
6. 前端有空状态和错误状态。
7. 推荐链路没有被删除。
8. 文档更新到位。
