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

import axios from 'axios';
import {GenericContainer} from 'testcontainers';
import {isDockerInstalled} from './integrationUtils';

const imageName = 'adobe/s3mock';
const imageTag = 'latest';
const defaultS3Port = 9090;

class IntegrationS3Ops {
  async start() {
    const isInstalled = await isDockerInstalled();
    if (!isInstalled) {
      throw new Error('docker is not installed, cannot start s3mock container');
    }

    const image = `${imageName}:${imageTag}`;
    logger.info(`Starting docker container with image ${image}`);
    const container = await new GenericContainer(image).withExposedPorts(defaultS3Port).start();
    logger.info('Started dockerized s3mock');
    this.container = container;
    this.hostname = 'localhost';
    this.port = container.getMappedPort(defaultS3Port);

    logger.info(`S3Ops endpoint: ${this.getEndpointUrl()}`);
    const {CancelToken} = axios;
    const source = CancelToken.source();
    const timeout = setTimeout(() => {
      source.cancel('timed out, cancel the request');
    }, 15 * 1000);

    while (true) {
      try {
        const {status} = await axios.get(this.getEndpointUrl(), {cancelToken: source.token});
        if (status === 200) {
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

    throw new Error('s3 service health check failed in 15s');
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

export default IntegrationS3Ops;
