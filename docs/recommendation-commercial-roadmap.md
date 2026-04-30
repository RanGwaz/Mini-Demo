# Recommendation System Roadmap

## Current State

The current system already has the backbone of a modern recommendation stack:

- user behavior events
- Kafka-based event flow
- backend recall and ranking logic
- vector retrieval through Milvus
- mixed homepage recall sources such as hot, social, content, online-interest, explicit-interest, and explore

That is a solid prototype foundation, but it is still not commercial-grade because the system is missing the layers that let a team measure quality, debug bad recommendations, control experiments safely, and keep latency stable under load.

## What Is Missing For Commercial Use

### 1. Measurability

Right now it is hard to answer:

- why a post appeared
- which recall source actually worked
- whether CTR, detail-view rate, like rate, favorite rate, and negative-feedback rate improved
- whether a change helped new users, cold-start users, or active users

Without this, recommendation quality can only be judged by feeling.

### 2. Observability

A commercial system needs dashboards and drill-downs for:

- request latency by feed surface
- recall-source contribution
- experiment bucket health
- negative-feedback lift
- fallbacks and degraded-mode rate

### 3. Stable Multi-Stage Ranking

The current homepage already mixes several recall sources, but commercial systems usually formalize the stages:

1. candidate generation
2. filtering
3. lightweight pre-ranking
4. final ranking
5. diversity and business constraints

Each stage must have its own latency budget and fallback plan.

### 4. Cold Start And User Segmentation

Commercial feeds distinguish:

- anonymous users
- newly registered users
- sparse-signal users
- active users
- highly engaged niche-interest users

Each group should get different feed policies instead of sharing one generalized path.

### 5. Experimentation And Guardrails

Commercial rollout needs:

- A/B experiment registry
- exposure logging
- automatic rollback thresholds
- offline replay evaluation
- model and feature versioning

### 6. Content Governance

Before commercialization, the system also needs stronger:

- spam detection
- duplicate suppression
- creator-quality controls
- sensitive-content policy filters
- abuse and report workflows

## What I Started In This Round

I added the first practical evaluation entry:

- `GET /api/feed/metrics/online`

This endpoint exposes online feed metrics for:

- exposure
- click-through
- detail-view-through
- like/favorite-through
- negative-feedback-through
- recall-source breakdown
- experiment breakdown

It supports:

- `scope=mine` for the current user
- `scope=global` for the global aggregate

Example:

```text
GET /api/feed/metrics/online?scope=global&days=3&surface=home_feed
GET /api/feed/metrics/online?scope=mine&days=7&surface=home_feed
```

## What I Added In The Latest Round

I added the next Phase 1 capability:

- `GET /api/feed/home/diagnostics`

This endpoint answers:

- which home-feed bucket the user landed in
- which recall sources ran, were skipped, or failed
- how many candidates each source returned
- how many unique items each source actually contributed
- how the candidate pool changed after fallback, safety filtering, recent-seen suppression, diversity reranking, and semantic filtering
- what the final page-level reason mix looked like

Example:

```text
GET /api/feed/home/diagnostics?page=1&size=24
GET /api/feed/home/diagnostics?page=1&size=24&topic=travel&style=minimal
```

This makes the homepage explainable enough to debug:

- why a logged-in user dropped to fallback-heavy feed composition
- whether a source is timing out or contributing zero unique candidates
- whether diversity or semantic filters are overly shrinking the final result set

## What I Added In The Current Round

I added the first operator-facing evaluation workspace:

- `GET /api/feed/workbench/overview`

This aggregated endpoint joins together:

- the current home-feed diagnostics snapshot
- online metrics for the current user window
- online metrics for the global window
- source-health snapshots
- recent behavior signals from Redis-backed online features
- architecture readiness and feature-coverage scorecards

Example:

```text
GET /api/feed/workbench/overview
GET /api/feed/workbench/overview?days=14&seed=lab-abc12345&size=24&sourceLimit=12
```

It gives us a single place to answer:

- why the first screen currently looks the way it does
- whether recent likes / favorites / detail views are actually changing the result set
- whether the bottleneck is online behavior activation, source health, or feature coverage
- how close the current stack is to an enterprise hybrid offline + online + vector + deep-rank setup

I also kept the process-level source-health entry:

- `GET /api/feed/health/sources`

This endpoint exposes process-level source health snapshots for the home feed pipeline:

- source-level call counts
- success / empty / failed / skipped counters
- latency budgets per source class
- over-budget counters
- average / max / last latency
- latest status and latest degradation message

Example:

```text
GET /api/feed/health/sources
GET /api/feed/health/sources?sourceLimit=12
```

I also pushed latency budget metadata into `GET /api/feed/home/diagnostics`, so every source row now shows:

- `latencyBudgetMs`
- `latencyMs`
- `overBudget`

That gives us both:

- request-level explainability
- process-level operational visibility
- a single Recommendation Lab surface for product/debug iteration

## Recommended Build Order

### Phase 1. Make The Feed Explainable

Goal:

- know why content was served
- know which source won
- know whether quality actually improved

Tasks:

- expose online metrics
- add feed request diagnostics
- add source-level latency logging
- add per-user and per-segment dashboard definitions

### Phase 2. Formalize Candidate Generation

Goal:

- separate hot, follow, vector, content, explicit-interest, and explore recalls into clear modules with quotas and latency budgets

Tasks:

- configurable source quotas
- timeout and fallback per source
- candidate dedup and author/topic caps
- source contribution snapshots

### Phase 3. Pre-Rank And Final-Rank

Goal:

- make the homepage ranking deterministic and tunable

Tasks:

- feature registry for user, item, and context features
- lightweight pre-ranker
- final ranker inputs and score tracing
- configurable diversity penalties

### Phase 4. Offline Evaluation

Goal:

- stop relying only on live feeling

Tasks:

- event replay dataset
- offline ranking metrics: CTR proxy, recall@K, NDCG, coverage, novelty, diversity
- segment-based evaluation reports

### Phase 5. Production Reliability

Goal:

- survive real traffic and failures

Tasks:

- p95/p99 latency budgets
- degraded-mode counters
- backpressure rules
- cache strategy for hot content and embeddings
- service-level alarms

## Suggested Next Step

The next high-value implementation should be:

- offline replay evaluation plus user segmentation policies, then persist workbench snapshots into a queryable store for historical dashboards

That will let us answer the most important product question:

"Which policy should serve which user segment, and did it actually improve recommendation quality instead of just moving counters around?"
