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

const {EntityId} = require('../entityId');
const utils = require('../utils');

class TransactionModel {
  /**
   * Parses transaction table columns into object
   */
  constructor(dbRow) {
    this.charged_tx_fee = dbRow.charged_tx_fee;
    this.consensus_ns = dbRow.consensus_ns;
    this.entity_id = dbRow.entity_id;
    this.initial_balance = dbRow.initial_balance;
    this.max_fee = dbRow.max_fee;
    this.memo = dbRow.memo;
    this.node_account_id = dbRow.node_account_id;
    this.payer_account_id = dbRow.payer_account_id;
    this.result = dbRow.result;
    this.scheduled = dbRow.scheduled;
    this.transaction_hash = dbRow.transaction_hash;
    this.transaction_bytes = dbRow.transaction_bytes;
    this.type = dbRow.type;
    this.valid_duration_seconds = dbRow.valid_duration_seconds;
    this.valid_start_ns = dbRow.valid_start_ns;
  }

  static tableAlias = 't';
  static tableName = 'transaction';
  static transactionColumns = {
    CHARGED_TX_FEE: `charged_tx_fee`,
    CONSENSUS_NS: `consensus_ns`,
    ENTITY_ID: `entity_id`,
    INITIAL_BALANCE: `initial_balance`,
    MAX_FEE: `max_fee`,
    MEMO: `memo`,
    NODE_ACCOUNT_ID: `node_account_id`,
    PAYER_ACCOUNT_ID: `payer_account_id`,
    RESULT: `result`,
    SCHEDULED: `scheduled`,
    TRANSACTION_HASH: `transaction_hash`,
    TRANSACTION_BYTES: `transaction_bytes`,
    TYPE: `type`,
    VALID_DURATION_SECONDS: `valid_duration_seconds`,
    VALID_START_NS: `valid_start_ns`,
  };
  static transactionFullNameColumns = {
    CHARGED_TX_FEE: `${this.tableAlias}.${this.transactionColumns.CHARGED_TX_FEE}`,
    CONSENSUS_NS: `${this.tableAlias}.${this.transactionColumns.CONSENSUS_NS}`,
    ENTITY_ID: `${this.tableAlias}.${this.transactionColumns.ENTITY_ID}`,
    INITIAL_BALANCE: `${this.tableAlias}.${this.transactionColumns.INITIAL_BALANCE}`,
    MAX_FEE: `${this.tableAlias}.${this.transactionColumns.MAX_FEE}`,
    MEMO: `${this.tableAlias}.${this.transactionColumns.MEMO}`,
    NODE_ACCOUNT_ID: `${this.tableAlias}.${this.transactionColumns.NODE_ACCOUNT_ID}`,
    PAYER_ACCOUNT_ID: `${this.tableAlias}.${this.transactionColumns.PAYER_ACCOUNT_ID}`,
    RESULT: `${this.tableAlias}.${this.transactionColumns.RESULT}`,
    SCHEDULED: `${this.tableAlias}.${this.transactionColumns.SCHEDULED}`,
    TRANSACTION_HASH: `${this.tableAlias}.${this.transactionColumns.TRANSACTION_HASH}`,
    TRANSACTION_BYTES: `${this.tableAlias}.${this.transactionColumns.TRANSACTION_BYTES}`,
    TYPE: `${this.tableAlias}.${this.transactionColumns.TYPE}`,
    VALID_DURATION_SECONDS: `${this.tableAlias}.${this.transactionColumns.VALID_DURATION_SECONDS}`,
    VALID_START_NS: `${this.tableAlias}.${this.transactionColumns.VALID_START_NS}`,
  };
}

module.exports = TransactionModel;
