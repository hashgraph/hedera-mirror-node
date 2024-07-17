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

import crypto from 'crypto';

import {ETH_HASH_LENGTH} from "../../constants";
import {getTransactionHash} from "../../transactionHash";
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';

setupIntegrationTest();

describe('getTransactionHash', () => {
  test('getTransactionHash', async () =>  {
    const transactionHashes = [
      {consensus_timestamp: 1, hash: crypto.randomBytes(48), payer_account_id: 10},
      {consensus_timestamp: 2, hash: crypto.randomBytes(32), payer_account_id: 11},
    ];
    const samePrefixHash = Buffer.from(transactionHashes[0].hash)
      .fill(~transactionHashes[0].hash.at(ETH_HASH_LENGTH), ETH_HASH_LENGTH, ETH_HASH_LENGTH + 1);
    transactionHashes.push({hash: samePrefixHash, consensus_timestamp: 3, payer_account_id: 12});
    await integrationDomainOps.loadTransactionHashes(transactionHashes);

    await expect(getTransactionHash(transactionHashes[0].hash)).resolves.toEqual([transactionHashes[0]]);
    await expect(getTransactionHash(transactionHashes[1].hash)).resolves.toEqual([transactionHashes[1]]);
    await expect(getTransactionHash(transactionHashes[0].hash.subarray(0, ETH_HASH_LENGTH))).resolves.toEqual([transactionHashes[0], transactionHashes[2]]);
  });
});
