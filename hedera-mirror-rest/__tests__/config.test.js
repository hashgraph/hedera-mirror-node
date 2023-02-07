/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import fs from 'fs';
import {jest} from '@jest/globals';
import os from 'os';
import path from 'path';
import yaml from 'js-yaml';
import _ from 'lodash';
import {cloudProviders, defaultBucketNames, networks} from '../constants';

let tempDir;
const custom = {
  hedera: {
    mirror: {
      rest: {
        response: {
          compression: false,
          limit: {
            max: 30,
          },
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
  fs.rmSync(tempDir, {recursive: true});
  cleanup();
});

const assertCustomConfig = (actual, customConfig) => {
  // fields custom doesn't override
  expect(actual.response.includeHostInLink).toBe(false);
  expect(actual.log.level).toBe('info');

  // fields overridden by custom
  expect(actual.shard).toBe(customConfig.hedera.mirror.rest.shard);
  expect(actual.response.limit.max).toBe(customConfig.hedera.mirror.rest.response.limit.max);
  expect(actual.response.compression).toBe(customConfig.hedera.mirror.rest.response.compression);
};

const loadConfig = async () => (await import('../config.js')).default;

const loadCustomConfig = async (customConfig, filename = 'application.yml') => {
  fs.writeFileSync(path.join(tempDir, filename), yaml.dump(customConfig));
  return loadConfig();
};

describe('Load YAML configuration:', () => {
  test('./config/application.yml', async () => {
    const config = await loadConfig();
    expect(config.shard).toBe(0);
    expect(config.response.includeHostInLink).toBe(false);
    expect(config.log.level).toBe('info');
  });

  test('./application.yml', async () => {
    fs.writeFileSync(path.join('.', 'application.yml'), yaml.dump(custom));
    const config = await loadConfig();
    assertCustomConfig(config, custom);
  });

  test('CONFIG_PATH/application.yml', async () => {
    const config = await loadCustomConfig(custom);
    assertCustomConfig(config, custom);
  });

  test('CONFIG_PATH/application.yaml', async () => {
    const config = await loadCustomConfig(custom, 'application.yaml');
    assertCustomConfig(config, custom);
  });

  test('CONFIG_PATH/CONFIG_NAME.yml', async () => {
    process.env = {CONFIG_NAME: 'config', CONFIG_PATH: tempDir};
    const config = await loadCustomConfig(custom, 'config.yml');
    assertCustomConfig(config, custom);
  });
});

describe('Load environment configuration:', () => {
  test('Number', async () => {
    process.env = {HEDERA_MIRROR_REST_SHARD: '2', HEDERA_MIRROR_REST_PORT: '5552'};
    const config = await loadConfig();
    expect(config.shard).toBe(2);
    expect(config.port).toBe(5552);
  });

  test('Secret', async () => {
    const secret = 'secret';
    process.env = {HEDERA_MIRROR_REST_DB_PASSWORD: secret, HEDERA_MIRROR_REST_DB_TLS_KEY: secret};
    const config = await loadConfig();
    expect(config.db.password).toBe(secret);
    expect(config.db.tls.key).toBe(secret);
  });

  test('String', async () => {
    process.env = {HEDERA_MIRROR_REST_LOG_LEVEL: 'warn'};
    const config = await loadConfig();
    expect(config.log.level).toBe('warn');
  });

  test('Boolean', async () => {
    process.env = {HEDERA_MIRROR_REST_RESPONSE_INCLUDEHOSTINLINK: 'true'};
    const config = await loadConfig();
    expect(config.response.includeHostInLink).toBe(true);
  });

  test('Camel case', async () => {
    process.env = {HEDERA_MIRROR_REST_QUERY_MAXREPEATEDQUERYPARAMETERS: '50'};
    const config = await loadConfig();
    expect(config.query.maxRepeatedQueryParameters).toBe(50);
  });

  test('Unknown property', async () => {
    process.env = {HEDERA_MIRROR_REST_FOO: '3'};
    const config = await loadConfig();
    expect(config.foo).toBeUndefined();
  });

  test('Invalid property path', async () => {
    process.env = {HEDERA_MIRROR_REST_SHARD_FOO: '3'};
    const config = await loadConfig();
    expect(config.shard).toBe(0);
  });

  test('Unexpected prefix', async () => {
    process.env = {HEDERA_MIRROR_NODE_REST_PORT: '80'};
    const config = await loadConfig();
    expect(config.port).not.toBe(80);
  });

  test('Extra path', async () => {
    process.env = {HEDERA_MIRROR_REST_SERVICE_PORT: '80'};
    const config = await loadConfig();
    expect(config.port).not.toBe(80);
  });

  test('Max Timestamp Range 3d', async () => {
    process.env = {HEDERA_MIRROR_REST_QUERY_MAXTIMESTAMPRANGE: '3d'};
    const config = await loadConfig();
    expect(config.query.maxTimestampRangeNs).toBe(259200000000000n);
  });

  test('Max Timestamp Range 120d - larger than js MAX_SAFE_INTEGER', async () => {
    process.env = {HEDERA_MIRROR_REST_QUERY_MAXTIMESTAMPRANGE: '120d'};
    const config = await loadConfig();
    expect(config.query.maxTimestampRangeNs).toBe(10368000000000000n);
  });

  test('Max Timestamp Range invalid', async () => {
    process.env = {HEDERA_MIRROR_REST_QUERY_MAXTIMESTAMPRANGE: '3x'};
    await expect(loadConfig()).rejects.toThrowErrorMatchingSnapshot();
  });

  test('Max Timestamp Range null', async () => {
    process.env = {HEDERA_MIRROR_REST_QUERY_MAXTIMESTAMPRANGE: null};
    await expect(loadConfig()).rejects.toThrowErrorMatchingSnapshot();
  });
});

describe('Override query config', () => {
  const customConfig = (queryConfig) => ({
    hedera: {
      mirror: {
        rest: {
          query: queryConfig,
        },
      },
    },
  });

  test('success', async () => {
    const queryConfig = {
      maxRepeatedQueryParameters: 2,
      maxTimestampRange: '1d',
      maxTransactionConsensusTimestampRange: '10m',
    };
    const expected = {
      maxRepeatedQueryParameters: 2,
      maxTimestampRange: '1d',
      maxTimestampRangeNs: 86400000000000n,
      maxTransactionConsensusTimestampRange: '10m',
      maxTransactionConsensusTimestampRangeNs: 600000000000n,
    };
    const config = await loadCustomConfig(customConfig(queryConfig));
    expect(config.query).toEqual(expected);
  });

  test.each`
    name                                               | queryConfig
    ${'invalid maxTimestampRange'}                     | ${{maxTimestampRange: '1q'}}
    ${'invalid maxTransactionConsensusTimestampRange'} | ${{maxTransactionConsensusTimestampRange: '1z'}}
  `('$name', async ({queryConfig}) => {
    await expect(loadCustomConfig(customConfig(queryConfig))).rejects.toThrowErrorMatchingSnapshot();
  });
});

describe('Override stateproof config', () => {
  const customConfig = (stateproofConfig) => ({
    hedera: {
      mirror: {
        rest: {
          stateproof: stateproofConfig,
        },
      },
    },
  });

  const getExpectedStreamsConfig = (override) => {
    // the default without network
    const streamsConfig = {
      network: networks.DEMO,
      cloudProvider: 'S3',
      region: 'us-east-1',
      accessKey: null,
      endpointOverride: null,
      gcpProjectId: null,
      httpOptions: {
        connectTimeout: 2000,
        timeout: 5000,
      },
      maxRetries: 3,
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
    test(testSpec.name, async () => {
      const stateproof = {enabled: testSpec.enabled};
      stateproof.streams = testSpec.override ? testSpec.override : {};

      if (!testSpec.expectThrow) {
        const config = await loadCustomConfig(customConfig(stateproof));
        if (testSpec.enabled) {
          expect(config.stateproof.enabled).toBeTruthy();
          expect(config.stateproof.streams).toEqual(getExpectedStreamsConfig(testSpec.override));
        } else {
          expect(config.stateproof.enabled).toBeFalsy();
        }
      } else {
        await expect(loadCustomConfig(customConfig(stateproof))).rejects.toThrow();
      }
    });
  });
});

describe('Override db pool config', () => {
  const customConfig = (poolConfig) => ({
    hedera: {
      mirror: {
        rest: {
          db: {
            pool: poolConfig,
          },
        },
      },
    },
  });

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
    test(name, async () => {
      if (!expectThrow) {
        const config = await loadCustomConfig(customConfig(override));
        if (expected) {
          expect(config.db.pool).toEqual(expected);
        }
      } else {
        await expect(loadCustomConfig(customConfig(override))).rejects.toThrow();
      }
    });
  });
});

describe('Override network currencyFormat config', () => {
  const customConfig = (networkCurrencyFormatConfig) => ({
    hedera: {
      mirror: {
        rest: {
          network: {
            currencyFormat: networkCurrencyFormatConfig,
          },
        },
      },
    },
  });

  const testSpecs = [
    {
      name: 'unspecified value should be valid',
      expectThrow: false,
    },
    {
      name: 'override with currencyFormat BOTH',
      override: 'BOTH',
      expectThrow: false,
    },
    {
      name: 'override with currencyFormat HBARS',
      override: 'HBARS',
      expectThrow: false,
    },
    {
      name: 'override with currencyFormat TINYBARS',
      override: 'TINYBARS',
      expectThrow: false,
    },
    {
      name: 'override with currencyFormat INVALID',
      override: 'INVALID',
      expectThrow: true,
    },
  ];

  testSpecs.forEach((testSpec) => {
    const {name, override, expectThrow} = testSpec;
    test(name, async () => {
      if (!expectThrow) {
        await loadCustomConfig(customConfig(override));
      } else {
        await expect(loadCustomConfig(customConfig(override))).rejects.toThrow();
      }
    });
  });
});

describe('getResponseLimit', () => {
  test('default', async () => {
    const func = (await import('../config')).getResponseLimit;
    expect(func()).toEqual({default: 25, max: 100, tokenBalance: {multipleAccounts: 50, singleAccount: 1000}});
  });
  test('custom response limit', async () => {
    const module = await import('../config');
    const customLimit = {default: 10, max: 200, tokenBalance: {multipleAccounts: 90, singleAccount: 200}};
    module.default.response.limit = customLimit;
    expect(module.getResponseLimit()).toEqual(customLimit);
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
