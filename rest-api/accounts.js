'use strict';
const utils = require('./utils.js');
const transactions = require('./transactions.js');

/**
 * Handler function for /accounts API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getAccounts = function (req) {
    // Parse the filter parameters for account-numbers, balances, publicKey and pagination
    const [accountQuery, accountParams] =
        utils.parseParams(req, 'account.id',
            [{ shard: 'entity_shard', realm: 'entity_realm', num: 'entity_num' }],
            'entityId');

    const [balanceQuery, balanceParams] = utils.parseParams(req, 'account.balance',
        ['balance']);

    let [pubKeyQuery, pubKeyParams] = utils.parseParams(req, 'account.publickey',
        ['e.key'], 'hexstring');
    pubKeyQuery = pubKeyQuery === '' ? '' :
        "(e.entity_shard = ab.shard \n" +
        " and e.entity_realm = ab.realm\n" +
        " and e.entity_num = ab.num and " +
        pubKeyQuery +
        ")";

    const { limitQuery, limitParams, order, limit } =
        utils.parseLimitAndOrderParams(req, 'asc');


    const entitySql =
        "select e.entity_shard, e.entity_realm, e.entity_num\n" +
        ", et.name as entity_type, exp_time_ns, auto_renew_period, admin_key, key, deleted\n" +
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
        "   and " + 
        [accountQuery, balanceQuery, pubKeyQuery].map(q => q === '' ? '1=1' : q).join(' and ') +
        " order by num " + order + "\n" +
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
                row.balance = {};
                row.account = row.entity_shard + '.' + row.entity_realm + '.' + row.entity_num;
                delete row.entity_shard;
                delete row.entity_realm;
                delete row.entity_num;

                row.balance.timestamp = '' + row.balance_asof_seconds + '.' +
                    ((row.balance_asof_nanos + '000000000').substring(0, 9));
                delete row.balance_asof_seconds;
                delete row.balance_asof_nanos;

                row.expiry_timestamp = utils.nsToSecNs(row.exp_time_ns);
                delete row.exp_time_ns;

                row.balance.balance = Number(row.account_balance);
                delete row.account_balance;

                row.auto_renew_period = Number(row.auto_renew_period);

                row.admin_key = utils.encodeKey(row.admin_key);
                row.key = utils.encodeKey(row.key);
            }

            let anchorAcc = '0.0.0';
            if (results.rows.length > 0) {
                anchorAcc = results.rows[results.rows.length - 1].account;
            }

            ret.accounts = results.rows;

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
        utils.parseParams(req, 'timestamp', ['t.consensus_seconds']);

    const resultTypeQuery = utils.parseResultParams(req);
    const { limitQuery, limitParams, order, limit } =
        utils.parseLimitAndOrderParams(req);

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
        "select tet.name as entity_type, exp_time_ns, auto_renew_period, admin_key, key, deleted\n" +
        "from t_entities te\n" +
        ", t_entity_types tet\n" +
        " where tet.id = te.fk_entity_type_id\n" +
        " and entity_shard = ?\n" +
        " and entity_realm = ?\n" +
        " and entity_num = ?\n"
    const entityParams = [acc.shard, acc.realm, acc.num];
    const pgEntityQuery = utils.convertMySqlStyleQueryToPostgress(
        entitySql, entityParams);

    logger.debug("getOneAccount entity query: " +
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
        '	, t.valid_stard_ns\n' +
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
        resultTypeQuery + '\n' +
        '     order by t.consensus_seconds ' + order +
        '     , t.consensus_nanos ' + order + '\n' +
        '     ' + limitQuery;
    const innerParams = accountParams
        .concat(tsParams)
        .concat(limitParams);

    let transactionsQuery =
        "select etrans.entity_shard,  etrans.entity_realm, etrans.entity_num\n" +
        "   , t.memo\n" +
        "   , t.consensus_seconds\n" +
        "   , t.consensus_nanos\n" +
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
            ret.balance.amount = Number(balanceResults.rows[0].balance);
            ret.auto_renew_period = Number(ret.auto_renew_period);

            // Process the results of t_entities query
            if (entityResults.rows.length !== 1) {
                res.status(500)
                    .send('Error: Could not get entity information');
                return;
            }

            for (let row of entityResults.rows) {
                row.expiry_timestamp = utils.nsToSecNs(row.exp_time_ns);
                delete row.exp_time_ns;

                row.admin_key = utils.encodeKey(row.admin_key);
                row.key = utils.encodeKey(row.key);

            }
            ret.entity_data = entityResults.rows;


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
                balanceResults.rows.length + " entries");
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
