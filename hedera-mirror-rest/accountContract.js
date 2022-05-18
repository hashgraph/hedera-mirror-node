/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

const utils = require('./utils');

const commonFields = [
  'auto_renew_period',
  'created_timestamp',
  'deleted',
  'evm_address',
  'expiration_timestamp',
  'id',
  'key',
  'max_automatic_token_associations',
  'memo',
  'num',
  'public_key',
  'proxy_account_id',
  'realm',
  'shard',
  'timestamp_range',
  'type',
];
const accountOnlyFields = ['alias', 'ethereum_nonce', 'receiver_sig_required'];
const accountFields = commonFields.concat(accountOnlyFields);
const contractFields = commonFields.concat(accountOnlyFields.map((f) => `null as ${f}`));

/**
 * Gets the account contract union query, with order options
 *
 * @param {{field: {string}, order: 'asc'|'desc'}} orderOptions
 * @return {string}
 */
const getAccountContractUnionQueryWithOrder = (...orderOptions) => {
  const order = orderOptions.map((option) => `${option.field} ${option.order}`).join(', ');
  const orderClause = (order && `order by ${order}`) || '';
  return `
  (
    select ${accountFields}
    from entity
    where type = 'ACCOUNT'
    ${orderClause}
   )
   union all
   (
     select ${contractFields}
     from contract
     ${orderClause}
   )
  `;
};

module.exports = {
  getAccountContractUnionQueryWithOrder,
};

if (utils.isTestEnv()) {
  Object.assign(module.exports, {
    accountFields,
    contractFields,
  });
}
