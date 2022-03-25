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

import http from 'k6/http';

import {
  accountListName,
  contractListName, messageListName,
  nftListName,
  scheduleListName,
  tokenListName,
  transactionListName
} from "./constants.js";

const getValidResponse = (requestUrl, requestBody, httpVerbMethod) => {
  const response = httpVerbMethod(requestUrl, JSON.stringify(requestBody));
  if (response.status !== 200) {
    throw new Error(`${response.status} received when requesting ${requestUrl}`);
  }
  return JSON.parse(response.body);
}

const getFirstEntity = (entityPath, key) => {
  const body = getValidResponse(entityPath, null, http.get);
  if (!body.hasOwnProperty(key)) {
    throw new Error(`Missing ${key} property in ${entityPath} response`);
  }
  const entity = body[key];
  if (entity.length === 0) {
    throw new Error(`No ${key} were found in the response for request at ${entityPath}`);
  }
  return entity[0];
};

const copyEnvParamsFromEnvMap = (propertyList) => {
  const envProperties = {};
  let allPropertiesFound = true;
  for (const property of propertyList) {
    if (__ENV.hasOwnProperty(property)) {
      envProperties[property] = __ENV[property];
    } else {
      allPropertiesFound = false;
    }
  }
  return {
    allPropertiesFound,
    envProperties
  };
}

const computeProperties = (propertyList, fallback) => {
  const copyResult = copyEnvParamsFromEnvMap(propertyList);
  if (copyResult.allPropertiesFound) {
    return copyResult.envProperties;
  }
  return Object.assign(copyResult.envProperties, fallback());
}

export const computeAccountParameters = (configuration) =>
  computeProperties(
    ['DEFAULT_ACCOUNT_ID', 'DEFAULT_ACCOUNT_BALANCE', 'DEFAULT_PUBLIC_KEY'],
    () => {
      const accountPath = `${configuration.baseApiUrl}/accounts?balance=true&limit=1&order=desc`;
      const firstAccount = getFirstEntity(accountPath, accountListName);
      return {
        DEFAULT_ACCOUNT_ID: firstAccount.account,
        DEFAULT_ACCOUNT_BALANCE: firstAccount.balance.balance || 0,
        DEFAULT_PUBLIC_KEY: firstAccount.key.key
      };
    });

export const computeContractParameters = (configuration) =>
  computeProperties(
    ['DEFAULT_CONTRACT_ID', 'DEFAULT_CONTRACT_TIMESTAMP'],
    () => {
      const contractPath = `${configuration.baseApiUrl}/contracts?limit=1&order=desc`;
      const firstContract = getFirstEntity(contractPath, contractListName)
      return {
        DEFAULT_CONTRACT_ID: firstContract.contract_id,
        DEFAULT_CONTRACT_TIMESTAMP: firstContract.created_timestamp
      };
    }
  );

export const computeNftParameters = (configuration) => {
  const tokenProperties = computeProperties(
    ['DEFAULT_NFT_ID'],
    () => {
      const tokenPath = `${configuration.baseApiUrl}/tokens?type=NON_FUNGIBLE_UNIQUE&limit=1&order=desc`;
      const firstNftFromTokenList = getFirstEntity(tokenPath, tokenListName);
      return {DEFAULT_NFT_ID: firstNftFromTokenList.token_id};
    }
  );

  const nftProperties = computeProperties(
    ['DEFAULT_NFT_SERIAL'],
    () => {
      const nftPath = `${configuration.baseApiUrl}/tokens/${tokenProperties.DEFAULT_NFT_ID}/nfts?limit=1&order=desc`;
      const firstNft = getFirstEntity(nftPath, nftListName);
      return {DEFAULT_NFT_SERIAL: firstNft.serial_number};
    }
  );

  return Object.assign(tokenProperties, nftProperties)
};

export const computeScheduleParameters = (configuration) =>
  computeProperties(
    ['DEFAULT_SCHEDULE_ACCOUNT_ID', 'DEFAULT_SCHEDULE_ID'],
    () => {
      const schedulePath = `${configuration.baseApiUrl}/schedules?limit=1&order=desc`;
      const firstSchedule = getFirstEntity(schedulePath, scheduleListName);
      return {
        DEFAULT_SCHEDULE_ACCOUNT_ID: firstSchedule.creator_account_id,
        DEFAULT_SCHEDULE_ID: firstSchedule.schedule_id
      };
    }
  );

export const computeFungibleTokenParameters = (configuration) =>
  computeProperties(
    ['DEFAULT_TOKEN_ID'],
    () => {
      const tokenPath = `${configuration.baseApiUrl}/tokens?type=FUNGIBLE_COMMON&limit=1&order=desc`;
      const firstToken = getFirstEntity(tokenPath, tokenListName);
      return {
        DEFAULT_TOKEN_ID: firstToken.token_id,
      };
    }
  );

export const computeTransactionParameters = (configuration) =>
  computeProperties(
    ['DEFAULT_TRANSACTION_ID'],
    () => {
      const tokenPath = `${configuration.baseApiUrl}/transactions?limit=1&transactiontype=cryptotransfer&order=desc`;
      const firstTransaction = getFirstEntity(tokenPath, transactionListName)
      return {
        DEFAULT_TRANSACTION_ID: firstTransaction.transaction_id
      };
    }
  );

export const computeTopicInfo = (configuration) => {
  const transactionProperties = computeProperties(
    ['DEFAULT_TOPIC_ID'],
    () => {
      const transactionPath = `${configuration.baseApiUrl}/transactions?transactiontype=CONSENSUSSUBMITMESSAGE&result=success&limit=1&order=desc`;
      const DEFAULT_TOPIC_ID = getFirstEntity(transactionPath, transactionListName).entity_id;
      return {DEFAULT_TOPIC_ID};
    }
  );

  const topicProperties = computeProperties(
    ['DEFAULT_TOPIC_SEQUENCE', 'DEFAULT_TOPIC_TIMESTAMP'],
    () => {
      const topicMessagePath = `${configuration.baseApiUrl}/topics/${transactionProperties.DEFAULT_TOPIC_ID}/messages`;
      const firstTopicMessage = getFirstEntity(topicMessagePath, messageListName);
      return {
        DEFAULT_TOPIC_SEQUENCE: firstTopicMessage.sequence_number,
        DEFAULT_TOPIC_TIMESTAMP: firstTopicMessage.consensus_timestamp
      };
    }
  );

  return Object.assign(transactionProperties, topicProperties);
};

export const computeBlockFromNetwork = (rosettaApiUrl, network) =>
  computeProperties(
    ['DEFAULT_BLOCK_INDEX', 'DEFAULT_BLOCK_HASH'],
    () => {
      const requestUrl = `${rosettaApiUrl}/rosetta/network/status`;
      const requestBody = {
        "network_identifier": {
          "blockchain": "Hedera",
          "network": network,
          "sub_network_identifier": {
            "network": "shard 0 realm 0"
          }
        },
        "metadata": {}
      };
      const response = getValidResponse(requestUrl, requestBody, http.post);
      return {
        DEFAULT_BLOCK_INDEX: parseInt(response.current_block_identifier.index),
        DEFAULT_BLOCK_HASH: response.current_block_identifier.hash
      };
    }
  );

export const computeTransactionFromBlock = (rosettaApiUrl, networkIdentifier, blockIdentifier) =>
  computeProperties(
    ['DEFAULT_TRANSACTION_HASH'],
    () => {
      const requestUrl = `${rosettaApiUrl}/rosetta/block`;
      const requestBody = {
        network_identifier: networkIdentifier,
        block_identifier: blockIdentifier
      };
      const response = getValidResponse(requestUrl, requestBody, http.post);
      const transactions = response.block.transactions;
      if (!transactions || transactions.length === 0) {
        throw new Error(`It was not possible to find a transaction with the block identifier: ${JSON.stringify(blockIdentifier)}`);
      }
      return {
        DEFAULT_TRANSACTION_HASH: transactions[0].transaction_identifier.hash
      };
    }
  );


export const computeNetworkInfo = (rosettaApiUrl) =>
  computeProperties(
    ['DEFAULT_NETWORK'],
    () => {
      const requestUrl = `${rosettaApiUrl}/rosetta/network/list`;
      const response = getValidResponse(requestUrl, {"metadata": {}}, http.post);
      const networks = response.network_identifiers;
      if (networks.length === 0) {
        throw new Error(`It was not possible to find a network at ${rosettaApiUrl}`);
      }
      return {
        DEFAULT_NETWORK: networks[0].network
      };
    }
  );


export const setDefaultValuesForEnvParameters = () => {
  __ENV['BASE_URL'] = __ENV['BASE_URL'] || 'http://localhost';
  __ENV['DEFAULT_DURATION'] = __ENV['DEFAULT_DURATION'] || '120s';
  __ENV['DEFAULT_VUS'] = __ENV['DEFAULT_VUS'] || 10;
  __ENV['DEFAULT_LIMIT'] = __ENV['DEFAULT_LIMIT'] || 100;
  __ENV['DEFAULT_PASS_RATE'] = __ENV['DEFAULT_PASS_RATE'] || 0.95;
  __ENV['DEFAULT_MAX_DURATION'] = __ENV['DEFAULT_MAX_DURATION'] || 500;
}
