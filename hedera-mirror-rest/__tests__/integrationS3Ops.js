/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import axios from 'axios';
import {GenericContainer} from 'testcontainers';
import {isDockerInstalled} from './integrationUtils';

const localstackImageName = 'localstack/localstack';
const localstackImageTag = 'latest';
const defaultS3Port = 4566;

class S3Ops {
  async start() {
    const isInstalled = await isDockerInstalled();
    if (!isInstalled) {
      throw new Error('docker is not installed, cannot start localstack container for mock s3 service');
    }

    const image = `${localstackImageName}:${localstackImageTag}`;
    logger.info(`Starting localstack docker container with image ${image}`);
    const container = await new GenericContainer(image)
      .withEnv('SERVICES', 's3')
      .withEnv('EAGER_SERVICE_LOADING', 1)
      .withExposedPorts(defaultS3Port)
      .start();
    logger.info('Started dockerized localstack');
    this.container = container;
    this.hostname = 'localhost';
    this.port = container.getMappedPort(defaultS3Port);

    logger.info(`S3Ops endpoint: ${this.getEndpointUrl()}`);
    const {CancelToken} = axios;
    const source = CancelToken.source();
    const timeout = setTimeout(() => {
      source.cancel('timed out, cancel the request');
    }, 15 * 1000);

    const healthEndpoint = `${this.getEndpointUrl()}/health`;
    while (true) {
      try {
        const res = await axios.get(healthEndpoint, {cancelToken: source.token});
        const {data} = res;
        if (data.services && data.services.s3 && data.services.s3 === 'running') {
          clearTimeout(timeout);
          return;
        }
      } catch (err) {
        if (axios.isCancel(err)) {
          break;
        }
      }
      await new Promise((r) => setTimeout(r, 200));
    }

    throw new Error('localstack s3 service health check failed in 15s');
  }

  async stop() {
    if (this.container) {
      await this.container.stop();
    }
  }

  getEndpointUrl() {
    return `http://${this.hostname}:${this.port}`;
  }
}

export default {
  S3Ops,
};
