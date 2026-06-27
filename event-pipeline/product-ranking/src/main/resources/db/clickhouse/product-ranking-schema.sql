CREATE TABLE IF NOT EXISTS product_ranking_events
(
    occurred_at DateTime64(3, 'UTC'),
    product_id UInt64,
    event_type LowCardinality(String),
    score Int64,
    inserted_at DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(occurred_at)
ORDER BY (occurred_at, product_id);

CREATE TABLE IF NOT EXISTS product_ranking_minute_scores
(
    bucket_start_at DateTime('UTC'),
    product_id UInt64,
    score Int64
)
ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(bucket_start_at)
ORDER BY (bucket_start_at, product_id);
