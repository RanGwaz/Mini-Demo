# Image Social（开发阶段唯一文档）

本项目当前仅保留本 README，内容只包含：

- 开发阶段启动（本地直启，不构建推理镜像）
- 系统架构与运行链路
- 常见启动问题排查

---

## 1. 系统整体架构（开发态）

### 1.1 模块划分

- `frontend`：Vue3 + Vite
- `backend`：Spring Boot（Java 21）
- `infra/docker`：中间件与定时任务编排（Docker Compose）
- `infra/docker/deep-rank-service`：推理服务（FastAPI + PyTorch）
- `backend/scripts`：特征工程、向量抽取、训练、评估脚本

### 1.2 主链路（请求流程）

1. 浏览器访问前端 `http://localhost:5173`
2. 前端调用后端 `http://localhost:8080`
3. 后端 Feed：多路召回 → 过滤 → 排序
4. 后端调用推理服务：
  - `POST http://localhost:18080/infer/recall`
  - `POST http://localhost:18080/infer/rank`
5. 推理服务访问：
  - MySQL（行为/业务数据）
  - Milvus（向量检索）

### 1.3 定时任务链路

- `feature-engineering-scheduler`：每 300 秒执行 `feature_engineering.py`
- `embedding-extract-scheduler`：每 1800 秒执行 `extract_image_embeddings.py`

日志文件：

- `infra/docker/data/reco-jobs/feature_engineering.log`
- `infra/docker/data/reco-jobs/extract_image_embeddings.log`

---

## 2. 一次性启动中间件 + 定时任务（不构建推理镜像）

```powershell
cd H:\桌面\一坨屎\infra\docker

docker compose up -d mysql redis minio minio-init etcd milvus attu kafka kafka-init elasticsearch kibana feature-engineering-scheduler embedding-extract-scheduler

docker compose up -d mysql redis minio minio-init etcd milvus attu feature-engineering-scheduler embedding-extract-scheduler
```

检查状态：

```powershell
docker compose ps
```

> 说明：上面命令不会启动 `deep-rank-service` 容器，因此不会构建推理镜像。

---

## 3. 推理服务启动（重点：进入 conda 环境）

目录：`H:\桌面\一坨屎\infra\docker\deep-rank-service`

### 3.1 首次创建环境（D 盘）

```powershell
mkdir D:\conda-envs -Force
conda create -p D:\conda-envs\deep-rank python=3.10 -y
```

### 3.2 安装依赖（在环境里）

> 你的镜像源可能缺新版包，统一使用官方 PyPI。

```powershell
conda run -p D:\conda-envs\deep-rank python -m pip install -U pip wheel
conda run -p D:\conda-envs\deep-rank python -m pip install "setuptools<81" -i https://pypi.org/simple
conda run -p D:\conda-envs\deep-rank python -m pip install --force-reinstall "marshmallow<4" -i https://pypi.org/simple
conda run -p D:\conda-envs\deep-rank python -m pip install -r H:\桌面\一坨屎\infra\docker\deep-rank-service\requirements.txt -i https://pypi.org/simple --extra-index-url https://download.pytorch.org/whl/cpu
```

### 3.4 启动方式 B（先激活再启动）

```powershell
# 如果你的 PowerShell 激活经常失败，先加载 conda hook
& "G:\Anaconda\shell\condabin\conda-hook.ps1"
conda activate D:\conda-envs\deep-rank

cd H:\桌面\一坨屎\infra\docker\deep-rank-service
uvicorn app:app --host 0.0.0.0 --port 18080 --reload
```

### 3.5 验证是否真的进入了 deep-rank 环境

```powershell
$env:CONDA_PREFIX
python -c "import sys; print(sys.executable)"
```

应看到：

- `D:\conda-envs\deep-rank`
- `D:\conda-envs\deep-rank\python.exe`

### 3.6 健康检查

```powershell
curl http://localhost:18080/healthz
```

返回 `{"status":"ok"}` 表示推理服务正常。

> 如果浏览器访问 `http://localhost:18080/healthz` 一直转圈，通常是进程启动时异常退出（最常见是 Milvus 未启动或连接失败）。请直接看启动终端日志；当前代码已改为“Milvus 懒连接”，只要服务进程还在，`/healthz` 就应立即返回。

### 3.7 关键提醒

- 正确：`uvicorn app:app ...`
- 错误：`python app.py`

---

## 4. 后端启动

启动类：`backend/src/main/java/com/rangwaz/imagesocial/ImageSocialApplication.java`

开发建议（`backend/src/main/resources/application.yml`）：

- `app.kafka.enabled: false`
- `app.elasticsearch.enabled: false`

---

## 5. 前端启动

```powershell
cd H:\桌面\一坨屎\frontend
npm install
npm run dev
```

访问：`http://localhost:5173`

---

## 6. 如何确认定时任务已开启、已执行

### 6.1 已开启（容器在线）

```powershell
cd H:\桌面\一坨屎\infra\docker
docker compose --profile core --profile feature ps feature-engineering-scheduler embedding-extract-scheduler
```

状态 `Up` 即已开启。

### 6.2 已执行（实时日志）

```powershell
docker compose logs -f feature-engineering-scheduler
docker compose logs -f embedding-extract-scheduler
```

关键字：

- `run feature_engineering.py`
- `run extract_image_embeddings.py`

### 6.3 执行结果（落盘日志）

- `H:\桌面\一坨屎\infra\docker\data\reco-jobs\feature_engineering.log`
- `H:\桌面\一坨屎\infra\docker\data\reco-jobs\extract_image_embeddings.log`

### 6.4 如果容器闪退、自动停止、没有日志

先看容器退出原因：

```powershell
cd H:\桌面\一坨屎\infra\docker
docker compose logs --tail=200 feature-engineering-scheduler
docker compose logs --tail=200 embedding-extract-scheduler
```

你遇到的典型原因是依赖构建失败（`pkg_resources` / `pandas` 版本与 Python 3.11 不兼容）。
项目已修复为 Python 3.11 兼容依赖：

- `backend/scripts/requirements-reco-jobs.txt`
  - `pandas==2.2.3`
  - `setuptools<81`
  - `marshmallow<4`

模型评估脚本现在会优先查找以下位置的模型文件：

- `RANK_MODEL_PATH`
- `RANK_MODEL_OUT`
- `H:\桌面\一坨屎\infra\docker\models\deep_rank.pt`
- `%TEMP%\deep_rank.pt`

如果训练脚本刚在 Windows 非 ASCII 路径下导出过模型，即使模型暂时只落在临时目录，评估脚本也能直接加载。

如果容器还是闪退，强制重建并重启：

```powershell
cd H:\桌面\一坨屎\infra\docker
docker compose rm -sf feature-engineering-scheduler embedding-extract-scheduler
docker compose up -d feature-engineering-scheduler embedding-extract-scheduler
docker compose logs -f feature-engineering-scheduler
```

---

## 7. 全链路自检

1. `docker compose ps`：中间件 + scheduler 均 `Up`
2. `curl http://localhost:18080/healthz` 正常
3. 后端 `8080` 正常
4. 前端 `5173` 正常
5. 前端 Feed 有返回

满足以上即全链路跑通。

---

## 8. CPU / GPU 说明

- 不是只能 CPU。
- 推理服务代码已支持自动设备选择：有 GPU 走 CUDA，没有就 CPU。

### 8.1 GPU 前提

- NVIDIA 显卡驱动正常
- `nvidia-smi` 可用

### 8.2 如需 GPU 版 PyTorch（示例：CUDA 12.1）

```powershell
conda run -p D:\conda-envs\deep-rank pip uninstall -y torch torchvision torchaudio
conda run -p D:\conda-envs\deep-rank pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121
conda run -p D:\conda-envs\deep-rank python -c "import torch; print(torch.cuda.is_available()); print(torch.__version__)"
```

---

## 9. 常见报错与处理

### 9.1 `Could not find a version that satisfies the requirement fastapi==0.115.0`

原因：镜像源缺新版包。  
处理：安装依赖时显式加 `-i https://pypi.org/simple`。

### 9.2 `No module named 'pkg_resources'`

原因：`setuptools` 版本过高或缺失。  
处理：

```powershell
conda run -p D:\conda-envs\deep-rank python -m pip install "setuptools<81" -i https://pypi.org/simple
```

### 9.3 `module 'marshmallow' has no attribute '__version_info__'`

原因：`marshmallow` 装成 4.x 与 `environs` 不兼容。  
处理：

```powershell
conda run -p D:\conda-envs\deep-rank python -m pip install --force-reinstall "marshmallow<4" -i https://pypi.org/simple
```

### 9.4 `Fail connecting to server on milvus:19530`

原因：本地进程连了容器内主机名。  
处理：

- 确认 `milvus` 已启动
- 本地连接参数用 `localhost:19530`

---

## 10. 开发阶段为什么常不启动 Kafka / 搜索服务（Elasticsearch）

你理解得对：Kafka 在这个项目里主要用于事件流（例如用户行为事件转发、搜索同步消息）。

但在开发阶段通常不默认启动 Kafka / 搜索，原因是：

1. **主功能不依赖它们**：前端浏览、发帖、Feed 主流程可以先跑通。
2. **资源占用高**：Kafka + ES + Kibana 会显著吃内存和 CPU，影响本机开发体验。
3. **启动慢、排错链路长**：这两类服务问题多时会干扰你先做业务联调。
4. **当前后端支持开关**：`app.kafka.enabled`、`app.elasticsearch.enabled` 可在开发期关闭。

建议：

- 日常开发：先不开 Kafka/ES，保证核心链路稳定。
- 做事件流或搜索功能联调时：再临时启动 Kafka/ES。

---

## 11. 常用停止命令

```powershell
cd H:\桌面\一坨屎\infra\docker
docker compose down
```

> 这只会停止 compose 管理的中间件/定时任务。前端、后端、PyCharm 启动的推理服务需单独停止。

