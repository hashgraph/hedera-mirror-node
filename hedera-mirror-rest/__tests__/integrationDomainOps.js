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

const math = require('mathjs');
const pgformat = require('pg-format');
const config = require('../config');
const EntityId = require('../entityId');

const NETWORK_FEE = 1;
const NODE_FEE = 2;
const SERVICE_FEE = 4;

let sqlConnection;

const setUp = async (testDataJson, sqlconn) => {
  sqlConnection = sqlconn;
  await loadAccounts(testDataJson.accounts);
  await loadAssessedCustomFees(testDataJson.assessedcustomfees);
  await loadBalances(testDataJson.balances);
  await loadCryptoTransfers(testDataJson.cryptotransfers);
  await loadCustomFees(testDataJson.customfees);
  await loadEntities(testDataJson.entities);
  await loadNfts(testDataJson.nfts);
  await loadSchedules(testDataJson.schedules);
  await loadTopicMessages(testDataJson.topicmessages);
  await loadTokens(testDataJson.tokens);
  await loadTokenAccounts(testDataJson.tokenaccounts);
  await loadTransactions(testDataJson.transactions);
  await loadTransactionSignatures(testDataJson.transactionsignatures);
};

const loadAccounts = async (accounts) => {
  if (accounts == null) {
    return;
  }

  for (const account of accounts) {
    await addAccount(account);
  }
};

const loadAssessedCustomFees = async (assessedCustomFees) => {
  if (assessedCustomFees == null) {
    return;
  }

  for (const assessedCustomFee of assessedCustomFees) {
    await addAssessedCustomFee(assessedCustomFee);
  }
};

const loadBalances = async (balances) => {
  if (balances == null) {
    return;
  }

  for (const balance of balances) {
    await setAccountBalance(balance);
  }
};

const loadCryptoTransfers = async (cryptoTransfers) => {
  if (cryptoTransfers == null) {
    return;
  }

  for (let i = 0; i < cryptoTransfers.length; ++i) {
    await addCryptoTransaction(cryptoTransfers[i]);
  }
};

const loadCustomFees = async (customFees) => {
  if (customFees == null) {
    return;
  }

  for (const customFee of customFees) {
    await addCustomFee(customFee);
  }
};

const loadEntities = async (entities) => {
  if (entities == null) {
    return;
  }

  for (const entity of entities) {
    await addEntity({}, entity);
  }
};

const loadNfts = async (nfts) => {
  if (nfts == null) {
    return;
  }

  for (const nft of nfts) {
    await addNft(nft);
  }
};

const loadSchedules = async (schedules) => {
  if (schedules == null) {
    return;
  }

  for (const schedule of schedules) {
    await addSchedule(schedule);
  }
};

const loadTransactionSignatures = async (transactionSignatures) => {
  if (transactionSignatures == null) {
    return;
  }

  for (const transactionSignature of transactionSignatures) {
    await addTransactionSignature(transactionSignature);
  }
};

const loadTokenAccounts = async (tokenAccounts) => {
  if (tokenAccounts == null) {
    return;
  }

  for (const tokenAccount of tokenAccounts) {
    await addTokenAccount(tokenAccount);
  }
};

const loadTokens = async (tokens) => {
  if (tokens == null) {
    return;
  }

  for (const token of tokens) {
    await addToken(token);
  }
};

const loadTransactions = async (transactions) => {
  if (transactions == null) {
    return;
  }

  for (let i = 0; i < transactions.length; ++i) {
    await addTransaction(transactions[i]);
  }
};

const loadTopicMessages = async (messages) => {
  if (messages == null) {
    return;
  }

  for (let i = 0; i < messages.length; ++i) {
    await addTopicMessage(messages[i]);
  }
};

const addEntity = async (defaults, entity) => {
  entity = {
    auto_renew_period: null,
    deleted: false,
    expiration_timestamp: null,
    key: null,
    memo: '',
    public_key: null,
    realm: 0,
    shard: 0,
    type: 1,
    ...defaults,
    ...entity,
  };

  await sqlConnection.query(
    `INSERT INTO entity (id, type, shard, realm, num, expiration_timestamp, deleted, public_key,
                         auto_renew_period, key, memo)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11);`,
    [
      EntityId.of(BigInt(entity.shard), BigInt(entity.realm), BigInt(entity.num)).getEncodedId(),
      entity.type,
      entity.shard,
      entity.realm,
      entity.num,
      entity.expiration_timestamp,
      entity.deleted,
      entity.public_key,
      entity.auto_renew_period,
      entity.key,
      entity.memo,
    ]
  );
};

const addAccount = async (account) => {
  await addEntity(
    {
      public_key: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
      type: 1,
    },
    account
  );
};

const addAssessedCustomFee = async (assessedCustomFee) => {
  const {amount, collector_account_id, consensus_timestamp, token_id} = assessedCustomFee;
  await sqlConnection.query(
    `insert into assessed_custom_fee (amount, collector_account_id, consensus_timestamp, token_id)
     values ($1, $2, $3, $4);`,
    [
      amount,
      EntityId.fromString(collector_account_id).getEncodedId(),
      consensus_timestamp.toString(),
      EntityId.fromString(token_id, 'tokenId', true).getEncodedId(),
    ]
  );
};

const addCustomFee = async (customFee) => {
  await sqlConnection.query(
    `insert into custom_fee (amount, amount_denominator, collector_account_id, created_timestamp, denominating_token_id, maximum_amount, minimum_amount, token_id)
     values ($1, $2, $3, $4, $5, $6, $7, $8);`,
    [
      customFee.amount || null,
      customFee.amount_denominator || null,
      EntityId.fromString(customFee.collector_account_id, 'collectorAccountId', true).getEncodedId(),
      customFee.created_timestamp.toString(),
      EntityId.fromString(customFee.denominating_token_id, 'denominatingTokenId', true).getEncodedId(),
      customFee.maximum_amount || null,
      customFee.minimum_amount || '0',
      EntityId.fromString(customFee.token_id).getEncodedId(),
    ]
  );
};

const setAccountBalance = async (balance) => {
  balance = {timestamp: 0, id: null, balance: 0, realm_num: 0, ...balance};
  const accountId = EntityId.of(BigInt(config.shard), BigInt(balance.realm_num), BigInt(balance.id)).getEncodedId();
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
      EntityId.of(
        BigInt(config.shard),
        BigInt(tokenBalance.token_realm),
        BigInt(tokenBalance.token_num)
      ).getEncodedId(),
    ]);
    await sqlConnection.query(
      pgformat(
        'INSERT INTO token_balance (consensus_timestamp, account_id, balance, token_id) VALUES %L',
        tokenBalances
      )
    );
  }
};

const addTransaction = async (transaction) => {
  transaction = {
    charged_tx_fee: NODE_FEE + NETWORK_FEE + SERVICE_FEE,
    max_fee: 33,
    non_fee_transfers: [],
    transfers: [],
    result: 22,
    scheduled: false,
    transaction_hash: 'hash',
    type: 14,
    valid_duration_seconds: 11,
    entity_id: null,
    transaction_bytes: 'bytes',
    ...transaction,
  };

  transaction.consensus_timestamp = math.bignumber(transaction.consensus_timestamp);
  if (transaction.valid_start_timestamp === undefined) {
    transaction.valid_start_timestamp = transaction.consensus_timestamp.minus(1);
  }
  const payerAccount = EntityId.fromString(transaction.payerAccountId);
  const nodeAccount = EntityId.fromString(transaction.nodeAccountId, 'nodeAccountId', true);
  const entityId = EntityId.fromString(transaction.entity_id, 'entity_id', true);
  await sqlConnection.query(
    `INSERT INTO transaction (consensus_ns, valid_start_ns, payer_account_id, node_account_id, result, type,
                              valid_duration_seconds, max_fee, charged_tx_fee, transaction_hash, scheduled, entity_id,
                              transaction_bytes)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13);`,
    [
      transaction.consensus_timestamp.toString(),
      transaction.valid_start_timestamp.toString(),
      payerAccount.getEncodedId(),
      nodeAccount.getEncodedId(),
      transaction.result,
      transaction.type,
      transaction.valid_duration_seconds,
      transaction.max_fee,
      transaction.charged_tx_fee,
      transaction.transaction_hash,
      transaction.scheduled,
      entityId.getEncodedId(),
      transaction.transaction_bytes,
    ]
  );
  await insertTransfers('crypto_transfer', transaction.consensus_timestamp, transaction.transfers);
  await insertTransfers('non_fee_transfer', transaction.consensus_timestamp, transaction.non_fee_transfers);
  await insertTokenTransfers(transaction.consensus_timestamp, transaction.token_transfer_list);
  await insertNftTransfers(transaction.consensus_timestamp, transaction.nft_transfer_list);
};

const insertTransfers = async (tableName, consensusTimestamp, transfers) => {
  for (let i = 0; i < transfers.length; ++i) {
    const transfer = transfers[i];
    await sqlConnection.query(
      `INSERT INTO ${tableName} (consensus_timestamp, amount, entity_id)
       VALUES ($1, $2, $3);`,
      [consensusTimestamp.toString(), transfer.amount, EntityId.fromString(transfer.account).getEncodedId()]
    );
  }
};

const insertTokenTransfers = async (consensusTimestamp, transfers) => {
  if (!transfers || transfers.length === 0) {
    return;
  }

  const tokenTransfers = transfers.map((transfer) => {
    return [
      `${consensusTimestamp}`,
      EntityId.fromString(transfer.token_id).getEncodedId(),
      EntityId.fromString(transfer.account).getEncodedId(),
      transfer.amount,
    ];
  });

  await sqlConnection.query(
    pgformat('INSERT INTO token_transfer (consensus_timestamp, token_id, account_id, amount) VALUES %L', tokenTransfers)
  );
};

const insertNftTransfers = async (consensusTimestamp, nftTransferList) => {
  if (!nftTransferList || nftTransferList.length === 0) {
    return;
  }

  const nftTransfers = nftTransferList.map((transfer) => {
    return [
      `${consensusTimestamp}`,
      EntityId.fromString(transfer.receiver_account_id, '', true).getEncodedId(),
      EntityId.fromString(transfer.sender_account_id, '', true).getEncodedId(),
      transfer.serial_number,
      EntityId.fromString(transfer.token_id).getEncodedId().toString(),
    ];
  });

  await sqlConnection.query(
    pgformat(
      'INSERT INTO nft_transfer (consensus_timestamp, receiver_account_id, sender_account_id, serial_number, token_id) VALUES %L',
      nftTransfers
    )
  );
};

const addCryptoTransaction = async (cryptoTransfer) => {
  if (!('senderAccountId' in cryptoTransfer)) {
    cryptoTransfer.senderAccountId = cryptoTransfer.payerAccountId;
  }
  if (!('amount' in cryptoTransfer)) {
    cryptoTransfer.amount = NODE_FEE;
  }
  if (!('recipientAccountId' in cryptoTransfer)) {
    cryptoTransfer.recipientAccountId = cryptoTransfer.nodeAccountId;
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

const addTopicMessage = async (message) => {
  message = {
    realm_num: 0,
    message: 'message', // Base64 encoding: bWVzc2FnZQ==
    running_hash: 'running_hash', // Base64 encoding: cnVubmluZ19oYXNo
    running_hash_version: 2,
    ...message,
  };

  await sqlConnection.query(
    `INSERT INTO topic_message (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number,
                                running_hash_version)
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

const addSchedule = async (schedule) => {
  schedule = {
    creator_account_id: '0.0.1024',
    payer_account_id: '0.0.1024',
    transaction_body: Buffer.from([1, 1, 2, 2, 3, 3]),
    ...schedule,
  };

  await sqlConnection.query(
    `INSERT INTO schedule (consensus_timestamp,
                           creator_account_id,
                           executed_timestamp,
                           payer_account_id,
                           schedule_id,
                           transaction_body)
     VALUES ($1, $2, $3, $4, $5, $6)`,
    [
      schedule.consensus_timestamp,
      EntityId.fromString(schedule.creator_account_id).getEncodedId().toString(),
      schedule.executed_timestamp,
      EntityId.fromString(schedule.payer_account_id).getEncodedId().toString(),
      EntityId.fromString(schedule.schedule_id).getEncodedId().toString(),
      schedule.transaction_body,
    ]
  );
};

const addTransactionSignature = async (transactionSignature) => {
  await sqlConnection.query(
    `INSERT INTO transaction_signature (consensus_timestamp,
                                        public_key_prefix,
                                        entity_id,
                                        signature)
     VALUES ($1, $2, $3, $4)`,
    [
      transactionSignature.consensus_timestamp,
      Buffer.from(transactionSignature.public_key_prefix),
      EntityId.fromString(transactionSignature.entity_id, '', true).getEncodedId().toString(),
      Buffer.from(transactionSignature.signature),
    ]
  );
};

const addToken = async (token) => {
  // create token object and insert into 'token' table
  token = {
    token_id: '0.0.0',
    created_timestamp: 0,
    decimals: 1000,
    fee_schedule_key: null,
    fee_schedule_key_ed25519_hex: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    freeze_default: false,
    freeze_key_ed25519_hex: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    initial_supply: 1000000,
    kyc_key: null,
    kyc_key_ed25519_hex: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    max_supply: '9223372036854775807', // max long, cast to string to avoid error from JavaScript Number cast
    name: 'Token name',
    supply_key: null,
    supply_key_ed25519_hex: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    supply_type: 'INFINITE',
    symbol: 'YBTJBOAZ',
    total_supply: 1000000,
    treasury_account_id: '0.0.98',
    wipe_key: null,
    wipe_key_ed25519_hex: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    ...token,
  };

  if (!token.modified_timestamp) {
    token.modified_timestamp = token.created_timestamp;
  }

  await sqlConnection.query(
    `INSERT INTO token (token_id,
                        created_timestamp,
                        decimals,
                        fee_schedule_key,
                        fee_schedule_key_ed25519_hex,
                        freeze_default,
                        freeze_key_ed25519_hex,
                        initial_supply,
                        kyc_key,
                        kyc_key_ed25519_hex,
                        max_supply,
                        modified_timestamp,
                        name,
                        supply_key,
                        supply_key_ed25519_hex,
                        supply_type,
                        symbol,
                        total_supply,
                        treasury_account_id,
                        type,
                        wipe_key,
                        wipe_key_ed25519_hex)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21, $22);`,
    [
      EntityId.fromString(token.token_id).getEncodedId(),
      token.created_timestamp,
      token.decimals,
      token.fee_schedule_key,
      token.fee_schedule_key_ed25519_hex,
      token.freeze_default,
      token.freeze_key_ed25519_hex,
      token.initial_supply,
      token.kyc_key,
      token.kyc_key_ed25519_hex,
      token.max_supply,
      token.modified_timestamp,
      token.name,
      token.supply_key,
      token.supply_key_ed25519_hex,
      token.supply_type,
      token.symbol,
      token.total_supply,
      EntityId.fromString(token.treasury_account_id).getEncodedId(),
      token.type,
      token.wipe_key,
      token.wipe_key_ed25519_hex,
    ]
  );

  if (!token.custom_fees) {
    await addCustomFee({
      created_timestamp: token.created_timestamp,
      token_id: token.token_id,
    });
  } else {
    await loadCustomFees(token.custom_fees);
  }
};

const addTokenAccount = async (tokenAccount) => {
  // create token account object
  tokenAccount = {
    account_id: '0.0.0',
    associated: true,
    created_timestamp: 0,
    freeze_status: 0,
    kyc_status: 0,
    modified_timestamp: 0,
    token_id: '0.0.0',
    ...tokenAccount,
  };

  if (!tokenAccount.modified_timestamp) {
    tokenAccount.modified_timestamp = tokenAccount.created_timestamp;
  }

  await sqlConnection.query(
    `INSERT INTO token_account (account_id, associated, created_timestamp, freeze_status, kyc_status,
                                modified_timestamp, token_id)
     VALUES ($1, $2, $3, $4, $5, $6, $7);`,
    [
      EntityId.fromString(tokenAccount.account_id).getEncodedId(),
      tokenAccount.associated,
      tokenAccount.created_timestamp,
      tokenAccount.freeze_status,
      tokenAccount.kyc_status,
      tokenAccount.modified_timestamp,
      EntityId.fromString(tokenAccount.token_id).getEncodedId(),
    ]
  );
};

const addNft = async (nft) => {
  // create nft account object
  nft = {
    account_id: '0.0.0',
    created_timestamp: 0,
    deleted: false,
    metadata: '\\x',
    modified_timestamp: 0,
    serial_number: 0,
    token_id: '0.0.0',
    ...nft,
  };

  if (!nft.modified_timestamp) {
    nft.modified_timestamp = nft.created_timestamp;
  }

  await sqlConnection.query(
    `INSERT INTO nft (account_id, created_timestamp, deleted, modified_timestamp, metadata, serial_number, token_id)
     VALUES ($1, $2, $3, $4, $5, $6, $7);`,
    [
      EntityId.fromString(nft.account_id).getEncodedId(),
      nft.created_timestamp,
      nft.deleted,
      nft.modified_timestamp,
      nft.metadata,
      nft.serial_number,
      EntityId.fromString(nft.token_id).getEncodedId(),
    ]
  );
};

module.exports = {
  addAccount,
  addCryptoTransaction,
  addNft,
  addToken,
  setAccountBalance,
  setUp,
};
