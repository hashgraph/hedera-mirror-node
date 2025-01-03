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

const setupTestParameters = (requiredParameters) => {
  const testParameters = {
    BASE_URL_PREFIX: __ENV.BASE_URL_PREFIX,
    DEFAULT_ACCOUNT_ID_NFTS_ALLOWANCE_OWNER: __ENV['DEFAULT_ACCOUNT_ID_NFTS_ALLOWANCE_OWNER'],
    DEFAULT_ACCOUNT_ID_NFTS_ALLOWANCE_SPENDER: __ENV['DEFAULT_ACCOUNT_ID_NFTS_ALLOWANCE_SPENDER'],
    DEFAULT_ACCOUNT_ID_AIRDROP_SENDER: __ENV['DEFAULT_ACCOUNT_ID_AIRDROP_SENDER'],
    DEFAULT_ACCOUNT_ID_AIRDROP_RECEIVER: __ENV['DEFAULT_ACCOUNT_ID_AIRDROP_RECEIVER'],
    DEFAULT_TOPIC_ID: __ENV['DEFAULT_TOPIC_ID'],
    DEFAULT_LIMIT: __ENV['DEFAULT_LIMIT'],
  };
  console.info(`Test parameters - ${JSON.stringify(testParameters, null, '\t')}`);
  return testParameters;
};

export {setupTestParameters};
