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

const {defaultMochaStatements} = require('./defaultMochaStatements');
defaultMochaStatements(jest, integrationDbOps, integrationDomainOps);

const defaultEntityAlias = new AccountAlias('1', '2', 'KGNABD5L3ZGSRVUCSPDR7TONZSRY3D5OMEBKQMVTD2AC6JL72HMQ');
const defaultInputEntity = [
  {
    alias: defaultEntityAlias.base32Alias,
    id: 281483566645248,
    shard: 1,
    realm: 2,
  },
];

const defaultExpectedEntity = {
  id: 281483566645248,
};

describe('EntityService.getAccountFromAlias tests', () => {
  test('EntityService.getAccountFromAlias - No match', async () => {
    await expect(EntityService.getAccountFromAlias({alias: '1'})).resolves.toBeNull();
  });

  test('EntityService.getAccountFromAlias - Matching entity', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.getAccountFromAlias(defaultEntityAlias)).resolves.toMatchObject(defaultExpectedEntity);
  });

  test('EntityService.getAccountFromAlias - Duplicate alias', async () => {
    const inputEntities = [
      {
        alias: defaultEntityAlias.base32Alias,
        id: 3,
        num: 3,
        shard: 1,
        realm: 2,
      },
      {
        alias: defaultEntityAlias.base32Alias,
        id: 4,
        num: 4,
        shard: 1,
        realm: 2,
      },
    ];
    await integrationDomainOps.loadEntities(inputEntities);

    await expect(() => EntityService.getAccountFromAlias(defaultEntityAlias)).rejects.toThrowErrorMatchingSnapshot();
  });
});

describe('EntityService.getAccountIdFromAlias tests', () => {
  test('EntityService.getAccountIdFromAlias - No match', async () => {
    await expect(() => EntityService.getAccountIdFromAlias(defaultEntityAlias)).rejects.toThrowErrorMatchingSnapshot();
  });

  test('EntityService.getAccountFromAlias - Matching id', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.getAccountIdFromAlias(defaultEntityAlias)).resolves.toBe(defaultExpectedEntity.id);
  });
});

describe('EntityService.isValidAccount tests', () => {
  test('EntityService.isValidAccount - No match', async () => {
    await expect(EntityService.isValidAccount(defaultInputEntity[0].id)).resolves.toBe(false);
  });

  test('EntityService.getAccountFromAlias - Matching', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.isValidAccount(defaultInputEntity[0].id)).resolves.toBe(true);
  });
});

describe('EntityService.getEncodedIdAccountIdOrAlias tests', () => {
  test('EntityService.getEncodedIdAccountIdOrAlias - No match', async () => {
    await expect(EntityService.getEncodedIdAccountIdOrAlias(defaultInputEntity[0].id)).resolves.toBe(
      defaultExpectedEntity.id
    );
  });

  test('EntityService.getEncodedIdAccountIdOrAlias - Matching id', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.getEncodedIdAccountIdOrAlias(defaultInputEntity[0].id)).resolves.toBe(
      defaultExpectedEntity.id
    );
  });

  test('EntityService.getEncodedIdAccountIdOrAlias - Matching alias', async () => {
    await integrationDomainOps.loadEntities(defaultInputEntity);

    await expect(EntityService.getEncodedIdAccountIdOrAlias(defaultInputEntity[0].alias)).resolves.toBe(
      defaultExpectedEntity.id
    );
  });

  test('EntityService.getEncodedIdAccountIdOrAlias - Invalid alias', async () => {
    await expect(EntityService.getEncodedIdAccountIdOrAlias('deadbeef=')).rejects.toThrowErrorMatchingSnapshot();
  });
});
