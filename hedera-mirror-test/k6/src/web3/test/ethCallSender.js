/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import http from "k6/http";

import {TestScenarioBuilder} from '../../lib/common.js';
import {isNonErrorResponse} from "./common.js";

const url = __ENV.BASE_URL;

const payload = JSON.stringify({
  "to":"00000000000000000000000000000000000003eb",
  "data":"0x5e01eb5a"
});

const httpParams = {
  headers: {
    'Content-Type': 'application/json',
  },
};

const {options, run} = new TestScenarioBuilder()
  .name('ethCallSender') // use unique scenario name among all tests
  .request(() => http.post(url, payload, httpParams))
  .check('ethCallSender', (r) => isNonErrorResponse(r))
  .build();

export {options, run};
