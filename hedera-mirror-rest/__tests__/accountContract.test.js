/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const accountContract = require('../accountContract');
const {assertSqlQueryEqual} = require('./testutils');

describe('getAccountContractUnionQueryWithOrder', () => {
  test('no order options', () => {
    assertSqlQueryEqual(
      accountContract.getAccountContractUnionQueryWithOrder(),
      `
    (
      select ${accountContract.accountFields}
      from entity
      where type = 'ACCOUNT'
    )
    union all
    (
      select ${accountContract.contractFields}
      from contract
    )`
    );
  });

  test('one order option', () => {
    assertSqlQueryEqual(
      accountContract.getAccountContractUnionQueryWithOrder({field: 'id', order: 'desc'}),
      `
    (
      select ${accountContract.accountFields}
      from entity
      where type = 'ACCOUNT'
      order by id desc
    )
    union all
    (
      select ${accountContract.contractFields}
      from contract
      order by id desc
    )`
    );
  });

  test('two order options', () => {
    const actual = accountContract.getAccountContractUnionQueryWithOrder(
      {field: 'id', order: 'asc'},
      {field: 'created_timestamp', order: 'desc'}
    );
    assertSqlQueryEqual(
      actual,
      `
    (
      select ${accountContract.accountFields}
      from entity
      where type = 'ACCOUNT'
      order by id asc, created_timestamp desc
    )
    union all
    (
      select ${accountContract.contractFields}
      from contract
      order by id asc, created_timestamp desc
    )`
    );
  });
});
