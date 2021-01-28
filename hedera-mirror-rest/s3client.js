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

const AWS = require('aws-sdk');
const querystring = require('querystring');
const config = require('./config');
const {cloudProviders, defaultCloudProviderEndpoints} = require('./constants');

class S3Client {
  constructor(s3, hasCredentials, gcpProjectId) {
    this.s3 = s3;
    this.hasCredentials = hasCredentials;
    this.gcpProjectId = gcpProjectId;
  }

  getObject(params, callback) {
    let request;
    if (this.hasCredentials) {
      request = this.s3.getObject(params, callback);
    } else {
      request = this.s3.makeUnauthenticatedRequest('getObject', params, callback);
    }

    if (this.gcpProjectId) {
      const userProject = this.gcpProjectId;
      request.on('build', () => {
        const {httpRequest} = request;
        const query = {
          ...querystring.parse(httpRequest.search()),
          userProject,
        };
        httpRequest.path = `${httpRequest.pathname()}?${querystring.stringify(query)}`;
      });
    }

    return request;
  }

  getConfig() {
    return this.s3.config;
  }

  getHasCredentials() {
    return this.hasCredentials;
  }
}

const buildS3ConfigFromStreamsConfig = () => {
  const {cloudProvider, endpointOverride, gcpProjectId, accessKey, secretKey, region} = config.stateproof.streams;
  const hasEndpointOverride = !!endpointOverride;
  const isGCP = cloudProvider === cloudProviders.GCP;

  const endpoint = hasEndpointOverride ? endpointOverride : defaultCloudProviderEndpoints[cloudProvider];
  const s3ForcePathStyle = hasEndpointOverride || isGCP;

  const s3Config = {
    endpoint,
    region,
    s3ForcePathStyle,
  };

  if (!!accessKey && !!secretKey) {
    logger.info('Building s3Config with provided access/secret key');
    s3Config.accessKeyId = accessKey;
    s3Config.secretAccessKey = secretKey;
  } else {
    logger.info('Building s3Config with no credentials');
  }

  return {
    s3Config,
    gcpProjectId: isGCP ? gcpProjectId : null,
  };
};

/**
 * Create a S3 client with configuration from config object.
 * @returns {S3Client}
 */
const createS3Client = () => {
  const {s3Config, gcpProjectId} = buildS3ConfigFromStreamsConfig();
  return new S3Client(new AWS.S3(s3Config), !!s3Config.accessKeyId, gcpProjectId);
};

module.exports = {
  createS3Client,
};
