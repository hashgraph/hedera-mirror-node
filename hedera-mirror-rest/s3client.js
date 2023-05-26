/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
 */

import {GetObjectCommand, S3} from '@aws-sdk/client-s3';
import {NodeHttpHandler} from '@aws-sdk/node-http-handler';

import config from './config';
import {cloudProviders, defaultCloudProviderEndpoints} from './constants';

class S3Client {
  constructor(s3, hasCredentials, gcpProjectId) {
    this.s3 = s3;
    this.hasCredentials = hasCredentials;
    this.gcpProjectId = gcpProjectId;
    this.httpRequest = null;

    this.s3.middlewareStack.add(
      (next, context) => (args) => {
        if (gcpProjectId) {
          args.request.query.userProject = this.gcpProjectId;
        }
        this.httpRequest = args.request;
        return next(args);
      },
      {
        step: 'build',
      }
    );
  }

  getHttpRequest() {
    return this.httpRequest;
  }

  getObject(params, abortSignal) {
    return this.s3.send(new GetObjectCommand(params), {abortSignal});
  }

  getConfig() {
    return this.s3.config;
  }

  getHasCredentials() {
    return this.hasCredentials;
  }
}

const buildS3ConfigFromStreamsConfig = () => {
  const {accessKey, cloudProvider, endpointOverride, gcpProjectId, httpOptions, maxRetries, secretKey, region} =
    config.stateproof.streams;
  const hasEndpointOverride = !!endpointOverride;
  const isGCP = cloudProvider === cloudProviders.GCP;

  const endpoint = hasEndpointOverride ? endpointOverride : defaultCloudProviderEndpoints[cloudProvider];
  const forcePathStyle = hasEndpointOverride || isGCP;
  const requestHandler = new NodeHttpHandler({
    connectionTimeout: httpOptions.connectTimeout,
    requestTimeout: httpOptions.timeout,
  });

  const s3Config = {
    credentials: {
      accessKeyId: '',
      secretAccessKey: '',
    },
    endpoint,
    forcePathStyle,
    maxAttempts: maxRetries + 1,
    region,
    requestHandler,
  };

  if (!!accessKey && !!secretKey) {
    logger.info('Building s3Config with provided access/secret key');
    s3Config.credentials.accessKeyId = accessKey;
    s3Config.credentials.secretAccessKey = secretKey;
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
  return new S3Client(new S3(s3Config), !!s3Config.credentials.accessKeyId, gcpProjectId);
};

export default {
  createS3Client,
};
