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
const pgformat = require('pg-format');
const config = require('../config');
const EntityId = require('../entityId');

const NETWORK_FEE = 1;
const NODE_FEE = 2;
const SERVICE_FEE = 4;

let sqlConnection;

const setUp = async function (testDataJson, sqlconn) {
  sqlConnection = sqlconn;
  await loadAccounts(testDataJson.accounts);
  await loadBalances(testDataJson.balances);
  await loadCryptoTransfers(testDataJson.cryptotransfers);
  await loadTokenTransfers(testDataJson.tokentransfers);
  await loadTransactions(testDataJson.transactions);
  await loadTopicMessages(testDataJson.topicmessages);
  await loadTokens(testDataJson.tokens);
  await loadTokenAccounts(testDataJson.tokenaccounts);
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

  for (const balance of balances) {
    await setAccountBalance(balance);
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

const loadTokenAccounts = async function (tokenAccounts) {
  if (tokenAccounts == null) {
    return;
  }

  for (const tokenAccount of tokenAccounts) {
    await addTokenAccount(tokenAccount);
  }
};

const loadTokens = async function (tokens) {
  if (tokens == null) {
    return;
  }

  for (const token of tokens) {
    await addToken(token);
  }
};

const loadTokenTransfers = async function (tokenTransfers) {
  if (tokenTransfers == null) {
    return;
  }

  for (const tokenTransfer of tokenTransfers) {
    await addTokenTransferTransaction(tokenTransfer);
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
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10);`,
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

const setAccountBalance = async function (balance) {
  balance = Object.assign({timestamp: 0, id: null, balance: 0, realm_num: 0}, balance);
  const accountId = EntityId.of(config.shard, balance.realm_num, balance.id).getEncodedId().toString();
  await sqlConnection.query(
    `INSERT INTO account_balance (consensus_timestamp, account_id, balance)
    VALUES ($1, $2, $3);`,
    [balance.timestamp, accountId, balance.balance]
  );

  if (balance.tokens) {
    const tokenBalances = balance.tokens.map((tokenBalance) => [
      balance.timestamp,
      accountId,
      tokenBalance.balance,
      EntityId.of(config.shard, tokenBalance.token_realm, tokenBalance.token_num).getEncodedId().toString(),
    ]);
    await sqlConnection.query(
      pgformat(
        'INSERT INTO token_balance (consensus_timestamp, account_id, balance, token_id) VALUES %L',
        tokenBalances
      )
    );
  }
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

  const payerAccount = EntityId.fromString(transaction.payerAccountId);
  const nodeAccount = EntityId.fromString(transaction.nodeAccountId);
  await sqlConnection.query(
    `INSERT INTO transaction (
      consensus_ns, valid_start_ns, payer_account_id, node_account_id,
      result, type, valid_duration_seconds, max_fee, charged_tx_fee, transaction_hash)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10);`,
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
  await insertTransfers('crypto_transfer', transaction.consensus_timestamp, transaction.transfers);
  await insertTransfers('non_fee_transfer', transaction.consensus_timestamp, transaction.non_fee_transfers);
  await insertTokenTransfers(transaction.consensus_timestamp, transaction.token_transfer_list);
};

const insertTransfers = async function (tableName, consensusTimestamp, transfers) {
  for (let i = 0; i < transfers.length; ++i) {
    const transfer = transfers[i];
    await sqlConnection.query(
      `INSERT INTO ${tableName} (consensus_timestamp, amount, entity_id) VALUES ($1, $2, $3);`,
      [consensusTimestamp.toString(), transfer.amount, EntityId.fromString(transfer.account).getEncodedId()]
    );
  }
};

const insertTokenTransfers = async function (consensusTimestamp, transfers) {
  if (!transfers || transfers.length === 0) {
    return;
  }

  const tokenTransfers = transfers.map((transfer) => {
    return [
      `${consensusTimestamp}`,
      EntityId.fromString(transfer.token_id).getEncodedId().toString(),
      EntityId.fromString(transfer.account).getEncodedId().toString(),
      transfer.amount,
    ];
  });

  await sqlConnection.query(
    pgformat('INSERT INTO token_transfer (consensus_timestamp, token_id, account_id, amount) VALUES %L', tokenTransfers)
  );
};

const addCryptoTransaction = async function (cryptoTransfer) {
  if (!('senderAccountId' in cryptoTransfer)) {
    cryptoTransfer.senderAccountId = cryptoTransfer.payerAccountId;
  }

  if (!('transfers' in cryptoTransfer)) {
    cryptoTransfer.transfers = [
      {account: cryptoTransfer.senderAccountId, amount: -NETWORK_FEE - cryptoTransfer.amount},
      {account: cryptoTransfer.recipientAccountId, amount: cryptoTransfer.amount},
      {account: cryptoTransfer.treasuryAccountId, amount: NETWORK_FEE},
    ];
  }
  await addTransaction(cryptoTransfer);
};

const addTokenTransferTransaction = async function (tokenTransfer) {
  // transaction fees
  tokenTransfer.transfers = [
    {account: tokenTransfer.payerAccountId, amount: -NETWORK_FEE - NODE_FEE},
    {account: tokenTransfer.treasuryAccountId, amount: NETWORK_FEE},
    {account: tokenTransfer.nodeAccountId, amount: NODE_FEE},
  ];
  tokenTransfer.type = 30; // TOKENTRANSFER
  await addTransaction(tokenTransfer);
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
    VALUES ($1, $2, $3, $4, $5, $6, $7);`,
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

const addToken = async function (token) {
  // create token object
  token = Object.assign(
    {
      token_id: '0.0.0',
      created_timestamp: 0,
      decimals: 1000,
      freeze_default: false,
      freeze_key_ed25519_hex: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
      initial_supply: 1000000,
      kyc_key: null,
      kyc_key_ed25519_hex: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
      modified_timestamp: 0,
      name: 'Token name',
      supply_key: null,
      supply_key_ed25519_hex: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
      symbol: 'YBTJBOAZ',
      total_supply: 1000000,
      treasury_account_id: '0.0.98',
      wipe_key: null,
      wipe_key_ed25519_hex: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    },
    token
  );

  await sqlConnection.query(
    `INSERT INTO token (
      token_id, created_timestamp, decimals, freeze_default, freeze_key_ed25519_hex, initial_supply, kyc_key, kyc_key_ed25519_hex,
      modified_timestamp, name, supply_key, supply_key_ed25519_hex, symbol, total_supply, treasury_account_id, wipe_key, wipe_key_ed25519_hex)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17);`,
    [
      EntityId.fromString(token.token_id).getEncodedId(),
      token.created_timestamp,
      token.decimals,
      token.freeze_default,
      token.freeze_key_ed25519_hex,
      token.initial_supply,
      token.kyc_key,
      token.kyc_key_ed25519_hex,
      token.modified_timestamp,
      token.name,
      token.supply_key,
      token.supply_key_ed25519_hex,
      token.symbol,
      token.total_supply,
      EntityId.fromString(token.treasury_account_id).getEncodedId(),
      token.wipe_key,
      token.wipe_key_ed25519_hex,
    ]
  );
};

const addTokenAccount = async function (tokenAccount) {
  // create token account object
  tokenAccount = Object.assign(
    {
      account_id: 0,
      associated: true,
      created_timestamp: 0,
      freeze_status: 0,
      kyc_status: 0,
      modified_timestamp: 0,
      token_id: 0,
    },
    tokenAccount
  );

  await sqlConnection.query(
    `INSERT INTO token_account (
      account_id, associated, created_timestamp, freeze_status, kyc_status, modified_timestamp, token_id)
    VALUES ($1, $2, $3, $4, $5, $6, $7);`,
    [
      tokenAccount.account_id,
      tokenAccount.associated,
      tokenAccount.created_timestamp,
      tokenAccount.freeze_status,
      tokenAccount.kyc_status,
      tokenAccount.modified_timestamp,
      tokenAccount.token_id,
    ]
  );
};

module.exports = {
  addAccount,
  addCryptoTransaction,
  setAccountBalance,
  setUp,
};
