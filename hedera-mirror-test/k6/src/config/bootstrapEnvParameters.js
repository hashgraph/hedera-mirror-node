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

const http = require('http');
const https = require('https');
const fs = require('fs');

const outputFile = "config.env";

function validateConfiguration(configuration) {
  if (configuration.baseApiUrl === null) {
    throw new Error("You must provide a base API URL. Example: -baseApiUrl https://testnet.mirrornode.hedera.com");
  }
}

function computeConfigurationFromArgv() {
  const bootstrapArguments = process.argv || [];

  const configuration = {
    baseApiUrl: null
  };

  //We can start at 2, because the first argument is the program executing the script,
  //and the second is the script being executed. Also, it is not needed to iterate
  //until the last position, because the last position should always be a value.
  for (let i = 2; i < bootstrapArguments.length - 1; i += 2) {
    if (bootstrapArguments[i] === "-baseApiUrl") {
      configuration.baseApiUrl = bootstrapArguments[i + 1];
    }
  }

  validateConfiguration(configuration);

  return configuration;
}

function makeGetRequest(url) {
  const httpClient = url.startsWith("https") ? https : http;
  return new Promise((resolve, reject) => {
    httpClient.get(url, (resp) => {
      let data = '';

      resp.on('data', (chunk) => {
        data += chunk;
      });

      resp.on('end', () => resolve(JSON.parse(data)));

    })
      .on("error", (err) => reject(err));
  });
}

async function computeAccountParameters(configuration) {
  const accountPath = `${configuration.baseApiUrl}/accounts?balance=true&limit=1&order=desc`;
  const response = await makeGetRequest(accountPath);
  if (response.accounts.length === 0) {
    throw new Error(`No account has been found for the configuration: ${JSON.stringify(configuration)}`);
  }
  const firstAccount = response.accounts[0];

  return {
    account: firstAccount.account,
    accountBalance: firstAccount.balance.balance || 0,
    publicKey: firstAccount.key.key
  };
}

async function computeContractParameters(configuration) {
  const contractPath = `${configuration.baseApiUrl}/contracts?limit=1&order=desc`;
  const response = await makeGetRequest(contractPath);
  if (response.contracts.length === 0) {
    throw new Error(`No contract has been found for the configuration: ${JSON.stringify(configuration)}`);
  }
  const firstContract = response.contracts[0];
  return {
    contractId: firstContract.contract_id,
    contractTimestamp: firstContract.created_timestamp
  };
}

async function computeNftParameters(configuration) {
  const tokenPath = `${configuration.baseApiUrl}/tokens?type=NON_FUNGIBLE_UNIQUE&limit=1&order=desc`;
  const tokensResponse = await makeGetRequest(tokenPath);
  if (tokensResponse.tokens.length === 0) {
    throw new Error(`No NFT has been found in the tokens route for the configuration: ${JSON.stringify(configuration)}`);
  }
  const firstNft = tokensResponse.tokens[0];

  const nftPath = `${configuration.baseApiUrl}/tokens/${firstNft.token_id}/nfts?limit=1&order=desc`;
  const nftResponse = await makeGetRequest(nftPath);
  if (nftResponse.nfts.length === 0) {
    throw new Error(`No NFT has been found in the NFT route for the configuration: ${JSON.stringify(configuration)}`);
  }
  return {
    nft: firstNft.token_id,
    nftSerial: nftResponse.nfts[0].serial_number
  };
}

async function computeScheduleParameters(configuration) {
  const schedulePath = `${configuration.baseApiUrl}/schedules?limit=1&order=desc`;
  const response = await makeGetRequest(schedulePath);
  if (response.schedules.length === 0) {
    throw new Error(`No schedule has been found for the configuration: ${JSON.stringify(configuration)}`);
  }
  const firstSchedule = response.schedules[0];
  return {
    scheduleAccount: firstSchedule.creator_account_id,
    scheduleId: firstSchedule.schedule_id
  };
}

async function computeFungibleTokenParameters(configuration) {
  const tokenPath = `${configuration.baseApiUrl}/tokens?type=FUNGIBLE_COMMON&limit=1&order=desc`;
  const response = await makeGetRequest(tokenPath);
  if (response.tokens.length === 0) {
    throw new Error(`No token has been found for the configuration: ${JSON.stringify(configuration)}`);
  }
  const firstToken = response.tokens[0];
  return {
    token: firstToken.token_id
  };
}

async function computeTransactionParameters(configuration) {
  const tokenPath = `${configuration.baseApiUrl}/transactions?limit=1&transactiontype=cryptotransfer&order=desc`;
  const response = await makeGetRequest(tokenPath);
  if (response.transactions.length === 0) {
    throw new Error(`No transaction has been found for the configuration: ${JSON.stringify(configuration)}`);
  }
  const firstTransaction = response.transactions[0];
  return {
    transaction: firstTransaction.transaction_id
  };
}

async function computeTestParameters(configuration) {
  const accountParameters = await computeAccountParameters(configuration);
  const contractParameters = await computeContractParameters(configuration);
  const nftParameters = await computeNftParameters(configuration);
  const scheduleParameters = await computeScheduleParameters(configuration);
  const fungibleTokenParameters = await computeFungibleTokenParameters(configuration);
  const transactionParameters = await computeTransactionParameters(configuration);
  return {
    ...accountParameters,
    ...contractParameters,
    ...nftParameters,
    ...scheduleParameters,
    ...fungibleTokenParameters,
    ...transactionParameters
  };
}

function writeTestParametersToDisk(testParameters) {
  const envParametersFile = fs.openSync(`${__dirname}/${outputFile}`, 'w', 0o700);
  const parameters = `export DEFAULT_ACCOUNT=${testParameters.account}
export DEFAULT_ACCOUNT_BALANCE=${testParameters.accountBalance}
export DEFAULT_CONTRACT_ID=${testParameters.contractId}
export DEFAULT_NFT=${testParameters.nft}
export DEFAULT_NFT_SERIAL=${testParameters.nftSerial}
export DEFAULT_PUBLICKEY=${testParameters.publicKey}
export DEFAULT_SCHEDULE_ACCOUNT=${testParameters.scheduleAccount}
export DEFAULT_SCHEDULE_ID=${testParameters.scheduleId}
export DEFAULT_TOKEN=${testParameters.token}
export DEFAULT_TRANSACTION=${testParameters.transaction}`;
  fs.writeFileSync(envParametersFile, parameters);
  fs.fdatasyncSync(envParametersFile);
  fs.closeSync(envParametersFile);
}

const configuration = computeConfigurationFromArgv();
computeTestParameters(configuration)
  .then(testParameters => writeTestParametersToDisk(testParameters))
  .catch(console.log);
