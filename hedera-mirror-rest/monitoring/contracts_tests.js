/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import _ from 'lodash';
import config from './config';

import {
  checkEntityId,
  checkAPIResponseError,
  checkMandatoryParams,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  getAPIResponse,
  getUrl,
  hasEmptyList,
  testRunner,
} from './utils';

const contractsPath = '/contracts';
const resource = 'contract';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const jsonRespKey = 'contracts';
const jsonResultsRespKey = 'results';
const mandatoryParams = [
  'admin_key',
  'auto_renew_account',
  'contract_id',
  'created_timestamp',
  'evm_address',
  'deleted',
  'expiration_timestamp',
  'file_id',
  'memo',
  'obtainer_id',
  'permanent_removal',
  'proxy_account_id',
  'timestamp',
];
const contractResultParams = ['address', 'bloom', 'contract_id', 'from', 'gas_limit', 'hash', 'timestamp', 'to'];

/**
 * Verify base contracts call
 * Also ensure a contract mentioned in the contracts can be confirmed as existing
 * @param {Object} server API host endpoint
 * @returns {{url: string, passed: boolean, message: string}}
 */
const getContractsWithCheck = async (server) => {
  let {url, contracts, result} = await getContractsList(server);

  if (!result.passed) {
    return {url, ...result};
  }

  const contract = _.max(_.map(contracts, (contract) => contract.contract_id));
  url = getUrl(server, contractsPath, {
    'contract.id': contract,
    limit: 1,
  });
  const singleContract = await getAPIResponse(url, jsonRespKey, hasEmptyList(jsonRespKey));

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'singleContract is undefined'})
    .withCheckSpec(checkEntityId, {contractId: singleContract, message: 'contract check was not found'})
    .run(singleContract);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts and performed contract check',
  };
};

/**
 * Verify single contract can be retrieved
 * @param {Object} server API host endpoint
 */
const getSingleContract = async (server) => {
  let {url, contracts, result} = await getContractsList(server);

  if (!result.passed) {
    return {url, ...result};
  }

  const contract = _.max(_.map(contracts, (contract) => contract.contract_id));
  url = getUrl(server, `${contractsPath}/${contract}`);
  const singleContract = await getAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contracts is undefined'})
    .withCheckSpec(checkEntityId, {contractId: singleContract, message: 'contract ID was not found'})
    .run(singleContract);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts for single contract',
  };
};

/**
 * Verify contract results can be retrieved for a given contractId and at a given timestamp
 * @param {Object} server API host endpoint
 */
const getContractResults = async (server) => {
  let contractId = config[resource].contractId;
  if (!contractId) {
    let {url, contractsResults, result} = await getContractsResultsList(server);

    if (!result.passed) {
      return {url, ...result};
    }
    contractId = _.max(_.map(contractsResults, (result) => result.contract_id));
  }

  let url = getUrl(server, `${contractsPath}/${contractId}/results`);
  const contractResults = await getAPIResponse(url, jsonResultsRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contract results is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractResultParams,
      message: 'contract results object is missing some mandatory fields',
    })
    .run(contractResults);
  if (!result.passed) {
    return {url, ...result};
  }

  const timestamp = _.max(_.map(contractResults, (result) => result.timestamp));
  url = getUrl(server, `${contractsPath}/${contractId}/results/${timestamp}`);
  const contractResultsAtTimestamp = await getAPIResponse(url);
  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contract results at a given timestamp is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractResultParams,
      message: 'contract results object is missing some mandatory fields',
    })
    .run(contractResultsAtTimestamp);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts for contract results at a given timestamp',
  };
};

/**
 * Verify contract result logs can be retrieved for a given contractId
 * @param {Object} server API host endpoint
 */
const getContractResultsLogs = async (server) => {
  let contractId = config[resource].contractId;
  if (!contractId) {
    let {url, contractsResults, result} = await getContractsResultsList(server);

    if (!result.passed) {
      return {url, ...result};
    }

    contractId = _.max(_.map(contractsResults, (contract) => contract.contract_id));
  }

  const jsonLogsRespKey = 'logs';
  const contractResultParams = ['address', 'bloom', 'contract_id', 'index', 'topics'];
  let url = getUrl(server, `${contractsPath}/${contractId}/results/logs`);
  const contractResults = await getAPIResponse(url, jsonLogsRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contract results logs is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractResultParams,
      message: 'contract results logs object is missing some mandatory fields',
    })
    .run(contractResults);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts for contract results logs ',
  };
};

/**
 * Verify contract state can be retrieved
 * @param {Object} server API host endpoint
 */
const getContractState = async (server) => {
  let {url, contracts, result} = await getContractsList(server);

  if (!result.passed) {
    return {url, ...result};
  }

  const contractStateParams = ['address', 'contract_id', 'timestamp', 'slot', 'value'];
  const jsonStateRespKey = 'state';
  const contract = _.max(_.map(contracts, (contract) => contract.contract_id));
  url = getUrl(server, `${contractsPath}/${contract}/state`);
  const contractState = await getAPIResponse(url, jsonStateRespKey);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contracts state is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: contractStateParams,
      message: 'contract state object is missing some mandatory fields',
    })
    .run(contractState);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called contracts for contract state',
  };
};
/**
 * Retrieves contract list
 * @param {Object} server API host endpoint
 */
async function getContractsList(server) {
  let url = getUrl(server, contractsPath, {limit: resourceLimit});
  const contracts = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contracts is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (contracts, limit) => `contracts.length of ${contracts.length} was expected to be ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'contract object is missing some mandatory fields',
    })
    .run(contracts);
  return {url, contracts, result};
}

/**
 * Retrieves contract results list
 * @param {Object} server API host endpoint
 */
async function getContractsResultsList(server) {
  const contractsResultsPath = '/contracts/results';
  let url = getUrl(server, contractsResultsPath, {limit: resourceLimit});
  const contractsResults = await getAPIResponse(url, jsonResultsRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'contracts results list is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (contracts, limit) =>
        `contractsResults.length of ${contractsResults.length} was expected to be ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: contractResultParams,
      message: 'contracts results list object is missing some mandatory fields',
    })
    .run(contractsResults);

  return {url, contractsResults, result};
}

/**
 * Run all contract tests in an asynchronous fashion waiting for all tests to complete
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getContractsWithCheck),
    runTest(getSingleContract),
    runTest(getContractResults),
    runTest(getContractResultsLogs),
    runTest(getContractState),
  ]);
};

export default {
  resource,
  runTests,
};
