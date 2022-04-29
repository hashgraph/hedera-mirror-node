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

const {RecordFileService} = require('../../service');

// add logger configuration support
require('../testutils');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');

const {defaultMochaStatements} = require('./defaultMochaStatements');
defaultMochaStatements(jest, integrationDbOps, integrationDomainOps);

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
