# P0/P1 阶段完成记录

状态：已完成  
日期：2026-05-08  
分支：`codex/enterprise-social-platform-phase-0-1`

## 完成范围

- 建立 P0 基线文档：
  - `docs/enterprise-social-platform/phases/p0-p1/data-cleanup-and-script-inventory.md`
  - `docs/enterprise-social-platform/phases/p0-p1/recommendation-current-baseline.md`
- 后续数据库迁移切到 SQL formatted changelog：
  - 主 changelog 只追加 `includeAll`
  - 新 SQL 目录：`backend/src/main/resources/db/changelog/sql`
  - 已新增：`064_channel_topic_foundation.sql`
- 扩展 `channels` 表，新增频道运营字段。
- 新建话题基础表：
  - `topics`
  - `topic_aliases`
  - `post_topics`
  - `user_topic_follows`
  - `topic_channel_bindings`
  - `topic_trend_snapshots`
  - `topic_merge_logs`
- 初始化 9 个中文核心频道和 180 个中文话题，每个频道 20 个话题。
- 新增频道/话题基础实体、Mapper、Service。

## 验证结果

后端测试：

```powershell
cd backend
mvn test
```

结果：通过，5 个测试全部成功。

受控短启动验证：

```powershell
cd backend
mvn -DskipTests spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

结果：

- Liquibase 成功执行 `db/changelog/sql/064_channel_topic_foundation.sql`
- 新增 21 个 SQL changeset 全部执行成功
- 应用启动成功后已停止验证进程
- 未出现 checksum 错误

数据库核对：

- `channels` 中 `status='ACTIVE'` 的频道数：9
- `topics` 总数：180
- `topic_channel_bindings` 总数：180

## 注意事项

- 已执行的 `064_channel_topic_foundation.sql` 不要再修改内容，后续数据库变更从 `065_*.sql` 开始追加。
- 当前轮没有切换 `GET /api/channels` 到数据库读取，这是 P2 范围。
- 当前轮没有改发布接口、Feed 排序、Deep Rank、前端页面和 MinIO/Milvus 数据。
- 历史 YAML changeset 保留不动，避免再次触发 Liquibase checksum 错误。
- `tech_moment` 频道已在数据库中标记为 `INACTIVE`，历史帖子迁移到 `ai_tools` 放到 P2/P6 处理。

## 下一阶段入口

P2 从后端主链路开始：

1. `GET /api/channels` 切到数据库频道。
2. 新增话题搜索、趋势、详情、关注接口。
3. 发布接口兼容 `tags`，新增 `topicIds/topics`，写入 `post_topics`。
4. 增加历史 `posts.tags` 到 `post_topics` 的迁移任务。
5. Feed 增加 `channelCode` 和 `topicId/topicSlug` 过滤，但不删除现有召回源。
