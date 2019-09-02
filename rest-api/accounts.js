/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
const utils = require('./utils.js');
const transactions = require('./transactions.js');

const ACCOUNT_ENTITY_TYPE = 1;

/**
 * Processes one row of the results of the SQL query and format into API return format
 * @param {Object} row One row of the SQL query result
 * @return {Object} accRecord Processed account record
 */
const processRow = function (row) {
    let accRecord = {};
    accRecord.balance = {};
    accRecord.account = row.entity_shard + '.' + row.entity_realm + '.' + row.entity_num;
    accRecord.balance.timestamp = (row.consensus_timestamp === null) ? null : utils.nsToSecNs(row.consensus_timestamp);
    accRecord.balance.balance = (row.account_balance === null) ? null : Number(row.account_balance);
    accRecord.expiry_timestamp = (row.exp_time_ns === null) ? null : utils.nsToSecNs(row.exp_time_ns);
    accRecord.auto_renew_period = (row.auto_renew_period=== null) ? null : Number(row.auto_renew_period);
    accRecord.admin_key = (row.admin_key === null) ? null : utils.encodeKey(row.admin_key);
    accRecord.key = (row.key === null) ? null : utils.encodeKey(row.key);
    accRecord.deleted = row.deleted;
    accRecord.entity_type = row.entity_type;

    return (accRecord);
}

const getAccountQueryPrefix = function() {
    const prefix = 
        "select ab.balance as account_balance\n" +
        "    , ab.consensus_timestamp as consensus_timestamp\n" +
        "    , " + process.env.SHARD_NUM + " as entity_shard\n" +
        "    , coalesce(ab.account_realm_num, e.entity_realm) as entity_realm\n" +
        "    , coalesce(ab.account_num, e.entity_num) as entity_num\n" +
        "    , e.exp_time_ns\n" +
        "    , e.auto_renew_period\n" +
        "    , e.admin_key\n" +
        "    , e.key\n" +
        "    , e.deleted\n" +
        "    , et.name as entity_type\n" +
        "from (\n" +
        "    select * from account_balances\n" +
        "    where consensus_timestamp = (select max(consensus_timestamp) from account_balances)\n" +
        ") ab\n" +
        "full outer join t_entities e\n" +
        "    on (ab.account_realm_num = e.entity_realm\n" +
        "        and ab.account_num =  e.entity_num\n" +
        "        and e.fk_entity_type_id = " + ACCOUNT_ENTITY_TYPE + ")\n" +
        "join t_entity_types et\n" +
        "    on et.id = " + ACCOUNT_ENTITY_TYPE + "\n" +
        "where 1=1\n";

    return (prefix);
}

/**
 * Handler function for /accounts API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getAccounts = function (req) {
    // Parse the filter parameters for account-numbers, balances, publicKey and pagination

    // Because of the outer join on the 'account_balances ab' and 't_entities e' below, we 
    // need to look  for the given account.id in both account_balances and t_entities table and combine with an 'or'
    const [accountQueryForAccountBalances, accountParamsForAccountBalances] =
        utils.parseParams(req, 'account.id',
            [{ shard: process.env.SHARD_NUM, realm: 'ab.account_realm_num', num: 'ab.account_num' }],
            'entityId');
    const [accountQueryForEntity, accountParamsForEntity] =
        utils.parseParams(req, 'account.id',
            [{ shard: process.env.SHARD_NUM, realm: 'e.entity_realm', num: ' e.entity_num' }],
            'entityId');
    const accountQuery = accountQueryForAccountBalances === '' ? '' :
        "(\n" + 
        "    " + accountQueryForAccountBalances + "\n" +
        "    or (" + accountQueryForEntity + " and e.fk_entity_type_id = " + ACCOUNT_ENTITY_TYPE + ")\n" +
        ")\n";

    const [balanceQuery, balanceParams] = utils.parseParams(req, 'account.balance',
        ['ab.balance']);

    let [pubKeyQuery, pubKeyParams] = utils.parseParams(req, 'account.publickey',
        ['e.key'], 'hexstring');

    const { limitQuery, limitParams, order, limit } =
        utils.parseLimitAndOrderParams(req, 'asc');

    const entitySql = getAccountQueryPrefix() +
        "    and \n" +
        [accountQuery, balanceQuery, pubKeyQuery].map(q => q === '' ? '1=1' : q).join(' and ') +
        " order by coalesce(ab.account_num, e.entity_num) " + order + "\n" +
        limitQuery;

    const entityParams = accountParamsForAccountBalances
        .concat(accountParamsForEntity)
        .concat(balanceParams)
        .concat(pubKeyParams)
        .concat(limitParams);

    const pgEntityQuery = utils.convertMySqlStyleQueryToPostgress(
        entitySql, entityParams);

    logger.debug("getAccounts query: " +
        pgEntityQuery + JSON.stringify(entityParams));

    // Execute query
    return (pool
        .query(pgEntityQuery, entityParams)
        .then(results => {
            let ret = {
                accounts: [],
                links: {
                    next: null
                }
            };

            for (let row of results.rows) {
                ret.accounts.push(processRow(row));
            }

            let anchorAcc = '0.0.0';
            if (ret.accounts.length > 0) {
                anchorAcc = ret.accounts[ret.accounts.length - 1].account;
            }

            ret.links = {
                next: utils.getPaginationLink(req,
                    (ret.accounts.length !== limit),
                    'account.id', anchorAcc, order)
            }

            logger.debug("getAccounts returning " +
                ret.accounts.length + " entries");

            return (ret);
        })
    )
}


/**
 * Handler function for /account/:id API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getOneAccount = function (req, res) {
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    // Parse the filter parameters for account-numbers, balance, and pagination

    const acc = utils.parseEntityId(req.params.id);

    if (acc.num === 0) {
        res.status(400)
            .send('Invalid account number');
    }

    const [tsQuery, tsParams] =
        utils.parseParams(req, 'timestamp', ['t.consensus_ns'], 'timestamp_ns');

    const resultTypeQuery = utils.parseResultParams(req);
    const { limitQuery, limitParams, order, limit } =
        utils.parseLimitAndOrderParams(req);

    let ret = {
        transactions: []
    };

    // Because of the outer join on the 'account_balances ab' and 't_entities e' below, we 
    // need to look  for the given account.id in both account_balances and t_entities table and combine with an 'or'
    const entitySql = getAccountQueryPrefix() +
        "and (\n" +
        "    (ab.account_realm_num  =  ? and ab.account_num  =  ?)\n" +
        "    or (e.entity_realm = ? and e.entity_num = ?\n" +
        "        and e.fk_entity_type_id = " + ACCOUNT_ENTITY_TYPE + ")\n" +
        ")\n";

    const entityParams = [acc.realm, acc.num, acc.realm, acc.num];
    const pgEntityQuery = utils.convertMySqlStyleQueryToPostgress(
        entitySql, entityParams);

    logger.debug("getOneAccount entity query: " +
        pgEntityQuery + JSON.stringify(entityParams));
    // Execute query & get a promise
    const entityPromise = pool.query(pgEntityQuery, entityParams);

     // Now, query the transactions for this entity

    const creditDebit = utils.parseCreditDebitParams(req);

    const accountQuery = 'eaccount.entity_shard = ?\n' +
        '    and eaccount.entity_realm = ?\n' +
        '    and eaccount.entity_num = ?' +
        (creditDebit === 'credit' ? ' and ctl.amount > 0 ' :
            creditDebit === 'debit' ? ' and ctl.amount < 0 ' : '');
    const accountParams = [acc.shard, acc.realm, acc.num];

    let innerQuery =
    '      select distinct t.consensus_ns\n' +
    '       from t_transactions t\n' +
    '       join t_transaction_results tr on t.fk_result_id = tr.id\n' +
    '       join t_cryptotransferlists ctl on t.id = ctl.fk_trans_id\n' +
    '       join t_entities eaccount on eaccount.id = ctl.account_id\n' +
    '       where ' +
            [accountQuery, tsQuery, resultTypeQuery].map(q => q === '' ? '1=1' : q).join(' and ') +
    '       order by t.consensus_ns ' + order + '\n' +
    '        ' + limitQuery;

  const innerParams = accountParams
        .concat(tsParams)
        .concat(limitParams);

    let transactionsQuery =
        "select etrans.entity_shard,  etrans.entity_realm, etrans.entity_num\n" +
        "   , t.memo\n" +
        "   , t.consensus_ns\n" +
        '   , valid_start_ns\n' +
        "   , ttr.result\n" +
        "   , t.fk_trans_type_id\n" +
        "   , ttt.name\n" +
        "   , t.fk_node_acc_id\n" +
        "   , enode.entity_shard as node_shard\n" +
        "   , enode.entity_realm as node_realm\n" +
        "   , enode.entity_num as node_num\n" +
        "   , account_id\n" +
        "   , eaccount.entity_shard as account_shard\n" +
        "   , eaccount.entity_realm as account_realm\n" +
        "   , eaccount.entity_num as account_num\n" +
        "   , amount\n" +
        "   , t.charged_tx_fee\n" +
        " from (" + innerQuery + ") as tlist\n" +
        "   join t_transactions t on tlist.consensus_ns = t.consensus_ns\n" +
        "   join t_transaction_results ttr on ttr.id = t.fk_result_id\n" +
        "   join t_entities enode on enode.id = t.fk_node_acc_id\n" +
        "   join t_entities etrans on etrans.id = t.fk_payer_acc_id\n" +
        "   join t_transaction_types ttt on ttt.id = t.fk_trans_type_id\n" +
        "   left outer join t_cryptotransferlists ctl on  ctl.fk_trans_id = t.id\n" +
        "   join t_entities eaccount on eaccount.id = ctl.account_id\n" +
        "   order by t.consensus_ns " + order + "\n";


    const pgTransactionsQuery = utils.convertMySqlStyleQueryToPostgress(
        transactionsQuery, innerParams);

    logger.debug("getOneAccount transactions query: " +
        pgTransactionsQuery + JSON.stringify(innerParams));

    // Execute query & get a promise
    const transactionsPromise = pool.query(pgTransactionsQuery, innerParams);


    // After all promises (for all of the above queries) have been resolved...
    Promise.all([entityPromise, transactionsPromise])
        .then(function (values) {
            const entityResults = values[0];
            const transactionsResults = values[1];

            // Process the results of entities query
            if (entityResults.rows.length !== 1) {
                res.status(500)
                    .send('Error: Could not get entity information');
                return;
            }

            for (let row of entityResults.rows) {
                const r = processRow(row);
                for (let key in r) {
                    ret[key] = r[key];
                }
            }

            // Process the results of t_transactions query
            const tl = transactions.createTransferLists(
                transactionsResults.rows, ret);
            ret = tl.ret;
            let anchorSecNs = tl.anchorSecNs;


            // Pagination links
            ret.links = {
                next: utils.getPaginationLink(req,
                    (ret.transactions.length !== limit),
                    'timestamp', anchorSecNs, order)
            }

            logger.debug("getOneAccount returning " +
                ret.transactions.length + " transactions entries");
            res.json(ret);
        })
        .catch(err => {
            logger.error("getOneAccount error: " +
                JSON.stringify(err.stack));
            res.status(500)
                .send('Internal error');
        });
}


module.exports = {
    getAccounts: getAccounts,
    getOneAccount: getOneAccount
}
