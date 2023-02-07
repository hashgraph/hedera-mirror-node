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

import {
  computeAccountParameters,
  computeAccountWithNftsParameters,
  computeAccountWithTokenAllowanceParameters,
  computeAccountWithTokenParameters,
  computeBlockParameters,
  computeContractParameters,
  computeFungibleTokenParameters,
  computeNftParameters,
  computeScheduleParameters,
  computeTopicInfo,
  computeTransactionParameters,
  setDefaultValuesForEnvParameters,
} from "../../lib/parameters.js";

const computeTestParameters = (configuration) =>
  Object.assign({},
    computeAccountParameters(configuration),
    computeAccountWithNftsParameters(configuration),
    computeAccountWithTokenAllowanceParameters(configuration),
    computeAccountWithTokenParameters(configuration),
    computeBlockParameters(configuration),
    computeContractParameters(configuration),
    computeNftParameters(configuration),
    computeScheduleParameters(configuration),
    computeFungibleTokenParameters(configuration),
    computeTransactionParameters(configuration),
    computeTopicInfo(configuration)
  );

const setupTestParameters = () => {
  setDefaultValuesForEnvParameters();
  const baseApiUrl = __ENV['BASE_URL'];
  const startAccountId = __ENV['DEFAULT_START_ACCOUNT'];
  const testParametersMap = computeTestParameters({baseApiUrl: `${baseApiUrl}/api/v1`, startAccountId: `${startAccountId}`});
  return Object.assign(testParametersMap, {
    BASE_URL: baseApiUrl,
    DEFAULT_LIMIT: __ENV.DEFAULT_LIMIT
  });
};

export {setupTestParameters};
