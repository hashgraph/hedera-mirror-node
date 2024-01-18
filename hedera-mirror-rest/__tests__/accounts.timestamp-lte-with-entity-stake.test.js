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

import {setupIntegrationTest} from './integrationUtils.js';
import integrationDomainOps from './integrationDomainOps.js';
import * as utils from '../utils.js';
import request from 'supertest';
import server from '../server.js';
import * as constants from '../constants.js';

setupIntegrationTest();

describe('Accounts timestamp lte with entity stake tests', () => {
  const REWARD_FOR_ENTITY_STAKE = 1;
  const REWARD_FOR_ENTITY_STAKE_HISTORY = 2;
  const RES_ACCOUNT_COMMON_FILEDS = {
    account: '0.0.16446',
    alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
    created_timestamp: null,
    decline_reward: false,
    deleted: false,
    ethereum_nonce: null,
    evm_address: '0x000000000000000000000000000000000000403e',
    expiry_timestamp: null,
    auto_renew_period: null,
    key: null,
    max_automatic_token_associations: 0,
    memo: 'entity memo',
    receiver_sig_required: false,
    staked_account_id: '0.0.2',
    staked_node_id: 1,
    stake_period_start: "147339525398400.000000000",
    links: {
      next: null,
    },
    balance: {
      balance: 80,
      timestamp: "1702684807.000000000",
      tokens: []
    },
    transactions: [],
  }
  const ENTITY_STAKE_COMMON_FILEDS = {
    decline_reward_start: false,
    end_stake_period: 147339525398408,
    id: 16446,
    staked_node_id_start: 1,
    staked_to_me: 95260078,
    stake_total_start: 10,
  }
  const nanoSecondsPerSecond = 10n ** 9n;
  const fifteenDaysInNs = constants.ONE_DAY_IN_NS * 15n;
  const currentNs = BigInt(Date.now()) * constants.NANOSECONDS_PER_MILLISECOND;
  const beginningOfCurrentMonth = utils.getFirstDayOfMonth(currentNs);
  const beginningOfPreviousMonth = utils.getFirstDayOfMonth(beginningOfCurrentMonth - 1n);
  const middleOfPreviousMonth = beginningOfPreviousMonth + fifteenDaysInNs;

  const timestamp_EntityStake = middleOfPreviousMonth + nanoSecondsPerSecond * 7n;
  const timestamp_EntityStakeHistory_1 = beginningOfPreviousMonth;
  const timestamp_EntityStakeHistory_2 = beginningOfPreviousMonth + nanoSecondsPerSecond * 7n;

  const ACCOUNT_COMMON_FILEDS = {
    balance: 80,
    balance_timestamp: timestamp_EntityStake,
    num: 16446,
    alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
    public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
    staked_node_id: 1,
    staked_account_id: 2,
    decline_reward: false,
    stake_period_start: 1705318581
  }

  beforeEach(async () => {
    await integrationDomainOps.loadAccounts([
      {
        ...ACCOUNT_COMMON_FILEDS,
        timestamp_range: `[${timestamp_EntityStake},)`,
      },
      {
        ...ACCOUNT_COMMON_FILEDS,
        timestamp_range: `[${timestamp_EntityStakeHistory_1},${timestamp_EntityStakeHistory_2})`,
      }
    ]);

    await integrationDomainOps.loadEntityStakes([
        {
          ...ENTITY_STAKE_COMMON_FILEDS,
          pending_reward: REWARD_FOR_ENTITY_STAKE,
          timestamp_range: `[${timestamp_EntityStake},)`
        },
        {
          ...ENTITY_STAKE_COMMON_FILEDS,
          pending_reward: REWARD_FOR_ENTITY_STAKE_HISTORY,

          timestamp_range: `[${timestamp_EntityStakeHistory_1},${timestamp_EntityStakeHistory_2})`
        }
      ]
    );
  });

  const testSpecs = [
    {
      name: 'Accounts timestamp with entity stake tests. Taken form entity_stake table',
      urls: [
        `/api/v1/accounts/0.0.16446?timestamp=gte:${utils.nsToSecNs(timestamp_EntityStake)}`,
      ],
      expected: {
        ...RES_ACCOUNT_COMMON_FILEDS,
        pending_reward: REWARD_FOR_ENTITY_STAKE,
      },
    },
    {
      name: 'Accounts timestamp with entity stake tests. Taken form entity_stake_history table',
      urls: [
        `/api/v1/accounts/0.0.16446?timestamp=gte:${utils.nsToSecNs(timestamp_EntityStakeHistory_1)}&timestamp=lt:${utils.nsToSecNs(timestamp_EntityStakeHistory_2)}`,
      ],
      expected: {
        ...RES_ACCOUNT_COMMON_FILEDS,
        pending_reward: REWARD_FOR_ENTITY_STAKE_HISTORY,
      },
    },
  ];

  testSpecs.forEach((spec) => {
    spec.urls.forEach((url) => {
      test(spec.name, async () => {
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        expect(response.body).toEqual(spec.expected);
      });
    });
  });
});
