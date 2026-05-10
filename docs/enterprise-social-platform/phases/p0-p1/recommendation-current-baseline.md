# 当前推荐链路基线

状态：P0 基线文档  
日期：2026-05-08  
目标：在后续增强频道、话题、中文内容和模型训练前，固定当前推荐链路的真实能力，避免误删或推倒重来。

## 1. 当前原则

推荐链路只增强，不删除。P1-P3 主要改频道和话题数据模型，不改 Feed 排序主逻辑。任何召回源失败都必须保留降级兜底。

## 2. 在线入口

主要入口：

- `FeedController`
- `FeedService`
- `FeedServiceImpl`

当前 Feed 支持：

- 首页推荐。
- 相似内容推荐。
- Feed 诊断。
- Feed 在线指标。
- 召回源健康快照。

## 3. 已有召回源

`FeedRecallService` 已有能力：

| 召回源 | 方法 | 当前作用 |
|---|---|---|
| 热度 | `recallHot` | 匿名和兜底内容池 |
| 社交 | `recallSocial` | 关注关系相关内容 |
| 内容语义 | `recallByContent` / `recallBySemanticTerms` | 根据用户特征、话题路径、标签、语义 cluster 召回 |
| 在线兴趣 | `recallByOnlineProfile` | 使用 Redis 实时兴趣词 |
| 显式兴趣 | `recallByExplicitInterests` | 使用用户订阅兴趣 |
| 最近正反馈 | `recallByRecentPositiveFeedback` | 根据收藏、点赞、评论、分享、详情浏览召回 |
| I2I | `recallByI2I` | 使用 `post_i2i_neighbors` 协同过滤邻居 |
| 向量 | `recallByVector` | 使用 Milvus/Deep Recall 相关向量召回 |
| 探索 | `recallExplore` | 为冷启动和多样性提供随机窗口 |

## 4. 排序与重排

当前 `FeedServiceImpl` 已有：

- 多召回源配额。
- 匿名用户 hot/explore 混合。
- 登录用户 hot/social/content/online/recent-positive/vector/explicit/explore 混合。
- 召回源健康统计。
- Deep Rank 调用和降级。
- 语义过滤。
- MMR 多样性重排。
- 作者上限和话题上限。
- 最近曝光抑制。
- 新鲜度插入。
- 诊断快照。

这些能力后续必须保留。

## 5. 特征与事件

`FeatureService` 当前负责：

- Redis 最近行为序列。
- Redis 最近曝光。
- Redis 实时兴趣窗口。
- 帖子实时指标。
- 用户强正反馈和负反馈。
- MySQL `user_features`、`post_features` 查询。
- `user_events` 历史行为 fallback。

当前事件类型包括：

- `FEED_EXPOSURE`
- `POST_CLICK`
- `POST_DETAIL_VIEW`
- `POST_LIKE`
- `POST_FAVORITE`
- `POST_COMMENT`
- `POST_SHARE`
- `NOT_INTERESTED`
- `POST_NEGATIVE_FEEDBACK`
- `POST_HIDE`
- `USER_FOLLOW`

## 6. 离线任务

当前推荐相关任务：

- `feature_engineering.py`
- `extract_image_embeddings.py`
- `build_post_semantics.py`
- `build_tag_vocabulary.py`
- `build_i2i_neighbors.py`
- `evaluate_recommendation_replay.py`
- `infra/docker/deep-rank-service/train_deep_rank_model.py`
- `infra/docker/deep-rank-service/build_user_embeddings.py`

后续 P7/P9 会改造这些脚本，但 P1 不改。

## 7. 当前已知问题

1. 频道仍来自枚举和旧 `channels` 表混合状态。
2. 对外“标签”需要迁移为“话题”。
3. 语义任务仍偏历史 Unsplash 英文标签。
4. 生产冷启动不能依赖模拟内容。
5. Feed 请求日志和曝光日志还没有完整持久化。
6. 模型训练、模型注册、A/B 上线闭环还不完整。

## 8. P1 不做事项

P1 不做：

- 不修改 `FeedServiceImpl` 排序策略。
- 不删除任何召回源。
- 不训练模型。
- 不清理 MinIO/Milvus。
- 不改前端页面。

P1 只做：

- SQL changelog 机制。
- 频道表扩展。
- 话题基础表。
- 初始化中文频道和话题。
- 后端实体、Mapper、基础 Service。

