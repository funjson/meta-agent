UPDATE checkpoint
SET checksum = LOWER(SHA2(CAST(state_json AS CHAR), 256));

