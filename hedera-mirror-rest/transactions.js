/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
 *
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
 */

import _ from 'lodash';
import {Range} from 'pg-range';

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

const {
  query: {maxTransactionConsensusTimestampRangeNs},
  response: {
    limit: {default: defaultResponseLimit},
  },
} = config;

const transactionFields = [
  Transaction.CHARGED_TX_FEE,
  Transaction.CONSENSUS_TIMESTAMP,
  Transaction.ENTITY_ID,
  Transaction.MAX_FEE,
  Transaction.MEMO,
  Transaction.NFT_TRANSFER,
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

const cryptoTransferJsonAgg = `jsonb_agg(jsonb_build_object(
    '${CryptoTransfer.AMOUNT}', ${CryptoTransfer.AMOUNT},
    '${CryptoTransfer.ENTITY_ID}', ${CryptoTransfer.ENTITY_ID},
    '${CryptoTransfer.IS_APPROVAL}', ${CryptoTransfer.IS_APPROVAL}
  ) order by ${CryptoTransfer.ENTITY_ID}, ${CryptoTransfer.AMOUNT})`;

const tokenTransferJsonAgg = `jsonb_agg(jsonb_build_object(
    '${TokenTransfer.ACCOUNT_ID}', ${TokenTransfer.ACCOUNT_ID},
    '${TokenTransfer.AMOUNT}', ${TokenTransfer.AMOUNT},
    '${TokenTransfer.TOKEN_ID}', ${TokenTransfer.TOKEN_ID},
    '${TokenTransfer.IS_APPROVAL}', ${TokenTransfer.IS_APPROVAL}
  ) order by ${TokenTransfer.TOKEN_ID}, ${TokenTransfer.ACCOUNT_ID})`;

const assessedCustomFeeJsonAgg = `jsonb_agg(jsonb_build_object(
    '${AssessedCustomFee.AMOUNT}', ${AssessedCustomFee.AMOUNT},
    '${AssessedCustomFee.COLLECTOR_ACCOUNT_ID}', ${AssessedCustomFee.COLLECTOR_ACCOUNT_ID},
    '${AssessedCustomFee.EFFECTIVE_PAYER_ACCOUNT_IDS}', ${AssessedCustomFee.EFFECTIVE_PAYER_ACCOUNT_IDS},
    '${AssessedCustomFee.TOKEN_ID}', ${AssessedCustomFee.TOKEN_ID}
  ) order by ${AssessedCustomFee.COLLECTOR_ACCOUNT_ID}, ${AssessedCustomFee.AMOUNT})`;

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
    return [];
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
    return [];
  }

  return nftTransferList.map((transfer) => {
    const nftTransfer = new NftTransfer(transfer);
    return new NftTransferViewModel(nftTransfer);
  });
};

/**
 * Format the output of the SQL query as an array of transaction objects per the view model.
 *
 * @param rows Array of rows returned as a result of the SQL query
 * @return An array of transaction objects
 */
const formatTransactionRows = async (rows) => {
  const stakingRewardMap = await createStakingRewardTransferList(rows);
  return rows.map((row) => {
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
      nft_transfers: createNftTransferList(row.nft_transfer),
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
 * Get the first transaction's consensus timestamp from the database. Note the db query runs once and the timestamp is
 * cached for subsequent calls.
 *
 * @return {Promise<bigint>} the first transaction's consensus timestamp
 */
const getFirstTransactionTimestamp = (() => {
  let timestamp;

  const func = async () => {
    if (timestamp === undefined) {
      const {rows} = await pool.queryQuietly(`select consensus_timestamp
        from transaction
        order by consensus_timestamp
        limit 1`);
      if (rows.length !== 1) {
        return 0n; // fallback to 0
      }

      timestamp = rows[0].consensus_timestamp;
      logger.info(`First transaction's consensus timestamp is ${timestamp}`);
    }

    return timestamp;
  };

  if (utils.isTestEnv()) {
    func.reset = () => (timestamp = undefined);
  }

  return func;
})();

/**
 * If enabled in config, ensure the returned timestamp range is fully bound; contains both a begin
 * and end timestamp value. The provided Range is not modified. If changes are made a copy is returned.
 *
 * @param {Range} range timestamp range, typically based on query parameters. Note the bounds should be '[]'
 * @param {string} order the order in the http request
 * @return {Range} fully bound timestamp range
 */
const bindTimestampRange = async (range, order) => {
  const {bindTimestampRange, maxTransactionsTimestampRangeNs} = config.query;
  if (!bindTimestampRange) {
    return range;
  }

  const boundRange = Range(range?.begin ?? (await getFirstTransactionTimestamp()), range?.end ?? utils.nowInNs(), '[]');
  if (boundRange.end - boundRange.begin + 1n <= maxTransactionsTimestampRangeNs) {
    return boundRange;
  }

  if (order === constants.orderFilterValues.DESC) {
    boundRange.begin = boundRange.end - maxTransactionsTimestampRangeNs + 1n;
  } else {
    boundRange.end = boundRange.begin + maxTransactionsTimestampRangeNs - 1n;
  }

  return boundRange;
};

/**
 * Build the where clause from an array of query conditions
 *
 * @param {string} conditions Query conditions
 * @return {string} The where clause built from the query conditions
 */
const buildWhereClause = function (...conditions) {
  const clause = conditions.filter((q) => q !== '').join(' and ');
  return clause === '' ? '' : `where ${clause}`;
};

/**
 * Get the query to find distinct timestamps for transactions matching the query filters from a transfers table when filtering by an account id
 *
 * @param {string} tableName - Name of the transfers table to query
 * @param {string} tableAlias - Alias to reference the table by
 * @param timestampQuery - Transaction table timestamp query with named parameters
 * @param resultTypeQuery - Transaction result query
 * @param transactionTypeQuery - Transaction type query
 * @param accountQuery - Account query with named parameters
 * @param creditDebitQuery - Credit/debit query
 * @param order - Sorting order
 * @param limitQuery - Limit query with named parameters
 * @return {string} - The distinct timestamp query for the given table
 */
const getTransferDistinctTimestampsQuery = (
  tableName,
  tableAlias,
  timestampQuery,
  resultTypeQuery,
  transactionTypeQuery,
  accountQuery,
  creditDebitQuery,
  order,
  limitQuery
) => {
  const fullTimestampColumn = `${tableAlias}.consensus_timestamp`;
  const fullPayerAccountIdColumn = `${tableAlias}.payer_account_id`;
  const transferTimestampQuery = timestampQuery.replace(/t\.consensus_timestamp/g, `${fullTimestampColumn}`);
  const joinClause =
    (resultTypeQuery || transactionTypeQuery) &&
    `join ${Transaction.tableName} as ${Transaction.tableAlias}
      on ${fullTimestampColumn} = ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} and
        ${fullPayerAccountIdColumn} = ${Transaction.getFullName(Transaction.PAYER_ACCOUNT_ID)}`;
  const whereClause = buildWhereClause(
    accountQuery,
    transferTimestampQuery,
    resultTypeQuery,
    transactionTypeQuery,
    creditDebitQuery
  );

  return `
    select
      distinct ${fullTimestampColumn} as consensus_timestamp,
      ${fullPayerAccountIdColumn} as payer_account_id
    from ${tableName} as ${tableAlias}
    ${joinClause}
    ${whereClause}
    order by ${fullTimestampColumn} ${order}
    ${limitQuery}`;
};

// the condition to exclude synthetic transactions attached to a user submitted transaction
const transactionByPayerExcludeSyntheticCondition = `${Transaction.getFullName(Transaction.NONCE)} = 0 or
  ${Transaction.getFullName(Transaction.PARENT_CONSENSUS_TIMESTAMP)} is not null`;

const getQueryWithEqualValues = (column, params, values) => {
  if (values.length === 0) {
    return '';
  }

  return values.length === 1 ? `${column} = $${params.push(values[0])}` : `${column} = any($${params.push(values)})`;
};

const extractSqlFromTransactionsRequest = (filters) => {
  const accountConditions = [];
  const accountIdEqValues = [];
  let creditDebitQuery = '';
  let lastCreditDebitValue = null;
  let limit = defaultResponseLimit;
  let order = constants.orderFilterValues.DESC;
  const params = [];
  let resultType = null;
  let resultTypeQuery = '';
  const transactionTypes = [];

  for (const filter of filters) {
    const {key, operator, value} = filter;
    switch (key) {
      case constants.filterKeys.ACCOUNT_ID:
        if (operator === utils.opsMap.eq) {
          accountIdEqValues.push(value);
        } else {
          accountConditions.push(`ctl.entity_id${operator}$${params.push(value)}`);
        }
        break;
      case constants.filterKeys.CREDIT_TYPE:
        if (lastCreditDebitValue !== null && lastCreditDebitValue !== value) {
          return null;
        }
        lastCreditDebitValue = value;
        break;
      case constants.filterKeys.LIMIT:
        limit = value;
        break;
      case constants.filterKeys.ORDER:
        order = value;
        break;
      case constants.filterKeys.RESULT:
        resultType = value;
        break;
      case constants.filterKeys.TRANSACTION_TYPE:
        transactionTypes.push(TransactionType.getProtoId(value));
        break;
    }
  }

  accountConditions.push(getQueryWithEqualValues('ctl.entity_id', params, accountIdEqValues));
  const accountQuery = accountConditions.filter(Boolean).join(' and ');

  if (lastCreditDebitValue) {
    const operator = lastCreditDebitValue.toLowerCase() === constants.cryptoTransferType.CREDIT ? '>' : '<';
    creditDebitQuery = `ctl.amount ${operator} 0`;
  }

  if (resultType) {
    const operator = resultType === constants.transactionResultFilter.SUCCESS ? '=' : '<>';
    resultTypeQuery = `t.result ${operator} $${params.push(utils.resultSuccess)}`;
  }

  const transactionTypeQuery = getQueryWithEqualValues('type', params, transactionTypes);
  const limitQuery = `limit $${params.push(limit)}`;

  return {
    accountQuery,
    creditDebitQuery,
    limit,
    limitQuery,
    order,
    params,
    resultTypeQuery,
    transactionTypeQuery,
  };
};

/**
 * @param filters The filters from the http request
 * @param timestampRange the timestamp range object
 * @return {Promise} the Promise for obtaining the results of the query
 */
const getTransactionTimestamps = async (filters, timestampRange) => {
  if (timestampRange.eqValues.length > 1 || timestampRange.range?.isEmpty()) {
    return {rows: []};
  }

  const result = extractSqlFromTransactionsRequest(filters);
  if (result === null) {
    return {rows: []};
  }
  const {accountQuery, creditDebitQuery, limit, limitQuery, order, resultTypeQuery, transactionTypeQuery, params} =
    result;

  if (timestampRange.eqValues.length === 0) {
    timestampRange.range = await bindTimestampRange(timestampRange.range, order);
  }

  let [timestampQuery, timestampParams] = utils.buildTimestampQuery('t.consensus_timestamp', timestampRange);
  timestampQuery = utils.convertMySqlStyleQueryToPostgres(timestampQuery, params.length + 1);
  params.push(...timestampParams);

  const query = getTransactionTimestampsQuery(
    accountQuery,
    timestampQuery,
    resultTypeQuery,
    limitQuery,
    creditDebitQuery,
    transactionTypeQuery,
    order
  );
  const {rows} = await pool.queryQuietly(query, params);

  return {limit, order, rows};
};

/**
 *
 *
 * @param {String} accountQuery SQL query that filters based on the account ids
 * @param {String} timestampQuery SQL query that filters based on the timestamps for transaction table
 * @param {String} resultTypeQuery SQL query that filters based on the result types
 * @param {String} limitQuery SQL query that limits the number of unique transactions returned
 * @param {String} creditDebitQuery SQL query that filters for credit/debit transactions
 * @param {String} transactionTypeQuery SQL query that filters by transaction type
 * @param {String} order Sorting order
 * @return {String} query to retrieve relevant timestamp and payer ID information
 */
const getTransactionTimestampsQuery = (
  accountQuery,
  timestampQuery,
  resultTypeQuery,
  limitQuery,
  creditDebitQuery,
  transactionTypeQuery,
  order
) => {
  const transactionAccountQuery = accountQuery
    ? `${accountQuery.replace(/ctl\.entity_id/g, 't.payer_account_id')}
      and (${transactionByPayerExcludeSyntheticCondition})`
    : '';
  const transactionWhereClause = buildWhereClause(
    transactionAccountQuery,
    timestampQuery,
    resultTypeQuery,
    transactionTypeQuery
  );
  const transactionOnlyQuery = `select ${Transaction.CONSENSUS_TIMESTAMP}, ${Transaction.PAYER_ACCOUNT_ID}
    from ${Transaction.tableName} as ${Transaction.tableAlias}
    ${transactionWhereClause}
    order by ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} ${order}
    ${limitQuery}`;

  if (creditDebitQuery || accountQuery) {
    const cryptoTransferQuery = getTransferDistinctTimestampsQuery(
      CryptoTransfer.tableName,
      'ctl',
      timestampQuery,
      resultTypeQuery,
      transactionTypeQuery,
      accountQuery,
      creditDebitQuery,
      order,
      limitQuery
    );

    const tokenTransferQuery = getTransferDistinctTimestampsQuery(
      TokenTransfer.tableName,
      'ttl',
      timestampQuery,
      resultTypeQuery,
      transactionTypeQuery,
      accountQuery.replace(/ctl\.entity_id/g, 'ttl.account_id'),
      creditDebitQuery.replace(/ctl\.amount/g, 'ttl.amount'),
      order,
      limitQuery
    );

    if (creditDebitQuery) {
      // credit/debit filter applies to crypto_transfer.amount and token_transfer.amount, a full outer join is needed to get
      // transactions that only have a crypto_transfer or a token_transfer
      return `
        select
          coalesce(ctl.consensus_timestamp, ttl.consensus_timestamp) as consensus_timestamp,
          coalesce(ctl.payer_account_id, ttl.payer_account_id) as payer_account_id
        from (${cryptoTransferQuery}) as ctl
        full outer join (${tokenTransferQuery}) as ttl
          on ctl.consensus_timestamp = ttl.consensus_timestamp
        order by consensus_timestamp ${order}
        ${limitQuery}`;
    }

    // account filter applies to transaction.payer_account_id, crypto_transfer.entity_id,
    // and token_transfer.account_id, a full outer join between the four tables is needed to get rows that may only exist in one.
    return `
      select
        coalesce(t.consensus_timestamp, ctl.consensus_timestamp, ttl.consensus_timestamp) as consensus_timestamp,
        coalesce(t.payer_account_id, ctl.payer_account_id, ttl.payer_account_id) as payer_account_id
      from (${transactionOnlyQuery}) as t
      full outer join (${cryptoTransferQuery}) as ctl
        on t.consensus_timestamp = ctl.consensus_timestamp
      full outer join (${tokenTransferQuery}) as ttl
        on coalesce(t.consensus_timestamp, ctl.consensus_timestamp) = ttl.consensus_timestamp
      order by consensus_timestamp ${order}
      ${limitQuery}`;
  }

  return transactionOnlyQuery;
};

/**
 * Get the transaction details given the payer account ids and consensus timestamps
 *
 * @param {{Object}[]} payerAndTimestamps The transaction payer and consensus timestamp pairs
 * @param {String} order Sorting order
 * @return {Promise} Promise returning the transaction details
 */
const getTransactionsDetails = async (payerAndTimestamps, order) => {
  if (payerAndTimestamps.length === 0) {
    return {rows: []};
  }

  const payerAccountIds = new Set();
  const timestamps = [];
  payerAndTimestamps.forEach((row) => {
    timestamps.push(row.consensus_timestamp);
    payerAccountIds.add(row.payer_account_id);
  });

  const params = [];
  const payerAccountIdsCondition = getQueryWithEqualValues('payer_account_id', params, Array.from(payerAccountIds));
  const timestampsCondition = getQueryWithEqualValues('consensus_timestamp', params, timestamps);
  const outerPayerAccountIdsCondition = 't.' + payerAccountIdsCondition;
  const outerTimestampsCondition = 't.' + timestampsCondition;

  const query = `with c_list as (
      select
        consensus_timestamp,
        payer_account_id,
        ${cryptoTransferJsonAgg} as crypto_transfer_list
      from crypto_transfer
      where ${payerAccountIdsCondition} and ${timestampsCondition}
      group by consensus_timestamp, payer_account_id
    ), t_list as (
      select
        consensus_timestamp,
        payer_account_id,
        ${tokenTransferJsonAgg} as token_transfer_list
      from token_transfer
      where ${payerAccountIdsCondition} and ${timestampsCondition}
      group by consensus_timestamp, payer_account_id
    )
    select
      ${transactionFullFields},
      (select crypto_transfer_list from c_list where consensus_timestamp = t.consensus_timestamp and payer_account_id = t.payer_account_id),
      (select token_transfer_list from t_list where consensus_timestamp = t.consensus_timestamp and payer_account_id = t.payer_account_id)
    from transaction as t
    where ${outerPayerAccountIdsCondition} and ${outerTimestampsCondition}
    order by t.consensus_timestamp ${order}`;

  return pool.queryQuietly(query, params);
};

/**
 * Handler function for /transactions API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {Promise}
 */
const getTransactions = async (req, res) => {
  const filters = utils.buildAndValidateFilters(req.query, acceptedTransactionParameters);
  const timestampFilters = filters.filter((filter) => filter.key === constants.filterKeys.TIMESTAMP);
  const timestampRange = utils.parseTimestampFilters(timestampFilters, false, true, true, false, false);

  res.locals[constants.responseDataLabel] = await doGetTransactions(filters, req, timestampRange);
};

/**
 * Get transactions per the http request.
 *
 * @param {Object[]} filters The filters extracted from the http request
 * @param {Request} req The http request
 * @param timestampRange The timestamp range parsed from the http request
 * @returns {Promise<{links: {next: String}, transactions: *}>}
 */
const doGetTransactions = async (filters, req, timestampRange) => {
  const {limit, order, rows: payAndTimestamps} = await getTransactionTimestamps(filters, timestampRange);
  const {rows} = await getTransactionsDetails(payAndTimestamps, order);

  const transactions = await formatTransactionRows(rows);
  const next = utils.getPaginationLink(
    req,
    transactions.length !== limit,
    {
      [constants.filterKeys.TIMESTAMP]: transactions[transactions.length - 1]?.consensus_timestamp,
    },
    order
  );
  return {
    transactions,
    links: {next},
  };
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

const transactionHashShardedQuery = `select ${TransactionHash.CONSENSUS_TIMESTAMP}, ${TransactionHash.PAYER_ACCOUNT_ID}
                                     from get_transaction_info_by_hash($1)`;

const transactionHashShardedQueryEnabled = (() => {
  let result = undefined;
  return () =>
    (async () => {
      if (result !== undefined) {
        return result;
      }

      const {rows} = await pool.queryQuietly(`select count(*) > 0 as enabled
                       from pg_proc
                       where proname = 'get_transaction_info_by_hash'`);
      result = rows[0].enabled;
      return result;
    })();
})();

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
      from ${CryptoTransfer.tableName}
      where ${CryptoTransfer.CONSENSUS_TIMESTAMP} = t.consensus_timestamp and ${subQueryCondition}
    ) as crypto_transfer_list,
    (
      select ${tokenTransferJsonAgg}
      from ${TokenTransfer.tableName}
      where ${TokenTransfer.CONSENSUS_TIMESTAMP} = t.consensus_timestamp and ${subQueryCondition}
    ) as token_transfer_list,
    (
      select ${assessedCustomFeeJsonAgg}
      from ${AssessedCustomFee.tableName}
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

    const v1ShardQueryEnabled = await transactionHashShardedQueryEnabled();
    const usedTransactionHashQuery = v1ShardQueryEnabled ? transactionHashShardedQuery : transactionHashQuery;
    const transactionHash = Buffer.from(transactionIdOrHash, encoding);

    const {rows} = await pool.queryQuietly(usedTransactionHashQuery, [transactionHash]);
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

  // Execute query
  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length === 0) {
    throw new NotFoundError();
  }

  const transactions = await formatTransactionRows(rows);

  logger.debug(`getTransactionsByIdOrHash returning ${transactions.length} entries`);
  res.locals[constants.responseDataLabel] = {
    transactions,
  };
};

const transactions = {
  doGetTransactions,
  getTransactions,
  getTransactionsByIdOrHash,
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
    bindTimestampRange,
    buildWhereClause,
    convertStakingRewardTransfers,
    createAssessedCustomFeeList,
    createCryptoTransferList,
    createNftTransferList,
    createStakingRewardTransferList,
    createTokenTransferList,
    extractSqlFromTransactionsByIdOrHashRequest,
    extractSqlFromTransactionsRequest,
    formatTransactionRows,
    getFirstTransactionTimestamp,
    getStakingRewardTimestamps,
    isValidTransactionHash,
  });
}

export default transactions;
