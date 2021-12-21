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

const MAX_INT32 = 2147483647;

// url query filer keys
const filterKeys = {
  ACCOUNT_ID: 'account.id',
  ACCOUNT_BALANCE: 'account.balance',
  ACCOUNT_PUBLICKEY: 'account.publickey',
  BALANCE: 'balance',
  CONTRACTID: 'contractid',
  CONTRACT_ID: 'contract.id',
  CREDIT_TYPE: 'type',
  ENCODING: 'encoding',
  FROM: 'from',
  ENTITY_PUBLICKEY: 'publickey',
  INDEX: 'index',
  LIMIT: 'limit',
  NONCE: 'nonce',
  ORDER: 'order',
  RESULT: 'result',
  SCHEDULED: 'scheduled',
  SCHEDULEID: 'scheduleid',
  SCHEDULE_ID: 'schedule.id',
  SERIAL_NUMBER: 'serialnumber',
  SEQUENCE_NUMBER: 'sequencenumber',
  TIMESTAMP: 'timestamp',
  TOKENID: 'tokenid',
  TOKEN_ID: 'token.id',
  TOKEN_TYPE: 'type',
  TOPIC_ID: 'topic.id',
  TOPIC0: 'topic0',
  TOPIC1: 'topic1',
  TOPIC2: 'topic2',
  TOPIC3: 'topic3',
  TRANSACTION_TYPE: 'transactiontype',
};

const entityTypes = {
  ACCOUNT: 'ACCOUNT',
  CONTRACT: 'CONTRACT',
  FILE: 'FILE',
  TOKEN: 'TOKEN',
  TOPIC: 'TOPIC',
  SCHEDULE: 'SCHEDULE',
};

const keyTypes = {
  ECDSA_SECP256K1: 'ECDSA_SECP256K1',
  ED25519: 'ED25519',
  PROTOBUF: 'ProtobufEncoded',
};

const transactionColumns = {
  TYPE: 'type',
};

const requestIdLabel = 'requestId';
const responseDataLabel = 'mirrorRestData';

const orderFilterValues = {
  ASC: 'asc',
  DESC: 'desc',
};

// topic messages filter options
const characterEncoding = {
  BASE64: 'base64',
  UTF8: 'utf-8',
};

const transactionResultFilter = {
  SUCCESS: 'success',
  FAIL: 'fail',
};

const cryptoTransferType = {
  CREDIT: 'credit',
  DEBIT: 'debit',
};

const cloudProviders = {
  S3: 'S3',
  GCP: 'GCP',
};

const defaultCloudProviderEndpoints = {
  [cloudProviders.S3]: 'https://s3.amazonaws.com',
  [cloudProviders.GCP]: 'https://storage.googleapis.com',
};

const networks = {
  DEMO: 'DEMO',
  MAINNET: 'MAINNET',
  TESTNET: 'TESTNET',
  PREVIEWNET: 'PREVIEWNET',
  OTHER: 'OTHER',
};

const defaultBucketNames = {
  [networks.DEMO]: 'hedera-demo-streams',
  [networks.MAINNET]: 'hedera-mainnet-streams',
  [networks.TESTNET]: 'hedera-stable-testnet-streams-2020-08-27',
  [networks.PREVIEWNET]: 'hedera-preview-testnet-streams',
  [networks.OTHER]: null,
};

const recordStreamPrefix = 'recordstreams/record';

const tokenTypeFilter = {
  ALL: 'all',
  FUNGIBLE_COMMON: 'fungible_common',
  NON_FUNGIBLE_UNIQUE: 'non_fungible_unique',
};

const zeroRandomPageCostQueryHint = 'set local random_page_cost = 0';

class StatusCode {
  constructor(code, message) {
    this.code = code;
    this.message = message;
  }

  isClientError() {
    return this.code >= 400 && this.code < 500;
  }

  toString() {
    return `${this.code} ${this.message}`;
  }
}

const httpStatusCodes = {
  BAD_GATEWAY: new StatusCode(502, 'Bad gateway'),
  BAD_REQUEST: new StatusCode(400, 'Bad request'),
  INTERNAL_ERROR: new StatusCode(500, 'Internal error'),
  NO_CONTENT: new StatusCode(204, 'No content'),
  NOT_FOUND: new StatusCode(404, 'Not found'),
  OK: new StatusCode(200, 'OK'),
  PARTIAL_CONTENT: new StatusCode(206, 'Partial mirror node'),
  SERVICE_UNAVAILABLE: new StatusCode(503, 'Service unavailable'),
};

const queryParamOperators = {
  eq: 'eq',
  ne: 'ne',
  lt: 'lt',
  lte: 'lte',
  gt: 'gt',
  gte: 'gte',
};

module.exports = {
  MAX_INT32,
  characterEncoding,
  cloudProviders,
  cryptoTransferType,
  defaultBucketNames,
  defaultCloudProviderEndpoints,
  entityTypes,
  filterKeys,
  httpStatusCodes,
  keyTypes,
  networks,
  orderFilterValues,
  queryParamOperators,
  recordStreamPrefix,
  requestIdLabel,
  responseDataLabel,
  tokenTypeFilter,
  transactionColumns,
  transactionResultFilter,
  zeroRandomPageCostQueryHint,
};
