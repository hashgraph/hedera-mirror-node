 ALTER TABLE contract_result
 ADD COLUMN IF NOT EXISTS gas_consumed bigint NULL;

 -- Function for counting zero and non-zero bytes
 CREATE OR REPLACE FUNCTION count_zero_bytes(bytea) RETURNS bigint AS $$
 DECLARE
   zero_count bigint := 0;
   non_zero_count bigint := 0;
   i int;
 BEGIN
   FOR i IN 1..octet_length($1) LOOP
     IF get_byte($1, i-1) = 0 THEN
       zero_count := zero_count + 1;
     ELSE
       non_zero_count := non_zero_count + 1;
     END IF;
   END LOOP;
   RETURN zero_count * 4 + non_zero_count * 16; -- 4 gas per zero byte, 16 gas per non-zero byte
 END;
 $$ LANGUAGE plpgsql IMMUTABLE;

 -- Update the contract_result table with gas_consumed values, by summing up the gas used by contract actions and adding the gas cost for contract creation
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
 )
 UPDATE contract_result cr
 SET gas_consumed = gu.total_gas_used + 21000 +
   (CASE
     WHEN gu.initcode IS NOT NULL THEN
       32000 + count_zero_bytes(gu.initcode) -- 32_000 EXTRA gas cost for contract creation
     ELSE 0
   END)
 FROM gas_usage gu
 WHERE cr.consensus_timestamp = gu.consensus_timestamp;