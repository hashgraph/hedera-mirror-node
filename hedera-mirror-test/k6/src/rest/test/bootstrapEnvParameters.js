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

import {
  computeAccountParameters,
  computeContractParameters,
  computeFungibleTokenParameters,
  computeNftParameters,
  computeScheduleParameters,
  computeTransactionParameters,
  setDefaultValuesForEnvParameters,
  validateEnvProperty
} from "../../lib/parameters.js";

const computeTestParameters = (configuration) =>
  Object.assign({},
    computeAccountParameters(configuration),
    computeContractParameters(configuration),
    computeNftParameters(configuration),
    computeScheduleParameters(configuration),
    computeFungibleTokenParameters(configuration),
    computeTransactionParameters(configuration)
  );

const buildConfigObject = (testParameters) => ({
  DEFAULT_ACCOUNT_ID: testParameters.account,
  DEFAULT_ACCOUNT_BALANCE: testParameters.accountBalance,
  DEFAULT_CONTRACT_ID: testParameters.contractId,
  DEFAULT_CONTRACT_TIMESTAMP: testParameters.DEFAULT_CONTRACT_TIMESTAMP,
  DEFAULT_NFT_ID: testParameters.nft,
  DEFAULT_NFT_SERIAL: testParameters.nftSerial,
  DEFAULT_PUBLIC_KEY: testParameters.publicKey,
  DEFAULT_SCHEDULE_ACCOUNT_ID: testParameters.scheduleAccount,
  DEFAULT_SCHEDULE_ID: testParameters.scheduleId,
  DEFAULT_TOKEN_ID: testParameters.token,
  DEFAULT_TRANSACTION_ID: testParameters.transaction
});

const bootstrap = (baseApiUrl) => {
  const configuration = {baseApiUrl: `${baseApiUrl}/api/v1`};
  const testParameters = computeTestParameters(configuration);
  return buildConfigObject(testParameters);
};

const setupTestParameters = () => {
  setDefaultValuesForEnvParameters();
  validateEnvProperty('DEFAULT_TOPIC_ID');
  validateEnvProperty('DEFAULT_TOPIC_SEQUENCE');
  validateEnvProperty('DEFAULT_TOPIC_TIMESTAMP');

  const testParametersMap = bootstrap(__ENV['BASE_URL']);
  return Object.assign(testParametersMap, {
    BASE_URL: __ENV.BASE_URL,
    DEFAULT_LIMIT: __ENV.DEFAULT_LIMIT,
    DEFAULT_TOPIC_ID: __ENV.DEFAULT_TOPIC_ID,
    DEFAULT_TOPIC_SEQUENCE: __ENV.DEFAULT_TOPIC_SEQUENCE,
    DEFAULT_TOPIC_TIMESTAMP: __ENV.DEFAULT_TOPIC_TIMESTAMP
  });
};

export {setupTestParameters};
