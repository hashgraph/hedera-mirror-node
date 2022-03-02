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

const {EntityService} = require('../../service');
const AccountAlias = require('../../accountAlias');

// add logger configuration support
require('../testutils');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');

jest.setTimeout(40000);

let dbConfig;

// set timeout for beforeAll to 4 minutes as downloading docker image if not exists can take quite some time
const defaultBeforeAllTimeoutMillis = 240 * 1000;

beforeAll(async () => {
  dbConfig = await integrationDbOps.instantiateDatabase();
  await integrationDomainOps.setUp({}, dbConfig.sqlConnection);
  global.pool = dbConfig.sqlConnection;
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await integrationDbOps.closeConnection(dbConfig);
});

beforeEach(async () => {
  if (!dbConfig.sqlConnection) {
    logger.warn(`sqlConnection undefined, acquire new connection`);
    dbConfig.sqlConnection = integrationDbOps.getConnection(dbConfig.dbSessionConfig);
  }

  await integrationDbOps.cleanUp(dbConfig.sqlConnection);
});

const defaultEntityAlias = new AccountAlias('1', '2', 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ');
const defaultInputEntity = [
  {
    alias: defaultEntityAlias.base32Alias,
    id: 3,
    shard: 1,
    realm: 2,
  },
];

const defaultExpectedEntity = {
  id: '281483566645248',
};

describe('EntityService.getAccountFromAlias tests', () => {
  test('EntityService.getAccountFromAlias - No match', async () => {
    await expect(EntityService.getAccountFromAlias({alias: '1'})).resolves.toBeNull();
  });

  test('EntityService.getAccountFromAlias - Matching entity', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.getAccountFromAlias(defaultEntityAlias)).resolves.toMatchObject(defaultExpectedEntity);
  });

  // test('EntityService.getAccountFromAlias - Duplicate alias', async () => {
  //   const inputEntities = [
  //     {
  //       alias: defaultEntityAlias.base32Alias,
  //       id: 3,
  //       num: 3,
  //       shard: 1,
  //       realm: 2,
  //     },
  //     {
  //       alias: defaultEntityAlias.base32Alias,
  //       id: 4,
  //       num: 4,
  //       shard: 1,
  //       realm: 2,
  //     },
  //   ];
  //   await integrationDomainOps.loadEntities(inputEntities);

  //   expect(() => EntityService.getAccountFromAlias(defaultEntityAlias)).toThrowErrorMatchingSnapshot();
  // });
});

// describe('EntityService.getAccountIdFromAlias tests', () => {
//   test('EntityService.getAccountIdFromAlias - No match', async () => {
//     const entityAlias = new AccountAlias('3', '4', 'abc');
//     expect(() => EntityService.getAccountIdFromAlias(entityAlias)).toThrowErrorMatchingSnapshot();
//   });

//   test('EntityService.getAccountFromAlias - Matching id', async () => {
//     await integrationDomainOps.loadEntities(defaultInputEntity);

//     await expect(EntityService.getAccountIdFromAlias(defaultEntityAlias)).resolves.toBe(defaultExpectedEntity.id);
//   });
// });
