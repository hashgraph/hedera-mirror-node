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
const _ = require('lodash');
const {cloudProviders, defaultBucketNames, networks} = require('../constants');

let tempDir;
const custom = {
  hedera: {
    mirror: {
      rest: {
        maxLimit: 10,
        response: {
          compression: false,
        },
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

const assertCustomConfig = (config, custom) => {
  // fields custom doesn't override
  expect(config.includeHostInLink).toBeFalsy();
  expect(config.log.level).toBe('debug');

  // fields overridden by custom
  expect(config.shard).toBe(custom.hedera.mirror.rest.shard);
  expect(config.maxLimit).toBe(custom.hedera.mirror.rest.maxLimit);
  expect(config.response.compression).toBe(custom.hedera.mirror.rest.response.compression);
};

describe('Load YAML configuration:', () => {
  test('./config/application.yml', () => {
    const config = require('../config');
    expect(config.shard).toBe(0);
    expect(config.includeHostInLink).toBeFalsy();
    expect(config.log.level).toBe('debug');
  });

  test('./application.yml', () => {
    fs.writeFileSync(path.join('.', 'application.yml'), yaml.dump(custom));
    const config = require('../config');
    assertCustomConfig(config, custom);
  });

  test('CONFIG_PATH/application.yml', () => {
    fs.writeFileSync(path.join(tempDir, 'application.yml'), yaml.dump(custom));
    const config = require('../config');
    assertCustomConfig(config, custom);
  });

  test('CONFIG_PATH/application.yaml', () => {
    fs.writeFileSync(path.join(tempDir, 'application.yaml'), yaml.dump(custom));
    const config = require('../config');
    assertCustomConfig(config, custom);
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
  test('Unexpected prefix', () => {
    process.env = {HEDERA_MIRROR_NODE_REST_PORT: '80'};
    const config = require('../config');
    expect(config.port).not.toBe(80);
  });
  test('Extra path', () => {
    process.env = {HEDERA_MIRROR_REST_SERVICE_PORT: '80'};
    const config = require('../config');
    expect(config.port).not.toBe(80);
  });
});

describe('Custom CONFIG_NAME:', () => {
  const loadConfigFromCustomObject = (custom) => {
    fs.writeFileSync(path.join(tempDir, 'config.yml'), yaml.dump(custom));
    process.env = {CONFIG_NAME: 'config', CONFIG_PATH: tempDir};
    return require('../config');
  };

  test('CONFIG_PATH/CONFIG_NAME.yml', () => {
    const config = loadConfigFromCustomObject(custom);
    assertCustomConfig(config, custom);
  });
});

describe('Override stateproof config', () => {
  const loadConfigWithCustomStateproofConfig = (customStateproofConfig) => {
    const customConfig = {
      hedera: {
        mirror: {
          rest: {
            stateproof: customStateproofConfig,
          },
        },
      },
    };
    fs.writeFileSync(path.join(tempDir, 'application.yml'), yaml.dump(customConfig));
    process.env = {CONFIG_PATH: tempDir};
    return require('../config');
  };

  const getExpectedStreamsConfig = (override) => {
    // the default without network
    const streamsConfig = {
      network: networks.DEMO,
      cloudProvider: 'S3',
      region: 'us-east-1',
      accessKey: null,
      endpointOverride: null,
      gcpProjectId: null,
      secretKey: null,
    };
    Object.assign(streamsConfig, override);
    if (!streamsConfig.bucketName) {
      streamsConfig.bucketName = defaultBucketNames[streamsConfig.network];
    }
    return streamsConfig;
  };

  const testSpecs = [
    {
      name: 'by default stateproof should be disabled',
      enabled: false,
    },
    {
      name: 'when stateproof enabled with no streams section the default should be populated',
      enabled: true,
      expectThrow: false,
    },
    ..._.values(_.omit(networks, networks.OTHER)).map((network) => {
      return {
        name: `when stateproof enabled with just streams network set to ${network} other fields should get default`,
        enabled: true,
        override: {network},
        expectThrow: false,
      };
    }),
    {
      name: 'when override all allowed fields',
      enabled: true,
      override: {
        network: networks.DEMO,
        cloudProvider: cloudProviders.GCP,
        endpointOverride: 'https://alternative.object.storage.service',
        region: 'us-east-west-3',
        gpProjectId: 'sampleProject',
        accessKey: 'FJHGRY',
        secretKey: 'IRPLKGJUIEOR=FweGR',
        bucketName: 'override-alternative-streams',
      },
      expectThrow: false,
    },
    {
      name: 'when network is OTHER and bucketName is set',
      enabled: true,
      override: {network: networks.OTHER, bucketName: 'other-streams'},
      expectThrow: false,
    },
    {
      name: 'with unsupported network',
      enabled: true,
      override: {network: 'unknown'},
      expectThrow: true,
    },
    {
      name: 'with invalid cloudProvider',
      enabled: true,
      override: {network: networks.OTHER, cloudProvider: 'invalid'},
      expectThrow: true,
    },
    {
      name: 'with OTHER network but bucketName set to null',
      enabled: true,
      override: {network: networks.OTHER, bucketName: null},
      expectThrow: true,
    },
    {
      name: 'with OTHER network but bucketName set to empty',
      enabled: true,
      override: {network: networks.OTHER, bucketName: ''},
      expectThrow: true,
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(testSpec.name, () => {
      const customConfig = {enabled: testSpec.enabled};
      customConfig.streams = testSpec.override ? testSpec.override : {};

      if (!testSpec.expectThrow) {
        const config = loadConfigWithCustomStateproofConfig(customConfig);
        if (testSpec.enabled) {
          expect(config.stateproof.enabled).toBeTruthy();
          expect(config.stateproof.streams).toEqual(getExpectedStreamsConfig(testSpec.override));
        } else {
          expect(config.stateproof.enabled).toBeFalsy();
        }
      } else {
        expect(() => {
          loadConfigWithCustomStateproofConfig(customConfig);
        }).toThrow();
      }
    });
  });
});

describe('Override db pool config', () => {
  const loadConfigWithCustomDbPoolConfig = (customDbPoolConfig) => {
    if (customDbPoolConfig) {
      const customConfig = {
        hedera: {
          mirror: {
            rest: {
              db: {
                pool: customDbPoolConfig,
              },
            },
          },
        },
      };
      fs.writeFileSync(path.join(tempDir, 'application.yml'), yaml.dump(customConfig));
      process.env = {CONFIG_PATH: tempDir};
    }

    return require('../config');
  };

  const testSpecs = [
    {
      name: 'the default values should be valid',
      expectThrow: false,
    },
    {
      name: 'override with valid integer values',
      override: {
        connectionTimeout: 200,
        maxConnections: 5,
        statementTimeout: 100,
      },
      expected: {
        connectionTimeout: 200,
        maxConnections: 5,
        statementTimeout: 100,
      },
    },
    {
      name: 'override with valid string values',
      override: {
        connectionTimeout: '200',
        maxConnections: '5',
        statementTimeout: '100',
      },
      expected: {
        connectionTimeout: 200,
        maxConnections: 5,
        statementTimeout: 100,
      },
    },
    ..._.flattenDeep(
      [-1, 0, true, false, '', 'NaN'].map((value) => {
        return ['connectionTimeout', 'maxConnections', 'statementTimeout'].map((configKey) => {
          return {
            name: `override ${configKey} with invalid value ${JSON.stringify(value)}`,
            override: {
              [configKey]: value,
            },
            expectThrow: true,
          };
        });
      })
    ),
  ];

  testSpecs.forEach((testSpec) => {
    const {name, override, expected, expectThrow} = testSpec;
    test(name, () => {
      if (!expectThrow) {
        const config = loadConfigWithCustomDbPoolConfig(override);
        if (expected) {
          expect(config.db.pool).toEqual(expected);
        }
      } else {
        expect(() => {
          loadConfigWithCustomDbPoolConfig(override);
        }).toThrow();
      }
    });
  });
});

function unlink(file) {
  if (fs.existsSync(file)) {
    fs.unlinkSync(file);
  }
}

function cleanup() {
  unlink(path.join('.', 'application.yml'));
  unlink(path.join('.', 'application.yaml'));
}
