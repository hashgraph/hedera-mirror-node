/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import http from 'k6/http';

import {setupTestParameters} from './parameters.js';
import {TestScenarioBuilder} from '../../lib/common.js';

const isSuccess = (response) => response.status >= 200 && response.status < 300;

const isValidListResponse = (response, listName) => {
  if (!isSuccess(response)) {
    return false;
  }

  const body = JSON.parse(response.body);
  const list = body[listName];
  if (!Array.isArray(list)) {
    return false;
  }

  return list.length > 0;
};

class RestTestScenarioBuilder extends TestScenarioBuilder {
  constructor() {
    super();
    this.fallbackRequest((testParameters) => {
      const url = `${testParameters['BASE_URL_PREFIX']}/transactions`;
      return http.get(url);
    });
  }

  build() {
    return Object.assign(super.build(), {setup: () => setupTestParameters(this._requiredParameters)});
  }
}

export {isValidListResponse, isSuccess, RestTestScenarioBuilder};
