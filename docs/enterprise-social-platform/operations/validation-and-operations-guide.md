# 总体验收与运营执行步骤

## 1. 基础启动验证

1. 启动 MySQL、Redis、MinIO、Milvus、Elasticsearch、Kafka。
2. 启动后端，让 Liquibase 自动执行到最新 SQL changeset。
3. 启动前端，确认登录、发布、收藏、评论、关注、详情、Feed 正常。

## 2. 数据与内容运营

1. 进入后台，确认频道、话题、内容管理、导入批次可用。
2. 清理 Unsplash/模拟内容后，导入中文冷启动内容。
3. 执行搜索、特征、向量、I2I 重建任务。
4. 检查频道页、话题页、搜索页、首页 Feed 都有有效中文内容。

## 3. 推荐链路验证

1. 刷新 Feed，确认不白屏、可分页、已下架内容不出现。
2. 查看后台推荐工作台，确认请求日志、曝光日志、召回源贡献、实验指标可见。
3. 构建训练集，登记模型版本和离线评估报告。
4. 新模型只走 shadow 或小流量实验，异常时保留现有推荐兜底。

## 4. 治理与商业验证

1. 前台举报一篇内容，后台治理队列应出现案件。
2. 后台处理为隐藏/下架后，Feed、搜索、话题页不再展示该内容。
3. 后台维护创作者 profile，确认等级、领域、质量分可保存。
4. 标记商业内容，确认系统能识别推广标识但默认不强推。

## 5. 发布前检查

```powershell
cd backend
mvn test
python scripts/validate_liquibase_sql.py
Get-ChildItem scripts -Filter *.py | ForEach-Object { python -m py_compile $_.FullName }

cd ..\frontend
npm ci
npm run build
```

## 6. 上线后巡检

1. 执行 `backend/scripts/production_smoke_check.py` 检查健康、频道、话题、Feed。
2. 观察 API 延迟、错误率、Feed 空结果率、召回失败率。
3. 每日备份 MySQL、MinIO、Milvus、Elasticsearch。
4. 每次运营导入后重新跑搜索、特征、向量和 I2I 重建。
