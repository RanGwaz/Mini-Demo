# Recommendation Jobs

Recommendation maintenance jobs are managed by Docker Compose scheduler
containers. This keeps development and later deployment on the same path.

## Canonical model directory

The active model directory is:

```text
infra/docker/models
```

The inference service reads model artifacts from that directory. The old
`backend/scripts/models` snapshot directory has been removed.

## Scheduler containers

All scheduler logs are written to:

```text
infra/docker/data/reco-jobs
```

| Service | Frequency | Purpose | Log |
| --- | ---: | --- | --- |
| `feature-engineering-scheduler` | 5 minutes | Refresh MySQL/Redis user and post features | `feature_engineering.log` |
| `embedding-extract-scheduler` | 30 minutes | Extract missing post image embeddings into Milvus | `extract_image_embeddings.log` |
| `i2i-neighbor-scheduler` | 60 minutes | Refresh collaborative-filtering item neighbors | `build_i2i_neighbors.log` |
| `user-embedding-scheduler` | 15 minutes | Refresh active user vectors in Milvus | `build_user_embeddings.log` |
| `semantic-taxonomy-scheduler` | 24 hours | Refresh semantic taxonomy and tag vocabulary | `semantic_taxonomy.log` |

## Development start command

This follows the explicit-service style you are already using and avoids
starting optional services accidentally:

```powershell
cd infra\docker
docker compose up -d mysql redis minio minio-init etcd milvus attu kafka kafka-init feature-engineering-scheduler embedding-extract-scheduler i2i-neighbor-scheduler user-embedding-scheduler semantic-taxonomy-scheduler
```

If you also want the Compose-managed inference service, append
`deep-rank-service` to the command.

## Production-style start command

Later, when the project is ready for deployment and you want a shorter command,
use profiles:

```powershell
cd infra\docker
docker compose --profile core --profile feature up -d
```

That starts all services in the `core` and `feature` profiles, including
`deep-rank-service`.

## Training

Training is intentionally not a Compose scheduler. Run the training script
manually when you want to generate new model artifacts, then restart the
inference service so it reloads `infra/docker/models`.
