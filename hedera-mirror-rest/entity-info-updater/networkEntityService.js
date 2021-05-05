/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

'use strict';

// external libraries
const {AccountBalanceQuery, AccountId, AccountInfo, AccountInfoQuery, Client, PrivateKey} = require('@hashgraph/sdk');
const log4js = require('log4js');

// local
const config = require('./config');
const utils = require('./utils');

const logger = log4js.getLogger();

let operatorId;
let client;
let clientConfigured = false;
let networkEntityCache = {};

/**
 * Get account info from network
 * @param accountId
 * @returns {Promise<AccountInfo>}
 */
const getAccountInfo = async (accountId) => {
  logger.trace(`Retrieve account info for ${accountId}`);
  let accountInfo;
  try {
    if (config.sdkClient.useCache && networkEntityCache[accountId.toString()] !== undefined) {
      accountInfo = AccountInfo.fromBytes(Buffer.from(networkEntityCache[accountId.toString()]));
      logger.trace(`Retrieved ${accountId.toString()} from cache`);
    } else {
      accountInfo = await new AccountInfoQuery().setAccountId(accountId).execute(client);
    }
  } catch (e) {
    if (e.toString().indexOf('INVALID_ACCOUNT_ID') < 0 && e.toString().indexOf('ACCOUNT_DELETED') < 0) {
      logger.trace(`Error getting accountInfo ${accountId} from network, error: ${e}.`);
      throw e;
    }

    logger.trace(`${accountId} is not present on the network, error: ${e}`);
    return null;
  }

  logger.trace(`Retrieved account info from network: ${JSON.stringify(accountInfo)}`);
  return accountInfo;
};

/**
 * Get account balance from network
 * @returns {Promise<Long>}
 */
const getAccountBalance = async () => {
  logger.trace(`Retrieve account balance for ${operatorId}`);
  let accountBalance;
  try {
    accountBalance = await new AccountBalanceQuery().setAccountId(operatorId).execute(client);
  } catch (e) {
    logger.trace(`Error retrieving ${operatorId} account balance, error: ${e}`);
    return -1;
  }

  logger.trace(`Retrieved account balance of ${accountBalance.hbars.toTinybars()} for ${operatorId} from network`);
  return accountBalance.hbars.toTinybars();
};

const restoreNetworkEntityCache = () => {
  networkEntityCache = utils.getNetworkEntityCache();
};

// configure sdk client on file load based off of config values
if (!clientConfigured) {
  logger.info(`Configure SDK client for ${config.sdkClient.network}`);
  if (config.sdkClient.network !== 'OTHER') {
    // prod env
    switch (config.sdkClient.network.toLowerCase()) {
      case 'mainnet':
        client = Client.forMainnet();
        break;
      case 'previewnet':
        client = Client.forPreviewnet();
        break;
      default:
        client = Client.forTestnet();
    }
  } else {
    const OTHERNET = {
      network: {
        [config.sdkClient.nodeAddress]: AccountId.fromString(config.sdkClient.nodeId),
      },
    };

    client = Client.fromConfig(OTHERNET);
  }

  if (!config.sdkClient.operatorId || !config.sdkClient.operatorKey) {
    throw new Error(
      'operatorId and operatorKey values must both be set under config.hedera.mirror.entityUpdate.sdkClient.'
    );
  }

  restoreNetworkEntityCache();
  if (config.sdkClient.useCache === true && networkEntityCache !== {}) {
    logger.info(
      "SDK network calls will pull from local cache as 'hedera.mirror.entityUpdate.sdkClient.useCache' is set to true and valid cache exists"
    );
  } else {
    operatorId = AccountId.fromString(config.sdkClient.operatorId);
    const operatorKey = PrivateKey.fromString(config.sdkClient.operatorKey);
    client.setOperator(operatorId, operatorKey);
    clientConfigured = true;
    logger.info(`SDK client successfully configured for Operator ID ${operatorId}`);
  }
}

module.exports = {
  getAccountBalance,
  getAccountInfo,
};
