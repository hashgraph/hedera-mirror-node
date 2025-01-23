/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {airdrops} from '../libex/constants.js';

const urlTag = '/accounts/{id}/airdrops/pending';

const getUrl = (testParameters) =>
  `/accounts/${testParameters['DEFAULT_ACCOUNT_ID_AIRDROP_RECEIVER']}/airdrops/pending?limit=${testParameters['DEFAULT_LIMIT']}&order=desc`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('accountsPendingAirdrop') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID_AIRDROP_RECEIVER')
  .check('Pending airdrop for receiver', (r) => isValidListResponse(r, airdrops))
  .build();

export {getUrl, options, run, setup};
