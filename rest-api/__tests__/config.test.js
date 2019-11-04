/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
      api: {
        maxLimit: 10
      },
      shard: 1
    }
  }
};

beforeEach(() => {
  jest.resetModules();
  tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'mirror-rest-api-'));
  process.env = {CONFIG_PATH: tempDir};
  cleanup();
});

afterEach(() => {
  fs.rmdirSync(tempDir, {recursive: true});
  cleanup();
});

describe('Load YAML configuration:', () => {
  test('./config/application.yml', () => {
    const config = require('../config');
    expect(config.shard).toBe(0);
    expect(config.api.includeHostInLink).toBeFalsy();
    expect(config.api.log.level).toBe('debug');
  });

  test('./application.yml', () => {
    fs.writeFileSync(path.join('.', 'application.yml'), yaml.safeDump(custom));
    const config = require('../config');

    expect(config.shard).toBe(custom.hedera.mirror.shard);
    expect(config.api.maxLimit).toBe(custom.hedera.mirror.api.maxLimit);
    expect(config.api.includeHostInLink).toBeFalsy();
    expect(config.api.log.level).toBe('debug');
  });

  test('${CONFIG_PATH}/application.yml', () => {
    fs.writeFileSync(path.join(tempDir, 'application.yml'), yaml.safeDump(custom));
    const config = require('../config');

    expect(config.shard).toBe(custom.hedera.mirror.shard);
    expect(config.api.maxLimit).toBe(custom.hedera.mirror.api.maxLimit);
    expect(config.api.includeHostInLink).toBeFalsy();
    expect(config.api.log.level).toBe('debug');
  });

  test('${CONFIG_PATH}/application.yaml', () => {
    fs.writeFileSync(path.join(tempDir, 'application.yaml'), yaml.safeDump(custom));
    const config = require('../config');

    expect(config.shard).toBe(custom.hedera.mirror.shard);
    expect(config.api.maxLimit).toBe(custom.hedera.mirror.api.maxLimit);
    expect(config.api.includeHostInLink).toBeFalsy();
    expect(config.api.log.level).toBe('debug');
  });
});

describe('Load environment configuration:', () => {
  test('Number', () => {
    process.env = {HEDERA_MIRROR_SHARD: '2'};
    const config = require('../config');
    expect(config.shard).toBe(2);
  });

  test('String', () => {
    process.env = {HEDERA_MIRROR_API_LOG_LEVEL: 'info'};
    const config = require('../config');
    expect(config.api.log.level).toBe('info');
  });

  test('Boolean', () => {
    process.env = {HEDERA_MIRROR_API_INCLUDEHOSTINLINK: 'true'};
    const config = require('../config');
    expect(config.api.includeHostInLink).toBe(true);
  });

  test('Camel case', () => {
    process.env = {HEDERA_MIRROR_API_MAXLIMIT: '10'};
    const config = require('../config');
    expect(config.api.maxLimit).toBe(10);
  });

  test('Unknown property', () => {
    process.env = {HEDERA_MIRROR_FOO: '3'};
    const config = require('../config');
    expect(config.foo).toBeUndefined();
  });

  test('Invalid property path', () => {
    process.env = {HEDERA_MIRROR_SHARD_FOO: '3'};
    const config = require('../config');
    expect(config.shard).toBe(0);
  });
});

describe('Custom CONFIG_NAME:', () => {
  test('${CONFIG_PATH}/${CONFIG_NAME}.yml', () => {
    fs.writeFileSync(path.join(tempDir, 'config.yml'), yaml.safeDump(custom));
    process.env = {CONFIG_NAME: 'config', CONFIG_PATH: tempDir};
    const config = require('../config');

    expect(config.shard).toBe(custom.hedera.mirror.shard);
    expect(config.api.maxLimit).toBe(custom.hedera.mirror.api.maxLimit);
    expect(config.api.log).toBeUndefined();
  });
});

function cleanup() {
  fs.unlink(path.join('.', 'application.yml'), e => {});
  fs.unlink(path.join('.', 'application.yaml'), e => {});
  fs.unlink(path.join('.', 'application.properties'), e => {});
}
