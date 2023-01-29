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

import {GenericContainer, Wait} from 'testcontainers';
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
    const container = await new GenericContainer(image)
      .withExposedPorts(defaultS3Port)
      .withStartupTimeout(180000)
      .withWaitStrategy(Wait.forHttp("/", defaultS3Port))
      .start();
    logger.info('Started dockerized s3mock');
    this.container = container;
    this.hostname = 'localhost';
    this.port = container.getMappedPort(defaultS3Port);
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
