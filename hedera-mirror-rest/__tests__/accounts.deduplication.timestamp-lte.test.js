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
import {nsToSecNsWithHyphen} from '../utils.js';

setupIntegrationTest();

describe('Accounts deduplicate tests', () => {
  const fifteenDaysInNs = 1_296_000_000_000_000n;
  const tenDaysInNs = 864_000_000_000_000n;
  const nanoSecondsPerSecond = 1_000_000_000n;
  const currentNs = BigInt(Date.now()) * constants.NANOSECONDS_PER_MILLISECOND;
  const beginningOfCurrentMonth = utils.getFirstDayOfMonth(currentNs);

  const beginningOfPreviousMonth = utils.getFirstDayOfMonth(beginningOfCurrentMonth - 1n);

  const tenDaysInToPreviousMonth = beginningOfPreviousMonth + tenDaysInNs;
  const tenDaysMinusNs = tenDaysInToPreviousMonth - 1n;
  const tenDaysMinusSecondMinusNs = tenDaysMinusNs - nanoSecondsPerSecond;

  const tenDaysMinusOneSecond = tenDaysInToPreviousMonth - nanoSecondsPerSecond;
  const tenDaysMinusTwoSecondsOneNano = utils.nsToSecNs(tenDaysInToPreviousMonth - nanoSecondsPerSecond - 1n);

  const oneSecondAfterTenDays = tenDaysInToPreviousMonth + nanoSecondsPerSecond;

  const tenDaysInToPreviousMonthAndSeven = tenDaysInToPreviousMonth + 7n;
  const tenDaysInToPreviousMonthAndSevenMinusOne = tenDaysInToPreviousMonthAndSeven - nanoSecondsPerSecond;

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
        balance_timestamp: middleOfPreviousMonth,
        num: 8,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${tenDaysInToPreviousMonthAndSeven},)`,
        staked_node_id: 1,
        staked_account_id: 1,
      },
      {
        balance: 30,
        balance_timestamp: tenDaysInToPreviousMonth - 3n,
        num: 8,
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        timestamp_range: `[${tenDaysInToPreviousMonthAndSevenMinusOne}, ${tenDaysInToPreviousMonthAndSeven})`,
        staked_node_id: 2,
        staked_account_id: 2,
      },
      {
        num: 9,
      },
      {
        num: 98,
      },
    ]);
    await integrationDomainOps.loadBalances([
      {
        timestamp: tenDaysInToPreviousMonth + nanoSecondsPerSecond,
        id: 2,
        balance: 2,
      },
      {
        timestamp: beginningOfPreviousMonth + nanoSecondsPerSecond,
        id: 8,
        balance: 555,
        tokens: [
          {
            token_num: 99998,
            balance: 71,
          },
          {
            token_num: 99999,
            balance: 72,
          },
        ],
      },
      {
        timestamp: tenDaysInToPreviousMonth,
        id: 2,
        balance: 2,
      },
      {
        timestamp: beginningOfPreviousMonth,
        id: 8,
        balance: 444,
        tokens: [
          {
            token_num: 99998,
            balance: 61,
          },
          {
            token_num: 99999,
            balance: 62,
          },
        ],
      },
    ]);

    await integrationDomainOps.loadTokenAccounts([
      {
        token_id: '0.0.99998',
        account_id: '0.0.7',
        balance: 7,
        created_timestamp: tenDaysMinusOneSecond - 1n,
      },
      {
        token_id: '0.0.99999',
        account_id: '0.0.7',
        balance: 77,
        created_timestamp: '2200',
      },
      {
        token_id: '0.0.99998',
        account_id: '0.0.8',
        balance: 8,
        created_timestamp: tenDaysMinusOneSecond,
      },
      {
        token_id: '0.0.99999',
        account_id: '0.0.8',
        balance: 88,
        created_timestamp: tenDaysMinusOneSecond,
      },
    ]);

    await integrationDomainOps.loadTransactions([
      {
        payerAccountId: '0.0.9',
        nodeAccountId: '0.0.3',
        consensus_timestamp: beginningOfPreviousMonth,
        name: 'TOKENCREATION',
        type: '29',
        entity_id: '0.0.90000',
      },
      {
        payerAccountId: '0.0.9',
        nodeAccountId: '0.0.3',
        consensus_timestamp: tenDaysMinusSecondMinusNs,
        name: 'CRYPTODELETE',
        type: '12',
        entity_id: '0.0.7',
      },
      {
        charged_tx_fee: 0,
        payerAccountId: '0.0.9',
        nodeAccountId: '0.0.3',
        consensus_timestamp: oneSecondAfterTenDays,
        name: 'CRYPTOUPDATEACCOUNT',
        type: '15',
        entity_id: '0.0.8',
      },
    ]);

    await integrationDomainOps.loadCryptoTransfers([
      {
        consensus_timestamp: tenDaysMinusOneSecond,
        payerAccountId: '0.0.8',
        nodeAccountId: '0.0.3',
        treasuryAccountId: '0.0.98',
        token_transfer_list: [
          {
            token_id: '0.0.90000',
            account: '0.0.8',
            amount: -1200,
            is_approval: true,
          },
          {
            token_id: '0.0.90000',
            account: '0.0.9',
            amount: 1200,
            is_approval: true,
          },
        ],
      },
      {
        consensus_timestamp: tenDaysInToPreviousMonth,
        payerAccountId: '0.0.8',
        nodeAccountId: '0.0.3',
        treasuryAccountId: '0.0.98',
        token_transfer_list: [
          {
            token_id: '0.0.90000',
            account: '0.0.8',
            amount: -200,
            is_approval: true,
          },
          {
            token_id: '0.0.90000',
            account: '0.0.1679',
            amount: 200,
            is_approval: true,
          },
        ],
      },
    ]);
  });

  const testSpecs = [
    {
      name: 'Account with timestamp lte',
      urls: [
        `/api/v1/accounts/0.0.8?timestamp=lte:${utils.nsToSecNs(beginningOfCurrentMonth - 1n)}`,
        //`/api/v1/accounts/0.0.KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ?timestamp=lte:${utils.nsToSecNs(tenDaysMinusSecondMinusNs)}`
      ],
      expected: {
        transactions: [
          {
            bytes: 'Ynl0ZXM=',
            charged_tx_fee: 7,
            consensus_timestamp: `${utils.nsToSecNs(tenDaysInToPreviousMonth)}`,
            entity_id: null,
            max_fee: '33',
            memo_base64: null,
            name: 'CRYPTOTRANSFER',
            nft_transfers: [],
            node: '0.0.3',
            nonce: 0,
            parent_consensus_timestamp: null,
            result: 'SUCCESS',
            scheduled: false,
            staking_reward_transfers: [],
            token_transfers: [
              {
                account: '0.0.8',
                amount: -200,
                token_id: '0.0.90000',
                is_approval: true,
              },
              {
                account: '0.0.1679',
                amount: 200,
                token_id: '0.0.90000',
                is_approval: true,
              },
            ],
            transaction_hash: 'AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqKywtLi8w',
            transaction_id: `0.0.8-${utils.nsToSecNsWithHyphen(tenDaysMinusNs.toString())}`,
            transfers: [
              {
                account: '0.0.3',
                amount: 2,
                is_approval: false,
              },
              {
                account: '0.0.8',
                amount: -3,
                is_approval: false,
              },
              {
                account: '0.0.98',
                amount: 1,
                is_approval: false,
              },
            ],
            valid_duration_seconds: '11',
            valid_start_timestamp: `${utils.nsToSecNs(tenDaysMinusNs)}`,
          },
          {
            bytes: 'Ynl0ZXM=',
            charged_tx_fee: 7,
            consensus_timestamp: `${utils.nsToSecNs(tenDaysMinusOneSecond)}`,
            entity_id: null,
            max_fee: '33',
            memo_base64: null,
            name: 'CRYPTOTRANSFER',
            nft_transfers: [],
            node: '0.0.3',
            nonce: 0,
            parent_consensus_timestamp: null,
            result: 'SUCCESS',
            scheduled: false,
            staking_reward_transfers: [],
            transaction_hash: 'AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqKywtLi8w',
            transaction_id: `0.0.8-${utils.nsToSecNsWithHyphen(tenDaysMinusSecondMinusNs.toString())}`,
            transfers: [
              {
                account: '0.0.3',
                amount: 2,
                is_approval: false,
              },
              {
                account: '0.0.8',
                amount: -3,
                is_approval: false,
              },
              {
                account: '0.0.98',
                amount: 1,
                is_approval: false,
              },
            ],
            token_transfers: [
              {
                account: '0.0.8',
                amount: -1200,
                token_id: '0.0.90000',
                is_approval: true,
              },
              {
                account: '0.0.9',
                amount: 1200,
                token_id: '0.0.90000',
                is_approval: true,
              },
            ],
            valid_duration_seconds: '11',
            valid_start_timestamp: `${tenDaysMinusTwoSecondsOneNano}`,
          },
        ],
        balance: {
          timestamp: `${utils.nsToSecNs(tenDaysInToPreviousMonth)}`,
          balance: 80,
          tokens: [
            {
              token_id: '0.0.99998',
              balance: 98,
            },
            {
              token_id: '0.0.99999',
              balance: 88,
            },
          ],
        },
        account: '0.0.8',
        alias: 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ',
        created_timestamp: null,
        decline_reward: false,
        deleted: false,
        ethereum_nonce: null,
        evm_address: '0x0000000000000000000000000000000000000008',
        expiry_timestamp: null,
        auto_renew_period: null,
        key: null,
        max_automatic_token_associations: 0,
        memo: 'entity memo',
        pending_reward: 0,
        receiver_sig_required: false,
        staked_account_id: '0.0.1',
        staked_node_id: 1,
        stake_period_start: null,
        links: {
          next: null,
        },
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
