/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 *
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
  fs.rmdirSync(tempDir, { recursive: true });
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
    fs.writeFileSync(path.join(tempDir, 'config.yml'), yaml.safeDump(custom));
    process.env = {CONFIG_NAME: 'config', CONFIG_PATH: tempDir};
    return require('../config');
  };

  test('${CONFIG_PATH}/${CONFIG_NAME}.yml', () => {
    const config = loadConfigFromCustomObject(custom);

    expect(config.shard).toBe(custom.hedera.mirror.rest.shard);
    expect(config.maxLimit).toBe(custom.hedera.mirror.rest.maxLimit);
    expect(config.log).toBeUndefined();
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
    fs.writeFileSync(path.join(tempDir, 'application.yml'), yaml.safeDump(customConfig));
    process.env = {CONFIG_PATH: tempDir};
    return require('../config');
  };

  const getStreamsConfigByNetworkAndOverride = (network, override) => {
    const streamsConfig = {
      network,
      cloudProvider: 'S3',
      region: 'us-east-1',
      accessKey: null,
      endpointOverride: null,
      gcpProjectId: null,
      secretKey: null,
    };

    switch (network) {
      case 'DEMO':
        streamsConfig.bucketName = 'hedera-demo-streams';
        break;
      case 'MAINNET':
        streamsConfig.bucketName = 'hedera-stable-mainnet-streams';
        break;
      case 'TESTNET':
        streamsConfig.bucketName = 'hedera-stable-testnet-streams';
        break;
      case 'OTHER':
        streamsConfig.network = network;
        break;
      default:
        break;
    }

    return Object.assign(streamsConfig, override);
  };

  test('by default stateproof should be disabled', () => {
    const config = loadConfigWithCustomStateproofConfig({});
    expect(config.stateproof.enabled).toBeFalsy();
  });

  test('when stateproof enabled with no streams section the default should be populated', () => {
    const customStateproofConfig = {enabled: true};
    const config = loadConfigWithCustomStateproofConfig(customStateproofConfig);

    expect(config.stateproof.enabled).toBeTruthy();
    expect(config.stateproof.streams).toEqual(getStreamsConfigByNetworkAndOverride('DEMO'));
  });

  ['DEMO', 'MAINNET', 'TESTNET'].forEach((network) => {
    test(`when stateproof enabled with just streams network set to ${network} other fields should get default`, () => {
      const config = loadConfigWithCustomStateproofConfig({enabled: true, streams: {network}});

      expect(config.stateproof.enabled).toBeTruthy();
      expect(config.stateproof.streams).toEqual(getStreamsConfigByNetworkAndOverride(network));
    });
  });

  test('when override all allowed fields', () => {
    const network = 'DEMO';
    const customStateproofConfig = {
      enabled: true,
      streams: {
        network,
      },
    };
    const override = {
      cloudProvider: 'GCP',
      endpointOverride: 'https://alternative.object.storage.service',
      region: 'us-east-west-3',
      gpProjectId: 'sampleProject',
      accessKey: 'FJHGRY',
      secretKey: 'IRPLKGJUIEOR=FweGR',
      bucketName: 'override-alternative-streams',
    };
    Object.assign(customStateproofConfig.streams, override);

    const config = loadConfigWithCustomStateproofConfig(customStateproofConfig);

    expect(config.stateproof.enabled).toBeTruthy();
    expect(config.stateproof.streams).toEqual(getStreamsConfigByNetworkAndOverride(network, override));
  });

  test('when network is OTHER and bucketName is set', () => {
    const network = 'OTHER';
    const customStateproofConfig = {
      enabled: true,
      streams: {
        network,
        bucketName: 'other-streams',
      },
    };
    const config = loadConfigWithCustomStateproofConfig(customStateproofConfig);

    expect(config.stateproof.enabled).toBeTruthy();
    expect(config.stateproof.streams).toEqual(
      getStreamsConfigByNetworkAndOverride(network, {
        bucketName: 'other-streams',
      })
    );
  });

  test('with unsupported network', () => {
    const customStateproofConfig = {
      enabled: true,
      streams: {
        network: 'unknown',
      },
    };

    expect(() => {
      loadConfigWithCustomStateproofConfig(customStateproofConfig);
    }).toThrow();
  });

  test('with invalid cloudProvider', () => {
    const customStateproofConfig = {
      enabled: true,
      streams: {
        network: 'OTHER',
        cloudProvider: 'invalid',
      },
    };

    expect(() => {
      loadConfigWithCustomStateproofConfig(customStateproofConfig);
    }).toThrow();
  });

  test('with OTHER network but no bucketName', () => {
    const customStateproofConfig = {
      enabled: true,
      streams: {
        network: 'OTHER',
        bucketName: null,
      },
    };

    expect(() => {
      loadConfigWithCustomStateproofConfig(customStateproofConfig);
    }).toThrow();
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
