-- Purpose: Add gas_consumed field to contract_result table and populate it by calculating it based on historical data.
ALTER TABLE contract_result
ADD COLUMN IF NOT EXISTS gas_consumed bigint NULL;

-- Calculate gas consumed for each transaction from contract_transaction
WITH gas_usage AS (
  SELECT
    ct.consensus_timestamp,
    ct.entity_id,
    COALESCE(SUM(ca.gas_used), 0) AS total_gas_used,
    c.initcode
  FROM
    contract_transaction ct
    LEFT JOIN contract_action ca ON ct.consensus_timestamp = ca.consensus_timestamp
    LEFT JOIN contract c ON ct.entity_id = c.id
  GROUP BY
    ct.consensus_timestamp, ct.entity_id, c.initcode
), intrinsic_gas_calc AS (
  SELECT
    consensus_timestamp,
    total_gas_used,
    CASE
      WHEN initcode IS NOT NULL THEN
        total_gas_used + 32000 + 21000 +
        -- 16 gas per non zero byte in initcode and 4 gas per zero byte in initcode
        (octet_length(initcode) - length(encode(initcode, 'hex')) / 2) * 4 +
        (length(encode(initcode, 'hex')) / 2) * 16
      ELSE
        total_gas_used + 21000
    END AS gas_with_intrinsic
  FROM gas_usage
)

UPDATE contract_result cr
SET gas_consumed = igc.gas_with_intrinsic
FROM intrinsic_gas_calc igc
WHERE cr.consensus_timestamp = igc.consensus_timestamp;