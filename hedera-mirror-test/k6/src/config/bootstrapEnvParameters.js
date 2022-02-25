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
  accountListName,
  contractListName,
  nftListName,
  scheduleListName,
  tokenListName,
  transactionListName
} from "../rest/test/constants.js";

import http from 'k6/http';

const getFirstEntity = (entityPath, key) => {
  const response = http.get(entityPath);
  const body = JSON.parse(response.body);
  const entity = body[key];
  if (entity.length === 0) {
    throw new Error(`No ${key} were found in the response for request at ${entityPath}`);
  }
  return entity[0];
};

const computeAccountParameters = (configuration) => {
  const accountPath = `${configuration.baseApiUrl}/accounts?balance=true&limit=1&order=desc`;
  const firstAccount = getFirstEntity(accountPath, accountListName);
  return {
    account: firstAccount.account,
    accountBalance: firstAccount.balance.balance || 0,
    publicKey: firstAccount.key.key
  };
};

const computeContractParameters = (configuration) => {
  const contractPath = `${configuration.baseApiUrl}/contracts?limit=1&order=desc`;
  const firstContract = getFirstEntity(contractPath, contractListName)
  return {
    contractId: firstContract.contract_id,
    contractTimestamp: firstContract.created_timestamp
  };
};

const computeNftParameters = (configuration) => {
  const tokenPath = `${configuration.baseApiUrl}/tokens?type=NON_FUNGIBLE_UNIQUE&limit=1&order=desc`;
  const firstNftFromTokenList = getFirstEntity(tokenPath, tokenListName);
  const nftPath = `${configuration.baseApiUrl}/tokens/${firstNftFromTokenList.token_id}/nfts?limit=1&order=desc`;
  const firstNft = getFirstEntity(nftPath, nftListName);
  return {
    nft: firstNftFromTokenList.token_id,
    nftSerial: firstNft.serial_number
  };
};

const computeScheduleParameters = (configuration) => {
  const schedulePath = `${configuration.baseApiUrl}/schedules?limit=1&order=desc`;
  const firstSchedule = getFirstEntity(schedulePath, scheduleListName);
  return {
    scheduleAccount: firstSchedule.creator_account_id,
    scheduleId: firstSchedule.schedule_id
  };
};

const computeFungibleTokenParameters = (configuration) => {
  const tokenPath = `${configuration.baseApiUrl}/tokens?type=FUNGIBLE_COMMON&limit=1&order=desc`;
  const firstToken = getFirstEntity(tokenPath, tokenListName);
  return {
    token: firstToken.token_id
  };
};

const computeTransactionParameters = (configuration) => {
  const tokenPath = `${configuration.baseApiUrl}/transactions?limit=1&transactiontype=cryptotransfer&order=desc`;
  const firstTransaction = getFirstEntity(tokenPath, transactionListName)
  return {
    transaction: firstTransaction.transaction_id
  };
};

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


const validateEnvProperty = (propertyName) => {
  if (!__ENV.hasOwnProperty(propertyName)) {
    throw new Error(`You must set a value for the environment property: ${propertyName}`);
  }
}

const setupTestParameters = () => {
  //verify if all the data that must be in __ENV is actually there;
  validateEnvProperty('DEFAULT_TOPIC_ID');
  validateEnvProperty('DEFAULT_TOPIC_SEQUENCE');
  validateEnvProperty('DEFAULT_TOPIC_TIMESTAMP');

  const testParametersMap = bootstrap(__ENV['BASE_URL']);
  return Object.assign(testParametersMap, {
    DEFAULT_TOPIC_ID: __ENV.DEFAULT_TOPIC_ID,
    DEFAULT_TOPIC_SEQUENCE: __ENV.DEFAULT_TOPIC_SEQUENCE,
    DEFAULT_TOPIC_TIMESTAMP: __ENV.DEFAULT_TOPIC_TIMESTAMP
  });
};

export {setupTestParameters};
