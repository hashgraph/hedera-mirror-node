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

const axios = require('axios');
const {GenericContainer} = require('testcontainers');
const {isDockerInstalled} = require('./integrationUtils');

const localStackImageName = 'localstack/localstack';
const localstackImageTag = '0.11.3';
const defaultS3Port = 4566;

class S3Ops {
  async start() {
    if (!process.env.TEST_S3_HOST) {
      const isInstalled = await isDockerInstalled();
      if (!isInstalled) {
        throw new Error('docker is not installed, cannot start localstack container for mock s3 service');
      }

      const container = await new GenericContainer(localStackImageName, localstackImageTag)
        .withEnv('SERVICES', 's3')
        .withExposedPorts(defaultS3Port)
        .start();
      this.container = container;
      this.hostname = 'localhost';
      this.port = container.getMappedPort(defaultS3Port);
    } else {
      this.hostname = process.env.TEST_S3_HOST;
      this.port = defaultS3Port;
    }

    let timeout = false;
    new Promise((r) =>
      setTimeout(() => {
        timeout = true;
        r();
      }, 15000)
    );

    const healthEndpoint = `${this.getEndpointUrl()}/health`;
    while (!timeout) {
      try {
        const res = await axios.get(healthEndpoint);
        const {data} = res;
        if (data.services && data.services.s3 && data.services.s3 === 'running') {
          return;
        }
      } catch (err) {
        //
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

module.exports = {
  S3Ops,
};
