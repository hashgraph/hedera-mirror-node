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

const {Range} = require('pg-range');
const ContractViewModel = require('../../viewmodel/contractViewModel');

describe('ContractViewModel', () => {
  const defaultContract = {
    autoRenewPeriod: '1000',
    createdTimestamp: '999123456789',
    deleted: false,
    expirationTimestamp: '99999999000000000',
    fileId: '2800',
    id: '3001',
    key: Buffer.from([0xaa, 0xbb, 0xcc, 0x77]),
    memo: 'sample contract',
    obtainerId: '2005',
    proxyAccountId: '2002',
    timestampRange: Range('1000123456789', '2000123456789', '[)'),
  };
  const defaultExpected = {
    admin_key: {
      _type: 'ProtobufEncoded',
      key: 'aabbcc77',
    },
    auto_renew_period: 1000,
    contract_id: '0.0.3001',
    created_timestamp: '999.123456789',
    deleted: false,
    expiration_timestamp: '99999999.000000000',
    file_id: '0.0.2800',
    memo: 'sample contract',
    obtainer_id: '0.0.2005',
    proxy_account_id: '0.0.2002',
    solidity_address: '0x00000000000000000000000000000bb900000000',
    timestamp: {
      from: '1000.123456789',
      to: '2000.123456789',
    },
  };

  test('no bytecode', () => {
    expect(new ContractViewModel(defaultContract)).toEqual(defaultExpected);
  });

  test('bytecode', () => {
    expect(
      new ContractViewModel({
        ...defaultContract,
        bytecode: Buffer.from([0xde, 0xad, 0xbe, 0xef]),
      })
    ).toEqual({
      ...defaultExpected,
      bytecode: '0xdeadbeef',
    });
  });

  test('open-ended timestamp range', () => {
    expect(
      new ContractViewModel({
        ...defaultContract,
        timestampRange: Range('1000123456789', null, '[)'),
      })
    ).toEqual({
      ...defaultExpected,
      timestamp: {
        from: '1000.123456789',
        to: null,
      },
    });
  });
});
