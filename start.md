# Project Start Notes

## Infrastructure

```powershell
cd H:\桌面\一坨屎\infra\docker
docker compose up -d mysql redis minio minio-init etcd milvus attu kafka kafka-init
```

Kafka is optional for local development because `APP_KAFKA_ENABLED` defaults to
`false`. If you do not need stream consumers, this smaller command is enough:

```powershell
cd H:\桌面\一坨屎\infra\docker
docker compose up -d mysql redis minio minio-init etcd milvus attu
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

## Recommendation Rebuild

No always-on recommendation scheduler is kept. After importing a fresh Pinterest
dataset, run the model/recommendation scripts manually for feature, embedding,
i2i, training, and evaluation rebuilds.
