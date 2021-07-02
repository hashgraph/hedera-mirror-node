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

const constants = require('../constants');

class Transaction {
  /**
   * Parses transaction table columns into object
   */
  constructor(transaction) {
    this.chargedTxFee = transaction.charged_tx_fee;
    this.consensusNs = transaction.consensus_ns;
    this.entityId = transaction.entity_id;
    this.initialBalance = transaction.initial_balance;
    this.maxFee = transaction.max_fee;
    this.memo = transaction.memo;
    this.nodeAccountId = transaction.node_account_id;
    this.payerAccountId = transaction.payer_account_id;
    this.result = transaction.result;
    this.scheduled = transaction.scheduled;
    this.transactionHash = transaction.transaction_hash;
    this.transactionBytes = transaction.transaction_bytes;
    this.type = transaction.type;
    this.validDurationSeconds = transaction.valid_duration_seconds;
    this.validStartNs = transaction.valid_start_ns;
  }

  static tableAlias = 't';
  static tableName = 'transaction';

  static CHARGED_TX_FEE = `charged_tx_fee`;
  static CHARGED_TX_FEE_FULL_NAME = `${this.tableAlias}.${this.CHARGED_TX_FEE}`;
  static CONSENSUS_NS = `consensus_ns`;
  static CONSENSUS_NS_FULL_NAME = `${this.tableAlias}.${this.CONSENSUS_NS}`;
  static ENTITY_ID = `entity_id`;
  static ENTITY_ID_FULL_NAME = `${this.tableAlias}.${this.ENTITY_ID}`;
  static INITIAL_BALANCE = `initial_balance`;
  static INITIAL_BALANCE_FULL_NAME = `${this.tableAlias}.${this.INITIAL_BALANCE}`;
  static MAX_FEE = `max_fee`;
  static MAX_FEE_FULL_NAME = `${this.tableAlias}.${this.MAX_FEE}`;
  static MEMO = `memo`;
  static MEMO_FULL_NAME = `${this.tableAlias}.${this.MEMO}`;
  static NODE_ACCOUNT_ID = `node_account_id`;
  static NODE_ACCOUNT_ID_FULL_NAME = `${this.tableAlias}.${this.NODE_ACCOUNT_ID}`;
  static PAYER_ACCOUNT_ID = `payer_account_id`;
  static PAYER_ACCOUNT_ID_FULL_NAME = `${this.tableAlias}.${this.PAYER_ACCOUNT_ID}`;
  static RESULT = `result`;
  static RESULT_FULL_NAME = `${this.tableAlias}.${this.RESULT}`;
  static SCHEDULED = `scheduled`;
  static SCHEDULED_FULL_NAME = `${this.tableAlias}.${this.SCHEDULED}`;
  static TRANSACTION_HASH = `transaction_hash`;
  static TRANSACTION_HASH_FULL_NAME = `${this.tableAlias}.${this.TRANSACTION_HASH}`;
  static TRANSACTION_BYTES = `transaction_bytes`;
  static TRANSACTION_BYTES_FULL_NAME = `${this.tableAlias}.${this.TRANSACTION_BYTES}`;
  static TYPE = `type`;
  static TYPE_FULL_NAME = `${this.tableAlias}.${this.TYPE}`;
  static VALID_DURATION_SECONDS = `valid_duration_seconds`;
  static VALID_DURATION_SECONDS_FULL_NAME = `${this.tableAlias}.${this.VALID_DURATION_SECONDS}`;
  static VALID_START_NS = `valid_start_ns`;
  static VALID_START_NS_FULL_NAME = `${this.tableAlias}.${this.VALID_START_NS}`;

  static FILTER_MAP = {
    [constants.filterKeys.TIMESTAMP]: Transaction.CONSENSUS_NS_FULL_NAME,
  };
}

module.exports = Transaction;
