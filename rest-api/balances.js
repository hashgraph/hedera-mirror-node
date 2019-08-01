'use strict';
const utils = require('./utils.js');

/**
 * Handler function for /balances API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getBalances = function (req, res) {
    logger.debug("--------------------  getBalances --------------------");
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    // Parse the filter parameters for credit/debit, account-numbers, 
    // timestamp and pagination (anchor and limit/offset)
    const [accountQuery, accountParams] =
        utils.parseParams(req, 'account.id', ['ab.num']);
    const [tsQuery, tsParams] = utils.parseParams(req,
        'timestamp', ['abh.seconds']);

    let [anchorQuery, anchorParams] =
        utils.parseParams(req, 'pageanchor', ['abh.seconds']);
    anchorQuery = anchorQuery.replace('=', '<=');

    const { limitOffsetQuery, limitOffsetParams, order, limit, offset } =
        utils.parsePaginationAndOrderParams(req);

    let sqlQuery =
        "Select abh.seconds\n" +
        "    ,concat(ab.shard, '.', ab.realm, '.', ab.num) as account\n" +
        "    ,abh.balance\n" +
        "    ,abh.snapshot_time\n" +
        " from t_account_balance_history as abh\n" +
        "    ,t_account_balances ab\n" +
        " Where ab.id = abh.fk_balance_id\n" +
        (accountQuery === '' ? '' : '     and ') + accountQuery + "\n" +
        (tsQuery === '' ? '' : '     and ') + tsQuery + "\n" +
        (anchorQuery === '' ? '' : '     and ') + anchorQuery + '\n' +
        "     order by abh.seconds " + "\n" +
        "     " + order + "\n" +
        //        "   , account asc\n" +  // TODO: needed to ensure that pagination works 
                                          // fine. need to optimize this query
        "     " + limitOffsetQuery;


    let sqlParams = accountParams.concat(tsParams)
        .concat(anchorParams)
        .concat(limitOffsetParams);

    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getBalances query: " +
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
            logger.error("getBalances error: " +
                JSON.stringify(error, Object.getOwnPropertyNames(error)));
            res.json(ret);
            return;
        }

        let anchorSeconds = null;

        // Go through all results, and collect them by seconds.
        // These need to be returned as an array (and not an object) because
        // per ECMA ES2015, the order of keys parsable as integers are implicitly 
        // sorted (i.e. insert order is not maintained)
        let retObj = {}
        for (let entry of results.rows) {
            let sec = entry.seconds;
            if (anchorSeconds === null) {
                anchorSeconds = sec;
            }
            delete entry.seconds;
            if (!(sec in retObj)) {
                retObj[sec] = [];
                ret.balances.push({
                    seconds: sec,
                    accountbalances: []
                });
            }
            retObj[sec].push(entry);
        }

        for (let index in ret.balances) {
            ret.balances[index].accountbalances = retObj[ret.balances[index].seconds];
        }

        ret.links = {
            next: utils.getPaginationLink(req, false,
                limit, offset, order, anchorSeconds)
        }

        logger.debug("getBalances returning " +
            results.rows.length + " entries");
        res.json(ret);
    })
}


module.exports = {
    getBalances: getBalances
}
