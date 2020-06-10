/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

const math = require('mathjs');
const EntityId = require('../entityId');

const NETWORK_FEE = 1;
const NODE_FEE = 2;
const SERVICE_FEE = 4;

let sqlConnection;
let accountEntityIds;

const setUp = async function (testDataJson, sqlconn) {
  accountEntityIds = {};
  sqlConnection = sqlconn;
  await loadAccounts(testDataJson['accounts']);
  await loadBalances(testDataJson['balances']);
  await loadCryptoTransfers(testDataJson['cryptotransfers']);
  await loadTransactions(testDataJson['transactions']);
  await loadTopicMessages(testDataJson['topicmessages']);
};

const loadAccounts = async function (accounts) {
  if (accounts == null) {
    return;
  }

  for (let i = 0; i < accounts.length; ++i) {
    await addAccount(accounts[i]);
  }
};

const loadBalances = async function (balances) {
  if (balances == null) {
    return;
  }

  for (let i = 0; i < balances.length; ++i) {
    await setAccountBalance(balances[i]);
  }
};

const loadCryptoTransfers = async function (cryptoTransfers) {
  if (cryptoTransfers == null) {
    return;
  }

  for (let i = 0; i < cryptoTransfers.length; ++i) {
    await addCryptoTransaction(cryptoTransfers[i]);
  }
};

const loadTransactions = async function (transactions) {
  if (transactions == null) {
    return;
  }

  for (let i = 0; i < transactions.length; ++i) {
    await addTransaction(transactions[i]);
  }
};

const loadTopicMessages = async function (messages) {
  if (messages == null) {
    return;
  }

  for (let i = 0; i < messages.length; ++i) {
    await addTopicMessage(messages[i]);
  }
};

const addAccount = async function (account) {
  account = Object.assign(
    {
      entity_shard: 0,
      entity_realm: 0,
      exp_time_ns: null,
      public_key: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
      entity_type: 1,
      auto_renew_period: null,
      key: null,
    },
    account
  );

  await sqlConnection.query(
    `INSERT INTO t_entities (
      id, fk_entity_type_id, entity_shard, entity_realm, entity_num, exp_time_ns, deleted, ed25519_public_key_hex,
      auto_renew_period, key)
    VALUES (\$1, \$2, \$3, \$4, \$5, \$6, \$7, \$8, \$9, \$10);`,
    [
      EntityId.of(account.entity_shard, account.entity_realm, account.entity_num).getEncodedId(),
      account.entity_type,
      account.entity_shard,
      account.entity_realm,
      account.entity_num,
      account.exp_time_ns,
      false,
      account.public_key,
      account.auto_renew_period,
      account.key,
    ]
  );
};

const setAccountBalance = async function (account) {
  account = Object.assign({timestamp: 0, realm_num: 0, id: null, balance: 0}, account);
  await sqlConnection.query(
    `INSERT INTO account_balances (consensus_timestamp, account_realm_num, account_num, balance)
    VALUES (\$1, \$2, \$3, \$4);`,
    [account.timestamp, account.realm_num, account.id, account.balance]
  );
};

const addTransaction = async function (transaction) {
  transaction = Object.assign(
    {
      type: 14,
      result: 22,
      max_fee: 33,
      valid_duration_seconds: 11,
      transfers: [],
      non_fee_transfers: [],
      charged_tx_fee: NODE_FEE + NETWORK_FEE + SERVICE_FEE,
      transaction_hash: 'hash',
    },
    transaction
  );

  transaction.consensus_timestamp = math.bignumber(transaction.consensus_timestamp);

  let payerAccount = EntityId.fromString(transaction.payerAccountId);
  let nodeAccount = EntityId.fromString(transaction.nodeAccountId);
  await sqlConnection.query(
    `INSERT INTO t_transactions (
      consensus_ns, valid_start_ns, payer_account_id, node_account_id,
      result, type, valid_duration_seconds, max_fee, charged_tx_fee, transaction_hash)
    VALUES (\$1, \$2, \$3, \$4, \$5, \$6, \$7, \$8, \$9, \$10);`,
    [
      transaction.consensus_timestamp.toString(),
      transaction.consensus_timestamp.minus(1).toString(),
      payerAccount.getEncodedId(),
      nodeAccount.getEncodedId(),
      transaction.result,
      transaction.type,
      transaction.valid_duration_seconds,
      transaction.max_fee,
      transaction.charged_tx_fee,
      transaction.transaction_hash,
    ]
  );
  await insertTransfers('t_cryptotransferlists', transaction.consensus_timestamp, transaction.transfers);
  await insertTransfers('non_fee_transfers', transaction.consensus_timestamp, transaction.non_fee_transfers);
};

const insertTransfers = async function (tableName, consensusTimestamp, transfers) {
  for (let i = 0; i < transfers.length; ++i) {
    let transfer = transfers[i];
    let account = EntityId.fromString(transfer.account);
    await sqlConnection.query(
      `INSERT INTO ${tableName} (consensus_timestamp, amount, realm_num, entity_num) VALUES (\$1, \$2, \$3, \$4);`,
      [consensusTimestamp.toString(), transfer.amount, account.realm, account.num]
    );
  }
};

const addCryptoTransaction = async function (cryptoTransfer) {
  if (!('senderAccountId' in cryptoTransfer)) {
    cryptoTransfer.senderAccountId = cryptoTransfer.payerAccountId;
  }

  if (!('transfers' in cryptoTransfer)) {
    cryptoTransfer['transfers'] = [
      {account: cryptoTransfer.senderAccountId, amount: -NETWORK_FEE - cryptoTransfer.amount},
      {account: cryptoTransfer.recipientAccountId, amount: cryptoTransfer.amount},
      {account: cryptoTransfer.treasuryAccountId, amount: NETWORK_FEE},
    ];
  }
  await addTransaction(cryptoTransfer);
};

const addTopicMessage = async function (message) {
  message = Object.assign(
    {
      realm_num: 0,
      message: 'message', // Base64 encoding: bWVzc2FnZQ==
      running_hash: 'running_hash', // Base64 encoding: cnVubmluZ19oYXNo
      running_hash_version: 2,
    },
    message
  );

  await sqlConnection.query(
    `INSERT INTO topic_message (
       consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number, running_hash_version)
    VALUES (\$1, \$2, \$3, \$4, \$5, \$6, \$7);`,
    [
      message.timestamp,
      message.realm_num,
      message.topic_num,
      message.message,
      message.running_hash,
      message.seq_num,
      message.running_hash_version,
    ]
  );
};

module.exports = {
  addAccount: addAccount,
  addCryptoTransaction: addCryptoTransaction,
  setAccountBalance: setAccountBalance,
  setUp: setUp,
};
