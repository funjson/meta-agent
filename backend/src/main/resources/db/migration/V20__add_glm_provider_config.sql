INSERT INTO provider_config (
    id, provider_type, display_name, base_url, model_name, secret_source, enabled
) VALUES (
    'glm', 'GLM', 'GLM / Zhipu', 'https://open.bigmodel.cn/api/paas/v4',
    'glm-4.5', 'ENVIRONMENT_OR_NONE', TRUE
)
ON DUPLICATE KEY UPDATE id = id;
