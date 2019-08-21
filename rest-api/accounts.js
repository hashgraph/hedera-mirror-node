'use strict';
const utils = require('./utils.js');
const transactions = require('./transactions.js');

/**
 * Handler function for /accounts API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getAccounts = function (req, res) {
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    // Parse the filter parameters for account-numbers, balances, publicKey and pagination (limit/offset)

    const [accountQuery, accountParams] =
        utils.parseParams(req, 'account.id',
            [{ shard: 'entity_shard', realm: 'entity_realm', num: 'entity_num' }],
            'entityId');

    const [balanceQuery, balanceParams] = utils.parseParams(req, 'balance',
        ['balance']);

    let [pubKeyQuery, pubKeyParams] = utils.parseParams(req, 'publickey',
        ['e.key']);

    pubKeyQuery = pubKeyQuery === '' ? '' :
        "(e.entity_shard = ab.shard \n" +
        " and e.entity_realm = ab.realm\n" +
        " and e.entity_num = ab.num and " +
        pubKeyQuery +
        ")";

    let [anchorQuery, anchorParams] =
        utils.parseParams(req, 'pageanchor', ['t.consensus_seconds']);
    anchorQuery = anchorQuery.replace('=', '<=');

    const { limitOffsetQuery, limitOffsetParams, order, limit, offset } =
        utils.parsePaginationAndOrderParams(req, 'asc');

    let querySuffix = '';
    querySuffix += (accountQuery === '' ? '' : ' and ') + accountQuery;
    querySuffix += (balanceQuery === '' ? '' : ' and ') + balanceQuery;
    querySuffix += (pubKeyQuery === '' ? '' : ' and ') + pubKeyQuery;
    querySuffix += ' order by num ' + order + '\n';
    querySuffix += limitOffsetQuery;

    const entitySql =
        "select concat(e.entity_shard, '.', e.entity_realm, '.', e.entity_num) as account\n" +
        ", et.name as entity_type, exp_time_ns, auto_renew_period, ed25519_public_key_hex, key, deleted\n" +
        ", ab.balance as account_balance\n" +
        ", abrt.seconds as balance_asof_seconds, abrt.nanos as balance_asof_nanos\n" +
        "from t_entities e\n" +
        ", t_entity_types et\n" +
        ", t_account_balances ab\n" +
        ", t_account_balance_refresh_time abrt\n" +
        " where et.id = e.fk_entity_type_id\n" +
        " and ab.shard = e.entity_shard\n" +
        " and ab.realm = e.entity_realm\n" +
        " and ab.num = e.entity_num\n" +
        querySuffix;

    const entityParams = accountParams
        .concat(balanceParams)
        .concat(pubKeyParams)
        .concat(limitOffsetParams);

    const pgEntityQuery = utils.convertMySqlStyleQueryToPostgress(
        entitySql, entityParams);

    logger.debug("getAccounts query: " +
        pgEntityQuery + JSON.stringify(entityParams));

    // Execute query
    pool.query(pgEntityQuery, entityParams, (error, results) => {
        let ret = {
            accounts: [],
            links: {
                next: null
            }
        };

        if (error) {
            logger.error("getAccounts error: " +
                JSON.stringify(error, Object.getOwnPropertyNames(error)));
            res.json(ret);
            return;
        }

        for (let row of results.rows) {
            row.balance = {};
            row.balance.timestamp = '' + row.balance_asof_seconds + '.' +
                ((row.balance_asof_nanos + '000000000').substring(0, 9));
            delete row.balance_asof_seconds;
            delete row.balance_asof_nanos;

            row.expiry_timestamp = utils.nsToSecNs(row.exp_time_ns);
            delete row.exp_time_ns;

            row.balance.balance = row.account_balance;
            delete row.account_balance;
        }

        ret.accounts = results.rows;

        logger.debug("ret.accounts.length: " +
            ret.accounts.length + ' === limit: ' + limit);

        ret.links = {
            next: utils.getPaginationLink(req, (ret.accounts.length !== limit),
                limit, offset, order)
        }

        logger.debug("getAccounts returning " +
            ret.accounts.length + " entries");

        res.json(ret);
    });
}


/**
 * Handler function for /account/:id API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getOneAccount = function (req, res) {
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    // Parse the filter parameters for account-numbers, balance, and pagination (limit/offset)

    const acc = utils.parseEntityId(req.params.id);

    if (acc.num === 0) {
        res.status(400)
            .send('Invalid account number');
    }

    const [tsQuery, tsParams] =
        utils.parseParams(req, 'timestamp', ['t.consensus_seconds']);

    let [anchorQuery, anchorParams] =
        utils.parseParams(req, 'pageanchor', ['t.consensus_seconds']);
    anchorQuery = anchorQuery.replace('=', '<=');

    const resultTypeQuery = utils.parseResultParams(req);
    const { limitOffsetQuery, limitOffsetParams, order, limit, offset } =
        utils.parsePaginationAndOrderParams(req);


    let ret = {
        balance: {
            timestamp: null,
            amount: null,
        },
        entity_data: [],
        transactions: []
    };

    const timeQuerySql =
        "select seconds, nanos from  t_account_balance_refresh_time limit 1";
    // Execute query & get a promise
    const timePromise = pool.query(timeQuerySql);

    const balanceSql =
        "select balance\n" +
        " from t_account_balances\n" +
        " where shard = $1 and realm = $2 and num = $3\n";
    const balanceParams = [acc.shard, acc.realm, acc.num];
    // Execute query & get a promise
    const balancePromise = pool.query(balanceSql, balanceParams);

    const entitySql =
        "select tet.name as entity_type, exp_time_ns, auto_renew_period, ed25519_public_key_hex, key, deleted\n" +
        "from t_entities te\n" +
        ", t_entity_types tet\n" +
        " where tet.id = te.fk_entity_type_id\n" +
        " and entity_shard = ?\n" +
        " and entity_realm = ?\n" +
        " and entity_num = ?\n"
    const entityParams = [acc.shard, acc.realm, acc.num];
    const pgEntityQuery = utils.convertMySqlStyleQueryToPostgress(
        entitySql, entityParams);

    logger.debug("getOneAccount transactions query: " +
        pgEntityQuery + JSON.stringify(entityParams));
    // Execute query & get a promise
    const entityPromise = pool.query(pgEntityQuery, entityParams);

    const creditDebit = utils.parseCreditDebitParams(req);

    const accountQuery = 'eaccount.entity_shard = ?\n' +
        '    and eaccount.entity_realm = ?\n' +
        '    and eaccount.entity_num = ?' +
        (creditDebit === 'credit' ? ' and ctl.amount > 0 ' :
            creditDebit === 'debit' ? ' and ctl.amount < 0 ' : '');
    const accountParams = [acc.shard, acc.realm, acc.num];

    const innerQuery =
        'select distinct t.id\n' +
        '	, t.vs_seconds\n' +
        '	, t.vs_nanos\n' +
        '	, t.consensus_seconds\n' +
        '	, t.consensus_nanos\n' +
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
        '     order by t.consensus_seconds ' + order +
        '     , t.consensus_nanos ' + order + '\n' +
        '     ' + limitOffsetQuery;
    const innerParams = accountParams
        .concat(tsParams)
        .concat(anchorParams)
        .concat(limitOffsetParams);

    let transactionsQuery =
        "select  concat(etrans.entity_shard, '.', etrans.entity_realm, '.'," +
        "           etrans.entity_num, '-', t.vs_seconds, '-', t.vs_nanos) " +
        "           as transaction_id\n" +
        "   , t.memo\n" +
        "   , t.consensus_seconds\n" +
        "   , t.consensus_nanos\n" +
        "   , t.consensus_ns\n" +
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


    const pgTransactionsQuery = utils.convertMySqlStyleQueryToPostgress(
        transactionsQuery, innerParams);

    logger.debug("getOneAccount transactions query: " +
        pgTransactionsQuery + JSON.stringify(innerParams));

    // Execute query & get a promise
    const transactionsPromise = pool.query(pgTransactionsQuery, innerParams);


    // After all 3 of the promises (for all three queries) have been resolved...
    Promise.all([timePromise, balancePromise, entityPromise, transactionsPromise])
        .then(function (values) {
            const timeResults = values[0];
            const balanceResults = values[1];
            const entityResults = values[2];
            const transactionsResults = values[3];

            // Process the results of t_account_balance_refresh_time query
            if (timeResults.rows.length !== 1) {
                res.status(500)
                    .send('Error: Could not get balance');
                return;
            }

            ret.balance.timestamp = '' + timeResults.rows[0].seconds +
                '.' + ((timeResults.rows[0].nanos + '000000000').substring(0, 9));

            // Process the results of t_account_balances query
            if (balanceResults.rows.length !== 1) {
                res.status(500)
                    .send('Error: Could not get balance');
                return;
            }
            ret.balance.amount = balanceResults.rows[0].balance;

            // Process the results of t_entities query
            if (entityResults.rows.length !== 1) {
                res.status(500)
                    .send('Error: Could not get entity information');
                return;
            }

            for (let row of entityResults.rows) {
                row.expiry_timestamp = utils.nsToSecNs(row.exp_time_ns);
                delete row.exp_time_ns;
            }
            ret.entity_data = entityResults.rows;


            // Process the results of t_transactions query
            const tl = transactions.createTransferLists(
                transactionsResults.rows, ret);
            ret = tl.ret;
            let anchorSeconds = tl.anchorSeconds;

            // Pagination links
            ret.links = {
                next: utils.getPaginationLink(req,
                    (ret.transactions.length !== limit),
                    limit, offset, order, anchorSeconds)
            }

            logger.debug("getBalances returning " +
                balanceResults.rows.length + " entries");
            res.json(ret);
        })
        .catch(err => {
            logger.error("getBalances error: " +
                JSON.stringify(err.stack));
            res.status(500)
                .send('Internal error');
        });
}


module.exports = {
    getAccounts: getAccounts,
    getOneAccount: getOneAccount
}
