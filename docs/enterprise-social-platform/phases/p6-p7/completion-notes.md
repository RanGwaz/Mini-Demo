# P6/P7 第一轮交接

## 已完成

- 新增 `content_rebuild_tasks` 表，用于搜索、缩略图、Embedding、语义、特征、I2I 等重建任务排队。
- 运营后台新增“重建”工作区，可创建、查询、更新重建任务。
- 后端新增 `/api/admin/rebuild-tasks` 管理接口。
- 新增中文冷启动导入脚本：`backend/scripts/import_chinese_seed_content.py`。
- 新增模拟内容清理脚本：`backend/scripts/cleanup_simulated_content.py`，默认只生成计划，执行模式只软下架。
- 新增重建执行脚本：`backend/scripts/run_rebuild_tasks.py`。
- 新增搜索索引占位重建脚本：`backend/scripts/rebuild_search_index.py`，适配当前 MySQL 搜索实现。
- 新增中文冷启动样例：`backend/scripts/seed/chinese_seed_content.sample.jsonl`，不含 Unsplash 数据。

## 验证

- `backend`: `mvn test` 通过，5 个测试全部成功。
- `frontend`: `npm run build` 通过。

## 使用顺序

1. 先用 `cleanup_simulated_content.py` 生成清理计划并人工确认。
2. 再用 `import_chinese_seed_content.py` 创建中文内容导入批次。
3. 在运营后台审核并发布批次。
4. 在运营后台创建 `ALL` 或单项重建任务。
5. 用 `run_rebuild_tasks.py --once` 执行队列。

## 注意

- 本阶段没有直接执行数据库清理，也没有删除 MinIO/Milvus 数据。
- `SEARCH_INDEX` 当前是 MySQL 搜索有效集校验；后续接入 Elasticsearch 时替换同名脚本即可。
