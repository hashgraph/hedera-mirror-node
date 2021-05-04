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
const os = require('os');
const path = require('path');
const yaml = require('js-yaml');

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
          useCache: true,
        },
      },
    },
  },
};

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
