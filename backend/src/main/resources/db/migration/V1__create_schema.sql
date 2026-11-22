CREATE TABLE plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    bucket_capacity INT NOT NULL,
    refill_rate_seconds INT NOT NULL
);

CREATE TABLE api_keys (
    api_key VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    plan_id BIGINT NOT NULL,
    tokens INT NOT NULL,
    last_refill_timestamp BIGINT NOT NULL,
    CONSTRAINT fk_api_keys_plan FOREIGN KEY (plan_id) REFERENCES plans(id)
);

CREATE TABLE endpoint_costs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    endpoint_name VARCHAR(100) NOT NULL UNIQUE,
    token_cost INT NOT NULL DEFAULT 1
);