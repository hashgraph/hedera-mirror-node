'use strict';
const math = require('mathjs');
const utils = require('./utils.js');

/**
 * Handler function for /balances/history API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getBalancesHistory = function (req, res) {
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    // Parse the filter parameters for credit/debit, account-numbers, 
    // timestamp and pagination (anchor and limit/offset)
    let [accountQuery, accountParams] =
        utils.parseParams(req, 'account.id',
            [{ shard: 'ab.shard', realm: 'ab.realm', num: 'ab.num' }],
            'entityId');

    const [tsQuery, tsParams] = utils.parseParams(req,
        'timestamp', ['abh.snapshot_time_ns'], 'timestamp_ns');

    let [anchorQuery, anchorParams] =
        utils.parseParams(req, 'pageanchor', ['abh.snapshot_time_ns']);
    anchorQuery = anchorQuery.replace('=', '<=');

    const { limitOffsetQuery, limitOffsetParams, order, limit, offset } =
        utils.parsePaginationAndOrderParams(req);

    const [balanceQuery, balanceParams] = utils.parseParams(req, 'balance',
        ['abh.balance']);

    let sqlQuery =
        "Select \n" +
        "    abh.snapshot_time_ns\n" +
        "    , concat(ab.shard, '.', ab.realm, '.', ab.num) as account\n" +
        "    , abh.balance\n" +
        " from t_account_balance_history as abh\n" +
        "    , t_account_balances ab\n" +
        " Where ab.id = abh.fk_balance_id\n" +
        (accountQuery === '' ? '' : '     and ') + accountQuery + "\n" +
        (tsQuery === '' ? '' : '     and ') + tsQuery + "\n" +
        (balanceQuery === '' ? '' : '     and ') + balanceQuery + "\n" +
        (anchorQuery === '' ? '' : '     and ') + anchorQuery + '\n' +
        "     order by abh.snapshot_time_ns " + "\n" +
        "     " + order + "\n" +
        // "   , account asc\n" +  // TODO: needed to ensure that pagination works 
        // fine. need to optimize this query
        "     " + limitOffsetQuery;


    let sqlParams = accountParams.concat(tsParams)
        .concat(balanceParams)
        .concat(anchorParams)
        .concat(limitOffsetParams);

    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getBalancesHistory query: " +
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query
    pool.query(pgSqlQuery, sqlParams, (error, results) => {
        let ret = {
            balances: [],
            links: {
                next: null
            }
        };

        if (error) {
            logger.error("getBalancesHistory error: " +
                JSON.stringify(error, Object.getOwnPropertyNames(error)));
            res.json(ret);
            return;
        }

        let anchorSecNs = null;

        // Go through all results, and collect them by seconds.
        // These need to be returned as an array (and not an object) because
        // per ECMA ES2015, the order of keys parsable as integers are implicitly 
        // sorted (i.e. insert order is not maintained)
        let retObj = {}
        for (let entry of results.rows) {
            let ns = utils.nsToSecNs(entry.snapshot_time_ns);

            if (anchorSecNs === null) {
                anchorSecNs = ns;
            }
            delete entry.snapshot_time_ns;

            if (!(ns in retObj)) {
                retObj[ns] = [];
                ret.balances.push({
                    timestamp: ns,
                    accountbalances: []
                });
            }
            retObj[ns].push(entry);
        }

        let totalEntries = 0;
        for (let index in ret.balances) {
            ret.balances[index].accountbalances = retObj[ret.balances[index].timestamp];
            totalEntries += ret.balances[index].accountbalances.length;
        }

        ret.links = {
            next: utils.getPaginationLink(req,
                (totalEntries !== limit),
                limit, offset, order, anchorSecNs)
        }

        logger.debug("getBalancesHistory returning " +
            results.rows.length + " entries");
        res.json(ret);
    })
}

/**
 * Handler function for /balances API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getBalances = function (req, res) {
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    // Parse the filter parameters for account-numbers, and pagination (limit/offset)
    const [accountQuery, accountParams] =
        utils.parseParams(req, 'account.id',
            [{ shard: 'shard', realm: 'realm', num: 'num' }],
            'entityId');

    logger.debug("req: " + JSON.stringify(req.query['account.id']));
    logger.debug("accountQuery: " + JSON.stringify(accountQuery));
    logger.debug("accountParams: " + JSON.stringify(accountParams));

    const [balanceQuery, balanceParams] = utils.parseParams(req, 'balance',
        ['balance']);

    let [pubKeyQuery, pubKeyParams] = utils.parseParams(req, 'publickey',
        ['e.key']);

    logger.debug("1 pubkeyquery: " + pubKeyQuery);
    logger.debug('1 pubkeyparams: ' + JSON.stringify(pubKeyParams));

    pubKeyQuery = pubKeyQuery === '' ? '' :
        "(e.entity_shard = ab.shard \n" +
        " and e.entity_realm = ab.realm\n" +
        " and e.entity_num = ab.num and " +
        pubKeyQuery +
        ")";

    logger.debug("2 pubkeyquery: " + pubKeyQuery);
    logger.debug(' pubkeyparams: ' + JSON.stringify(pubKeyParams));

    const { limitOffsetQuery, limitOffsetParams, order, limit, offset } =
        utils.parsePaginationAndOrderParams(req, 'asc');

    let ret = {
        timestamp: null,
        balances: [],
        links: {
            next: null
        }
    };

    let timeQuerySql =
        "select seconds, nanos from  t_account_balance_refresh_time limit 1";
    // Execute query & get a promise
    const timePromise = pool.query(timeQuerySql);

    let querySuffix = '';
    querySuffix += (accountQuery === '' ? ''
        : (querySuffix === '' ? ' where ' : ' and ')) + accountQuery;
    querySuffix += (balanceQuery === '' ? ''
        : (querySuffix === '' ? ' where ' : ' and ')) + balanceQuery;
    querySuffix += (pubKeyQuery === '' ? ''
        : (querySuffix === '' ? ' where ' : ' and ')) + pubKeyQuery;
    querySuffix += 'order by num ' + order + '\n';
    querySuffix += limitOffsetQuery;


    let sqlQuery =
        "select concat(shard, '.', realm, '.', num) as account, balance\n" +
        " from t_account_balances ab\n" +
        querySuffix;

    let sqlParams = accountParams
        .concat(balanceParams)
        .concat(pubKeyParams)
        .concat(limitOffsetParams);

    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getBalances query: " +
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query & get a promise
    const balancesPromise = pool.query(pgSqlQuery, sqlParams);

    // After both the promises (for both the queries) have been resolved...
    Promise.all([timePromise, balancesPromise])
        .then(function (values) {
            const timeResults = values[0];
            const balancesResults = values[1];

            // Process the results of t_account_balance_refresh_time query
            if (timeResults.rows.length !== 1) {
                res.status(500)
                    .send('Error: Could not get balance');
                return;
            }
            // ret.asOf.seconds = timeResults.rows[0].seconds;
            // ret.asOf.nanos = timeResults.rows[0].nanos;
            ret.timestamp = '' + timeResults.rows[0].seconds + '.' +
                ((timeResults.rows[0].nanos + '000000000').substring(0, 9));

            // Process the results of t_account_balances query
            ret.balances = balancesResults.rows;

            // Pagination links
            ret.links = {
                next: utils.getPaginationLink(req,
                    (balancesResults.rows.length !== limit),
                    limit, offset, order)
            }

            logger.debug("getBalances returning " +
                balancesResults.rows.length + " entries");
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
    getBalances: getBalances,
    getBalancesHistory: getBalancesHistory
}
