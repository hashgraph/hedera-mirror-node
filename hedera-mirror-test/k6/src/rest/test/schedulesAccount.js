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

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {scheduleListName} from '../libex/constants.js';

const urlTag = '/schedules?account.id=gte:{accountId}';

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('schedulesAccount') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}/schedules?account.id=gte:${testParameters['DEFAULT_SCHEDULE_ACCOUNT_ID']}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_SCHEDULE_ACCOUNT_ID')
  .check('Schedules account OK', (r) => isValidListResponse(r, scheduleListName))
  .build();

export {options, run, setup};
