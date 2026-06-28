ALTER TABLE clarification_request
    ADD COLUMN contract_json JSON NULL AFTER question;
