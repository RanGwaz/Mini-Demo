# P2 阶段交接

日期：2026-05-09  
分支：`codex/enterprise-social-platform-phase-0-1`

## 完成

- `GET /api/channels` 改为读取数据库频道，不再枚举兜底。
- 新增话题搜索、趋势、详情、帖子列表、关注/取消关注接口。
- 发布支持 `topicIds/topics`，兼容旧 `tags`，并写入 `post_topics`。
- Feed 支持 `channelCode`、`topicId`、`topicSlug` 范围过滤；不带过滤时仍走原推荐链路。
- 增加 `POST /api/topics/backfill/posts?limit=`，用于把历史 `posts.tags` 回填到 `post_topics`。
- 删除帖子时同步清理 `post_topics`。

## 验证

- `mvn test` 通过，5 个测试成功。
- 受控短启动通过，无 Liquibase checksum 错误；验证进程已关闭。

## 下一步

P3：前端“标签”统一改“话题”，发布页/频道 tab 接真实接口，删除前端硬编码频道兜底。
