# P8 推荐观测交接

## 已完成

- 新增推荐观测表：
  - `feed_request_logs`
  - `feed_impression_logs`
  - `recommendation_experiments`
  - `recommendation_source_snapshots`
- 首页 Feed 返回末端写入请求日志和曝光日志，不参与排序决策。
- 日志包含 requestId、用户分层、实验桶、筛选条件、候选数量、返回数量、延迟、内容位置、召回来源、分数、频道和话题。
- 后台新增“推荐”工作区，可查看 Feed 请求和对应曝光明细。
- 新增 `/api/admin/feed-requests` 与 `/api/admin/feed-impressions` 查询接口。

## 验证

- `backend`: `mvn test` 通过，5 个测试全部成功。
- `frontend`: `npm run build` 通过。

## 注意

- 这一阶段只追加观测日志，不删除、不替换已有召回源。
- `recommendation_experiments` 和 `recommendation_source_snapshots` 已建表，当前先为后续实验配置和来源快照预留。
