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

import {
  accountListName,
  actionListName,
  allowanceListName,
  balanceListName,
  blockListName,
  contractListName,
  messageListName,
  resultListName,
  scheduleListName,
  tokenListName,
  transactionListName,
} from './constants.js';

const last = (arr) => arr[arr.length - 1];

const getValidResponse = (requestUrl, requestBody, httpVerbMethod) => {
  const response = httpVerbMethod(requestUrl, JSON.stringify(requestBody));
  if (response.status !== 200) {
    throw new Error(`${response.status} received when requesting ${requestUrl}`);
  }
  return JSON.parse(response.body);
};

const getEntities = (listEntityPath, key) => {
  const body = getValidResponse(listEntityPath, null, http.get);
  const entities = body[key];
  if (entities == null) {
    throw new Error(`Missing ${key} property in ${listEntityPath} response`);
  }

  return entities;
};

const getFirstEntity = (listEntityPath, key) => {
  const entities = getEntities(listEntityPath, key);
  if (entities.length === 0) {
    throw new Error(`No ${key} were found in the response for request at ${listEntityPath}`);
  }
  return entities[0];
};

const getPropertiesForEntity = (configuration, extractProperties, properties) => {
  const {baseApiUrl} = configuration;
  const {entitiesKey, subResourcePath} = properties;
  const queryParamMap = Object.assign({limit: 100, order: 'desc'}, properties.queryParamMap || {});
  const queryParams = Object.entries(queryParamMap)
    .map(([key, val]) => `${key}=${val}`)
    .join('&');
  const defaultEntitiesQuery = `${baseApiUrl}/${entitiesKey}?${queryParams}`;
  const entityIdPaginationKey = `${entitiesKey.substring(0, entitiesKey.length - 1)}.id`;
  const entityIdResponseKey = properties.entityIdResponseKey || entityIdPaginationKey.replace('.', '_');
  const subResourceKey = subResourcePath && last(subResourcePath.split('/'));

  let lastEntityId = '';

  while (true) {
    const query = `${defaultEntitiesQuery}${lastEntityId && entityIdPaginationKey + '=lt:' + lastEntityId}`;
    const entities = getEntities(query, entitiesKey.includes('/') ? resultListName : entitiesKey);
    if (entities.length === 0) {
      throw new Error('No ${entitiesKey} matching criteria found');
    }
    for (const entity of entities) {
      try {
        lastEntityId = entity[entityIdResponseKey];
        const args = [entity];

        if (subResourcePath) {
          const query = `${baseApiUrl}/${entitiesKey}/${lastEntityId}/${subResourcePath}?limit=1&order=desc`;
          const subResource = getFirstEntity(query, subResourceKey);
          args.push(subResource);
        }

        return extractProperties(...args);
      } catch (err) {
        // Ignore the error and continue to the next entity
      }
    }
  }
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
    envProperties,
  };
};

const computeProperties = (propertyList, fallback) => {
  const copyResult = copyEnvParamsFromEnvMap(propertyList);
  if (copyResult.allPropertiesFound) {
    return copyResult.envProperties;
  }
  return Object.assign(copyResult.envProperties, fallback());
};

export const computeAccountParameters = (configuration) =>
  computeProperties(['DEFAULT_ACCOUNT_ID', 'DEFAULT_ACCOUNT_BALANCE', 'DEFAULT_PUBLIC_KEY'], () => {
    const extractProperties = (account) => {
      const {
        balance: {balance},
        key,
      } = account;
      if (key === null) {
        throw new Error('The account has no key');
      }

      if (balance === 0) {
        throw new Error('The account has a zero balance');
      }

      return {
        DEFAULT_ACCOUNT_BALANCE: balance,
        DEFAULT_ACCOUNT_ID: account.account,
        DEFAULT_PUBLIC_KEY: key.key,
      };
    };
    return getPropertiesForEntity(configuration, extractProperties, {
      entitiesKey: accountListName,
      entityIdResponseKey: 'account',
    });
  });

export const computeAccountWithNftsParameters = (configuration) =>
  computeProperties(['DEFAULT_ACCOUNT_ID_NFTS'], () => {
    const tokensPath = `${configuration.baseApiUrl}/tokens?type=NON_FUNGIBLE_UNIQUE&limit=100&order=asc`;
    const tokensResult = getEntities(tokensPath, tokenListName);
    for (const entity of tokensResult) {
      const tokensBalancePath = `${configuration.baseApiUrl}/tokens/${entity.token_id}/balances`;
      const tokensBalanceEntity = getEntities(tokensBalancePath, balanceListName);
      if(tokensBalanceEntity.length === undefined) continue;
      for (const balanceEntity of tokensBalanceEntity) {
        if (balanceEntity.balance >= 20) {
          return {
            DEFAULT_ACCOUNT_ID_NFTS: balanceEntity.account
          };
        }
      }
    }
    throw new Error(
      `It was not possible to find an account with with significant number of nfts.`
    );
  });

export const computeAccountWithTokenAllowanceParameters = (configuration) => //29631749
  computeProperties(['DEFAULT_ACCOUNT_ID_TOKEN_ALLOWANCE'], () => {
    const accountsPath = `${configuration.baseApiUrl}/accounts?account.id=gt%3A${configuration.startAccountId}&balance=false&order=asc&limit=100`;
    const accountsResult = getEntities(accountsPath, accountListName);
    for (const entity of accountsResult) {
      const tokensAllowancePath = `${configuration.baseApiUrl}/accounts/${entity.account}/allowances/tokens`;
      let tokensAllowanceEntities;
      try {
        tokensAllowanceEntities = getEntities(tokensAllowancePath, allowanceListName);
      } catch (err) {
        //Continuing to avoid errors due to accounts not having allowance tokens.
        continue;
      }
      if(tokensAllowanceEntities.length === undefined) continue;
      if (tokensAllowanceEntities.length >= 25) {
          return {
            DEFAULT_ACCOUNT_ID_TOKEN_ALLOWANCE: entity.account
          };
      }
    }
    throw new Error(
      `It was not possible to find an account with with significant number of allowance tokens.`
    );
  });

export const computeAccountWithTokenParameters = (configuration) =>
  computeProperties(['DEFAULT_ACCOUNT_ID_TOKEN'], () => {
    const accountsPath = `${configuration.baseApiUrl}/accounts`;
    const accountsResult = getEntities(accountsPath, accountListName);
    for (const entity of accountsResult) {
      const tokensPath = `${configuration.baseApiUrl}/accounts/${entity.account}/tokens`;
      const tokensEntities = getEntities(tokensPath, tokenListName);
      if (tokensEntities.length === undefined) continue;
      if (tokensEntities.length >= 25) {
        return {
          DEFAULT_ACCOUNT_ID_TOKEN: entity.account
        };
      }
    }
    throw new Error(
      `It was not possible to find an account with with significant number of tokens.`
    );
  });



export const computeBlockParameters = (configuration) =>
  computeProperties(['DEFAULT_BLOCK_NUMBER', 'DEFAULT_BLOCK_HASH'], () => {
    const extractProperties = (block) => {
      return {
        DEFAULT_BLOCK_NUMBER: block.number,
        DEFAULT_BLOCK_HASH: block.hash,
      };
    };
    return getPropertiesForEntity(configuration, extractProperties, {
      entitiesKey: blockListName,
      queryParamMap: {limit: 1},
    });
});

export const computeContractParameters = (configuration) => {
  const contractProperties = computeProperties(['DEFAULT_CONTRACT_ID', 'DEFAULT_CONTRACT_TIMESTAMP'], () => {
    const extractProperties = (contract, log) => ({
      DEFAULT_CONTRACT_ID: contract.contract_id,
      DEFAULT_CONTRACT_TIMESTAMP: log.timestamp,
    });
    return getPropertiesForEntity(configuration, extractProperties, {
      entitiesKey: contractListName,
      subResourcePath: 'results/logs',
    });
  });

  const contractResultHashProperty = computeProperties(['DEFAULT_CONTRACT_RESULT_HASH'], () => {
    const contractResultsPath = `${configuration.baseApiUrl}/contracts/results`;
    const firstContractResult = getFirstEntity(contractResultsPath, resultListName);
    return {
      DEFAULT_CONTRACT_RESULT_HASH: firstContractResult.hash,
    };
  });

  return Object.assign(contractProperties, contractResultHashProperty);
};

export const computeNftParameters = (configuration) => {
  const extractProperties = (token, nft) => ({DEFAULT_NFT_ID: token.token_id, DEFAULT_NFT_SERIAL: nft.serial_number});
  return computeProperties(['DEFAULT_NFT_ID', 'DEFAULT_NFT_SERIAL'], () =>
    getPropertiesForEntity(configuration, extractProperties, {
      entitiesKey: tokenListName,
      queryParamMap: {type: 'NON_FUNGIBLE_UNIQUE'},
      subResourcePath: 'nfts',
    })
  );
};

export const computeScheduleParameters = (configuration) =>
  computeProperties(['DEFAULT_SCHEDULE_ACCOUNT_ID', 'DEFAULT_SCHEDULE_ID'], () => {
    const extractProperties = (schedule) => ({
      DEFAULT_SCHEDULE_ACCOUNT_ID: schedule.creator_account_id,
      DEFAULT_SCHEDULE_ID: schedule.schedule_id,
    });
    return getPropertiesForEntity(configuration, extractProperties, {
      entitiesKey: scheduleListName,
      queryParamMap: {limit: 1},
    });
  });

export const computeFungibleTokenParameters = (configuration) =>
  computeProperties(['DEFAULT_TOKEN_ID'], () => {
    const extractProperties = (token) => ({DEFAULT_TOKEN_ID: token.token_id});
    return getPropertiesForEntity(configuration, extractProperties, {
      entitiesKey: tokenListName,
      queryParamMap: {type: 'FUNGIBLE_COMMON', limit: 1},
    });
  });

export const computeTransactionParameters = (configuration) =>
  computeProperties(['DEFAULT_TRANSACTION_ID'], () => {
    const extractProperties = (transaction) => ({DEFAULT_TRANSACTION_ID: transaction.transaction_id});
    return getPropertiesForEntity(configuration, extractProperties, {
      entitiesKey: transactionListName,
      queryParamMap: {limit: 1, transactiontype: 'cryptotransfer'},
    });
  });

export const computeTopicInfo = (configuration) => {
  const transactionProperties = computeProperties(['DEFAULT_TOPIC_ID'], () => {
    const extractProperties = (transaction) => ({DEFAULT_TOPIC_ID: transaction.entity_id});
    return getPropertiesForEntity(configuration, extractProperties, {
      entitiesKey: transactionListName,
      queryParamMap: {limit: 1, result: 'success', transactiontype: 'CONSENSUSSUBMITMESSAGE'},
    });
  });

  const topicProperties = computeProperties(['DEFAULT_TOPIC_SEQUENCE', 'DEFAULT_TOPIC_TIMESTAMP'], () => {
    const topicMessagePath = `${configuration.baseApiUrl}/topics/${transactionProperties.DEFAULT_TOPIC_ID}/messages`;
    const firstTopicMessage = getFirstEntity(topicMessagePath, messageListName);
    return {
      DEFAULT_TOPIC_SEQUENCE: firstTopicMessage.sequence_number,
      DEFAULT_TOPIC_TIMESTAMP: firstTopicMessage.consensus_timestamp,
    };
  });

  return Object.assign(transactionProperties, topicProperties);
};

export const computeBlockFromNetwork = (rosettaApiUrl, network) =>
  computeProperties(['DEFAULT_BLOCK_INDEX', 'DEFAULT_BLOCK_HASH'], () => {
    const requestUrl = `${rosettaApiUrl}/rosetta/network/status`;
    const requestBody = {
      network_identifier: {
        blockchain: 'Hedera',
        network: network,
        sub_network_identifier: {
          network: 'shard 0 realm 0',
        },
      },
      metadata: {},
    };
    const response = getValidResponse(requestUrl, requestBody, http.post);
    return {
      DEFAULT_BLOCK_INDEX: parseInt(response.current_block_identifier.index),
      DEFAULT_BLOCK_HASH: response.current_block_identifier.hash,
    };
  });

export const computeTransactionFromBlock = (rosettaApiUrl, networkIdentifier, blockIdentifier) =>
  computeProperties(['DEFAULT_TRANSACTION_HASH'], () => {
    const requestUrl = `${rosettaApiUrl}/rosetta/block`;
    const requestBody = {
      network_identifier: networkIdentifier,
      block_identifier: blockIdentifier,
    };
    const response = getValidResponse(requestUrl, requestBody, http.post);
    const transactions = response.block.transactions;
    if (!transactions || transactions.length === 0) {
      throw new Error(
        `It was not possible to find a transaction with the block identifier: ${JSON.stringify(blockIdentifier)}`
      );
    }
    return {
      DEFAULT_TRANSACTION_HASH: transactions[0].transaction_identifier.hash,
    };
  });

export const computeNetworkInfo = (rosettaApiUrl) =>
  computeProperties(['DEFAULT_NETWORK'], () => {
    const requestUrl = `${rosettaApiUrl}/rosetta/network/list`;
    const response = getValidResponse(requestUrl, {metadata: {}}, http.post);
    const networks = response.network_identifiers;
    if (networks.length === 0) {
      throw new Error(`It was not possible to find a network at ${rosettaApiUrl}`);
    }
    return {
      DEFAULT_NETWORK: networks[0].network,
    };
  });

export const setDefaultValuesForEnvParameters = () => {
  __ENV['BASE_URL'] = __ENV['BASE_URL'] || 'http://localhost';
  __ENV['DEFAULT_DURATION'] = __ENV['DEFAULT_DURATION'] || '120s';
  __ENV['DEFAULT_VUS'] = __ENV['DEFAULT_VUS'] || 10;
  __ENV['DEFAULT_LIMIT'] = __ENV['DEFAULT_LIMIT'] || 100;
  __ENV['DEFAULT_PASS_RATE'] = __ENV['DEFAULT_PASS_RATE'] || 0.95;
  __ENV['DEFAULT_MAX_DURATION'] = __ENV['DEFAULT_MAX_DURATION'] || 500;
};
