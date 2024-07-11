/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
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
 */

import {ETH_HASH_LENGTH} from './constants';
import {TransactionHash} from './model';

const transactionHashQuery = `
  select *
  from ${TransactionHash.tableName}
  where substring(${TransactionHash.HASH} from 1 for ${ETH_HASH_LENGTH}) = $1
  order by ${TransactionHash.CONSENSUS_TIMESTAMP}`;

const transactionHashShardedQuery = `
  select *
  from get_transaction_info_by_hash($1)
  order by ${TransactionHash.CONSENSUS_TIMESTAMP}`;

const transactionHashShardedQueryEnabled = (() => {
  let result = undefined;
  return () =>
    (async () => {
      if (result !== undefined) {
        return result;
      }

      const {rows} = await pool.queryQuietly(`select count(*) > 0 as enabled
                                              from pg_proc
                                              where proname = 'get_transaction_info_by_hash'`);
      result = rows[0].enabled;
      return result;
    })();
})();

const getTransactionHashQuery = async () => {
  return (await transactionHashShardedQueryEnabled()) ? transactionHashShardedQuery : transactionHashQuery;
};

// The first part of the regex is for the base64url encoded 48-byte transaction hash. Note base64url replaces '+' with
// '-' and '/' with '_'. The padding character '=' is not included since base64 encoding a 48-byte array always
// produces a 64-byte string without padding
const transactionHashRegex = /^([\dA-Za-z+\-\/_]{64}|(0x)?[\dA-Fa-f]{96})$/;

const isValidTransactionHash = (hash) => transactionHashRegex.test(hash);

export {getTransactionHashQuery, isValidTransactionHash};
