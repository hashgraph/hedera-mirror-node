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

// url query filer keys
const filterKeys = {
  ACCOUNT_ID: 'account.id',
  ACCOUNT_BALANCE: 'account.balance',
  ACCOUNT_PUBLICKEY: 'account.publickey',
  CREDIT_TYPE: 'type',
  ENCODING: 'encoding',
  ENTITY_PUBLICKEY: 'publickey',
  LIMIT: 'limit',
  ORDER: 'order',
  RESULT: 'result',
  SCHEDULED: 'scheduled',
  SCHEDULEID: 'scheduleid',
  SCHEDULE_ID: 'schedule.id',
  SEQUENCE_NUMBER: 'sequencenumber',
  TIMESTAMP: 'timestamp',
  TOKENID: 'tokenid',
  TOKEN_ID: 'token.id',
  TRANSACTION_TYPE: 'transactiontype',
};

// sql table columns
const entityColumns = {
  ENTITY_NUM: 'num',
  ENTITY_REALM: 'realm',
  ENTITY_SHARD: 'shard',
  PUBLIC_KEY: 'public_key',
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

module.exports = {
  characterEncoding,
  cloudProviders,
  cryptoTransferType,
  defaultBucketNames,
  defaultCloudProviderEndpoints,
  entityColumns,
  filterKeys,
  networks,
  orderFilterValues,
  recordStreamPrefix,
  requestIdLabel,
  responseDataLabel,
  transactionColumns,
  transactionResultFilter,
};
