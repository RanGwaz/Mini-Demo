# P4 交接

## 完成
- 新增频道详情、频道话题、频道内容接口：`/api/channels/{code}`、`/topics`、`/posts`。
- 话题内容接口支持 `hot/latest`，新增相关话题接口：`/api/topics/{slug}/related`。
- 综合搜索返回笔记、用户、话题、频道，并补充频道搜索接口。
- 前端新增频道页、话题页、搜索发现页，并接入左侧栏、热门话题、全局搜索跳转。

## 验证
- `backend`: `mvn test` 通过。
- `frontend`: `npm run build` 通过。

## 下一步
- P5 进入运营后台第一版：频道管理、话题管理、内容管理和导入批次入口。
