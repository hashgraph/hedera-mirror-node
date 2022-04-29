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

const constants = require('../../constants');
const {BlockController} = require('../../controllers');

describe('Block Controller', () => {
  test('Verify extractOrderFromFilters', async () => {
    const order = BlockController.extractOrderFromFilters({}, constants.orderFilterValues.DESC);
    expect(order).toEqual('desc');
  });

  test('Verify extractOrderFromFilters with param asc', async () => {
    const order = BlockController.extractOrderFromFilters({order: 'asc'}, constants.orderFilterValues.DESC);
    expect(order).toEqual('asc');
  });

  test('Verify extractLimitFromFilters', async () => {
    const limit = BlockController.extractLimitFromFilters({}, 25);
    expect(limit).toEqual(25);
  });

  test('Verify extractLimitFromFilters with param', async () => {
    const limit = BlockController.extractLimitFromFilters({limit: 50}, 25);
    expect(limit).toEqual(50);
  });

  test('Verify extractLimitFromFilters with out of range limit', async () => {
    let hasError = false;
    try {
      await BlockController.extractLimitFromFilters({limit: 150}, 25);
    } catch (e) {
      hasError = true;
      expect(e.message).toEqual('Invalid limit param value, must be between 1 and 100');
    }
    expect(hasError).toBeTruthy();
  });

  test('Verify extractSqlFromBlockFilters', async () => {
    const queryObj = BlockController.extractSqlFromBlockFilters({});
    expect(queryObj).toEqual({order: 'desc', limit: 25, whereQuery: []});
  });

  test('Verify extractSqlFromBlockFilters with block.number, order and limit params', async () => {
    const queryObj = BlockController.extractSqlFromBlockFilters({
      'block.number': 'gt:10',
      order: 'asc',
      limit: '10',
    });

    expect(queryObj.order).toEqual('asc');
    expect(queryObj.limit).toEqual(10);
    expect(queryObj.whereQuery[0][0]).toEqual('index  >  ?');
    expect(queryObj.whereQuery[0][1]).toEqual(['10']);
  });

  test('Verify extractSqlFromBlockFilters with block.number, timestamp, order and limit params', async () => {
    const queryObj = BlockController.extractSqlFromBlockFilters({
      'block.number': 'gte:10',
      timestamp: 'lt:1676540001.234810000',
      order: 'asc',
      limit: '10',
    });

    expect(queryObj.order).toEqual('asc');
    expect(queryObj.limit).toEqual(10);
    expect(queryObj.whereQuery[0][0]).toEqual('index  >=  ?');
    expect(queryObj.whereQuery[0][1]).toEqual(['10']);
    expect(queryObj.whereQuery[1][0]).toEqual('consensus_end  <  ?');
    expect(queryObj.whereQuery[1][1]).toEqual(['1676540001234810000']);
  });
});
