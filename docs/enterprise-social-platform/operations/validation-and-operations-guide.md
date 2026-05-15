# 验收与运营指南

管理后台已经从当前工程中移除：前端不再提供 `/admin` 页面，后端不再暴露 `/api/admin/**` 或 `/api/admin/enterprise/**` 接口。后续运营动作优先通过用户侧功能、维护脚本、定时任务和必要的数据库运维完成。

历史 Liquibase migration 仍保留，避免已执行过的环境启动失败，也避免误删已有数据表。除非明确进入数据清理窗口，否则不要新增 `DROP TABLE` 迁移。

## 1. 启动验收

1. 启动基础设施：MySQL、Redis、MinIO、Milvus，以及按需启动 Kafka、Elasticsearch。
2. 启动后端，确认 Liquibase 已自动执行到最新 changeset。
3. 启动 deep-rank 服务，确认 `deep_rank.pt` 和 `candidate_generator.pt` 路径正确。
4. 启动前端，确认登录、发布、收藏、评论、关注、详情页、首页 Feed 正常。
5. 打开消息中心，确认私信、互动通知和系统通知接口正常。

常用页面入口：

| 场景 | 地址 | 说明 |
| :-- | :-- | :-- |
| 首页 Feed | `/home` 或 `/feed` | 首页推荐、关注、朋友动态、频道筛选 |
| 频道页 | `/channels/{channelCode}` | 单频道内容流 |
| 搜索页 | `/search` | 搜索内容、用户、频道 |
| 发布页 | `/publish` | 用户发布内容 |
| 消息中心 | `/messages` | 私信、通知、互动消息 |
| 详情页 | `/posts/{postId}` | 图片详情和相似推荐 |
| 个人页 | `/profile` 或 `/users/{userId}` | 用户资料和内容 |

## 2. 当前运营边界

后台删除后，当前系统不再支持网页端运营编辑频道、话题、导入批次、模型版本、治理案件和创作者商业档案。

保留的运营能力：

1. 用户侧发布、评论、点赞、收藏、关注和举报。
2. 首页推荐、频道页、搜索页和详情页相似推荐。
3. 消息中心：私信、关注/点赞/收藏/评论通知。
4. 推荐维护脚本：特征、I2I、embedding、taxonomy、用户向量。
5. Feed 请求和曝光日志仍由推荐链路写入，用于后续排查和评估。

已移除的能力：

1. `/admin` 前端页面和顶部“运营后台”入口。
2. `/api/admin/**` 后端接口。
3. 内容导入批次、重建任务登记、模型版本登记、治理案件、账号处罚、创作者商业档案等后台代码。
4. 举报后不再自动生成后台治理案件，只保留用户举报记录和行为事件。

## 3. 内容与频道运维

没有后台后，频道和话题应尽量通过稳定配置和 migration 管理，不建议频繁在线改动。

新增或调整频道时：

1. 优先用 Liquibase 新增独立 changeset，不修改已经执行过的 SQL。
2. 保持 `code` 稳定，不要随便改已有频道编码。
3. 变更后检查 `/home`、`/channels/{code}`、发布页和搜索页。
4. 如果影响推荐召回，执行特征、I2I、taxonomy 等维护任务。

批量内容冷启动建议：

1. 优先使用脚本导入，保证每条内容有标题、频道、话题和可访问图片。
2. 导入后抽查详情页图片、首页展示、频道页和相似推荐。
3. 大批量导入后执行搜索/特征/I2I/embedding/taxonomy 维护任务。

## 4. 推荐排查

首页慢或推荐不准时，优先按这个顺序排查：

1. 后端日志是否有 deep-rank timeout。
2. deep-rank 服务是否加载了正确模型。
3. Redis 用户行为序列是否有数据。
4. MySQL 特征表、I2I 表是否按维护任务刷新。
5. Feed 请求日志中返回数、候选数、耗时和降级状态是否异常。
6. 曝光日志中召回来源是否过于单一。

常用修复方向：

1. deep-rank 慢：降低超时、缩小候选批次、先规则兜底。
2. 推荐重复：检查去重、最近曝光过滤、作者 cap、topic cap。
3. 新内容不出现：确认内容 `PUBLIC + APPROVED`，并刷新特征/I2I。
4. 图片不显示：确认 post assets、coverUrl/thumbUrl 和 `/minio-img` 代理。

## 5. 模型与评估

模型训练和评估不再通过后台登记，直接以训练脚本输出和报告文件为准。

推荐流程：

1. 跑训练脚本，保存模型文件和训练报告。
2. 用评估脚本检查 AUC、NDCG、Recall、Precision、Hit@K、MRR。
3. 手动更新 deep-rank 服务的 `RANK_MODEL_PATH` 和 `CANDIDATE_MODEL_PATH`。
4. 重启 deep-rank 服务后先小流量观察。
5. 保留规则排序兜底，避免模型异常时首页不可用。

## 6. 发布前检查

```powershell
cd backend
mvn -DskipTests clean compile
python scripts/validate_liquibase_sql.py
Get-ChildItem scripts -Filter *.py | ForEach-Object { python -m py_compile $_.FullName }

cd ..\frontend
npm ci
npm run build
```

## 7. 上线后巡检

1. 执行 `backend/scripts/production_smoke_check.py` 检查健康、频道、话题、Feed。
2. 观察 API 延迟、错误率、Feed 空结果率、召回失败率。
3. 每日备份 MySQL、MinIO、Milvus、Elasticsearch。
4. 每次导入内容后重新检查搜索、特征、I2I、详情页图片和首页推荐。
5. 每周抽查举报记录、低质内容、重复内容和用户反馈。
