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


/**
 * Processes one row of the results of the SQL query and format into API return format
 * @param {Object} row One row of the SQL query result
 * @return {Object} row Processed row
 */
const processRow = function (row) {
    row.balance = {};
    row.account = row.entity_shard + '.' + row.entity_realm + '.' + row.entity_num;
    delete row.entity_shard;
    delete row.entity_realm;
    delete row.entity_num;

    row.balance.timestamp = utils.nsToSecNs(row.consensus_timestamp);
    delete row.consensus_timestamp;

    row.expiry_timestamp = utils.nsToSecNs(row.exp_time_ns);
    delete row.exp_time_ns;

    row.balance.balance = Number(row.account_balance);
    delete row.account_balance;

    row.auto_renew_period = Number(row.auto_renew_period);

    row.admin_key = utils.encodeKey(row.admin_key);
    row.key = utils.encodeKey(row.key);

    return (row);
}


/**
 * Handler function for /accounts API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getAccounts = function (req) {
    // Parse the filter parameters for account-numbers, balances, publicKey and pagination
    const [accountQuery, accountParams] =
        utils.parseParams(req, 'account.id',
            [{ shard: process.env.SHARD_NUM, realm: 'ab.account_realm_num', num: 'ab.account_num' }],
            'entityId');

    const [balanceQuery, balanceParams] = utils.parseParams(req, 'account.balance',
        ['ab.balance']);

    let [pubKeyQuery, pubKeyParams] = utils.parseParams(req, 'account.publickey',
        ['e.key'], 'hexstring');

    const { limitQuery, limitParams, order, limit } =
        utils.parseLimitAndOrderParams(req, 'asc');


    const entitySql = 
        "select ab.balance as account_balance\n" +
        "    , ab.consensus_timestamp as consensus_timestamp\n" +
        "    , " + process.env.SHARD_NUM + " as entity_shard\n" +
            "    , ab.account_realm_num as entity_realm\n" +
            "    , ab.account_num as entity_num\n" +
            "    , e.exp_time_ns, e.auto_renew_period, e.admin_key, e.key, e.deleted\n" +
            "    , et.name as entity_type\n" +
            "from account_balances ab\n" +
            "left outer join t_entities e on (ab.account_realm_num = e.entity_realm and ab.account_num =  e.entity_num)\n" +
            "left outer join t_entity_types et on et.id = e.fk_entity_type_id\n" +
        "where \n" +
            "ab.consensus_timestamp = (select consensus_timestamp from account_balances order by consensus_timestamp desc limit 1)\n" +
        "and \n" +
            [accountQuery, balanceQuery, pubKeyQuery].map(q => q === '' ? '1=1' : q).join(' and ') +
            " order by ab.account_num " + order + "\n" +
            limitQuery;

    const entityParams = accountParams
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

    const entitySql = 
        "select ab.balance as account_balance\n" +
        "    , ab.consensus_timestamp as consensus_timestamp\n" +
        "    , " + process.env.SHARD_NUM + " as entity_shard\n" +
            "    , ab.account_realm_num as entity_realm\n" +
            "    , ab.account_num as entity_num\n" +
            "    , e.exp_time_ns, e.auto_renew_period, e.admin_key, e.key, e.deleted\n" +
            "    , et.name as entity_type\n" +
            "from account_balances ab\n" +
            "left outer join t_entities e\n" +
            "  on (ab.account_realm_num = e.entity_realm and ab.account_num =  e.entity_num)\n" +
            "left outer join t_entity_types et\n" +
            "  on et.id = e.fk_entity_type_id\n" +
        "where \n" +
            "ab.consensus_timestamp =\n" +
            "  (select consensus_timestamp from account_balances order by consensus_timestamp desc limit 1)\n" +
        "and (ab.account_realm_num  =  ? and ab.account_num  =  ?)\n";

    const entityParams = [acc.realm, acc.num];
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
