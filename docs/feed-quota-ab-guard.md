# Feed 配额实验：AB / 守护 / 回滚快照

这套能力是给首页推荐做“可控上线”的，不是另一个推荐模型。

## 它分别在干什么

- `AB`：把登录用户稳定分到 `control` 或 `treatment` 桶，两边使用不同的召回配额比例，比较线上效果。
- `守护`：持续看最近几天的曝光、点击率、负反馈率，如果实验桶明显变差，就触发保护。
- `回滚快照`：把当前保护判断结果、CTR 变化、负反馈变化、曝光量这些指标整理成一个可查询的快照对象。

## 代码在哪

- 实验分桶和真正生效的位置：
  - `backend/src/main/java/com/rangwaz/imagesocial/feed/FeedService.java`
  - 关键方法：`resolveHomeQuotaExperiment()`
- 守护计算与回滚判断：
  - `backend/src/main/java/com/rangwaz/imagesocial/feed/FeedQuotaGuardService.java`
- 快照结构：
  - `backend/src/main/java/com/rangwaz/imagesocial/feed/dto/FeedQuotaExperimentSnapshot.java`
- 对外查看入口：
  - `backend/src/main/java/com/rangwaz/imagesocial/feed/FeedController.java`
  - 接口：`GET /api/feed/experiments/home-quota`

## 它怎么工作

### 1. AB

`FeedService.resolveHomeQuotaExperiment()` 会：

- 匿名用户直接走匿名配额。
- 登录用户按 `Objects.hash("feed-quota-home-ab", userId)` 稳定分桶。
- 如果命中 `treatment`，就用 `application.yml` 里 `app.recommendation.feed-quota.treatment` 的比例。
- 如果命中 `control`，就用 `control` 的比例。

也就是说，这里的 AB 不是“换一个接口”，而是“同一条首页链路里，不同召回源拿不同 quota”。

### 2. 守护

`FeedQuotaGuardService.computeSnapshot()` 会在最近窗口内对比：

- `controlCtr` 和 `treatmentCtr`
- `controlNegativeRate` 和 `treatmentNegativeRate`
- `controlExposure` 和 `treatmentExposure`

如果满足下面任一条件，就会触发回滚：

- CTR 相对下降超过阈值
- 负反馈相对上升超过阈值
- 或两者同时超阈值

阈值配置在：

- `app.recommendation.feed-quota.guard-window-days`
- `app.recommendation.feed-quota.guard-min-exposure`
- `app.recommendation.feed-quota.guard-max-ctr-drop`
- `app.recommendation.feed-quota.guard-max-negative-lift`

### 3. 回滚快照

一旦 `rollbackTriggered=true`：

- `FeedService.resolveHomeQuotaExperiment()` 会强制返回 `rollback_control`
- 首页继续出结果，但统一退回 `control` 配额，不再放量 `treatment`

所以“回滚快照”不是回滚文件，也不是数据库快照，而是“当前实验是否该强制回退到 control”的诊断结果。

## 怎么看

本地启动后可直接请求：

```bash
curl "http://localhost:8080/api/feed/experiments/home-quota"
```

重点看这些字段：

- `rollbackTriggered`：是否已触发保护
- `rollbackReason`：为什么触发
- `ctrRelativeDrop`：实验桶 CTR 相对下降了多少
- `negativeRelativeLift`：实验桶负反馈相对上升了多少
- `controlExposure` / `treatmentExposure`：两边样本量够不够

## 什么时候该用它

- 想灰度新的首页召回配额时，用 `AB`
- 想避免实验把线上点击率打崩时，用 `守护`
- 想确认系统现在是不是已经自动回退到保守策略时，看 `回滚快照`
