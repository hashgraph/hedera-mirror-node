/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import {jest} from '@jest/globals';
import {exec} from 'child_process';

import integrationDbOps from './integrationDbOps';
import transactions from '../transactions';
import {TokenService} from '../service';

// set a large timeout for beforeAll as downloading docker image if not exists can take quite some time. Note
// it's 12 minutes for CI to workaround possible DockerHub rate limit.
const defaultBeforeAllTimeoutMillis = process.env.CI ? 12 * 60 * 1000 : 4 * 60 * 1000;

const applyResponseJsonMatrix = (spec, key) => {
  if (spec.responseJsonMatrix?.[key]) {
    spec.responseJson = {
      ...spec.responseJson,
      ...spec.responseJsonMatrix[key],
    };
  }

  if (spec.tests) {
    for (const test of spec.tests) {
      if (test.responseJsonMatrix?.[key]) {
        test.responseJson = {
          ...test.responseJson,
          ...test.responseJsonMatrix[key],
        };
      }
    }
  }

  return spec;
};

const isDockerInstalled = function () {
  return new Promise((resolve) => {
    exec('docker --version', (err) => {
      resolve(!err);
    });
  });
};

const setupIntegrationTest = () => {
  jest.retryTimes(3);
  jest.setTimeout(40000);

  beforeAll(async () => {
    return await integrationDbOps.createPool();
  }, defaultBeforeAllTimeoutMillis);

  afterAll(async () => Promise.all([ownerPool.end(), pool.end()]));

  beforeEach(async () => {
    await integrationDbOps.cleanUp();
    TokenService.clearTokenCache();
    transactions.getFirstTransactionTimestamp.reset();
  });
};

export {applyResponseJsonMatrix, defaultBeforeAllTimeoutMillis, isDockerInstalled, setupIntegrationTest};
