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

const nullifyPayerAccountId = async () => pool.queryQuietly('update transaction_hash set payer_account_id = null');

const applyMatrix = (spec) => {
  if (isV2Schema()) {
    return [spec];
  }

  const defaultSpec = {...spec};
  defaultSpec.name = `${defaultSpec.name} - default`;

  const nullPayerAccountIdSpec = {...spec};
  nullPayerAccountIdSpec.name = `${nullPayerAccountIdSpec.name} - null transaction_hash.payer_account_id`;
  nullPayerAccountIdSpec.postSetup = nullifyPayerAccountId;

  return [defaultSpec, nullPayerAccountIdSpec];
};

export default applyMatrix;
