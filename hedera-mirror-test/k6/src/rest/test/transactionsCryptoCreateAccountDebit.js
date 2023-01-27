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

/**
 * This is a very particular test case related to https://github.com/hashgraph/hedera-mirror-node/issues/2385.
 * While testing performance issues, it was found that calls using both the transaction type (e.g. CRYPTOCREATEACCOUNT)
 * and balance modification type (e.g. debit) query string parameters, performance was especially slow. API calls would timeout after 20 seconds.
 * This test uses hard-coded transaction type and balance modification type values because slow performance seems to be
 * associated with a less frequently used transaction type.
 * An attempt to make this test more generic seems to have low-value while also making variable names confusing.
 */

import http from "k6/http";

import { TestScenarioBuilder } from '../../lib/common.js';
import { transactionListName, urlPrefix } from '../../lib/constants.js';
import { isValidListResponse } from "./common.js";
import { setupTestParameters } from "./bootstrapEnvParameters.js";

const urlTag = '/transactions?transactionType=CRYPTOCREATEACCOUNT&type=debit';

const { options, run } = new TestScenarioBuilder()
  .name('transactionsCryptoCreateAccountDebit') // use unique scenario name among all tests
  .tags({ url: urlTag })
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL']}${urlPrefix}/transactions?transactiontype=CRYPTOCREATEACCOUNT&type=debit&order=asc&limit=${testParameters['DEFAULT_LIMIT']}`;
    return http.get(url);
  })
  .check('Transactions of type CRYPTOCREATEACCOUNT and debit balance modification type OK', (r) => isValidListResponse(r, transactionListName))
  .build();

export { options, run };

export const setup = setupTestParameters;
