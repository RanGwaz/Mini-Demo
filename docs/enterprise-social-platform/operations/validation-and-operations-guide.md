# 总体验收与运营执行步骤

## 1. 基础启动验证

1. 启动 MySQL、Redis、MinIO、Milvus、Elasticsearch、Kafka。
2. 启动后端，让 Liquibase 自动执行到最新 SQL changeset。
3. 启动前端，确认登录、发布、收藏、评论、关注、详情、Feed 正常。

## 2. 页面入口确认

1. 用户前台：
   - 首页/Feed：`/home` 或 `/feed`
   - 频道页：`/channels/{channelCode}`
   - 话题页：`/topics/{topicSlug}`
   - 搜索页：`/search`
   - 发布页：`/publish`
   - 详情页：`/posts/{postId}`
   - 个人页：`/profile` 或 `/users/{userId}`
2. 运营后台：
   - 管理后台：`/admin`
   - 当前后台模块包括概览、频道、话题、内容、导入、重建、推荐、模型、治理、创作者。
   - 后台接口统一走 `/api/admin/**` 和 `/api/admin/enterprise/**`，需要 `ROLE_ADMIN`。

## 3. 后台部署风险与拆分建议

1. 当前阶段管理后台和用户前台放在同一个前端项目里可以接受，优点是开发快、复用登录态、复用组件和 API 封装。
2. 风险点：
   - 后台路由代码会随前台包一起发布，虽然没有管理员权限不能调用接口，但会暴露后台页面结构。
   - 前台包体积会被后台页面拖大。
   - 前台发布节奏和后台发布节奏绑在一起，后续运营功能变多后容易互相影响。
   - 如果 CDN、缓存或路由配置不严谨，可能出现后台入口被普通用户看到的体验问题。
3. 当前必须保留的安全底线：
   - 后端接口必须继续用 `ROLE_ADMIN` 鉴权，不能只靠前端隐藏入口。
   - `/admin` 页面只能作为入口保护，真正权限判断必须以后端为准。
   - 生产环境不要把管理员账号、JWT secret、数据库密码写死在配置里。
4. 后续建议：
   - MVP 和内测期：继续同项目，但隐藏普通用户入口，并限制管理员账号。
   - 准商用期：把后台拆成独立前端应用，例如 `admin.vibelo.com`，前台保留 `www.vibelo.com`。
   - 企业级阶段：后台独立仓库或独立 package，单独 CI/CD、单独权限策略、单独审计日志、单独灰度发布。
   - 后端接口可以继续共用一个服务，但建议在网关层对 `/api/admin/**` 增加 IP 白名单、管理员 MFA、操作审计和限流。

## 4. 数据与内容运营

1. 进入后台，确认频道、话题、内容管理、导入批次可用。
2. 清理 Unsplash/模拟内容后，导入中文冷启动内容。
3. 执行搜索、特征、向量、I2I 重建任务。
4. 检查频道页、话题页、搜索页、首页 Feed 都有有效中文内容。

## 5. 推荐链路验证

1. 刷新 Feed，确认不白屏、可分页、已下架内容不出现。
2. 查看后台推荐工作台，确认请求日志、曝光日志、召回源贡献、实验指标可见。
3. 构建训练集，登记模型版本和离线评估报告。
4. 新模型只走 shadow 或小流量实验，异常时保留现有推荐兜底。

## 6. 治理与商业验证

1. 前台举报一篇内容，后台治理队列应出现案件。
2. 后台处理为隐藏/下架后，Feed、搜索、话题页不再展示该内容。
3. 后台维护创作者 profile，确认等级、领域、质量分可保存。
4. 标记商业内容，确认系统能识别推广标识但默认不强推。

## 7. 发布前检查

```powershell
cd backend
mvn test
python scripts/validate_liquibase_sql.py
Get-ChildItem scripts -Filter *.py | ForEach-Object { python -m py_compile $_.FullName }

cd ..\frontend
npm ci
npm run build
```

## 8. 上线后巡检

1. 执行 `backend/scripts/production_smoke_check.py` 检查健康、频道、话题、Feed。
2. 观察 API 延迟、错误率、Feed 空结果率、召回失败率。
3. 每日备份 MySQL、MinIO、Milvus、Elasticsearch。
4. 每次运营导入后重新跑搜索、特征、向量和 I2I 重建。
