# 数据清理与脚本清单

状态：P0 基线文档  
日期：2026-05-08  
目标：明确哪些数据和脚本属于历史 Unsplash/模拟体系，后续清理时不误删用户真实内容和推荐主链路。

## 1. 当前结论

当前项目已经具备 MySQL、Redis、MinIO、Milvus、Elasticsearch、Kafka 和推荐任务脚本。下一阶段要做的是从零构建中文高质量内容池，所以历史 Unsplash/模拟内容不能继续作为公开内容来源。

本阶段只做清单，不直接清库、不删脚本、不删 MinIO/Milvus 数据。真正清理放到 P6，并且需要先确认哪些内容是用户真实上传。

## 2. 数据清理原则

1. 清理只针对模拟/Unsplash/迁移内容，不碰真实用户发布内容。
2. MySQL、MinIO、Milvus、Redis 必须同批次清理，避免 Feed 指到不存在的图片或向量。
3. 删除优先使用逻辑下架或批次回滚，只有明确是本地模拟数据时才物理删除。
4. 推荐脚本不因清理删除，先改造成只处理健康中文内容。
5. 每次清理前导出表数量、对象数量、collection 状态和脚本版本。

## 3. 脚本清单

| 文件 | 当前用途 | 处理建议 | 阶段 |
|---|---|---|---|
| `backend/scripts/feature_engineering.py` | 重算用户/帖子离线特征和 Redis 行为序列 | 保留，P7 改为读取 `post_topics`、频道和话题特征 | P7 |
| `backend/scripts/extract_image_embeddings.py` | 提取图片 embedding 写入 Milvus | 保留，P7 增加审核状态、非模拟内容、批次过滤 | P7 |
| `backend/scripts/build_post_semantics.py` | 语义聚类、语义标签、质量分回写 | 保留，P7 改为中文话题优先，不再以 Unsplash 英文词为中心 | P7 |
| `backend/scripts/build_tag_vocabulary.py` | 构建推荐标签词典 | 迁移为话题词典构建，输出与 `topics`/`topic_aliases` 对齐 | P7 |
| `backend/scripts/build_i2i_neighbors.py` | 构建帖子 I2I 邻居 | 保留，P7 排除下架、审核未通过和模拟内容 | P7 |
| `backend/scripts/evaluate_recommendation_replay.py` | 离线回放评估 | 保留，P9 接入新训练样本和话题维度 | P9 |
| `backend/scripts/randomize_user_avatars.sql` | 随机头像/模拟用户辅助 | 待删除或迁移到开发专用目录，不用于生产 | P0/P6 |
| `backend/scripts/handle_data/generate_post_thumbnails.py` | 生成帖子缩略图 | 保留，P7 接入内容重建队列 | P7 |
| `backend/scripts/requirements-*.txt` | Python 推荐任务依赖 | 保留，后续合并去重 | P7 |

## 4. MySQL 清理范围

P6 执行前先统计：

```sql
SELECT COUNT(*) FROM posts;
SELECT COUNT(*) FROM post_assets;
SELECT COUNT(*) FROM user_events;
SELECT COUNT(*) FROM post_features;
SELECT COUNT(*) FROM user_features;
SELECT COUNT(*) FROM post_i2i_neighbors;
SELECT COUNT(*) FROM topic_clusters;
SELECT COUNT(*) FROM recommendation_tag_dictionary;
```

P6 候选清理表：

- `posts`
- `post_assets`
- `post_likes`
- `post_favorites`
- `post_comments`
- `post_negative_feedbacks`
- `content_reports`
- `user_events`
- `post_features`
- `user_features`
- `post_i2i_neighbors`
- `topic_clusters`
- `recommendation_tag_dictionary`
- `post_topics`
- `topic_trend_snapshots`

不直接清理：

- `users`
- `user_oauth`
- `user_follows`
- `user_blocks`
- `channels`
- `topics`
- `topic_aliases`
- `topic_channel_bindings`

## 5. MinIO 清理范围

P6 前先列出 bucket 和对象前缀。当前重点关注：

- `migrated/`
- `posts/` 下历史批量迁移对象
- 明显来自 Unsplash 迁移的对象

保留：

- 用户手动上传且能对应真实帖子的数据。
- 后续运营后台导入的中文内容图片。
- 授权或审核材料。

## 6. Milvus 清理范围

P6 前先确认 collection：

- `post_embeddings`
- `user_embeddings`

清理策略：

1. 如果 collection 只由模拟数据构成，直接重建 collection。
2. 如果混有真实内容，按 post_id 删除模拟内容向量。
3. 清理后必须重新跑 embedding 提取任务。

## 7. Redis 清理范围

P6 清理候选 key：

- `user:recent_view:*`
- `user:recent_click:*`
- `user:recent_detail_view:*`
- `user:recent_like:*`
- `user:recent_fav:*`
- `user:recent_comment:*`
- `user:recent_negative_feedback:*`
- `user:recent_hidden_post:*`
- `user:recent_share:*`
- `user:recent_follow:*`
- `user:recent_exposure:*`
- `user:behavior_sequence:*`
- `user:*:interests:*`
- `post:metrics:*`

## 8. Liquibase 规则

从 P1 开始，数据库变更采用 SQL formatted changelog：

- 目录：`backend/src/main/resources/db/changelog/sql`
- 文件命名：`064_channel_topic_foundation.sql`、`065_xxx.sql`
- 主 YAML 只追加 `includeAll`，历史 changeset 不再修改。
- 每个 SQL 文件都必须使用 `--liquibase formatted sql` 和唯一 `--changeset`。

## 9. 后续删除清单

暂不直接删除，P6 前确认：

- `backend/scripts/randomize_user_avatars.sql`
- 明确只服务 Unsplash 导入的一次性脚本。
- 本地 `.idea`、`.venv-local`、`__pycache__`、本地数据卷进入 `.gitignore` 或清理计划。

