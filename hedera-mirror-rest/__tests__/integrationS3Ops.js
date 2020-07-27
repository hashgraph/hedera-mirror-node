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

const { GenericContainer } = require('testcontainers');
const { Duration, TemporalUnit } = require('node-duration');
const { isDockerInstalled } = require('./integrationUtils');

const localStackImageName = 'localstack/localstack';
const localstackImageTag = '0.11.3';
const defaultS3Port = 4566;
const DATA_DIR = '/tmp/localstack/data';

class S3Ops {
  constructor(dataDir) {
    this.dataDir = dataDir;
  }

  async start() {
    const isInstalled = await isDockerInstalled();
    if (!isInstalled) {
      throw new Error('docker is not installed, cannot start localstack container for mock s3 service');
    }

    const container = await new GenericContainer(localStackImageName, localstackImageTag)
      .withEnv('SERVICES', 's3')
      .withEnv('DATA_DIR', DATA_DIR)
      .withBindMount(this.dataDir, DATA_DIR)
      .withExposedPorts(defaultS3Port)
      .withHealthCheck({
        test: "curl -f http://localhost:4566/health | grep 's3'",
        interval: new Duration(1, TemporalUnit.SECONDS),
        timeout: new Duration(3, TemporalUnit.SECONDS),
        retries: 10,
        startPeriod: new Duration(1, TemporalUnit.SECONDS),
      })
      .start();
    this.container = container;
    this.port = container.getMappedPort(defaultS3Port);
  }

  async stop() {
    await this.container.stop();
  }

  getEndpointUrl() {
    return `http://localhost:${this.port}`;
  }
}

module.exports = {
  S3Ops,
};
