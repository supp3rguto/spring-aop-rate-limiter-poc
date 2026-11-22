INSERT INTO plans (name, bucket_capacity, refill_rate_seconds)
VALUES ('Free', 10, 60),
       ('Pro', 1000, 60);

INSERT INTO api_keys (api_key, user_id, plan_id, tokens, last_refill_timestamp)
SELECT 'augusto-dev-key', 'augusto_user', id, 10, 0
FROM plans
WHERE name = 'Free';

INSERT INTO api_keys (api_key, user_id, plan_id, tokens, last_refill_timestamp)
SELECT 'pro-dev-key', 'pro_user', id, 1000, 0
FROM plans
WHERE name = 'Pro';

INSERT INTO endpoint_costs (endpoint_name, token_cost)
VALUES ('get_limited_data', 1),
       ('create_resource', 5);