'use strict';
const math = require('mathjs');
const config = require('./config.js');
const utils = require('./utils.js');


/**
 * Handler function for /balances API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getBalances = function (req) {
    // Parse the filter parameters for credit/debit, account-numbers, 
    // timestamp and pagination
    let [accountQuery, accountParams] =
        utils.parseParams(req, 'account.id',
            [{ shard: 'ab.shard', realm: 'ab.realm', num: 'ab.num' }],
            'entityId');

    // if the request has a timestamp=xxxx or timestamp=eq:xxxxx, then 
    // modify that to be timestamp <= xxxx, so we return the latest balances
    // as of the user-supplied timestamp.
    if ('timestamp' in req.query) {
        const pattern = /^(eq:)?(\d*\.?\d*)$/;
        const replacement = "lte:$2";
        if (Array.isArray(req.query.timestamp)) {
            for (let index = 0; index < req.query.timestamp.length; index++) {
                req.query.timestamp[index] = req.query.timestamp[index].replace(pattern, replacement);
            }
        } else {
            req.query.timestamp = req.query.timestamp.replace(pattern, replacement);
        }
    }

    const [tsQuery, tsParams] = utils.parseParams(req,
        'timestamp', ['snapshot_time_ns'], 'timestamp_ns');

    const [balanceQuery, balanceParams] = utils.parseParams(req, 'account.balance',
        ['abh.balance']);

    let [pubKeyQuery, pubKeyParams] = utils.parseParams(req, 'account.publickey',
        ['e.key'], 'hexstring');
    pubKeyQuery = pubKeyQuery === '' ? '' :
        "(e.entity_shard = ab.shard \n" +
        " and e.entity_realm = ab.realm\n" +
        " and e.entity_num = ab.num and " +
        pubKeyQuery +
        ")";

    const { limitQuery, limitParams, order, limit } =
        utils.parseLimitAndOrderParams(req, 'desc');

    // Use the inner query to find the latest snapshot timestamp from the balance history table
    let innerQuery =
        " select snapshot_time_ns from t_account_balance_history\n" +
        " where " +
        (tsQuery === '' ? ('snapshot_time_ns < ' + config.limits.MAX_BIGINT) : tsQuery) + '\n' +
        " order by snapshot_time_ns desc limit 1";

    let sqlQuery =
        "Select \n" +
        "    abh.snapshot_time_ns\n" +
        "    , ab.shard, ab.realm, ab.num\n" +
        "    , abh.balance\n" +
        " from t_account_balance_history as abh\n" +
        "    , t_account_balances ab\n" +
        (pubKeyQuery === '' ? '' : ', t_entities e\n') +
        " Where ab.id = abh.fk_balance_id\n" +
        " and snapshot_time_ns = (" + innerQuery + ")\n" +
        ' and ' + 
        [accountQuery, pubKeyQuery, balanceQuery].map(q => q === '' ? '1=1' : q).join(' and ') +
        " order by (ab.shard, ab.realm, ab.num) " + order + "\n" +
        "     " + limitQuery;

    let sqlParams = tsParams
        .concat(accountParams)
        .concat(pubKeyParams)
        .concat(balanceParams)
        .concat(limitParams);

    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getBalance query: " +
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query
    return (pool
        .query(pgSqlQuery, sqlParams)
        .then(results => {
            let ret = {
                timestamp: null,
                balances: [],
                links: {
                    next: null
                }
            };

            // Go through all results, and collect them by seconds.
            // These need to be returned as an array (and not an object) because
            // per ECMA ES2015, the order of keys parsable as integers are implicitly 
            // sorted (i.e. insert order is not maintained)
            // let retObj = {}
            for (let row of results.rows) {
                let ns = utils.nsToSecNs(row.snapshot_time_ns);
                row.account = row.shard + '.' + row.realm + '.' + row.num;

                if (ret.timestamp === null) {
                    ret.timestamp = ns;
                }
                ret.balances.push({
                    'account': row.account,
                    'balance': Number(row.balance)
                });
            }
            const anchorAccountId = results.rows.length > 0 ?
                results.rows[results.rows.length - 1].account : 0;

            // Pagination links
            ret.links = {
                next: utils.getPaginationLink(req,
                    (ret.balances.length !== limit),
                    'account.id', anchorAccountId, order)
            }

            logger.debug("getBalances returning " +
                ret.balances.length + " entries");
            return (ret);
        })
    )
}

module.exports = {
    getBalances: getBalances
}
