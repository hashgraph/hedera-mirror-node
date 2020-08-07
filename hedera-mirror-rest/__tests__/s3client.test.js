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

const log4js = require('log4js');
const AWSMock = require('aws-sdk-mock');
const querystring = require('querystring');
const { createS3Client } = require('../s3client');
const config = require('../config');

// create a minimal global logger for createS3Client to log errors.
global.logger = log4js.getLogger();

const defaultValidStreamsConfig = {
  cloudProvider: 'S3',
  region: 'us-east-3',
  accessKey: 'testAccessKey',
  secretKey: 'testSecretKey',
  bucketName: 'testBucket',
  record: {
    prefix: 'recordstreams/record',
  },
};

beforeEach(() => {
  config.stateproof = {
    streams: { ...defaultValidStreamsConfig },
  };
});

const overrideStreamsConfig = (override) => {
  config.stateproof.streams = Object.assign(config.stateproof.streams, override);
};

describe('createS3Client with invalid config', () => {
  test('no streams config', () => {
    config.stateproof.streams = null;
    expect(() => {
      createS3Client();
    }).toThrow();
  });

  test('invalid cloudProvider', () => {
    overrideStreamsConfig({ cloudProvider: 'badprovider' });
    expect(() => {
      createS3Client();
    }).toThrow();
  });
});

describe('createS3Client with valid config', () => {
  const verifyForSuccess = async (streamsConfig, s3Client) => {
    expect(s3Client).toBeTruthy();

    const s3Config = s3Client.getConfig();
    if (streamsConfig.endpointOverride) {
      expect(s3Config.endpoint).toEqual(streamsConfig.endpointOverride);
    } else if (streamsConfig.cloudProvider === 'S3') {
      expect(s3Config.endpoint).toEqual('https://s3.amazonaws.com');
    } else {
      expect(s3Config.endpoint).toEqual('https://storage.googleapis.com');
    }

    if (streamsConfig.region) {
      expect(s3Config.region).toEqual(streamsConfig.region);
    } else {
      expect(s3Config.region).toEqual('us-east-1');
    }

    if (!streamsConfig.accessKey || !streamsConfig.secretKey) {
      expect(s3Client.getHasCredentials()).toBeFalsy();
    } else {
      expect(s3Client.getHasCredentials()).toBeTruthy();
      expect(s3Config.accessKeyId).toEqual(streamsConfig.accessKey);
      expect(s3Config.secretAccessKey).toEqual(streamsConfig.secretKey);
      expect(s3Config.credentials).toBeTruthy();
      expect(s3Config.credentials.accessKeyId).toEqual(streamsConfig.accessKey);
    }

    if (streamsConfig.cloudProvider === 'GCP' && streamsConfig.gcpProjectId) {
      const request = s3Client.getObject({
        Bucket: 'demo-bucket',
        Key: 'foobar/foobar.txt',
      });
      try {
        await request.promise();
      } catch (err) {
        //
      }
      expect(querystring.parse(request.httpRequest.search())).toEqual({ userProject: streamsConfig.gcpProjectId });
    }
  };

  const testSpecs = [
    {
      name: 'default valid config',
    },
    {
      name: 'valid config with cloudProvider S3',
      override: { cloudProvider: 'S3' },
    },
    {
      name: 'valid config with cloudProvider gcp',
      override: { cloudProvider: 'GCP' },
    },
    {
      name: 'valid config with cloudProvider gcp',
      override: { cloudProvider: 'GCP' },
    },
    {
      name: 'valid config with cloudProvider gcp and gcpProjectId',
      override: {
        cloudProvider: 'GCP',
        gcpProjectId: 'demoGcpProject',
      },
    },
    {
      name: 'valid config with region "us-west-1"',
      override: { region: 'us-west-1' },
    },
    {
      name: 'valid config with endpointOverride',
      override: { endpointOverride: 'us-south-north-1' },
    },
    {
      name: 'valid config with null region',
      override: { region: null },
    },
    {
      name: 'valid config with empty region',
      override: { region: '' },
    },
    {
      name: 'valid config with null accessKey',
      override: { accessKey: null },
    },
    {
      name: 'valid config with empty accessKey',
      override: { accessKey: '' },
    },
    {
      name: 'valid config with null secretKey',
      override: { secretKey: null },
    },
    {
      name: 'valid config with empty secretKey',
      override: { secretKey: '' },
    },
  ];

  testSpecs.forEach((spec) => {
    test(spec.name, async () => {
      overrideStreamsConfig(spec.override);
      const s3Client = createS3Client();
      await verifyForSuccess(config.stateproof.streams, s3Client);
    });
  });
});

describe('S3Client.getObject', () => {
  const params = {
    Bucket: 'sample-bucket',
    Key: 'sample-key',
  };
  const getObjectMessage = 'getObject is called when credentials are provided';
  const makeUnauthenticatedRequestMessage = 'makeUnauthenticatedRequest is called when no credentials are provided';

  beforeEach(() => {
    AWSMock.mock('S3', 'getObject', (_params, callback) => {
      callback(null, getObjectMessage);
    });
    AWSMock.mock('S3', 'makeUnauthenticatedRequest', (method, _params, callback) => {
      callback(null, makeUnauthenticatedRequestMessage);
    });
  });

  afterEach(() => {
    AWSMock.restore();
  });

  test('with credentials provided', async () => {
    const s3Client = createS3Client();
    await new Promise((resolve) => {
      s3Client.getObject(params, (err, data) => {
        expect(data).toEqual(getObjectMessage);
        resolve();
      });
    });
  });

  test('without credentials', async () => {
    overrideStreamsConfig({ secretKey: '' });
    const s3Client = createS3Client();
    await new Promise((resolve) => {
      s3Client.getObject(params, (err, data) => {
        expect(data).toEqual(makeUnauthenticatedRequestMessage);
        resolve();
      });
    });
  });
});
