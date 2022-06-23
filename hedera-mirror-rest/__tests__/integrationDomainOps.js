/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

const _ = require('lodash');
const math = require('mathjs');
const pgformat = require('pg-format');

const base32 = require('../base32');
const config = require('../config');
const constants = require('../constants');
const EntityId = require('../entityId');
const testUtils = require('./testutils');

const NETWORK_FEE = 1n;
const NODE_FEE = 2n;
const SERVICE_FEE = 4n;
const DEFAULT_NODE_ID = '3';
const DEFAULT_TREASURY_ID = '98';

let sqlConnection;

const defaultFileData = '\\x97c1fc0a6ed5551bc831571325e9bdb365d06803100dc20648640ba24ce69750';

const setUp = async (testDataJson, sqlconn) => {
  sqlConnection = sqlconn;
  await loadAccounts(testDataJson.accounts);
  await loadAddressBooks(testDataJson.addressbooks);
  await loadAddressBookEntries(testDataJson.addressbookentries);
  await loadAddressBookServiceEndpoints(testDataJson.addressbookserviceendpoints);
  await loadAssessedCustomFees(testDataJson.assessedcustomfees);
  await loadBalances(testDataJson.balances);
  await loadCryptoTransfers(testDataJson.cryptotransfers);
  await loadContracts(testDataJson.contracts);
  await loadContractLogs(testDataJson.contractlogs);
  await loadContractResults(testDataJson.contractresults);
  await loadContractStateChanges(testDataJson.contractStateChanges);
  await loadCryptoAllowances(testDataJson.cryptoAllowances);
  await loadCustomFees(testDataJson.customfees);
  await loadEntities(testDataJson.entities);
  await loadEthereumTransactions(testDataJson.ethereumtransactions);
  await loadFileData(testDataJson.filedata);
  await loadNfts(testDataJson.nfts);
  await loadNodeStakes(testDataJson.nodestakes);
  await loadRecordFiles(testDataJson.recordFiles);
  await loadSchedules(testDataJson.schedules);
  await loadTopicMessages(testDataJson.topicmessages);
  await loadTokens(testDataJson.tokens);
  await loadTokenAccounts(testDataJson.tokenaccounts);
  await loadTokenAllowances(testDataJson.tokenAllowances);
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

const loadAddressBooks = async (addressBooks) => {
  if (addressBooks == null) {
    return;
  }

  for (const addressBook of addressBooks) {
    await addAddressBook(addressBook);
  }
};

const loadAddressBookEntries = async (entries) => {
  if (entries == null) {
    return;
  }

  for (const addressBookEntry of entries) {
    await addAddressBookEntry(addressBookEntry);
  }
};

const loadAddressBookServiceEndpoints = async (endpoints) => {
  if (endpoints == null) {
    return;
  }

  for (const endpoint of endpoints) {
    await addAddressBookServiceEndpoint(endpoint);
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

const loadContracts = async (contracts) => {
  if (contracts == null) {
    return;
  }

  for (const contract of contracts) {
    await addContract(contract);
  }
};

const loadContractResults = async (contractResults) => {
  if (contractResults == null) {
    return;
  }

  for (const contractResult of contractResults) {
    await addContractResult(contractResult);
  }
};

const loadContractLogs = async (contractLogs) => {
  if (contractLogs == null) {
    return;
  }

  for (const contractLog of contractLogs) {
    await addContractLog(contractLog);
  }
};

const loadContractStateChanges = async (contractStateChanges) => {
  if (contractStateChanges == null) {
    return;
  }

  for (const contractStateChange of contractStateChanges) {
    await addContractStateChange(contractStateChange);
  }
};

const loadCryptoAllowances = async (cryptoAllowances) => {
  if (cryptoAllowances == null) {
    return;
  }

  for (const cryptoAllowance of cryptoAllowances) {
    await addCryptoAllowance(cryptoAllowance);
  }
};

const loadCryptoTransfers = async (cryptoTransfers) => {
  if (cryptoTransfers == null) {
    return;
  }

  for (const cryptoTransfer of cryptoTransfers) {
    await addCryptoTransaction(cryptoTransfer);
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

const loadEthereumTransactions = async (ethereumTransactions) => {
  if (ethereumTransactions == null) {
    return;
  }

  for (const ethereumTransaction of ethereumTransactions) {
    await addEthereumTransaction(ethereumTransaction);
  }
};

const loadFileData = async (fileData) => {
  if (fileData == null) {
    return;
  }

  for (const data of fileData) {
    await addFileData(data);
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

const loadNodeStakes = async (nodeStakes) => {
  if (nodeStakes == null) {
    return;
  }

  for (const nodeStake of nodeStakes) {
    await addNodeStake(nodeStake);
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

const loadRecordFiles = async (recordFiles) => {
  if (recordFiles == null) {
    return;
  }
  for (const recordFile of recordFiles) {
    await addRecordFile(recordFile);
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

const loadTokenAllowances = async (tokenAllowances) => {
  if (tokenAllowances == null) {
    return;
  }

  for (const tokenAllowance of tokenAllowances) {
    await addTokenAllowance(tokenAllowance);
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

  for (const transaction of transactions) {
    await addTransaction(transaction);
  }
};

const loadTopicMessages = async (messages) => {
  if (messages == null) {
    return;
  }

  for (const message of messages) {
    await addTopicMessage(message);
  }
};

const addAddressBook = async (addressBookInput) => {
  const insertFields = ['start_consensus_timestamp', 'end_consensus_timestamp', 'file_id', 'node_count', 'file_data'];

  const addressBook = {
    start_consensus_timestamp: 0,
    end_consensus_timestamp: null,
    file_id: 102,
    node_count: 20,
    file_data: defaultFileData,
    ...addressBookInput,
  };

  addressBook.file_data =
    typeof addressBookInput.file_data === 'string'
      ? Buffer.from(addressBookInput.file_data, 'utf-8')
      : Buffer.from(addressBook.file_data);

  await insertDomainObject('address_book', insertFields, addressBook);
};

const addAddressBookEntry = async (addressBookEntryInput) => {
  const insertFields = [
    'consensus_timestamp',
    'memo',
    'public_key',
    'node_id',
    'node_account_id',
    'node_cert_hash',
    'description',
    'stake',
  ];

  const addressBookEntry = {
    consensus_timestamp: 0,
    memo: '0.0.3',
    public_key: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    node_id: 2000,
    node_account_id: 3,
    node_cert_hash: '01d173753810c0aae794ba72d5443c292e9ff962b01046220dd99f5816422696e0569c977e2f169e1e5688afc8f4aa16',
    description: 'description',
    stake: 0,
    ...addressBookEntryInput,
  };

  addressBookEntry.node_cert_hash =
    typeof addressBookEntryInput.node_cert_hash === 'string'
      ? Buffer.from(addressBookEntryInput.node_cert_hash, 'hex')
      : Buffer.from(addressBookEntry.node_cert_hash);

  await insertDomainObject('address_book_entry', insertFields, addressBookEntry);
};

const addAddressBookServiceEndpoint = async (addressBookServiceEndpointInput) => {
  const insertFields = ['consensus_timestamp', 'ip_address_v4', 'node_id', 'port'];

  const addressBookServiceEndpoint = {
    consensus_timestamp: 0,
    ip_address_v4: '127.0.0.1',
    node_id: 0,
    port: 50211,
    ...addressBookServiceEndpointInput,
  };

  await insertDomainObject('address_book_service_endpoint', insertFields, addressBookServiceEndpoint);
};

const addEntity = async (defaults, entity) => {
  const localDefaults = {
    alias: null,
    auto_renew_account_id: null,
    auto_renew_period: null,
    decline_reward: false,
    deleted: false,
    ethereum_nonce: null,
    evm_address: null,
    expiration_timestamp: null,
    id: null,
    key: null,
    max_automatic_token_associations: null,
    memo: 'entity memo',
    num: 0,
    public_key: null,
    realm: 0,
    receiver_sig_required: false,
    shard: 0,
    staked_account_id: null,
    staked_node_id: -1,
    stake_period_start: -1,
    timestamp_range: '[0,)',
    type: constants.entityTypes.ACCOUNT,
  };
  const insertFields = Object.keys(localDefaults).sort();
  entity = {
    ...localDefaults,
    ...defaults,
    ...entity,
  };
  entity.id = EntityId.of(BigInt(entity.shard), BigInt(entity.realm), BigInt(entity.num)).getEncodedId();
  entity.alias = base32.decode(entity.alias);
  entity.evm_address = entity.evm_address && Buffer.from(entity.evm_address, 'hex');
  if (typeof entity.key === 'string') {
    entity.key = Buffer.from(entity.key, 'hex');
  }

  await insertDomainObject('entity', insertFields, entity);
};

const addEthereumTransaction = async (ethereumTransaction) => {
  // any attribute starting with '_' is not a db column
  ethereumTransaction = _.omitBy(ethereumTransaction, (v, k) => k.startsWith('_'));
  const localDefaults = {
    access_list: null,
    call_data_id: null,
    call_data: null,
    chain_id: null,
    consensus_timestamp: '187654000123456',
    data: '0x000000000',
    gas_limit: 1000000,
    gas_price: '0x4a817c80',
    hash: '0x0000000000000000000000000000000000000000000000000000000000000123',
    max_fee_per_gas: null,
    max_gas_allowance: 10000,
    max_priority_fee_per_gas: null,
    nonce: 1,
    payer_account_id: 5001,
    recovery_id: 1,
    signature_r: '0xd693b532a80fed6392b428604171fb32fdbf953728a3a7ecc7d4062b1652c042',
    signature_s: '0x24e9c602ac800b983b035700a14b23f78a253ab762deab5dc27e3555a750b354',
    signature_v: '0x1b',
    to_address: null,
    type: 2,
    value: '0x0',
  };

  const ethTx = {
    ...localDefaults,
    ...ethereumTransaction,
  };

  const insertFields = Object.keys(ethTx);

  const byteaFields = [
    'access_list',
    'call_data',
    'chain_id',
    'data',
    'gas_price',
    'hash',
    'max_fee_per_gas',
    'max_priority_fee_per_gas',
    'signature_r',
    'signature_s',
    'signature_v',
    'to_address',
    'value',
  ];
  for (const field of byteaFields) {
    if (!_.isNull(ethTx[field])) {
      const stringValue = ethTx[field].toString();
      ethTx[field] = Buffer.from(stringValue.replace(/^0x/, '').padStart(2, '0'), 'hex');
    }
  }

  await insertDomainObject('ethereum_transaction', insertFields, ethTx);
};

const hexEncodedFileIds = [111, 112];

const addFileData = async (fileDataInput) => {
  const encoding = hexEncodedFileIds.includes(fileDataInput.entity_id) ? 'hex' : 'utf-8';

  const fileData = {
    transaction_type: 17,
    ...fileDataInput,
  };

  // contract bytecode is provided as encoded hex string
  fileData.file_data =
    typeof fileDataInput.file_data === 'string'
      ? Buffer.from(fileDataInput.file_data, encoding)
      : Buffer.from(fileData.file_data);

  await sqlConnection.query(
    `insert into file_data (file_data, consensus_timestamp, entity_id, transaction_type)
     values ($1, $2, $3, $4)`,
    [fileData.file_data, fileData.consensus_timestamp, fileData.entity_id, fileData.transaction_type]
  );
};

const addAccount = async (account) => {
  await addEntity(
    {
      max_automatic_token_associations: 0,
      public_key: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
      type: constants.entityTypes.ACCOUNT,
    },
    account
  );
};

const addAssessedCustomFee = async (assessedCustomFee) => {
  assessedCustomFee = {
    effective_payer_account_ids: [],
    payer_account_id: '0.0.300',
    ...assessedCustomFee,
  };
  const {amount, collector_account_id, consensus_timestamp, effective_payer_account_ids, payer_account_id, token_id} =
    assessedCustomFee;
  const effectivePayerAccountIds = [
    '{',
    effective_payer_account_ids.map((payer) => EntityId.parse(payer).getEncodedId()).join(','),
    '}',
  ].join('');

  await sqlConnection.query(
    `insert into assessed_custom_fee
     (amount, collector_account_id, consensus_timestamp, effective_payer_account_ids, token_id, payer_account_id)
     values ($1, $2, $3, $4, $5, $6);`,
    [
      amount,
      EntityId.parse(collector_account_id).getEncodedId(),
      consensus_timestamp.toString(),
      effectivePayerAccountIds,
      EntityId.parse(token_id, {isNullable: true}).getEncodedId(),
      EntityId.parse(payer_account_id).getEncodedId(),
    ]
  );
};

const addCustomFee = async (customFee) => {
  let netOfTransfers = customFee.net_of_transfers;
  if (customFee.amount_denominator && netOfTransfers == null) {
    // set default netOfTransfers for fractional fees
    netOfTransfers = false;
  }

  await sqlConnection.query(
    `insert into custom_fee (amount,
                             amount_denominator,
                             collector_account_id,
                             created_timestamp,
                             denominating_token_id,
                             maximum_amount,
                             minimum_amount,
                             net_of_transfers,
                             royalty_denominator,
                             royalty_numerator,
                             token_id)
     values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11);`,
    [
      customFee.amount || null,
      customFee.amount_denominator || null,
      EntityId.parse(customFee.collector_account_id, {isNullable: true}).getEncodedId(),
      customFee.created_timestamp.toString(),
      EntityId.parse(customFee.denominating_token_id, {isNullable: true}).getEncodedId(),
      customFee.maximum_amount || null,
      customFee.minimum_amount || '0',
      netOfTransfers != null ? netOfTransfers : null,
      customFee.royalty_denominator || null,
      customFee.royalty_numerator || null,
      EntityId.parse(customFee.token_id).getEncodedId(),
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

  await sqlConnection.query(
    `INSERT INTO account_balance_file
     (consensus_timestamp, count, load_start, load_end, name, node_account_id)
     VALUES ($1, $2, $3, $4, $5, $6)
     ON CONFLICT DO NOTHING;`,
    [balance.timestamp, 1, balance.timestamp, balance.timestamp, `${balance.timestamp}_Balances.pb.gz`, 3]
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
  const defaults = {
    charged_tx_fee: NODE_FEE + NETWORK_FEE + SERVICE_FEE,
    consensus_timestamp: null,
    entity_id: null,
    max_fee: 33,
    node_account_id: null,
    nonce: 0,
    parent_consensus_timestamp: null,
    payer_account_id: null,
    result: 22,
    scheduled: false,
    transaction_bytes: 'bytes',
    transaction_hash: 'hash',
    type: 14,
    valid_duration_seconds: 11,
    valid_start_ns: null,
    index: 1,
  };
  const insertFields = Object.keys(defaults);

  transaction = {
    ...defaults,
    // transfer which aren't in the defaults
    non_fee_transfers: [],
    transfers: [],
    ...transaction,
    entity_id: EntityId.parse(transaction.entity_id, {isNullable: true}).getEncodedId(),
    node_account_id: EntityId.parse(transaction.nodeAccountId, {isNullable: true}).getEncodedId(),
    payer_account_id: EntityId.parse(transaction.payerAccountId).getEncodedId(),
    valid_start_ns: transaction.valid_start_timestamp,
  };

  if (transaction.valid_start_ns === undefined) {
    // set valid_start_ns to consensus_timestamp - 1 if not set
    const consensusTimestamp = math.bignumber(transaction.consensus_timestamp);
    transaction.valid_start_ns = consensusTimestamp.minus(1).toString();
  }

  const {node_account_id: nodeAccount, payer_account_id: payerAccount} = transaction;
  await insertDomainObject('transaction', insertFields, transaction);

  await insertTransfers(
    'crypto_transfer',
    transaction.consensus_timestamp,
    transaction.transfers,
    transaction.charged_tx_fee > 0,
    payerAccount,
    nodeAccount
  );
  await insertTransfers(
    'non_fee_transfer',
    transaction.consensus_timestamp,
    transaction.non_fee_transfers,
    false,
    payerAccount
  );
  await insertTokenTransfers(transaction.consensus_timestamp, transaction.token_transfer_list, payerAccount);
  await insertNftTransfers(transaction.consensus_timestamp, transaction.nft_transfer_list, payerAccount);
};

const insertTransfers = async (
  tableName,
  consensusTimestamp,
  transfers,
  hasChargedTransactionFee,
  payerAccountId,
  nodeAccount
) => {
  if (transfers.length === 0 && hasChargedTransactionFee && payerAccountId) {
    // insert default crypto transfers to node and treasury
    await sqlConnection.query(
      `INSERT INTO ${tableName} (consensus_timestamp, amount, entity_id, payer_account_id, is_approval)
       VALUES ($1, $2, $3, $4, $5);`,
      [consensusTimestamp.toString(), NODE_FEE, nodeAccount || DEFAULT_NODE_ID, payerAccountId, false]
    );
    await sqlConnection.query(
      `INSERT INTO ${tableName} (consensus_timestamp, amount, entity_id, payer_account_id, is_approval)
       VALUES ($1, $2, $3, $4, $5);`,
      [consensusTimestamp.toString(), NETWORK_FEE, DEFAULT_TREASURY_ID, payerAccountId, false]
    );
    await sqlConnection.query(
      `INSERT INTO ${tableName} (consensus_timestamp, amount, entity_id, payer_account_id, is_approval)
       VALUES ($1, $2, $3, $4, $5);`,
      [consensusTimestamp.toString(), -(NODE_FEE + NETWORK_FEE), payerAccountId, payerAccountId, false]
    );
  }

  for (const transfer of transfers) {
    await sqlConnection.query(
      `INSERT INTO ${tableName} (consensus_timestamp, amount, entity_id, payer_account_id, is_approval)
       VALUES ($1, $2, $3, $4, $5);`,
      [
        consensusTimestamp.toString(),
        transfer.amount,
        EntityId.parse(transfer.account).getEncodedId(),
        payerAccountId,
        transfer.is_approval,
      ]
    );
  }
};

const insertTokenTransfers = async (consensusTimestamp, transfers, payerAccountId) => {
  if (!transfers || transfers.length === 0) {
    return;
  }

  const tokenTransfers = transfers.map((transfer) => {
    return [
      `${consensusTimestamp}`,
      EntityId.parse(transfer.token_id).getEncodedId(),
      EntityId.parse(transfer.account).getEncodedId(),
      transfer.amount,
      payerAccountId,
      transfer.is_approval,
    ];
  });

  await sqlConnection.query(
    pgformat(
      'INSERT INTO token_transfer (consensus_timestamp, token_id, account_id, amount, payer_account_id, is_approval) VALUES %L',
      tokenTransfers
    )
  );
};

const insertNftTransfers = async (consensusTimestamp, nftTransferList, payerAccountId) => {
  if (!nftTransferList || nftTransferList.length === 0) {
    return;
  }

  const nftTransfers = nftTransferList.map((transfer) => {
    return [
      `${consensusTimestamp}`,
      EntityId.parse(transfer.receiver_account_id, {isNullable: true}).getEncodedId(),
      EntityId.parse(transfer.sender_account_id, {isNullable: true}).getEncodedId(),
      transfer.serial_number,
      EntityId.parse(transfer.token_id).getEncodedId().toString(),
      payerAccountId,
      transfer.is_approval,
    ];
  });

  await sqlConnection.query(
    pgformat(
      'INSERT INTO nft_transfer (consensus_timestamp, receiver_account_id, sender_account_id, serial_number, token_id, payer_account_id, is_approval) VALUES %L',
      nftTransfers
    )
  );
};

const addContract = async (contract) => {
  contract = {
    auto_renew_account_id: null,
    auto_renew_period: null,
    decline_reward: false,
    deleted: false,
    evm_address: null,
    expiration_timestamp: null,
    initcode: null,
    key: null,
    max_automatic_token_associations: 0,
    memo: 'contract memo',
    permanent_removal: null,
    public_key: null,
    realm: 0,
    shard: 0,
    staked_account_id: null,
    staked_node_id: -1,
    stake_period_start: -1,
    type: constants.entityTypes.CONTRACT,
    timestamp_range: '[0,)',
    ...contract,
  };
  contract.evm_address = contract.evm_address != null ? Buffer.from(contract.evm_address, 'hex') : null;
  contract.id = EntityId.of(BigInt(contract.shard), BigInt(contract.realm), BigInt(contract.num)).getEncodedId();
  contract.initcode = contract.initcode != null ? Buffer.from(contract.initcode) : null;
  contract.key = contract.key != null ? Buffer.from(contract.key) : null;
  const insertFields = Object.keys(contract)
    .filter((k) => !k.startsWith('_'))
    .sort();

  const table = getTableName('contract', contract);
  await insertDomainObject(table, insertFields, contract);
};

const addContractResult = async (contractResultInput) => {
  const insertFields = [
    'amount',
    'bloom',
    'call_result',
    'consensus_timestamp',
    'contract_id',
    'created_contract_ids',
    'error_message',
    'function_parameters',
    'function_result',
    'gas_limit',
    'gas_used',
    'payer_account_id',
    'sender_id',
  ];

  const contractResult = {
    amount: 0,
    bloom: null,
    call_result: null,
    consensus_timestamp: 1234510001,
    contract_id: 0,
    created_contract_ids: [],
    error_message: '',
    function_parameters: Buffer.from([1, 1, 2, 2, 3, 3]),
    function_result: null,
    gas_limit: 1000,
    gas_used: null,
    payer_account_id: 101,
    ...contractResultInput,
  };

  contractResult.bloom =
    contractResultInput.bloom != null ? Buffer.from(contractResultInput.bloom) : contractResult.bloom;
  contractResult.call_result =
    contractResultInput.call_result != null ? Buffer.from(contractResultInput.call_result) : contractResult.call_result;
  contractResult.function_parameters =
    contractResultInput.function_parameters != null
      ? Buffer.from(contractResultInput.function_parameters)
      : contractResult.function_parameters;
  contractResult.function_result =
    contractResultInput.function_result != null
      ? Buffer.from(contractResultInput.function_result)
      : contractResult.function_result;

  await insertDomainObject('contract_result', insertFields, contractResult);
};

const addContractLog = async (contractLogInput) => {
  const insertFields = [
    'bloom',
    'consensus_timestamp',
    'contract_id',
    'data',
    'index',
    'payer_account_id',
    'root_contract_id',
    'topic0',
    'topic1',
    'topic2',
    'topic3',
  ];
  const positions = _.range(1, insertFields.length + 1)
    .map((position) => `$${position}`)
    .join(',');

  const contractLog = {
    bloom: '\\x0123',
    consensus_timestamp: 1234510001,
    contract_id: 1,
    data: '\\x0123',
    index: 0,
    payer_account_id: 2,
    root_contract_id: null,
    topic0: defaultFileData,
    topic1: '\\x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925',
    topic2: '\\xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef',
    topic3: '\\xe8d47b56e8cdfa95f871b19d4f50a857217c44a95502b0811a350fec1500dd67',
    ...contractLogInput,
  };

  contractLog.bloom = testUtils.getBuffer(contractLogInput.bloom, contractLog.bloom);
  contractLog.data = testUtils.getBuffer(contractLogInput.data, contractLog.data);
  contractLog.topic0 = testUtils.getBuffer(contractLogInput.topic0, contractLog.topic0);
  contractLog.topic1 = testUtils.getBuffer(contractLogInput.topic1, contractLog.topic1);
  contractLog.topic2 = testUtils.getBuffer(contractLogInput.topic2, contractLog.topic2);
  contractLog.topic3 = testUtils.getBuffer(contractLogInput.topic3, contractLog.topic3);

  await sqlConnection.query(
    `insert into contract_log (${insertFields.join(',')})
     values (${positions})`,
    insertFields.map((name) => contractLog[name])
  );
};

const addContractStateChange = async (contractStateChangeInput) => {
  const insertFields = [
    'consensus_timestamp',
    'contract_id',
    'payer_account_id',
    'slot',
    'value_read',
    'value_written',
  ];

  const contractStateChange = {
    consensus_timestamp: 1234510001,
    contract_id: 1,
    payer_account_id: 2,
    slot: '\\x0123',
    value_read: defaultFileData,
    value_written: '\\x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925',
    ...contractStateChangeInput,
  };

  contractStateChange.slot = testUtils.getBuffer(contractStateChangeInput.slot, contractStateChange.slot);
  contractStateChange.value_read = testUtils.getBuffer(
    contractStateChangeInput.value_read,
    contractStateChange.value_read
  );
  contractStateChange.value_written = testUtils.getBuffer(
    contractStateChangeInput.value_written,
    contractStateChange.value_written
  );

  await insertDomainObject('contract_state_change', insertFields, contractStateChange);
};

const addCryptoAllowance = async (cryptoAllowanceInput) => {
  const insertFields = ['amount', 'owner', 'payer_account_id', 'spender', 'timestamp_range'];

  const cryptoAllowance = {
    amount: 0,
    owner: 1000,
    payer_account_id: 101,
    spender: 2000,
    timestamp_range: '[0,)',
    ...cryptoAllowanceInput,
  };

  const table = getTableName('crypto_allowance', cryptoAllowance);
  await insertDomainObject(table, insertFields, cryptoAllowance);
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
      {
        account: cryptoTransfer.senderAccountId,
        amount: -NETWORK_FEE - BigInt(cryptoTransfer.amount),
        is_approval: false,
      },
      {account: cryptoTransfer.recipientAccountId, amount: cryptoTransfer.amount, is_approval: false},
      {account: cryptoTransfer.treasuryAccountId, amount: NETWORK_FEE, is_approval: false},
    ];
  }
  await addTransaction(cryptoTransfer);
};

const addTopicMessage = async (message) => {
  const insertFields = [
    'chunk_num',
    'chunk_total',
    'consensus_timestamp',
    'initial_transaction_id',
    'message',
    'payer_account_id',
    'running_hash',
    'running_hash_version',
    'sequence_number',
    'topic_id',
    'valid_start_timestamp',
  ];

  const table = 'topic_message';

  message = {
    chunk_num: null,
    chunk_total: null,
    initial_transaction_id: null,
    message: 'message', // Base64 encoding: bWVzc2FnZQ==
    payer_account_id: 3,
    running_hash: 'running_hash', // Base64 encoding: cnVubmluZ19oYXNo
    running_hash_version: 2,
    valid_start_timestamp: null,
    ...message,
  };

  message.initial_transaction_id =
    message.initial_transaction_id == null ? null : Buffer.from(message.initial_transaction_id);
  await insertDomainObject(table, insertFields, message);
};

const addSchedule = async (schedule) => {
  schedule = {
    creator_account_id: '0.0.1024',
    payer_account_id: '0.0.1024',
    transaction_body: Buffer.from([1, 1, 2, 2, 3, 3]),
    wait_for_expiry: false,
    ...schedule,
  };

  await sqlConnection.query(
    `INSERT INTO schedule (consensus_timestamp,
                           creator_account_id,
                           executed_timestamp,
                           payer_account_id,
                           schedule_id,
                           transaction_body,
                           expiration_time,
                           wait_for_expiry)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
    [
      schedule.consensus_timestamp,
      EntityId.parse(schedule.creator_account_id).getEncodedId(),
      schedule.executed_timestamp,
      EntityId.parse(schedule.payer_account_id).getEncodedId(),
      EntityId.parse(schedule.schedule_id).getEncodedId(),
      schedule.transaction_body,
      schedule.expiration_time,
      schedule.wait_for_expiry,
    ]
  );
};

const addTransactionSignature = async (transactionSignature) => {
  await sqlConnection.query(
    `INSERT INTO transaction_signature (consensus_timestamp,
                                        public_key_prefix,
                                        entity_id,
                                        signature,
                                        type)
     VALUES ($1, $2, $3, $4, $5)`,
    [
      transactionSignature.consensus_timestamp,
      Buffer.from(transactionSignature.public_key_prefix),
      EntityId.parse(transactionSignature.entity_id, {isNullable: true}).getEncodedId(),
      Buffer.from(transactionSignature.signature),
      transactionSignature.type,
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
    freeze_default: false,
    initial_supply: 1000000,
    kyc_key: null,
    max_supply: '9223372036854775807', // max long, cast to string to avoid error from JavaScript Number cast
    name: 'Token name',
    pause_key: null,
    pause_status: 'NOT_APPLICABLE',
    supply_key: null,
    supply_type: 'INFINITE',
    symbol: 'YBTJBOAZ',
    total_supply: 1000000,
    treasury_account_id: '0.0.98',
    wipe_key: null,
    ...token,
  };

  if (token.type === 'NON_FUNGIBLE_UNIQUE') {
    token.decimals = 0;
    token.initial_supply = 0;
  }

  if (!token.modified_timestamp) {
    token.modified_timestamp = token.created_timestamp;
  }

  await sqlConnection.query(
    `INSERT INTO token (token_id,
                        created_timestamp,
                        decimals,
                        fee_schedule_key,
                        freeze_default,
                        initial_supply,
                        kyc_key,
                        max_supply,
                        modified_timestamp,
                        name,
                        pause_key,
                        pause_status,
                        supply_key,
                        supply_type,
                        symbol,
                        total_supply,
                        treasury_account_id,
                        type,
                        wipe_key)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19);`,
    [
      EntityId.parse(token.token_id).getEncodedId(),
      token.created_timestamp,
      token.decimals,
      token.fee_schedule_key,
      token.freeze_default,
      token.initial_supply,
      token.kyc_key,
      token.max_supply,
      token.modified_timestamp,
      token.name,
      token.pause_key,
      token.pause_status,
      token.supply_key,
      token.supply_type,
      token.symbol,
      token.total_supply,
      EntityId.parse(token.treasury_account_id).getEncodedId(),
      token.type,
      token.wipe_key,
    ]
  );

  if (!token.custom_fees) {
    // if there is no custom fees schedule for the token, add the default empty fee schedule at created_timestamp
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
    automatic_association: false,
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
    `INSERT INTO token_account (account_id, associated, automatic_association, created_timestamp, freeze_status,
                                kyc_status, modified_timestamp, token_id)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8);`,
    [
      EntityId.parse(tokenAccount.account_id).getEncodedId(),
      tokenAccount.associated,
      tokenAccount.automatic_association,
      tokenAccount.created_timestamp,
      tokenAccount.freeze_status,
      tokenAccount.kyc_status,
      tokenAccount.modified_timestamp,
      EntityId.parse(tokenAccount.token_id).getEncodedId(),
    ]
  );
};

const addTokenAllowance = async (tokenAllowance) => {
  const insertFields = ['amount', 'owner', 'payer_account_id', 'spender', 'token_id', 'timestamp_range'];

  tokenAllowance = {
    amount: 0,
    owner: 1000,
    payer_account_id: 1000,
    spender: 2000,
    token_id: 3000,
    timestamp_range: '[0,)',
    ...tokenAllowance,
  };

  const table = getTableName('token_allowance', tokenAllowance);
  await insertDomainObject(table, insertFields, tokenAllowance);
};

const addNft = async (nft) => {
  // create nft account object
  nft = {
    account_id: '0.0.0',
    created_timestamp: 0,
    delegating_spender: null,
    deleted: false,
    metadata: '\\x',
    modified_timestamp: 0,
    serial_number: 0,
    spender: null,
    token_id: '0.0.0',
    ...nft,
  };

  if (!nft.modified_timestamp) {
    nft.modified_timestamp = nft.created_timestamp;
  }

  await sqlConnection.query(
    `INSERT INTO nft (account_id, created_timestamp, delegating_spender, deleted, modified_timestamp, metadata, serial_number, spender, token_id)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9);`,
    [
      EntityId.parse(nft.account_id, {isNullable: true}).getEncodedId(),
      nft.created_timestamp,
      EntityId.parse(nft.delegating_spender, {isNullable: true}).getEncodedId(),
      nft.deleted,
      nft.modified_timestamp,
      nft.metadata,
      nft.serial_number,
      EntityId.parse(nft.spender, {isNullable: true}).getEncodedId(),
      EntityId.parse(nft.token_id).getEncodedId(),
    ]
  );
};

const addNodeStake = async (nodeStakeInput) => {
  const stakingPeriodEnd = 86_400_000_000_000n - 1n;
  const nodeStake = {
    consensus_timestamp: 0,
    epoch_day: 0,
    max_stake: 2000,
    min_stake: 1,
    node_id: 0,
    reward_rate: 0,
    stake: 0,
    stake_not_rewarded: 0,
    stake_rewarded: 0,
    stake_total: 0,
    staking_period: stakingPeriodEnd,
    ...nodeStakeInput,
  };
  const insertFields = Object.keys(nodeStake)
    .filter((k) => !k.startsWith('_'))
    .sort();

  await insertDomainObject('node_stake', insertFields, nodeStake);
};

const addRecordFile = async (recordFileInput) => {
  const insertFields = [
    'bytes',
    'gas_used',
    'consensus_end',
    'consensus_start',
    'count',
    'digest_algorithm',
    'file_hash',
    'index',
    'hapi_version_major',
    'hapi_version_minor',
    'hapi_version_patch',
    'hash',
    'load_end',
    'load_start',
    'logs_bloom',
    'name',
    'node_account_id',
    'prev_hash',
    'size',
    'version',
  ];

  const bytes = Buffer.from([1, 1, 2, 2, 3, 3]);
  const recordFile = {
    bytes,
    gas_used: 0,
    consensus_end: 1628751573995691000,
    consensus_start: 1628751572000852000,
    count: 1200,
    digest_algorithm: 0,
    file_hash: 'dee34bdd8bbe32fdb53ce7e3cf764a0495fa5e93b15ca567208cfb384231301bedf821de07b0d8dc3fb55c5b3c90ac61',
    index: 123456789,
    hapi_version_major: 0,
    hapi_version_minor: 11,
    hapi_version_patch: 0,
    hash: 'ed55d98d53fd55c9caf5f61affe88cd2978d37128ec54af5dace29b6fd271cbd079ebe487bda5f227087e2638b1100cf',
    load_end: 1629298236,
    load_start: 1629298233,
    name: '2021-08-12T06_59_32.000852000Z.rcd',
    node_account_id: 3,
    prev_hash: '000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000',
    version: 5,
    logs_bloom: Buffer.alloc(0),
    size: bytes.length,
    ...recordFileInput,
  };
  recordFile.bytes = recordFileInput.bytes != null ? Buffer.from(recordFileInput.bytes) : recordFile.bytes;
  recordFile.logs_bloom =
    recordFileInput.logs_bloom != null ? Buffer.from(recordFileInput.logs_bloom, 'hex') : recordFile.logs_bloom;

  await insertDomainObject('record_file', insertFields, recordFile);
};

const insertDomainObject = async (table, fields, obj) => {
  const positions = _.range(1, fields.length + 1).map((position) => `$${position}`);
  await sqlConnection.query(
    `INSERT INTO ${table} (${fields}) VALUES (${positions});`,
    fields.map((f) => obj[f])
  );
};

// for a pair of current and history tables, if the timestamp range is open-ended, use the current table, otherwise
// use the history table
const getTableName = (base, entity) => (entity.timestamp_range.endsWith(',)') ? base : `${base}_history`);

module.exports = {
  addAccount,
  addCryptoTransaction,
  addNft,
  addToken,
  loadAddressBooks,
  loadAddressBookEntries,
  loadAddressBookServiceEndpoints,
  loadContracts,
  loadContractResults,
  loadCryptoAllowances,
  loadEntities,
  loadFileData,
  loadNodeStakes,
  loadRecordFiles,
  loadTransactions,
  loadEthereumTransactions,
  loadContractLogs,
  loadContractStateChanges,
  setAccountBalance,
  setUp,
};
