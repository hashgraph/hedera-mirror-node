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

/**
 * Create transferlists from the output of SQL queries. The SQL table has different 
 * rows for each of the transfers in a single transaction. This function collates all
 * transfers into a single list. 
 * @param {Array of objects} rows Array of rows returned as a result of an SQL query
 * @param {Array} arr REST API return array  
 * @return {Array} arr Updated REST API return array
 */
const createTransferLists = function (rows, arr) {
    // If the transaction has a transferlist (i.e. list of individual trasnfers, it 
    // will show up as separate rows. Combine those into a single transferlist for
    // a given consensus_ns (Note that there could be two records for the same
    // transaction-id where one would pass and others could fail as duplicates)
    let transactions = {};
    const epochSecondsDigits = 10; // ('' + (Math.round)(new Date().getTime() / 1000)).length

    for (let row of rows) {
        if (!(row.consensus_ns in transactions)) {
            transactions[row.consensus_ns] = {};
            transactions[row.consensus_ns]['consensus_timestamp'] = utils.nsToSecNs(row['consensus_ns']);
            transactions[row.consensus_ns]['valid_start_timestamp'] = utils.nsToSecNs(row['valid_start_ns']);
            transactions[row.consensus_ns]['charged_tx_fee'] = Number(row['charged_tx_fee']);
            transactions[row.consensus_ns]['id'] = row['id'];
            transactions[row.consensus_ns]['memo_base64'] = utils.encodeBase64(row['memo']);
            transactions[row.consensus_ns]['result'] = row['result'];
            transactions[row.consensus_ns]['name'] = row['name'];
            transactions[row.consensus_ns]['node'] =
                row.node_shard + '.' + row.node_realm + '.' + row.node_num;

            // Construct a transaction id using format: shard.realm.num-sssssssssss-nnnnnnnnn
            transactions[row.consensus_ns]['transaction_id'] =
                row.entity_shard + '.' +
                row.entity_realm + '.' +
                row.entity_num + '-' +
                row.valid_start_ns.slice(0, epochSecondsDigits) + '-' +
                row.valid_start_ns.slice(epochSecondsDigits);

            transactions[row.consensus_ns].transfers = []
        }

        transactions[row.consensus_ns].transfers.push({
            account: row.account_shard + '.' + row.account_realm + '.' + row.account_num,
            amount: Number(row.amount)
        });
    }

    const anchorSecNs = (rows.length > 0) ?
        utils.nsToSecNs(rows[rows.length - 1].consensus_ns) : 0;

    arr.transactions = Object.values(transactions);

    return ({
        ret: arr,
        anchorSecNs: anchorSecNs
    })
}


/**
 * Handler function for /transactions API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getTransactions = function (req) {
    // Parse the filter parameters for credit/debit, account-numbers, 
    // timestamp, and pagination (limit)
    const creditDebit = utils.parseCreditDebitParams(req);

    let [accountQuery, accountParams] =
        utils.parseParams(req, 'account.id', [{
            shard: 'entity_shard',
            realm: 'entity_realm',
            num: 'entity_num'

        }], 'entityId');

    if (accountQuery !== '') {
        accountQuery = 
            'ctl.account_id in\n' +
            '    (select distinct id\n' +
            '     from t_entities\n' +                                                   
            '     where (' + accountQuery + 
            '     and eaccount.fk_entity_type_id in (select id from t_entity_types)))\n';
        accountQuery = accountQuery +
            (creditDebit === 'credit' ? ' and ctl.amount > 0 ' :
                creditDebit === 'debit' ? ' and ctl.amount < 0 ' : '');
    }

    const [tsQuery, tsParams] =
        utils.parseParams(req, 'timestamp', ['t.consensus_ns'], 'timestamp_ns');

    const resultTypeQuery = utils.parseResultParams(req);

    const { limitQuery, limitParams, order, limit } =
        utils.parseLimitAndOrderParams(req);

    // Create an inner query that returns the ids of transactions that 
    // match the filter criteria based on the filter criteria in the REST query
    // The transaction ids returned from this query is then used in the outer 
    // query to generate the output to return to the REST query.
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

    let sqlParams = accountParams.concat(tsParams)
        .concat(limitParams);

    let sqlQuery =
        "select etrans.entity_shard,  etrans.entity_realm, etrans.entity_num\n" +
        "   , t.memo\n" +
        '	, t.consensus_ns\n' +
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


    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getTransactions query: " +
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query
    return (pool
        .query(pgSqlQuery, sqlParams)
        .then(results => {
            let ret = {
                transactions: [],
                links: {
                    next: null
                }
            };

            const tl = createTransferLists(results.rows, ret);
            ret = tl.ret;
            let anchorSecNs = tl.anchorSecNs;

            ret.links = {
                next: utils.getPaginationLink(req,
                    (ret.transactions.length !== limit),
                    'timestamp', anchorSecNs, order)
            }

            logger.debug("getTransactions returning " +
                ret.transactions.length + " entries");

            return (ret);
        })
    );
}

/**
 * Handler function for /transactions/:transaction_id API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getOneTransaction = function (req, res) {
    logger.debug("--------------------  getTransactions --------------------");
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    // The transaction id is in the format of 'shard.realm.num-ssssssssss.nnnnnnnnn'
    // convert it in shard, realm, num and nanoseconds parameters
    const sqlParams = req.params.id.replace(/(\d{10})-(\d{9})/, '$1$2').split(/[.-]/);

    let sqlQuery =
        "select etrans.entity_shard,  etrans.entity_realm, etrans.entity_num\n" +
        "   , t.memo\n" +
        '	, t.consensus_ns\n' +
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
        "   , charged_tx_fee\n" +
        " from t_transactions t\n" +
        "   join t_transaction_results ttr on ttr.id = t.fk_result_id\n" +
        "   join t_entities enode on enode.id = t.fk_node_acc_id\n" +
        "   join t_entities etrans on etrans.id = t.fk_payer_acc_id\n" +
        "   join t_transaction_types ttt on ttt.id = t.fk_trans_type_id\n" +
        "   join t_cryptotransferlists ctl on  ctl.fk_trans_id = t.id\n" +
        "   join t_entities eaccount on eaccount.id = ctl.account_id\n" +
        " where etrans.entity_shard = ?\n" +
        "   and  etrans.entity_realm = ?\n" +
        "   and  etrans.entity_num = ?\n" +
        "   and  t.valid_start_ns = ?\n" +
        " order by consensus_ns asc"; // In case of duplicate transactions, only the first succeeds

    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getTransactions query: " +
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query
    pool.query(pgSqlQuery, sqlParams, (error, results) => {
        let ret = {
            'transactions': []
        };

        if (error) {
            logger.error("getOneTransaction error: " +
                JSON.stringify(error, Object.getOwnPropertyNames(error)));
            res.status(404)
                .send('Not found');
            return;
        }

        const tl = createTransferLists(results.rows, ret);
        ret = tl.ret;

        if (ret.transactions.length === 0) {
            res.status(404)
                .send('Not found');
            return;
        }

        logger.debug("getOneTransaction returning " +
            ret.transactions.length + " entries");
        res.json(ret);
    })
}


module.exports = {
    getTransactions: getTransactions,
    getOneTransaction: getOneTransaction,
    createTransferLists: createTransferLists
}
