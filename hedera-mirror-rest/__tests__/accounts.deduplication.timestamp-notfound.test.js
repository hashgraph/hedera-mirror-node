/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import {setupIntegrationTest} from './integrationUtils.js';
import integrationDomainOps from './integrationDomainOps.js';
import * as utils from '../utils.js';
import request from 'supertest';
import server from '../server.js';
import * as constants from '../constants.js';

setupIntegrationTest();

describe('Accounts deduplicate tests', () => {
  const fifteenDaysInNs = 1_296_000_000_000_000n;
  const tenDaysInNs = 864_000_000_000_000n;
  const nanoSecondsPerSecond = 1_000_000_000n;
  const currentNs = BigInt(Date.now()) * constants.NANOSECONDS_PER_MILLISECOND;
  const beginningOfCurrentMonth = utils.getFirstDayOfMonth(currentNs);
  const beginningOfPreviousMonth = utils.getFirstDayOfMonth(beginningOfCurrentMonth - 1n);
  const tenDaysInToPreviousMonth = beginningOfPreviousMonth + tenDaysInNs;
  const middleOfPreviousMonth = beginningOfPreviousMonth + fifteenDaysInNs;

  beforeEach(async () => {
    await integrationDomainOps.loadAccounts([
      {
        num: 3,
      },
      {
        num: 7,
      },
      {
        balance: 80,
        balance_timestamp: middleOfPreviousMonth + nanoSecondsPerSecond * 4n,
        num: 8,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${middleOfPreviousMonth + nanoSecondsPerSecond * 7n},)`,
        staked_node_id: 1,
        staked_account_id: 1,
      },
      {
        balance: 30,
        balance_timestamp: tenDaysInToPreviousMonth + nanoSecondsPerSecond * 4n,
        num: 8,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${middleOfPreviousMonth + nanoSecondsPerSecond * 7n - nanoSecondsPerSecond}, ${
          middleOfPreviousMonth + nanoSecondsPerSecond * 7n
        })`,
        staked_node_id: 2,
        staked_account_id: 2,
      },
      {
        balance: 75,
        balance_timestamp: '5000000000',
        num: 9,
        alias: 'HIQQEXWKW53RKN4W6XXC4Q232SYNZ3SZANVZZSUME5B5PRGXL663UAQA',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d8',
        timestamp_range: `[${middleOfPreviousMonth + nanoSecondsPerSecond * 7n},)`,
        staked_node_id: 1,
        staked_account_id: 1,
      },
      {
        num: 98,
      },
    ]);
    await integrationDomainOps.loadBalances([
      {
        timestamp: tenDaysInToPreviousMonth + nanoSecondsPerSecond * 4n,
        id: 2,
        balance: 2,
      },
      {
        timestamp: tenDaysInToPreviousMonth + nanoSecondsPerSecond * 4n,
        id: 8,
        balance: 555,
      },
      {
        timestamp: '5000000000',
        id: 2,
        balance: 2,
      },
      {
        timestamp: '5000000000',
        id: 9,
        balance: 400,
      },
    ]);
  });

  const testSpecs = [
    {
      name: 'Accounts not found',
      urls: [
        `/api/v1/accounts/0.0.8?timestamp=lte:1234567880.000000006`,
        //`/api/v1/accounts/0.0.8?timestamp=ne:1234567890.000000004`,
        //`api/v1/accounts/0.0.9?timestamp=ne:5.000&order=asc`,
        `/api/v1/accounts/0.0.KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ?timestamp=lte:1234567880.000000006`,
      ],
      expected: {
        message: 'Not found',
      },
    },
  ];

  testSpecs.forEach((spec) => {
    spec.urls.forEach((url) => {
      test(spec.name, async () => {
        const response = await request(server).get(url);
        expect(response.status).toEqual(404);
        expect(response.body._status.messages[0]).toEqual(spec.expected);
      });
    });
  });
});
