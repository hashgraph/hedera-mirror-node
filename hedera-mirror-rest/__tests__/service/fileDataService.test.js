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

const {FileData} = require('../../model');
const {FileDataService} = require('../../service');

// add logger configuration support
require('../testutils');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');

const {defaultMochaStatements} = require('./defaultMochaStatements');
defaultMochaStatements(jest, integrationDbOps, integrationDomainOps);

const files = [
  {
    consensus_timestamp: 1,
    entity_id: 112,
    file_data: '0a1008b0ea0110cac1181a0608a0a1d09306121008b0ea0110e18e191a0608b0bdd09306',
    transaction_type: 17,
  },
  {
    consensus_timestamp: 2,
    entity_id: 112,
    file_data: '0a1008b0ea0110f5f3191a06089085d09306121008b0ea0110cac1181a0608a0a1d09306',
    transaction_type: 16,
  },
  {
    consensus_timestamp: 3,
    entity_id: 112,
    file_data: '0a1008b0ea0110e9c81a1a060880e9cf9306121008b0ea0110f5f3191a06089085d09306',
    transaction_type: 19,
  },
  {
    consensus_timestamp: 4,
    entity_id: 112,
    file_data: '0a1008b0ea0110f9bb1b1a0608f0cccf9306121008b0ea0110e9c81a1a060880e9cf9306',
    transaction_type: 19,
  },
];

const fileId = 112;
describe('FileDataService.getExchangeRate tests', () => {
  test('FileDataService.getExchangeRate - No match', async () => {
    await expect(FileDataService.getExchangeRate({order: 'desc', whereQuery: []})).resolves.toBeNull();
  });

  const expectedPreviousFile = {
    current_cent: 435305,
    current_expiration: 1651766400,
    current_hbar: 30000,
    next_cent: 424437,
    next_expiration: 1651770000,
    next_hbar: 30000,
    timestamp: 3,
  };

  const expectedLatestFile = {
    current_cent: 450041,
    current_expiration: 1651762800,
    current_hbar: 30000,
    next_cent: 435305,
    next_expiration: 1651766400,
    next_hbar: 30000,
    timestamp: 4,
  };

  test('FileDataService.getExchangeRate - Row match w latest', async () => {
    await integrationDomainOps.loadFileData(files);

    await expect(FileDataService.getExchangeRate({order: 'desc', whereQuery: []})).resolves.toMatchObject(
      expectedLatestFile
    );
  });

  test('FileDataService.getExchangeRate - Row match w previous latest', async () => {
    await integrationDomainOps.loadFileData(files);

    const where = [
      {
        query: `${FileData.CONSENSUS_TIMESTAMP} <= `,
        param: expectedPreviousFile.timestamp,
      },
    ];
    await expect(FileDataService.getExchangeRate({order: 'desc', whereQuery: where})).resolves.toMatchObject(
      expectedPreviousFile
    );
  });
});

describe('FileDataService.getLatestFileDataContents tests', () => {
  test('FileDataService.getLatestFileDataContents - No match', async () => {
    await expect(FileDataService.getLatestFileDataContents({order: 'desc', whereQuery: []})).resolves.toBeNull();
  });

  const expectedPreviousFile = {
    consensus_timestamp: 3,
    file_data: '0a1008b0ea0110e9c81a1a060880e9cf9306121008b0ea0110f5f3191a06089085d09306',
  };

  const expectedLatestFile = {
    consensus_timestamp: 4,
    file_data: '0a1008b0ea0110f9bb1b1a0608f0cccf9306121008b0ea0110e9c81a1a060880e9cf9306',
  };

  test('FileDataService.getLatestFileDataContents - Row match w latest', async () => {
    await integrationDomainOps.loadFileData(files);

    const where = [
      {
        query: `${FileData.ENTITY_ID} = `,
        param: fileId,
      },
    ];
    await expect(FileDataService.getLatestFileDataContents({order: 'desc', whereQuery: where})).resolves.toMatchObject(
      expectedLatestFile
    );
  });

  test('FileDataService.getLatestFileDataContents - Row match w previous latest', async () => {
    await integrationDomainOps.loadFileData(files);

    const where = [
      {
        query: `${FileData.ENTITY_ID} = `,
        param: fileId,
      },
      {
        query: `${FileData.CONSENSUS_TIMESTAMP} <= `,
        param: expectedPreviousFile.consensus_timestamp,
      },
    ];
    await expect(FileDataService.getLatestFileDataContents({order: 'desc', whereQuery: where})).resolves.toMatchObject(
      expectedPreviousFile
    );
  });
});
