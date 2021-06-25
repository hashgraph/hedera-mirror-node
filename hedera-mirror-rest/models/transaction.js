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

class Transaction {
  /**
   * Parses transaction table columns into object
   */
  constructor(
    chargedTxFee,
    consensusNs,
    entityId,
    initialBalance,
    maxFee,
    memo,
    nodeAccountId,
    payerAccountId,
    result,
    scheduled,
    transactionHash,
    transactionBytes,
    type,
    validDurationSeconds,
    validStartNs
  ) {
    this.charged_tx_fee = consensusNs;
    this.consensus_ns = consensusNs;
    this.entity_id = EntityId.fromEncodedId(entityId).toString();
    this.initial_balance = initialBalance;
    this.max_fee = maxFee;
    this.memo = utils.encodeKey(memo); // base64 encode
    this.node_account_id = nodeAccountId;
    this.payer_account_id = EntityId.fromEncodedId(payerAccountId).toString();
    this.result = result;
    this.scheduled = scheduled;
    this.transaction_hash = transactionHash;
    this.transaction_bytes = transactionBytes;
    this.type = type;
    this.valid_duration_seconds = validDurationSeconds;
    this.valid_start_ns = validStartNs;
  }

  static tableAlias = 't';
  static tableName = 'transaction';
  static transactionQueryColumns = {
    CHARGED_TX_FEE: `${this.tableAlias}.charged_tx_fee`,
    CONSENSUS_NS: `${this.tableAlias}.consensus_ns`,
    ENTITY_ID: `${this.tableAlias}.entity_id`,
    INITIAL_BALANCE: `${this.tableAlias}.initial_balance`,
    MAX_FEE: `${this.tableAlias}.max_fee`,
    MEMO: `${this.tableAlias}.memo`,
    NODE_ACCOUNT_ID: `${this.tableAlias}.node_account_id`,
    PAYER_ACCOUNT_ID: `${this.tableAlias}.payer_account_id`,
    RESULT: `${this.tableAlias}.result`,
    SCHEDULED: `${this.tableAlias}.scheduled`,
    TRANSACTION_HASH: `${this.tableAlias}.transaction_hash`,
    TRANSACTION_BYTES: `${this.tableAlias}.transaction_bytes`,
    TYPE: `${this.tableAlias}.type`,
    VALID_DURATION_SECONDS: `${this.tableAlias}.valid_duration_seconds`,
    VALID_START_NS: `${this.tableAlias}.valid_start_ns`,
  };
}

module.exports = Transaction;
