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

const {RecordFileService} = require('../../service');

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

describe('RecordFileService.getRecordFileBlockDetailsFromTimestamp tests', () => {
  test('RecordFileService.getRecordFileBlockDetailsFromTimestamp - No match', async () => {
    await expect(RecordFileService.getRecordFileBlockDetailsFromTimestamp(1)).resolves.toBeNull();
  });

  const inputRecordFile = [
    {
      index: 1,
      consensus_start: 1,
      consensus_end: 3,
      hash: 'dee34',
    },
  ];

  const expectedRecordFile = {
    consensusEnd: '3',
    hash: 'dee34',
    index: '1',
  };

  test('RecordFileService.getRecordFileBlockDetailsFromTimestamp - Row match w start', async () => {
    await integrationDomainOps.loadRecordFiles(inputRecordFile);

    await expect(RecordFileService.getRecordFileBlockDetailsFromTimestamp(1)).resolves.toMatchObject(
      expectedRecordFile
    );
  });

  test('RecordFileService.getRecordFileBlockDetailsFromTimestamp - Row match w end', async () => {
    await integrationDomainOps.loadRecordFiles(inputRecordFile);

    await expect(RecordFileService.getRecordFileBlockDetailsFromTimestamp(3)).resolves.toMatchObject(
      expectedRecordFile
    );
  });

  test('RecordFileService.getRecordFileBlockDetailsFromTimestamp - Row match in range', async () => {
    await integrationDomainOps.loadRecordFiles(inputRecordFile);

    await expect(RecordFileService.getRecordFileBlockDetailsFromTimestamp(2)).resolves.toMatchObject(
      expectedRecordFile
    );
  });
});
