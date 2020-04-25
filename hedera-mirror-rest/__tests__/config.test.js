/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
      rest: {
        maxLimit: 10,
        shard: 1,
      },
    },
  },
};

beforeEach(() => {
  jest.resetModules();
  tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'hedera-mirror-rest-'));
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
    expect(config.includeHostInLink).toBeFalsy();
    expect(config.log.level).toBe('debug');
  });

  test('./application.yml', () => {
    fs.writeFileSync(path.join('.', 'application.yml'), yaml.safeDump(custom));
    const config = require('../config');

    expect(config.shard).toBe(custom.hedera.mirror.rest.shard);
    expect(config.maxLimit).toBe(custom.hedera.mirror.rest.maxLimit);
    expect(config.includeHostInLink).toBeFalsy();
    expect(config.log.level).toBe('debug');
  });

  test('${CONFIG_PATH}/application.yml', () => {
    fs.writeFileSync(path.join(tempDir, 'application.yml'), yaml.safeDump(custom));
    const config = require('../config');

    expect(config.shard).toBe(custom.hedera.mirror.rest.shard);
    expect(config.maxLimit).toBe(custom.hedera.mirror.rest.maxLimit);
    expect(config.includeHostInLink).toBeFalsy();
    expect(config.log.level).toBe('debug');
  });

  test('${CONFIG_PATH}/application.yaml', () => {
    fs.writeFileSync(path.join(tempDir, 'application.yaml'), yaml.safeDump(custom));
    const config = require('../config');

    expect(config.shard).toBe(custom.hedera.mirror.rest.shard);
    expect(config.maxLimit).toBe(custom.hedera.mirror.rest.maxLimit);
    expect(config.includeHostInLink).toBeFalsy();
    expect(config.log.level).toBe('debug');
  });
});

describe('Load environment configuration:', () => {
  test('Number', () => {
    process.env = {HEDERA_MIRROR_REST_SHARD: '2', HEDERA_MIRROR_REST_PORT: '5552'};
    const config = require('../config');
    expect(config.shard).toBe(2);
    expect(config.port).toBe(5552);
  });

  test('String', () => {
    process.env = {HEDERA_MIRROR_REST_LOG_LEVEL: 'info'};
    const config = require('../config');
    expect(config.log.level).toBe('info');
  });

  test('Boolean', () => {
    process.env = {HEDERA_MIRROR_REST_INCLUDEHOSTINLINK: 'true'};
    const config = require('../config');
    expect(config.includeHostInLink).toBe(true);
  });

  test('Camel case', () => {
    process.env = {HEDERA_MIRROR_REST_MAXLIMIT: '10'};
    const config = require('../config');
    expect(config.maxLimit).toBe(10);
  });

  test('Unknown property', () => {
    process.env = {HEDERA_MIRROR_REST_FOO: '3'};
    const config = require('../config');
    expect(config.foo).toBeUndefined();
  });

  test('Invalid property path', () => {
    process.env = {HEDERA_MIRROR_REST_SHARD_FOO: '3'};
    const config = require('../config');
    expect(config.shard).toBe(0);
  });
});

describe('Custom CONFIG_NAME:', () => {
  test('${CONFIG_PATH}/${CONFIG_NAME}.yml', () => {
    fs.writeFileSync(path.join(tempDir, 'config.yml'), yaml.safeDump(custom));
    process.env = {CONFIG_NAME: 'config', CONFIG_PATH: tempDir};
    const config = require('../config');

    expect(config.shard).toBe(custom.hedera.mirror.rest.shard);
    expect(config.maxLimit).toBe(custom.hedera.mirror.rest.maxLimit);
    expect(config.log).toBeUndefined();
  });
});

function cleanup() {
  unlink(path.join('.', 'application.yml'));
  unlink(path.join('.', 'application.yaml'));
}

function unlink(file) {
  if (fs.existsSync(file)) {
    fs.unlinkSync(file);
  }
}
