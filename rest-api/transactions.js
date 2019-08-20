'use strict';
const utils = require('./utils.js');

/**
 * Create transferlists from the output of SQL queries. 
 * 
 * @param {Array of objects} rows Array of rows returned as a result of an SQL query
 * @param {Array} arr REST API return array  
 * @return {Array} arr Updated REST API return array
 */
const createTransferLists = function (rows, arr) {
    // If the transaction has a transferlist (i.e. list of individual trasnfers, it 
    // will show up as separate rows. Combine those into a single transferlist for
    // a given transaction_id
    let transactions = {};
    let anchorSecNs = null;  // Used for pagination to anchor the subsequent 
                             // paginated queries based on 'seconds'

    for (let row of rows) {
        if (!(row.transaction_id in transactions)) {
            transactions[row.transaction_id] = {};
            for (let key of ["transaction_id", "id", "memo",
                "consensus_ns", "valid_start_ns", "result", "name",
                "node", "charged_tx_fee"]) {
                if (key === 'consensus_ns') {
                    transactions[row.transaction_id]['consensus_timestamp'] = utils.nsToSecNs(row[key]);
                } else if (key === 'valid_start_ns') {
                    transactions[row.transaction_id]['valid_start_timestamp'] = utils.nsToSecNs(row[key]);
                } else {
                    transactions[row.transaction_id][key] = row[key];
                }
                if (anchorSecNs === null) {
                    anchorSecNs = utils.nsToSecNs(row.consensus_ns);
                }
            }
            transactions[row.transaction_id].transfers = []
        }
        transactions[row.transaction_id].transfers.push({
            account: row.account,
            amount: row.amount
        });
    }

    // Push all transactions in a return array
    for (let transaction of Object.values(transactions)) {
        transaction.memoAsString = transaction.memo.toString(); // TODO: Remove this someday
        arr.transactions.push(transaction);
    }

    return ({
        ret: arr,
        anchorSecNs: anchorSecNs
    })
}


/**
 * Handler function for /transactions API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getTransactions = function (req, res) {
    logger.debug("--------------------  getTransactions --------------------");
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    // Parse the filter parameters for credit/debit, account-numbers, 
    // timestamp, and pagination (anchor and limit/offset)
    const creditDebit = utils.parseCreditDebitParams(req);

    let [accountQuery, accountParams] =
        utils.parseParams(req, 'account.id', [{
            shard: 'eaccount.entity_shard',
            realm: 'eaccount.entity_realm',
            num: 'eaccount.entity_num'
        }], 'entityId');

    if (accountQuery !== '') {
        accountQuery = accountQuery +
            (creditDebit === 'credit' ? ' and ctl.amount > 0 ' :
                creditDebit === 'debit' ? ' and ctl.amount < 0 ' : '');
    }

    const [tsQuery, tsParams] =
        utils.parseParams(req, 'timestamp', ['t.consensus_ns'], 'timestamp_ns');

    let [anchorQuery, anchorParams] =
        utils.parseParams(req, 'pageanchor', ['t.consensus_ns']);
    anchorQuery = anchorQuery.replace('=', '<=');

    const resultTypeQuery = utils.parseResultParams(req);
    const { limitOffsetQuery, limitOffsetParams, order, limit, offset } =
        utils.parsePaginationAndOrderParams(req);

    // Create an inner query that returns the ids of transactions that 
    // match the filter criteria based on the filter criteria in the REST query
    // The transaction ids returned from this query is then used in the outer 
    // query to generate the output to return to the REST query.
    let innerQuery =
        'select distinct t.id\n' +
        '	, t.vs_seconds\n' +
        '	, t.vs_nanos\n' +
        '	, t.consensus_ns\n' +
        'from t_transactions t\n' +
        '	, t_cryptotransferlists ctl\n' +
        '	, t_entities eaccount\n' +
        '   , t_transaction_results tr\n' +
        'where ctl.fk_trans_id = t.id\n' +
        '   and eaccount.id = ctl.account_id\n' +
        '   and t.fk_result_id = tr.id\n' +
        (accountQuery === '' ? '' : '     and ') + accountQuery + '\n' +
        (tsQuery === '' ? '' : '     and ') + tsQuery + '\n' +
        (anchorQuery === '' ? '' : '     and ') + anchorQuery + '\n' +
        resultTypeQuery + '\n' +
        '     order by t.consensus_ns ' + order + '\n' +
        '     ' + limitOffsetQuery;
    let sqlParams = accountParams.concat(tsParams)
        .concat(anchorParams)
        .concat(limitOffsetParams);

    let sqlQuery =
        "select  concat(etrans.entity_shard, '.', etrans.entity_realm, '.'," +
        "           etrans.entity_num, '-', t.vs_seconds, '-', t.vs_nanos) " +
        "           as transaction_id\n" +
        "   , t.memo\n" +
        '	, t.consensus_ns\n' +
        '   , valid_start_ns\n' +
        "   , ttr.result\n" +
        "   , t.fk_trans_type_id\n" +
        "   , ttt.name\n" +
        "   , t.fk_node_acc_id\n" +
        "   , concat(enode.entity_shard, '.', enode.entity_realm, '.', " +
        "       enode.entity_num) as node\n" +
        "   , account_id\n" +
        "   , concat(eaccount.entity_shard, '.', eaccount.entity_realm, " +
        "       '.', eaccount.entity_num) as account\n" +
        "   , amount\n" +
        "   , t.charged_tx_fee\n" +
        " from (" + innerQuery + ") as tlist\n" +
        "   join t_transactions t on tlist.id = t.id\n" +
        "   join t_transaction_results ttr on ttr.id = t.fk_result_id\n" +
        "   join t_entities enode on enode.id = t.fk_node_acc_id\n" +
        "   join t_entities etrans on etrans.id = t.fk_payer_acc_id\n" +
        "   join t_transaction_types ttt on ttt.id = t.fk_trans_type_id\n" +
        "   left outer join t_cryptotransferlists ctl on  ctl.fk_trans_id = t.id\n" +
        "   join t_entities eaccount on eaccount.id = ctl.account_id\n";


    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getTransactions query: " +
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query
    pool.query(pgSqlQuery, sqlParams, (error, results) => {
        let ret = {
            transactions: [],
            links: {
                next: null
            }
        };

        if (error) {
            logger.error("getTransactions error: " +
                JSON.stringify(error, Object.getOwnPropertyNames(error)));
            res.json(ret);
            return;
        }


        const tl = createTransferLists(results.rows, ret);
        ret = tl.ret;
        let anchorSecNs = tl.anchorSecNs;

        logger.debug("ret.transactions.length: " +
            ret.transactions.length + ' === limit: ' + limit);


        ret.links = {
            next: utils.getPaginationLink(req, (ret.transactions.length !== limit),
                limit, offset, order, anchorSecNs)
        }

        logger.debug("getTransactions returning " +
            ret.transactions.length + " entries");

        res.json(ret);
    })
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

    const accountQuery = 'transaction_id = ?\n';
    const sqlParams = req.params.id.split(/[.-]/);

    let sqlQuery =
        "select  concat(etrans.entity_shard, '.', etrans.entity_realm, '.'," +
        "           etrans.entity_num, '-', t.vs_seconds, '-', t.vs_nanos) " +
        "           as transaction_id\n" +
        "   , t.memo\n" +
        '	, t.consensus_ns\n' +
        '   , valid_start_ns\n' +
        "   , ttr.result\n" +
        "   , t.fk_trans_type_id\n" +
        "   , ttt.name\n" +
        "   , t.fk_node_acc_id\n" +
        "   , concat(enode.entity_shard, '.', enode.entity_realm, '.', " +
        "       enode.entity_num) as node\n" +
        "   , account_id\n" +
        "   , concat(eaccount.entity_shard, '.', eaccount.entity_realm, " +
        "       '.', eaccount.entity_num) as account\n" +
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
        "   and  t.vs_seconds = ?\n" +
        "   and  t.vs_nanos = ?\n";


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
        let anchorSeconds = tl.anchorSeconds;

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
