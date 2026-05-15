# Recommendation Jobs

Recommendation maintenance is intentionally small now. Only the data that the
current feed path needs continuously is refreshed by a scheduler.

## Always-on scheduler

Logs are written to:

```text
infra/docker/data/reco-jobs/reco_maintenance.log
```

| Job | Frequency | Why it stays scheduled |
| --- | ---: | --- |
| `feature` | 5 minutes | Rebuilds `user_features`, `post_features`, and Redis behavior sequences used by the home feed. |
| `i2i` | 60 minutes | Rebuilds `post_i2i_neighbors`, which powers collaborative similar-post recall. |

Start it with the core infrastructure:

```powershell
cd infra\docker
docker compose up -d mysql redis minio minio-init etcd milvus attu kafka kafka-init reco-maintenance-scheduler
```

If Kafka is not needed for the current run, you can omit `kafka kafka-init`.

## Manual rebuild jobs

These jobs are useful, but they are too heavy or too brittle to run forever in
development. Run them only after importing many new posts or when you want a
full offline refresh.

```powershell
cd infra\docker

# Extract missing post image embeddings into Milvus.
docker compose run --rm reco-maintenance-scheduler /bin/sh -lc "pip install --no-cache-dir -r requirements-reco-jobs.txt && python extract_image_embeddings.py"

# Rebuild semantic taxonomy and tag vocabulary after embeddings or content changes.
docker compose run --rm reco-maintenance-scheduler /bin/sh -lc "pip install --no-cache-dir -r requirements-reco-jobs.txt && python build_post_semantics.py && python build_tag_vocabulary.py"

# Optional: precompute user vectors. Deep-rank can compute them on demand, so this is not a default scheduler.
docker compose run --rm -v ./deep-rank-service:/app/rank -w /app/rank reco-maintenance-scheduler /bin/sh -lc "pip install --no-cache-dir -r requirements.txt && CANDIDATE_MODEL_PATH=/models/candidate_generator.pt python build_user_embeddings.py"
```

## Removed schedulers

The old setup had five always-on containers:

```text
feature-engineering-scheduler
embedding-extract-scheduler
i2i-neighbor-scheduler
user-embedding-scheduler
semantic-taxonomy-scheduler
```

Those are replaced by `reco-maintenance-scheduler`. The removed containers were
noisy in development: image embedding tried to fetch MinIO through
`localhost:9000` from inside its own container, taxonomy depended on those
embeddings, and user-vector refresh is optional because the inference service
can compute vectors when cache is missing.
