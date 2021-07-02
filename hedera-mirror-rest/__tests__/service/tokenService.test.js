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

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');
const TokenModel = require('../../model/token');
const TokenService = require('../../service/tokenService');

jest.setTimeout(40000);

let sqlConnection;

// set timeout for beforeAll to 2 minutes as downloading docker image if not exists can take quite some time
const defaultBeforeAllTimeoutMillis = 240 * 1000;

beforeAll(async () => {
  sqlConnection = await integrationDbOps.instantiateDatabase();
  await integrationDomainOps.setUp({}, sqlConnection);
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await integrationDbOps.closeConnection();
});

beforeEach(async () => {
  if (!sqlConnection) {
    logger.warn(`sqlConnection undefined, acquire new connection`);
    sqlConnection = await integrationDbOps.instantiateDatabase();
  }

  await integrationDbOps.cleanUp();
});

describe('DB integration test - TokenService.getToken', () => {
  test('DB integration test -  TokenService.getToken - Verify simple insert and find', async () => {
    await integrationDomainOps.addToken({
      created_timestamp: 1,
      modified_timestamp: 1,
      freeze_key_ed25519_hex: null,
      kyc_key_ed25519_hex: null,
      supply_key_ed25519_hex: null,
      token_id: '0.0.2',
      type: 'FUNGIBLE_COMMON',
      wipe_key_ed25519_hex: null,
    });

    const expectedToken = new TokenModel({
      created_timestamp: '1',
      decimals: '1000',
      freeze_default: false,
      freeze_key: null,
      freeze_key_ed25519_hex: null,
      initial_supply: '1000000',
      kyc_key: null,
      kyc_key_ed25519_hex: null,
      max_supply: '9223372036854775807',
      modified_timestamp: '1',
      name: 'Token name',
      supply_key: null,
      supply_key_ed25519_hex: null,
      supply_type: 'INFINITE',
      symbol: 'YBTJBOAZ',
      token_id: '2',
      total_supply: '1000000',
      treasury_account_id: '98',
      type: 'FUNGIBLE_COMMON',
      wipe_key: null,
      wipe_key_ed25519_hex: null,
    });
    await expect(TokenService.getToken(2)).resolves.toMatchObject(expectedToken);
  });
});
