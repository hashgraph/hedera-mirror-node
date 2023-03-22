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

const MAX_INT32 = 2147483647;
const ONE_DAY_IN_NS = 86_400_000_000_000n;
const ZERO_UINT256 = '0x0000000000000000000000000000000000000000000000000000000000000000';
const AUTO_RENEW_PERIOD_MULTIPLE = BigInt(1e9);
const DECIMALS_IN_HBARS = 8;
const EVM_ADDRESS_LENGTH = 20;

// url query filer keys
const filterKeys = {
  ACCOUNT_BALANCE: 'account.balance',
  ACCOUNT_ID: 'account.id',
  ACCOUNT_PUBLICKEY: 'account.publickey',
  BALANCE: 'balance',
  BLOCK_HASH: 'block.hash',
  BLOCK_NUMBER: 'block.number',
  CONTRACTID: 'contractid',
  CONTRACT_ID: 'contract.id',
  CREDIT_TYPE: 'type',
  ENCODING: 'encoding',
  ENTITY_PUBLICKEY: 'publickey',
  FILE_ID: 'file.id',
  FROM: 'from',
  ID_OR_ALIAS_OR_EVM_ADDRESS: 'idOrAliasOrEvmAddress',
  INDEX: 'index',
  INTERNAL: 'internal',
  LIMIT: 'limit',
  NODE_ID: 'node.id',
  NONCE: 'nonce',
  ORDER: 'order',
  Q: 'q',
  RESULT: 'result',
  SCHEDULED: 'scheduled',
  SCHEDULEID: 'scheduleid',
  SCHEDULE_ID: 'schedule.id',
  SEQUENCE_NUMBER: 'sequencenumber',
  SERIAL_NUMBER: 'serialnumber',
  SPENDER_ID: 'spender.id',
  TIMESTAMP: 'timestamp',
  TOKENID: 'tokenid',
  TOKEN_ID: 'token.id',
  TOKEN_TYPE: 'type',
  TOPIC0: 'topic0',
  TOPIC1: 'topic1',
  TOPIC2: 'topic2',
  TOPIC3: 'topic3',
  TOPIC_ID: 'topic.id',
  TRANSACTION_INDEX: 'transaction.index',
  TRANSACTION_TYPE: 'transactiontype',
  HASH_OR_NUMBER: 'hashOrNumber',
  SLOT: 'slot',
};

const entityTypes = {
  ACCOUNT: 'ACCOUNT',
  CONTRACT: 'CONTRACT',
  FILE: 'FILE',
  TOKEN: 'TOKEN',
  TOPIC: 'TOPIC',
  SCHEDULE: 'SCHEDULE',
};

const EvmAddressType = {
  // evm address without shard and realm and with 0x prefix
  NO_SHARD_REALM: 0,
  // evm address with shard and realm as optionals
  OPTIONAL_SHARD_REALM: 1,
  // can be either a NO_SHARD_REALM or OPTIONAL_SHARD_REALM
  ANY: 2,
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
const requestStartTime = 'requestStartTime';
const responseContentType = 'responseContentType';
const responseDataLabel = 'responseData';

const orderFilterValues = {
  ASC: 'asc',
  DESC: 'desc',
};

// topic messages filter options
const characterEncoding = {
  BASE64: 'base64',
  UTF8: 'utf-8',
};

const networkSupplyQuery = {
  CIRCULATING: 'circulating',
  TOTALCOINS: 'totalcoins',
};

const networkSupplyCurrencyFormatType = {
  TINYBARS: 'TINYBARS', // output circulating or total coins in tinybars
  HBARS: 'HBARS', // output circulating or total coins in hbars (rounded to nearest integer)
  BOTH: 'BOTH', // default; output circulating or total coins in fractional hbars (with a decimal point between hbars and remaining tinybars)
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
  [networks.TESTNET]: 'hedera-testnet-streams-2023-01',
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

const queryParamOperatorPatterns = {
  gt: /^>$/,
  gte: /^>=$/,
  gtorgte: /^>[=]?$/,
  eq: /^=$/,
  lt: /^<$/,
  lte: /^<=$/,
  ltorlte: /^<[=]?$/,
  ne: /^!=$/,
};

export {
  AUTO_RENEW_PERIOD_MULTIPLE,
  DECIMALS_IN_HBARS,
  EVM_ADDRESS_LENGTH,
  MAX_INT32,
  ONE_DAY_IN_NS,
  ZERO_UINT256,
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
  networkSupplyCurrencyFormatType,
  networkSupplyQuery,
  orderFilterValues,
  queryParamOperators,
  queryParamOperatorPatterns,
  recordStreamPrefix,
  requestIdLabel,
  requestStartTime,
  responseContentType,
  responseDataLabel,
  tokenTypeFilter,
  transactionColumns,
  transactionResultFilter,
  zeroRandomPageCostQueryHint,
  EvmAddressType,
};
