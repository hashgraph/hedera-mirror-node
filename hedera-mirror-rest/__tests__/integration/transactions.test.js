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

import {Range} from 'pg-range';
import sinon from 'sinon';

import config from '../../config';
import {NANOSECONDS_PER_MILLISECOND, orderFilterValues} from '../../constants';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import transactions from '../../transactions';

setupIntegrationTest();

describe('bindTimestampRange', () => {
  const {ASC, DESC} = orderFilterValues;

  describe('bindTimestampRange=true', () => {
    let clock;
    const now = Date.parse('2022-10-15T08:10:15.333Z');
    const nowInNs = BigInt(now) * NANOSECONDS_PER_MILLISECOND;
    const {query: {maxTimestampRangeNs}} = config;

    beforeEach(async () => {
      await integrationDomainOps.loadTransactions([{consensus_timestamp: 1606123456000111222n, payerAccountId: 2000}]);
      config.query.bindTimestampRange = true;
      clock = sinon.useFakeTimers(now);
    });

    afterEach(() => {
      config.query.bindTimestampRange = false;
      clock.restore();
    });

    const spec = [
      {
        name: 'empty and desc',
        expected: Range(nowInNs - maxTimestampRangeNs + 1n, nowInNs),
      },
      {
        name: 'empty and asc',
        order: ASC,
        expected: Range(1606123456000111222n, 1606123456000111222n + maxTimestampRangeNs - 1n),
      },
      {
        name: 'no adjustment',
        range: Range(1000n, maxTimestampRangeNs + 999n),
        expected: Range(1000n, maxTimestampRangeNs + 999n)
      },
      {
        name: 'adjust lower bound',
        range: Range(1000n, 1000n + maxTimestampRangeNs),
        expected: Range(1001n, 1000n + maxTimestampRangeNs),
      },
      {
        name: 'adjust upper bound',
        range: Range(1000n, 1000n + maxTimestampRangeNs),
        order: ASC,
        expected: Range(1000n, 999n + maxTimestampRangeNs),
      }
    ];
    test.each(spec)('$name', async ({expected, range, order = DESC}) => {
      await expect(transactions.bindTimestampRange(range, order)).resolves.toEqual(expected);
    });
  });

  describe('bindTimestampRange=false', () => {
    const spec = [
      {name: 'null', range: null},
      {name: 'no lower bound', range: Range(undefined, 1500n)},
      {name: 'no upper bound', range: Range(1500n, undefined)},
      {name: 'closed range', range: Range(1500n, 3000n)},
    ];
    test.each(spec)('$name', async ({range, order = DESC}) => {
      await expect(transactions.bindTimestampRange(range, order)).resolves.toEqual(range);
    });
  });
});

describe('getFirstTransactionTimestamp', () => {
  test('no transaction expect 0n', async () => {
    await expect(transactions.getFirstTransactionTimestamp()).resolves.toEqual(0n);
  });

  test('first transaction timestamp', async () => {
    await integrationDomainOps.loadTransactions([
      {consensus_timestamp: 1606123456000111222n, payerAccountId: 2000},
      {consensus_timestamp: 1606137065111222333n, payerAccountId: 2000},
    ]);
    await expect(transactions.getFirstTransactionTimestamp()).resolves.toEqual(1606123456000111222n);
  });
});
