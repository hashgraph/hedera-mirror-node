/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */
'use strict';

const transactions = require('../transactions.js');

function normalizeSql(str) {
  return str.replace(/\s+/g, ' ').replace(/,\s*/g, ',').replace(/\s*,/g, ',').replace(/\s+$/, '');
}

const boilerplatePrefix = `SELECT etrans.entity_shard, etrans.entity_realm, etrans.entity_num, t.memo, t.consensus_ns,
       t.valid_start_ns, coalesce(ttr.result,'UNKNOWN') AS result, coalesce(ttt.name,'UNKNOWN') AS name,
       t.fk_node_acc_id, enode.entity_realm AS node_realm, enode.entity_num AS node_num, ctl.realm_num AS account_realm,
       ctl.entity_num AS account_num, ctl.amount, t.charged_tx_fee, t.valid_duration_seconds, t.max_fee,
       t.transaction_hash
    FROM (
       SELECT DISTINCT ctl.consensus_timestamp
       FROM t_cryptotransferlists ctl JOIN t_transactions t ON t.consensus_ns = ctl.consensus_timestamp `;

const boilerplateSuffix = ` JOIN t_transactions t ON tlist.consensus_timestamp = t.consensus_ns
      LEFT OUTER JOIN t_transaction_results ttr ON ttr.proto_id = t.result
      JOIN t_entities enode ON enode.id = t.fk_node_acc_id
      JOIN t_entities etrans ON etrans.id = t.fk_payer_acc_id
      LEFT OUTER JOIN t_transaction_types ttt ON ttt.proto_id = t.type
      JOIN t_cryptotransferlists ctl ON tlist.consensus_timestamp = ctl.consensus_timestamp
    ORDER BY t.consensus_ns desc, account_num ASC, amount ASC`;

test('transactions by timestamp gte', () => {
  let sql = transactions.reqToSql({query: {timestamp: 'gte:1234'}});
  let expected = normalizeSql(
    boilerplatePrefix +
      `WHERE 1=1 AND ((t.consensus_ns >= $1) ) AND 1=1 ORDER BY ctl.consensus_timestamp desc limit $2 ) AS tlist` +
      boilerplateSuffix
  );
  expect(normalizeSql(sql.query)).toEqual(expected);
  expect(sql.params).toEqual(['1234000000000', 1000]);
});

test('transactions by timestamp eq', () => {
  let sql = transactions.reqToSql({query: {timestamp: 'eq:123'}});
  let expected = normalizeSql(
    boilerplatePrefix +
      `WHERE 1=1 AND ((t.consensus_ns = $1) ) AND 1=1 ORDER BY ctl.consensus_timestamp desc limit $2 ) AS tlist` +
      boilerplateSuffix
  );
  expect(normalizeSql(sql.query)).toEqual(expected);
  expect(sql.params).toEqual(['123000000000', 1000]);
});

test('transactions by account eq', () => {
  let sql = transactions.reqToSql({query: {'account.id': '0.1.123'}});
  let expected = normalizeSql(
    boilerplatePrefix +
      `WHERE (realm_num = $1 and entity_num = $2 ) AND 1=1 AND 1=1
    ORDER BY ctl.consensus_timestamp desc limit $3 ) AS tlist` +
      boilerplateSuffix
  );
  expect(normalizeSql(sql.query)).toEqual(expected);
  expect(sql.params).toEqual(['1', '123', 1000]);
});
