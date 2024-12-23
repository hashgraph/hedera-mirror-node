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

import fetchMock, {manageFetchMockGlobally} from '@fetch-mock/jest';
import {jest} from '@jest/globals';
import console from 'console';
import {report} from '../src/report.js';

manageFetchMockGlobally(jest);

global.console = console;

describe('report', () => {
  test('single account', async () => {
    const accountUrl = 'https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1000?timestamp=1734393600';
    const testnetUrl = 'https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=0.0.1000&limit=100&order=asc&timestamp=gt:1734393025.693371991&timestamp=lt:1734480000';

    fetchMock.get(accountUrl, {
      status: 200, body:
        JSON.stringify({balance: {balance: 100, timestamp: 1734393500}})
    });
    fetchMock.get(testnetUrl, {
      status: 200, body:
        JSON.stringify({
          transactions: [{consensus_timestamp: 1734393500, transaction_id: "0.0.1000-1734393500-000000000"}],
          links: {next: null}
        })
    });

    await report({account: ["0.0.1000"], date: "2024-12-17", network: "testnet"});
    expect(fetchMock).toHaveFetched(accountUrl, testnetUrl);
  });
});
