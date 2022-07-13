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

import {response} from '../../config';
import constants from '../../constants';
import {BlockController} from '../../controllers';

describe('Block Controller', () => {
  test('Verify extractOrderFromFilters', async () => {
    const order = BlockController.extractOrderFromFilters([]);
    expect(order).toEqual(constants.orderFilterValues.DESC);
  });

  test('Verify extractOrderFromFilters with param asc', async () => {
    const order = BlockController.extractOrderFromFilters([{key: 'order', operator: '=', value: 'asc'}]);
    expect(order).toEqual('asc');
  });

  test('Verify extractLimitFromFilters', async () => {
    const limit = BlockController.extractLimitFromFilters({});
    expect(limit).toEqual(defaultLimit);
  });

  test('Verify extractLimitFromFilters with param', async () => {
    const limit = BlockController.extractLimitFromFilters([{key: 'limit', operator: '=', value: 50}]);
    expect(limit).toEqual(50);
  });

  test('Verify extractLimitFromFilters with out of range limit', async () => {
    const limit = await BlockController.extractLimitFromFilters([{key: 'limit', operator: '=', value: maxLimit + 1}]);
    expect(limit).toEqual(defaultLimit);
  });

  test('Verify extractSqlFromBlockFilters', async () => {
    const queryObj = BlockController.extractSqlFromBlockFilters([]);
    expect(queryObj).toEqual({order: 'desc', limit: 25, whereQuery: []});
  });

  test('Verify extractSqlFromBlockFilters with block.number, order and limit params', async () => {
    const queryObj = BlockController.extractSqlFromBlockFilters([
      {key: 'block.number', operator: '>', value: 10},
      {key: 'order', operator: '=', value: 'asc'},
      {key: 'limit', operator: '=', value: 10},
    ]);

    expect(queryObj.order).toEqual('asc');
    expect(queryObj.limit).toEqual(10);
    expect(queryObj.whereQuery[0].query).toEqual('index >');
    expect(queryObj.whereQuery[0].param).toEqual(10);
  });

  test('Verify extractSqlFromBlockFilters with block.number, timestamp, order and limit params', async () => {
    const queryObj = BlockController.extractSqlFromBlockFilters([
      {key: 'block.number', operator: '>=', value: 10},
      {key: 'timestamp', operator: '<', value: '1676540001.234810000'},
      {key: 'order', operator: '=', value: 'asc'},
      {key: 'limit', operator: '=', value: 10},
    ]);

    expect(queryObj.order).toEqual('asc');
    expect(queryObj.limit).toEqual(10);
    expect(queryObj.whereQuery[0].query).toEqual('index >=');
    expect(queryObj.whereQuery[0].param).toEqual(10);
    expect(queryObj.whereQuery[1].query).toEqual('consensus_end <');
    expect(queryObj.whereQuery[1].param).toEqual('1676540001.234810000');
  });

  test('Verify getFilterWhereCondition', async () => {
    const whereConditions = BlockController.getFilterWhereCondition('index', {operator: '=', value: 10});
    expect(whereConditions).toHaveProperty('query');
    expect(whereConditions).toHaveProperty('param');
    expect(whereConditions.query).toEqual('index =');
    expect(whereConditions.param).toEqual(10);
  });
});
