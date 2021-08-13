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

const fs = require('fs');
const log4js = require('log4js');
const os = require('os');
const path = require('path');
const yaml = require('js-yaml');
const zlib = require('zlib');

global.logger = log4js.getLogger();

let tempDir;
const custom = {
  hedera: {
    mirror: {
      entityUpdate: {
        accountInfoBatchSize: 100,
        db: {
          useCache: true,
        },
        dryRun: true,
        filePath: path.join(__dirname, '../sample', `keys.csv`),
        log: {
          level: 'info',
        },
        sdkClient: {
          network: 'OTHER',
          nodeAddress: '127.0.0.1',
          nodeId: '0.0.3',
          operatorId: '0.0.2',
          operatorKey: 'fakeKey',
          retryCount: 10,
          retryMsDelay: 1000,
          useCache: true,
        },
      },
    },
  },
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

beforeAll(async () => {
  const sampleDirectory = fs.readdirSync(path.join(__dirname, '../sample'));
  sampleDirectory.map((file) => {
    const unCompressedFileName = file.replace('.gz', '');
    const unCompressedFilePath = path.join(__dirname, '../sample', unCompressedFileName);
    let unCompressedFileExists = fs.existsSync(unCompressedFilePath);

    // skip decompression if non gzip file or if decompressed file already exists
    if (file.indexOf('.gz') <= 0 || unCompressedFileExists) {
      return;
    }

    const compressedFilePath = path.join(__dirname, '../sample', file);
    const compressedFile = fs.createReadStream(compressedFilePath);
    const unCompressedFile = fs.createWriteStream(unCompressedFilePath);
    const ungzip = zlib.createUnzip();
    compressedFile.pipe(ungzip).pipe(unCompressedFile);

    unCompressedFileExists = fs.existsSync(unCompressedFilePath);
    if (unCompressedFileExists) {
      logger.info(`Successfully extracted ${unCompressedFilePath}`);
    } else {
      logger.info(`Unable to gunzip ${compressedFilePath}, file exists: ${fs.existsSync(compressedFilePath)}`);
    }
  });

  await sleep(1000); // wait a second to allow files to get picked up by tests
});

beforeEach(() => {
  jest.resetModules();
  tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'entity-info-updater-'));
  process.env = {CONFIG_PATH: tempDir};
});

afterEach(() => {
  fs.rmdirSync(tempDir, {recursive: true});
});

describe('entityInfoUpdater sample test', () => {
  test('entityInfoUpdater using base case', async () => {
    fs.writeFileSync(path.join(tempDir, 'application.yml'), yaml.dump(custom));
    const entityInfoUpdater = require('../index');
    const responseCount = await entityInfoUpdater.runUpdater();
    expect(responseCount).toBeGreaterThan(0);
  });

  test('entityInfoUpdater using updated entity case', async () => {
    process.env.DB_ENTITY_CACHE_FILE = `dbEntityCacheUpdated.json`;
    fs.writeFileSync(path.join(tempDir, 'application.yml'), yaml.dump(custom));
    const entityInfoUpdater = require('../index');
    const responseCount = await entityInfoUpdater.runUpdater();
    expect(responseCount).toBe(0);
  });
});
