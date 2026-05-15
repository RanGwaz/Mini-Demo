# Project Start Notes

## Infrastructure

```powershell
cd H:\桌面\一坨屎\infra\docker
docker compose up -d mysql redis minio minio-init etcd milvus attu kafka kafka-init reco-maintenance-scheduler
```

Kafka is optional for local development because `APP_KAFKA_ENABLED` defaults to
`false`. If you do not need stream consumers, this smaller command is enough:

```powershell
cd H:\桌面\一坨屎\infra\docker
docker compose up -d mysql redis minio minio-init etcd milvus attu reco-maintenance-scheduler
```

## Deep Rank Service

```powershell
& "G:\Anaconda\shell\condabin\conda-hook.ps1"
conda activate D:\conda-envs\deep-rank

cd H:\桌面\一坨屎\infra\docker\deep-rank-service
uvicorn app:app --host 0.0.0.0 --port 18080 --reload
```

## Frontend

```powershell
cd H:\桌面\一坨屎\frontend
npm run dev
```

## Recommendation Maintenance

Only one scheduler is kept now:

| Job | Frequency | Purpose |
| :--- | :--- | :--- |
| feature | 5 minutes | Refresh `user_features`, `post_features`, and Redis behavior sequences. |
| i2i | 60 minutes | Refresh `post_i2i_neighbors` for similar-post and collaborative recall. |

The old `embedding`, `taxonomy`, and `user-embeddings` schedulers are no longer
always-on tasks. Run them manually only when importing lots of new content or
doing a full offline rebuild. See `infra/docker/RECO_JOBS.md`.
