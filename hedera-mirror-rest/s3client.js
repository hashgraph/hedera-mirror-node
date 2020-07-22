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

const AWS = require('aws-sdk');
const config = require('./config');
const {InvalidConfigError} = require('./errors/invalidConfigError');

class S3Client {
  constructor(s3, hasCredentials) {
    this.s3 = s3;
    this.hasCredentials = hasCredentials;
  }

  getObject = (params, callback) => {
    if (this.hasCredentials) {
      return this.s3.getObject(params, callback);
    } else {
      return this.s3.makeUnauthenticatedRequest('getObject', params, callback);
    }
  }

  getConfig = () => {
    return this.s3.config;
  }

  getHasCredentials = () => {
    return this.hasCredentials;
  }

}

/**
 * Create a S3 client with configuration from config object. Throws InvalidConfigError if failed.
 * @returns {S3Client}
 */
const createS3Client = () => {
  try {
    const streamsConfig = config.stateproof.streams;
    const cloudProvider = streamsConfig.cloudProvider.toLowerCase();
    let endpoint = undefined;
    if (cloudProvider === 's3') {
      endpoint = 'https://s3.amazonaws.com';
    } else if (cloudProvider === 'gcp') {
      endpoint = 'https://storage.googleapis.com';
    } else {
      throw new InvalidConfigError(`Invalid cloudProvider ${cloudProvider}`);
    }

    const s3Config = {
      endpoint: endpoint,
      region: streamsConfig.region ? streamsConfig.region : 'us-east-1'
    };

    if (!!streamsConfig.accessKey && !!streamsConfig.secretKey) {
      logger.info('Configuring s3Client with provided access/secret key');
      s3Config.accessKeyId = streamsConfig.accessKey;
      s3Config.secretAccessKey = streamsConfig.secretKey;
    } else {
      logger.info('Configuring s3Client with anonymous credentials');
    }

    return new S3Client(new AWS.S3(s3Config), !!s3Config.accessKeyId);
    // return new S3Client(new S3(s3Config), !!s3Config.accessKeyId);
  } catch (err) {
    logger.error(`Unable to create S3 client: ${err.message}`);

    if (err instanceof InvalidConfigError) {
      throw err;
    }
    throw new InvalidConfigError(`Invalid config, cannot create s3 client: ${err.message}`);
  }
};

module.exports = {
  createS3Client
}
