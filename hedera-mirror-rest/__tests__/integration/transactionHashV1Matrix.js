/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import {isV2Schema} from '../testutils.js';

const nullifyPayerAccountId = async () => pool.queryQuietly('update transaction_hash_sharded set payer_account_id = null');
const putHashInOldTable = async () => pool.queryQuietly(
  `with deleted as (DELETE from transaction_hash_sharded RETURNING *)
                    INSERT into transaction_hash_old(consensus_timestamp, hash, payer_account_id)
                       SELECT consensus_timestamp, hash, payer_account_id from deleted`);

const applyMatrix = (spec) => {
  if (isV2Schema()) {
    return [spec];
  }

  const defaultSpec = {...spec};

  defaultSpec.name = `${defaultSpec.name} - default`;

  const nullPayerAccountIdSpec = {...spec};
  nullPayerAccountIdSpec.name = `${nullPayerAccountIdSpec.name} - null transaction_hash.payer_account_id`;
  nullPayerAccountIdSpec.postSetup = nullifyPayerAccountId;

  const transactionHashOldSpec = {...spec};
  transactionHashOldSpec.name = `${transactionHashOldSpec.name} - in old transaction_hash table`
  transactionHashOldSpec.postSetup = putHashInOldTable;

  return [defaultSpec, transactionHashOldSpec, nullPayerAccountIdSpec];
};

export default applyMatrix;
