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

const filterKeys = {
  ACCOUNT_ID: 'account.id',
  ACCOUNT_BALANCE: 'account.balance',
  ACCOUNT_PUBLICKEY: 'account.publickey',
  LIMIT: 'limit',
  ORDER: 'order',
  RESULT: 'result',
  SEQUENCE_NUMBER: 'sequencenumber',
  TIMESTAMP: 'timestamp',
  TYPE: 'type',
};

const entityColumns = {
  ENTITY_NUM: 'entity_num',
  ENTITY_REALM: 'entity_realm',
  ENTITY_SHARD: 'entity_shard',
  PUBLIC_KEY: 'ed25519_public_key_hex',
};

module.exports = {
  entityColumns: entityColumns,
  filterKeys: filterKeys,
};
