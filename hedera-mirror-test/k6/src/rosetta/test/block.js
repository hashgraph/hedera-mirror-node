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

import { check } from "k6";
import http from "k6/http";

import {getOptionsWithScenario} from '../../lib/common.js';
import * as constants from './constants.js';

const urlTag = '/block';

// use unique scenario name among all tests
const options = getOptionsWithScenario('block',{url: urlTag});

function run() {
  const url = __ENV.BASE_URL + urlTag;
  const payload = JSON.stringify({
    block_identifier: constants.blockIdentifier,
    network_identifier: constants.networkIdentifier,
  });
  const response = http.post(url, payload);
  check(response, {
    "Block OK": (r) => r.status === 200,
  });
}

export {options, run};
