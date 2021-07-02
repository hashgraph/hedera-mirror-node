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
const {NFTModel} = require('../../model/nft');
const NFTService = require('../../service/nftService');

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

describe('DB integration test - NFTService.getNft', () => {
  test('DB integration test -  NFTService.getNft - Verify simple insert and find', async () => {
    await integrationDomainOps.addNft({
      account_id: '0.0.1',
      created_timestamp: 1,
      metadata: '\\x0D',
      modified_timestamp: 1,
      serial_number: 1,
      token_id: '0.0.2',
    });

    const expectedNft = new NFTModel({
      account_id: '1',
      created_timestamp: '1',
      deleted: false,
      metadata: Buffer.from([13]),
      modified_timestamp: '1',
      serial_number: '1',
      token_id: '2',
    });
    await expect(NFTService.getNft(2, 1)).resolves.toMatchObject(expectedNft);
  });
});
