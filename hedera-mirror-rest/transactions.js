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

import _ from 'lodash';

import config from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {NotFoundError} from './errors';
import TransactionId from './transactionId';
import * as utils from './utils';

import {
  AssessedCustomFee,
  CryptoTransfer,
  NftTransfer,
  StakingRewardTransfer,
  TokenTransfer,
  Transaction,
  TransactionHash,
  TransactionResult,
  TransactionType,
} from './model';

import {AssessedCustomFeeViewModel, NftTransferViewModel} from './viewmodel';

const {maxTransactionConsensusTimestampRangeNs} = config.query;

const transactionFields = [
  Transaction.CHARGED_TX_FEE,
  Transaction.CONSENSUS_TIMESTAMP,
  Transaction.ENTITY_ID,
  Transaction.MAX_FEE,
  Transaction.MEMO,
  Transaction.NODE_ACCOUNT_ID,
  Transaction.NONCE,
  Transaction.PARENT_CONSENSUS_TIMESTAMP,
  Transaction.PAYER_ACCOUNT_ID,
  Transaction.RESULT,
  Transaction.SCHEDULED,
  Transaction.TRANSACTION_BYTES,
  Transaction.TRANSACTION_HASH,
  Transaction.TYPE,
  Transaction.VALID_DURATION_SECONDS,
  Transaction.VALID_START_NS,
  Transaction.INDEX,
];
const transactionFullFields = transactionFields.map((f) => Transaction.getFullName(f));
// consensus_timestamp in transfer_list is a coalesce of multiple consensus timestamp columns
const transferListFullFields = transactionFields
  .filter((f) => f !== Transaction.CONSENSUS_TIMESTAMP)
  .map((f) => `t.${f}`);

const cryptoTransferJsonAgg = `jsonb_agg(jsonb_build_object(
    '${CryptoTransfer.AMOUNT}', ${CryptoTransfer.AMOUNT},
    '${CryptoTransfer.ENTITY_ID}', ${CryptoTransfer.getFullName(CryptoTransfer.ENTITY_ID)},
    '${CryptoTransfer.IS_APPROVAL}', ${CryptoTransfer.IS_APPROVAL}
  ) order by ${CryptoTransfer.getFullName(CryptoTransfer.ENTITY_ID)}, ${CryptoTransfer.AMOUNT})`;

const tokenTransferJsonAgg = `jsonb_agg(jsonb_build_object(
    '${TokenTransfer.ACCOUNT_ID}', ${TokenTransfer.ACCOUNT_ID},
    '${TokenTransfer.AMOUNT}', ${TokenTransfer.AMOUNT},
    '${TokenTransfer.TOKEN_ID}', ${TokenTransfer.TOKEN_ID},
    '${TokenTransfer.IS_APPROVAL}', ${TokenTransfer.IS_APPROVAL}
  ) order by ${TokenTransfer.TOKEN_ID}, ${TokenTransfer.ACCOUNT_ID})`;

const nftTransferJsonAgg = `jsonb_agg(jsonb_build_object(
    '${NftTransfer.RECEIVER_ACCOUNT_ID}', ${NftTransfer.RECEIVER_ACCOUNT_ID},
    '${NftTransfer.SENDER_ACCOUNT_ID}', ${NftTransfer.SENDER_ACCOUNT_ID},
    '${NftTransfer.SERIAL_NUMBER}', ${NftTransfer.SERIAL_NUMBER},
    '${NftTransfer.TOKEN_ID}', ${NftTransfer.TOKEN_ID},
    '${NftTransfer.IS_APPROVAL}', ${NftTransfer.IS_APPROVAL}
  ) order by ${NftTransfer.TOKEN_ID}, ${NftTransfer.SERIAL_NUMBER})`;

const assessedCustomFeeJsonAgg = `jsonb_agg(jsonb_build_object(
    '${AssessedCustomFee.AMOUNT}', ${AssessedCustomFee.AMOUNT},
    '${AssessedCustomFee.COLLECTOR_ACCOUNT_ID}', ${AssessedCustomFee.COLLECTOR_ACCOUNT_ID},
    '${AssessedCustomFee.EFFECTIVE_PAYER_ACCOUNT_IDS}', ${AssessedCustomFee.EFFECTIVE_PAYER_ACCOUNT_IDS},
    '${AssessedCustomFee.TOKEN_ID}', ${AssessedCustomFee.TOKEN_ID}
  ) order by ${AssessedCustomFee.COLLECTOR_ACCOUNT_ID}, ${AssessedCustomFee.AMOUNT})`;

/**
 * Gets the select clause with crypto transfers, token transfers, and nft transfers
 *
 * @param innerQuery
 * @param order
 * @return {string}
 */
const getSelectClauseWithTransfers = (innerQuery, order = 'desc') => {
  const transactionTimeStampCte = (modifyingQuery) => {
    let timestampFilter = '';
    let timestampFilterJoin = '';
    let limitQuery = 'limit $1';
    let fromTables = `from ${Transaction.tableName} ${Transaction.tableAlias}`;

    // populate pre-clause queries where a timestamp filter is applied
    if (!_.isUndefined(modifyingQuery)) {
      fromTables = `from timestamp_range tr, ${Transaction.tableName} ${Transaction.tableAlias}`;
      timestampFilter = `timestampFilter as (${modifyingQuery}),
      timestamp_range as (select min(consensus_timestamp) as min, max(consensus_timestamp) as max from timestampFilter),`;
      timestampFilterJoin = `join timestampFilter tf on ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} =
        tf.consensus_timestamp
        WHERE ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} >= tr.min
        AND ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} <= tr.max`;
      limitQuery = '';
    }

    const tquery = `select ${transactionFullFields}
                    ${fromTables}
                    ${timestampFilterJoin}
                    order by ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} ${order}
                    ${limitQuery}`;
    return `${timestampFilter}
      tlist as (${tquery})`;
    }

  const cryptoTransferListCte_select = `c_list as (
      select ${cryptoTransferJsonAgg} as ctr_list, ${CryptoTransfer.getFullName(CryptoTransfer.CONSENSUS_TIMESTAMP)}`;
  const cryptoTransferListCte_from_default = `from ${CryptoTransfer.tableName} ${CryptoTransfer.tableAlias}`;
  const cryptoTransferListCte_from_override = `from timestamp_range tr, ${CryptoTransfer.tableName} ${CryptoTransfer.tableAlias}`;
  const cryptoTransferListCte_join =
      `join tlist on ${CryptoTransfer.getFullName(CryptoTransfer.CONSENSUS_TIMESTAMP)} = tlist.consensus_timestamp
      and ${CryptoTransfer.getFullName(CryptoTransfer.PAYER_ACCOUNT_ID)} = tlist.payer_account_id`;
  const cryptoTransferListCte_where = `WHERE ${CryptoTransfer.getFullName(CryptoTransfer.CONSENSUS_TIMESTAMP)} >= tr.min
      AND ${CryptoTransfer.getFullName(CryptoTransfer.CONSENSUS_TIMESTAMP)} <= tr.max`;
  const cryptoTransferListCte_group = `group by ${CryptoTransfer.getFullName(CryptoTransfer.CONSENSUS_TIMESTAMP)}
  )`;

  let cryptoTransferListCte = ``;
  if (_.isUndefined(innerQuery)) {
    cryptoTransferListCte = cryptoTransferListCte_select + '\n' + cryptoTransferListCte_from_default + '\n'
        + cryptoTransferListCte_join + '\n' + cryptoTransferListCte_group;
  } else {
    cryptoTransferListCte = cryptoTransferListCte_select + '\n' + cryptoTransferListCte_from_override + '\n'
        + cryptoTransferListCte_join + '\n' + cryptoTransferListCte_where + '\n' + cryptoTransferListCte_group;
  }

  const tokenTransferListCte_select = `t_list as (
      select ${tokenTransferJsonAgg} as ttr_list, ${TokenTransfer.getFullName(TokenTransfer.CONSENSUS_TIMESTAMP)}`;
  const tokenTransferListCte_from_default = `from ${TokenTransfer.tableName} ${TokenTransfer.tableAlias}`;
  const tokenTransferListCte_from_override = `from timestamp_range tr, ${TokenTransfer.tableName} ${TokenTransfer.tableAlias}`;
  const tokenTransferListCte_join =
    `join tlist on ${TokenTransfer.getFullName(TokenTransfer.CONSENSUS_TIMESTAMP)} = tlist.consensus_timestamp
    and ${TokenTransfer.getFullName(TokenTransfer.PAYER_ACCOUNT_ID)} = tlist.payer_account_id`;
  const tokenTransferListCte_where = `WHERE ${TokenTransfer.getFullName(TokenTransfer.CONSENSUS_TIMESTAMP)} >= tr.min
      AND ${TokenTransfer.getFullName(TokenTransfer.CONSENSUS_TIMESTAMP)} <= tr.max`;
  const tokenTransferListCte_group = `group by ${TokenTransfer.getFullName(TokenTransfer.CONSENSUS_TIMESTAMP)}
  )`;

  let tokenTransferListCte = ``;
  if (_.isUndefined(innerQuery)) {
    tokenTransferListCte = tokenTransferListCte_select + '\n' + tokenTransferListCte_from_default + '\n'
        + tokenTransferListCte_join + '\n' + tokenTransferListCte_group;
  } else {
    tokenTransferListCte = tokenTransferListCte_select + '\n' + tokenTransferListCte_from_override + '\n'
        + tokenTransferListCte_join + '\n' + tokenTransferListCte_where + '\n' + tokenTransferListCte_group;
  }

  const transfersListCte_select = `transfer_list as (
    select coalesce(t.consensus_timestamp, ctrl.consensus_timestamp, ttrl.consensus_timestamp) AS consensus_timestamp,
      ctrl.ctr_list,
      ttrl.ttr_list,
      ${transferListFullFields}`;
  const transfersListCte_from_default = `from tlist t`;
  const transfersListCte_from_override = `from timestamp_range tr, tlist t`;
  const transfersListCte_join = `full outer join c_list ctrl on t.consensus_timestamp = ctrl.consensus_timestamp
        full outer join t_list ttrl on t.consensus_timestamp = ttrl.consensus_timestamp`;
  const transfersListCte_where = `WHERE t.consensus_timestamp >= tr.min AND t.consensus_timestamp <= tr.max`;
  const transfersListCte_end = `
  )`;

  let transfersListCte = ``;
  if (_.isUndefined(innerQuery)) {
    transfersListCte = transfersListCte_select + '\n' + transfersListCte_from_default + '\n'
        + transfersListCte_join + '\n' + transfersListCte_end;
  } else {
    transfersListCte = transfersListCte_select + '\n' + transfersListCte_from_override + '\n'
        + transfersListCte_join + '\n' + transfersListCte_where + '\n' + transfersListCte_end;
  }


  const ctes = [transactionTimeStampCte(innerQuery), cryptoTransferListCte, tokenTransferListCte, transfersListCte];
  const fields = [...transactionFullFields, `t.ctr_list AS crypto_transfer_list`, `t.ttr_list AS token_transfer_list`];

  return `with ${ctes.join(',\n')}
    SELECT
    ${fields.join(',\n')}
  `;
};

/**
 * Creates an assessed custom fee list from aggregated array of JSON objects in the query result
 *
 * @param assessedCustomFees assessed custom fees
 * @return {undefined|{amount: Number, collector_account_id: string, payer_account_id: string, token_id: string}[]}
 */
const createAssessedCustomFeeList = (assessedCustomFees) => {
  if (!assessedCustomFees) {
    return undefined;
  }

  return assessedCustomFees.map((assessedCustomFee) => {
    const model = new AssessedCustomFee(assessedCustomFee);
    return new AssessedCustomFeeViewModel(model);
  });
};

/**
 * Creates crypto transfer list from aggregated array of JSON objects in the query result. Note if the
 * cryptoTransferList is undefined, an empty array is returned.
 *
 * @param cryptoTransferList crypto transfer list
 * @return {{account: string, amount: Number}[]}
 */
const createCryptoTransferList = (cryptoTransferList) => {
  if (!cryptoTransferList) {
    return [];
  }

  return cryptoTransferList.map((transfer) => {
    const {entity_id: accountId, amount, is_approval} = transfer;
    return {
      account: EntityId.parse(accountId).toString(),
      amount,
      is_approval: _.isNil(is_approval) ? false : is_approval,
    };
  });
};

/**
 * Creates token transfer list from aggregated array of JSON objects in the query result
 *
 * @param tokenTransferList token transfer list
 * @return {undefined|{amount: Number, account: string, token_id: string}[]}
 */
const createTokenTransferList = (tokenTransferList) => {
  if (!tokenTransferList) {
    return undefined;
  }

  return tokenTransferList.map((transfer) => {
    const {token_id: tokenId, account_id: accountId, amount, is_approval} = transfer;
    return {
      token_id: EntityId.parse(tokenId).toString(),
      account: EntityId.parse(accountId).toString(),
      amount,
      is_approval: _.isNil(is_approval) ? false : is_approval,
    };
  });
};

/**
 * Creates an nft transfer list from aggregated array of JSON objects in the query result
 *
 * @param nftTransferList nft transfer list
 * @return {undefined|{receiver_account_id: string, sender_account_id: string, serial_number: Number, token_id: string}[]}
 */
const createNftTransferList = (nftTransferList) => {
  if (!nftTransferList) {
    return undefined;
  }

  return nftTransferList.map((transfer) => {
    const nftTransfer = new NftTransfer(transfer);
    return new NftTransferViewModel(nftTransfer);
  });
};

/**
 * Create transferlists from the output of SQL queries.
 *
 * @param {Array of objects} rows Array of rows returned as a result of an SQL query
 * @return {{anchorSecNs: (String|number), transactions: {}}}
 */
const createTransferLists = async (rows) => {
  const stakingRewardMap = await createStakingRewardTransferList(rows);
  const transactions = rows.map((row) => {
    const validStartTimestamp = row.valid_start_ns;
    const payerAccountId = EntityId.parse(row.payer_account_id).toString();
    return {
      assessed_custom_fees: createAssessedCustomFeeList(row.assessed_custom_fees),
      bytes: utils.encodeBase64(row.transaction_bytes),
      charged_tx_fee: row.charged_tx_fee,
      consensus_timestamp: utils.nsToSecNs(row.consensus_timestamp),
      entity_id: EntityId.parse(row.entity_id, {isNullable: true}).toString(),
      max_fee: utils.getNullableNumber(row.max_fee),
      memo_base64: utils.encodeBase64(row.memo),
      name: TransactionType.getName(row.type),
      nft_transfers: createNftTransferList(row.nft_transfer_list),
      node: EntityId.parse(row.node_account_id, {isNullable: true}).toString(),
      nonce: row.nonce,
      parent_consensus_timestamp: utils.nsToSecNs(row.parent_consensus_timestamp),
      result: TransactionResult.getName(row.result),
      scheduled: row.scheduled,
      staking_reward_transfers: stakingRewardMap.get(row.consensus_timestamp) || [],
      token_transfers: createTokenTransferList(row.token_transfer_list),
      transaction_hash: utils.encodeBase64(row.transaction_hash),
      transaction_id: utils.createTransactionId(payerAccountId, validStartTimestamp),
      transfers: createCryptoTransferList(row.crypto_transfer_list),
      valid_duration_seconds: utils.getNullableNumber(row.valid_duration_seconds),
      valid_start_timestamp: utils.nsToSecNs(validStartTimestamp),
    };
  });

  const anchorSecNs = transactions.length > 0 ? transactions[transactions.length - 1].consensus_timestamp : 0;

  return {
    transactions,
    anchorSecNs,
  };
};

/**
 * Gets consensus_timestamps of the transactions that include a transfer from the staking reward account 800
 * transfers are found, the staking_reward_transfers property will be added to the associated transaction object.
 *
 * @param transactions transactions list
 * @return {[]|{number}[]}
 */
const getStakingRewardTimestamps = (transactions) => {
  return transactions
    .filter(
      (transaction) =>
        !_.isNil(transaction.crypto_transfer_list) &&
        transaction.crypto_transfer_list.some(
          (cryptoTransfer) => cryptoTransfer.entity_id === StakingRewardTransfer.STAKING_REWARD_ACCOUNT
        )
    )
    .map((transaction) => transaction.consensus_timestamp);
};

/**
 * Queries for the staking reward transfer list from the transfer list. If staking reward
 * transfers are found, the staking_reward_transfers property will be added to the associated transaction object.
 *
 * @param transactions transactions list
 * @return {Map<consensus_timestamp, staking_reward_transfer>} Map<ConsensusTimestamp, StakingRewardTransfer>
 */
const createStakingRewardTransferList = async (transactions) => {
  const stakingRewardTimestamps = getStakingRewardTimestamps(transactions);
  const rows = await getStakingRewardTransferList(stakingRewardTimestamps);
  return convertStakingRewardTransfers(rows);
};

/**
 * Queries for the staking reward transfer list
 *
 * @param {[]|{number}[]} stakingRewardTimestamps
 * @return rows
 */
const getStakingRewardTransferList = async (stakingRewardTimestamps) => {
  if (stakingRewardTimestamps.length === 0) {
    return [];
  }

  const positions = _.range(1, stakingRewardTimestamps.length + 1).map((position) => `$${position}`);
  const query = `
    select ${StakingRewardTransfer.CONSENSUS_TIMESTAMP},
           json_agg(json_build_object(
             'account', ${StakingRewardTransfer.ACCOUNT_ID},
             '${StakingRewardTransfer.AMOUNT}', ${StakingRewardTransfer.AMOUNT})) as staking_reward_transfers
    from ${StakingRewardTransfer.tableName}
    where ${StakingRewardTransfer.CONSENSUS_TIMESTAMP} in (${positions})
    group by ${StakingRewardTransfer.CONSENSUS_TIMESTAMP}`;
  const {rows} = await pool.queryQuietly(query, stakingRewardTimestamps);
  return rows;
};

/**
 * Convert db rows to staking_reward_transfer objects
 * @param rows
 * @returns {Map<any, any>}
 */
const convertStakingRewardTransfers = (rows) => {
  const rewardsMap = new Map();
  rows.forEach((t) => {
    t.staking_reward_transfers.forEach((transfer) => {
      transfer.account = EntityId.parse(transfer.account).toString();
    });
    rewardsMap.set(t.consensus_timestamp, t.staking_reward_transfers || []);
  });
  return rewardsMap;
};

/**
 * Transactions queries are organized as follows: First there's an inner query that selects the
 * required number of unique transactions (identified by consensus_timestamp). And then queries other tables to
 * extract all relevant information for those transactions.
 * This function returns the outer query base on the consensus_timestamps list returned by the inner query.
 * Also see: getTransactionsInnerQuery function
 *
 * @param {String} innerQuery SQL query that provides a list of unique transactions that match the query criteria
 * @param {String} order Sorting order
 * @return {String} outerQuery Fully formed SQL query
 */
const getTransactionsOuterQuery = (innerQuery, order) => {
  let fromTables = `FROM transfer_list t`;
  let whereClause = '';

  // add constraints on consensus_timestamp where a timestamp filter is applied
  if (!_.isUndefined(innerQuery)) {
    fromTables = `FROM timestamp_range tr, transfer_list t`;
    whereClause = 'WHERE t.consensus_timestamp >= tr.min and t.consensus_timestamp <= tr.max';
  }

  return `
    ${getSelectClauseWithTransfers(innerQuery, order)}
    ${fromTables}
    ${whereClause}

    ORDER BY ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} ${order}`;
};

/**
 * Build the where clause from an array of query conditions
 *
 * @param {string} conditions Query conditions
 * @return {string} The where clause built from the query conditions
 */
const buildWhereClause = function (...conditions) {
  const clause = conditions.filter((q) => q !== '').join(' AND ');
  return clause === '' ? '' : `WHERE ${clause}`;
};

/**
 * Convert parameters to the named format.
 *
 * @param {String} query - the mysql query
 * @param {String} prefix - the prefix for the named parameters
 * @return {String} - The converted query
 */
const convertToNamedQuery = function (query, prefix) {
  let index = 0;
  return query.replace(/\?/g, () => {
    const namedParam = `?${prefix}${index}`;
    index += 1;
    return namedParam;
  });
};

/**
 * Get the query to find distinct timestamps for transactions matching the query filters from a transfers table when filtering by an account id
 *
 * @param {string} tableName - Name of the transfers table to query
 * @param {string} tableAlias - Alias to reference the table by
 * @param namedTsQuery - Transaction table timestamp query with named parameters
 * @param {string} timestampColumn - Name of the timestamp column for the table
 * @param resultTypeQuery - Transaction result query
 * @param transactionTypeQuery - Transaction type query
 * @param namedAccountQuery - Account query with named parameters
 * @param namedCreditDebitQuery - Credit/debit query
 * @param order - Sorting order
 * @param namedLimitQuery - Limit query with named parameters
 * @return {string} - The distinct timestamp query for the given table name
 */
const getTransferDistinctTimestampsQuery = function (
  tableName,
  tableAlias,
  namedTsQuery,
  timestampColumn,
  resultTypeQuery,
  transactionTypeQuery,
  namedAccountQuery,
  namedCreditDebitQuery,
  order,
  namedLimitQuery
) {
  const namedTransferTsQuery = namedTsQuery.replace(/t\.consensus_timestamp/g, `${tableAlias}.${timestampColumn}`);
  const joinClause =
    (resultTypeQuery || transactionTypeQuery) &&
    `join ${Transaction.tableName} as ${
      Transaction.tableAlias
    } on ${tableAlias}.${timestampColumn} = ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} and
      ${tableAlias}.${Transaction.PAYER_ACCOUNT_ID} = ${Transaction.getFullName(Transaction.PAYER_ACCOUNT_ID)}`;
  const whereClause = buildWhereClause(
    namedAccountQuery,
    namedTransferTsQuery,
    resultTypeQuery,
    transactionTypeQuery,
    namedCreditDebitQuery
  );

  return `
    SELECT DISTINCT ${tableAlias}.${timestampColumn} AS consensus_timestamp
    FROM ${tableName} AS ${tableAlias}
    ${joinClause}
    ${whereClause}
    ORDER BY ${tableAlias}.consensus_timestamp ${order}
    ${namedLimitQuery}`;
};

// the condition to exclude synthetic transactions attached to a user submitted transaction
const transactionByPayerExcludeSyntheticCondition = `${Transaction.getFullName(Transaction.NONCE)} = 0 or
  ${Transaction.getFullName(Transaction.PARENT_CONSENSUS_TIMESTAMP)} is not null`;

/**
 * Transactions queries are organized as follows: First there's an inner query that selects the
 * required number of unique transactions (identified by consensus_timestamp). And then queries other tables to
 * extract all relevant information for those transactions.
 * This function forms the inner query base based on all the query criteria specified in the REST URL
 * It selects a list of unique transactions (consensus_timestamps).
 * Also see: getTransactionsOuterQuery function
 *
 * @param {String} accountQuery SQL query that filters based on the account ids
 * @param {String} tsQuery SQL query that filters based on the timestamps for transaction table
 * @param {String} resultTypeQuery SQL query that filters based on the result types
 * @param {String} limitQuery SQL query that limits the number of unique transactions returned
 * @param {String} creditDebitQuery SQL query that filters for credit/debit transactions
 * @param {String} transactionTypeQuery SQL query that filters by transaction type
 * @param {String} order Sorting order
 * @return {String} innerQuery SQL query that filters transactions based on various types of queries
 */
const getTransactionsInnerQuery = function (
  accountQuery,
  tsQuery,
  resultTypeQuery,
  limitQuery,
  creditDebitQuery,
  transactionTypeQuery,
  order
) {
  // convert the mysql style '?' placeholder to the named parameter format, later the same named parameter is converted
  // to the same positional index, thus the caller only has to pass the value once for the same column
  const namedAccountQuery = convertToNamedQuery(accountQuery, 'acct');
  const namedTsQuery = convertToNamedQuery(tsQuery, 'ts');
  const namedLimitQuery = convertToNamedQuery(limitQuery, 'limit');
  const namedCreditDebitQuery = convertToNamedQuery(creditDebitQuery, 'cd');

  const transactionAccountQuery = namedAccountQuery
    ? `${namedAccountQuery.replace(/ctl\.entity_id/g, 't.payer_account_id')}
      and (${transactionByPayerExcludeSyntheticCondition})`
    : '';
  const transactionWhereClause = buildWhereClause(
    transactionAccountQuery,
    namedTsQuery,
    resultTypeQuery,
    transactionTypeQuery
  );
  const transactionOnlyLimitQuery = _.isNil(namedLimitQuery) ? '' : namedLimitQuery;
  const transactionOnlyQuery = _.isEmpty(transactionWhereClause)
    ? undefined
    : `select ${Transaction.CONSENSUS_TIMESTAMP}
    from ${Transaction.tableName} as ${Transaction.tableAlias}
    ${transactionWhereClause}
    order by ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} ${order}
    ${transactionOnlyLimitQuery}`;

  if (creditDebitQuery || namedAccountQuery) {
    const ctlQuery = getTransferDistinctTimestampsQuery(
      CryptoTransfer.tableName,
      'ctl',
      namedTsQuery,
      CryptoTransfer.CONSENSUS_TIMESTAMP,
      resultTypeQuery,
      transactionTypeQuery,
      namedAccountQuery,
      namedCreditDebitQuery,
      order,
      namedLimitQuery
    );

    const namedTtlAccountQuery = namedAccountQuery.replace(/ctl\.entity_id/g, 'ttl.account_id');
    const namedTtlCreditDebitQuery = namedCreditDebitQuery.replace(/ctl\.amount/g, 'ttl.amount');
    const ttlQuery = getTransferDistinctTimestampsQuery(
      TokenTransfer.tableName,
      'ttl',
      namedTsQuery,
      TokenTransfer.CONSENSUS_TIMESTAMP,
      resultTypeQuery,
      transactionTypeQuery,
      namedTtlAccountQuery,
      namedTtlCreditDebitQuery,
      order,
      namedLimitQuery
    );

    if (creditDebitQuery) {
      // credit/debit filter applies to crypto_transfer.amount and token_transfer.amount, a full outer join is needed to get
      // transactions that only have a crypto_transfer or a token_transfer
      return `
        SELECT COALESCE(ctl.consensus_timestamp, ttl.consensus_timestamp) AS consensus_timestamp
        FROM (${ctlQuery}) AS ctl
        FULL OUTER JOIN (${ttlQuery}) as ttl
        ON ctl.consensus_timestamp = ttl.consensus_timestamp
        ORDER BY consensus_timestamp ${order}
        ${namedLimitQuery}`;
    }

    // account filter applies to transaction.payer_account_id, crypto_transfer.entity_id, nft_transfer.account_id,
    // and token_transfer.account_id, a full outer join between the four tables is needed to get rows that may only exist in one.
    return `
      SELECT coalesce(t.consensus_timestamp, ctl.consensus_timestamp, ttl.consensus_timestamp) AS consensus_timestamp
      FROM (${transactionOnlyQuery}) AS ${Transaction.tableAlias}
      FULL OUTER JOIN (${ctlQuery}) AS ctl
      ON ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} = ctl.consensus_timestamp
      FULL OUTER JOIN (${ttlQuery}) AS ttl
      ON coalesce(${Transaction.getFullName(
        Transaction.CONSENSUS_TIMESTAMP
      )}, ctl.consensus_timestamp) = ttl.consensus_timestamp
      order by consensus_timestamp ${order}
      ${namedLimitQuery}`;
  }

  return transactionOnlyQuery;
};

const reqToSql = function (req) {
  // Parse the filter parameters for account-numbers, timestamp, credit/debit, and pagination (limit)
  const parsedQueryParams = req.query;
  const sqlParams = [];
  let [accountQuery, accountParams] = utils.parseAccountIdQueryParam(parsedQueryParams, 'ctl.entity_id');
  accountQuery = utils.convertMySqlStyleQueryToPostgres(accountQuery, sqlParams.length + 1);
  sqlParams.push(...accountParams);
  let [tsQuery, tsParams] = utils.parseTimestampQueryParam(
    parsedQueryParams,
    Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)
  );
  tsQuery = utils.convertMySqlStyleQueryToPostgres(tsQuery, sqlParams.length + 1);
  sqlParams.push(...tsParams);
  let [creditDebitQuery, creditDebitParams] = utils.parseCreditDebitParams(parsedQueryParams, 'ctl.amount');
  creditDebitQuery = utils.convertMySqlStyleQueryToPostgres(creditDebitQuery, sqlParams.length + 1);
  sqlParams.push(...creditDebitParams);
  const resultTypeQuery = utils.parseResultParams(req, Transaction.getFullName(Transaction.RESULT));
  const transactionTypeQuery = utils.parseTransactionTypeParam(parsedQueryParams);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);
  sqlParams.push(...params);

  const innerQuery = getTransactionsInnerQuery(
    accountQuery,
    tsQuery,
    resultTypeQuery,
    query,
    creditDebitQuery,
    transactionTypeQuery,
    order
  );
  const sqlQuery = getTransactionsOuterQuery(innerQuery, order);

  return {
    limit,
    query: utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams.length),
    order,
    params: sqlParams,
  };
};

/**
 * Handler function for /transactions API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getTransactions = async (req, res) => {
  // Validate query parameters first
  utils.validateReq(req, acceptedTransactionParameters);

  const query = reqToSql(req);
  if (logger.isTraceEnabled()) {
    logger.trace(`getTransactions query: ${query.query} ${utils.JSONStringify(query.params)}`);
  }

  // Execute query
  const {rows, sqlQuery} = await pool.queryQuietly(query.query, query.params);
  const transferList = await createTransferLists(rows);
  const ret = {
    transactions: transferList.transactions,
  };

  ret.links = {
    next: utils.getPaginationLink(
      req,
      ret.transactions.length !== query.limit,
      {
        [constants.filterKeys.TIMESTAMP]: transferList.anchorSecNs,
      },
      query.order
    ),
  };

  if (utils.isTestEnv()) {
    ret.sqlQuery = sqlQuery;
  }

  logger.debug(`getTransactions returning ${ret.transactions.length} entries`);
  res.locals[constants.responseDataLabel] = ret;
};

// The first part of the regex is for the base64url encoded 48-byte transaction hash. Note base64url replaces '+' with
// '-' and '/' with '_'. The padding character '=' is not included since base64 encoding a 48-byte array always
// produces a 64-byte string without padding
const transactionHashRegex = /^([\dA-Za-z+\-\/_]{64}|(0x)?[\dA-Fa-f]{96})$/;

const isValidTransactionHash = (hash) => transactionHashRegex.test(hash);

const transactionHashQuery = `
  select ${TransactionHash.CONSENSUS_TIMESTAMP}, ${TransactionHash.PAYER_ACCOUNT_ID}
  from ${TransactionHash.tableName}
  where ${TransactionHash.HASH} = $1
  order by ${TransactionHash.CONSENSUS_TIMESTAMP}`;

/**
 * Get the query for either getting transaction by id or getting transaction by payer account id and a list of
 * consensus timestamps
 *
 * @param {string} mainCondition conditions for the main query
 * @param {string} subQueryCondition conditions for the transfer table subquery
 * @return {string} The query
 */
const getTransactionQuery = (mainCondition, subQueryCondition) => {
  return `
    select
    ${transactionFullFields},
    (
      select ${cryptoTransferJsonAgg}
      from ${CryptoTransfer.tableName} ${CryptoTransfer.tableAlias}
      where ${CryptoTransfer.CONSENSUS_TIMESTAMP} = t.consensus_timestamp and ${subQueryCondition}
    ) as crypto_transfer_list,
    (
      select ${tokenTransferJsonAgg}
      from ${TokenTransfer.tableName} ${TokenTransfer.tableAlias}
      where ${TokenTransfer.CONSENSUS_TIMESTAMP} = t.consensus_timestamp and ${subQueryCondition}
    ) as token_transfer_list,
    (
      select ${nftTransferJsonAgg}
      from ${NftTransfer.tableName} ${NftTransfer.tableAlias}
      where ${NftTransfer.CONSENSUS_TIMESTAMP} = t.consensus_timestamp and ${subQueryCondition}
    ) as nft_transfer_list,
    (
      select ${assessedCustomFeeJsonAgg}
      from ${AssessedCustomFee.tableName} ${AssessedCustomFee.tableAlias}
      where ${AssessedCustomFee.CONSENSUS_TIMESTAMP} = t.consensus_timestamp and ${subQueryCondition}
    ) as assessed_custom_fees
  from ${Transaction.tableName} ${Transaction.tableAlias}
  where ${mainCondition}
  order by ${Transaction.CONSENSUS_TIMESTAMP}`;
};

/**
 * Extracts the sql query and params for transactions request by transaction id
 *
 * @param {String} transactionIdOrHash
 * @param {Array} filters
 * @return {{query: string, params: *[]}}
 */
const extractSqlFromTransactionsByIdOrHashRequest = async (transactionIdOrHash, filters) => {
  const mainConditions = [];
  const commonConditions = [];
  const params = [];

  if (isValidTransactionHash(transactionIdOrHash)) {
    const encoding = transactionIdOrHash.length === Transaction.BASE64_HASH_SIZE ? 'base64url' : 'hex';
    if (transactionIdOrHash.length === Transaction.HEX_HASH_WITH_PREFIX_SIZE) {
      transactionIdOrHash = transactionIdOrHash.substring(2);
    }

    const transactionHash = Buffer.from(transactionIdOrHash, encoding);
    if (logger.isTraceEnabled()) {
      logger.trace(`transactionHashQuery: ${transactionHashQuery}, ${utils.JSONStringify(transactionHash)}`);
    }

    const {rows} = await pool.queryQuietly(transactionHashQuery, [transactionHash]);
    if (rows.length === 0) {
      throw new NotFoundError();
    }

    if (rows[0].payer_account_id !== null) {
      params.push(rows[0].payer_account_id); // all rows should have the same payer account id
      commonConditions.push(`${Transaction.PAYER_ACCOUNT_ID} = $1`);
    }

    const minTimestampPosition = params.length + 1;
    const timestampPositions = rows
      .map((row) => params.push(row.consensus_timestamp))
      .map((pos) => `$${pos}`)
      .join(',');
    mainConditions.push(`${Transaction.CONSENSUS_TIMESTAMP} in (${timestampPositions})`);
    // timestamp range condition
    commonConditions.push(
      `${Transaction.CONSENSUS_TIMESTAMP} >= $${minTimestampPosition}`,
      `${Transaction.CONSENSUS_TIMESTAMP} <= $${params.length}`
    );
  } else {
    // try to parse it as a transaction id
    const transactionId = TransactionId.fromString(transactionIdOrHash);
    const maxConsensusTimestamp = BigInt(transactionId.getValidStartNs()) + maxTransactionConsensusTimestampRangeNs;
    params.push(transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs(), maxConsensusTimestamp);
    commonConditions.push(
      `${Transaction.PAYER_ACCOUNT_ID} = $1`,
      // timestamp range conditions
      `${Transaction.CONSENSUS_TIMESTAMP} >= $2`,
      `${Transaction.CONSENSUS_TIMESTAMP} <= $3`
    );
    mainConditions.push(`${Transaction.VALID_START_NS} = $2`);

    // only parse nonce and scheduled query filters if the path parameter is transaction id
    let nonce;
    let scheduled;
    for (const filter of filters) {
      // honor the last for both nonce and scheduled
      switch (filter.key) {
        case constants.filterKeys.NONCE:
          nonce = filter.value;
          break;
        case constants.filterKeys.SCHEDULED:
          scheduled = filter.value;
          break;
        default:
          break;
      }
    }

    if (nonce !== undefined) {
      params.push(nonce);
      mainConditions.push(`${Transaction.NONCE} = $${params.length}`);
    }

    if (scheduled !== undefined) {
      params.push(scheduled);
      mainConditions.push(`${Transaction.SCHEDULED} = $${params.length}`);
    }
  }

  mainConditions.unshift(...commonConditions);
  return {query: getTransactionQuery(mainConditions.join(' and '), commonConditions.join(' and ')), params};
};

/**
 * Handler function for /transactions/:transactionIdOrHash API.
 * @param {Request} req HTTP request object
 * @return {Promise<None>}
 */
const getTransactionsByIdOrHash = async (req, res) => {
  const filters = utils.buildAndValidateFilters(req.query, acceptedSingleTransactionParameters);
  const {query, params} = await extractSqlFromTransactionsByIdOrHashRequest(req.params.transactionIdOrHash, filters);
  if (logger.isTraceEnabled()) {
    logger.trace(`getTransactionsByIdOrHash query: ${query} ${utils.JSONStringify(params)}`);
  }

  // Execute query
  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length === 0) {
    throw new NotFoundError();
  }

  const transferList = await createTransferLists(rows);

  logger.debug(`getTransactionsByIdOrHash returning ${transferList.transactions.length} entries`);
  res.locals[constants.responseDataLabel] = {
    transactions: transferList.transactions,
  };
};

const transactions = {
  createTransferLists,
  getTransactions,
  getTransactionsByIdOrHash,
  getTransactionsInnerQuery,
  getTransactionsOuterQuery,
};

const acceptedTransactionParameters = new Set([
  constants.filterKeys.ACCOUNT_ID,
  constants.filterKeys.CREDIT_TYPE,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.RESULT,
  constants.filterKeys.TIMESTAMP,
  constants.filterKeys.TRANSACTION_TYPE,
]);

const acceptedSingleTransactionParameters = new Set([constants.filterKeys.NONCE, constants.filterKeys.SCHEDULED]);

if (utils.isTestEnv()) {
  Object.assign(transactions, {
    buildWhereClause,
    convertStakingRewardTransfers,
    createAssessedCustomFeeList,
    createCryptoTransferList,
    createNftTransferList,
    createStakingRewardTransferList,
    createTokenTransferList,
    extractSqlFromTransactionsByIdOrHashRequest,
    getStakingRewardTimestamps,
    isValidTransactionHash,
    reqToSql,
  });
}

export default transactions;
