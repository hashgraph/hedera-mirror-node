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
    // timestamp and pagination (limit/offset)
    const [accountQuery, accountParams] = 
        utils.parseParams(req, 'account.id', ['account_num']);
    const [tsQuery, tsParams] = utils.parseParams(req, 
            'timestamp', ['b.seconds']);
    const {limitOffsetQuery, limitOffsetParams, order} = 
        utils.parsePaginationAndOrderParams(req);

    let sqlQuery = 
        'Select b.seconds, b.account_num, b.balance\n' +
        ' from t_account_balance_history as b,\n' +
        ' (select distinct on (seconds) seconds\n' +
        '     from t_account_balance_history) as s\n' +
        ' Where b.seconds = s.seconds\n' +
        (accountQuery === '' ? '' : '     and ') + accountQuery + '\n' +
        (tsQuery === '' ? '' : '     and ') + tsQuery + '\n' +
        '     order by seconds ' + '\n' +
        '     ' + order + '\n' + 
        '     ' + limitOffsetQuery;


    let sqlParams = accountParams.concat(tsParams)
        .concat(limitOffsetParams);

    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getBalances query: " + 
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query
    pool.query(pgSqlQuery, sqlParams, (error, results) => {
        if (error) {
            logger.error("getBalances error: " + 
            JSON.stringify(error, Object.getOwnPropertyNames(error)));
            res.json({
                'balances': []
            });
            return;
        }
        let ret = {};
        for (let entry of results.rows) {
            if (!(entry.seconds in ret)) {
                ret[entry.seconds] = [];
            }
            ret[entry.seconds].push(entry);
        }
        logger.debug("getBalances returning " + 
            results.rows.length + " entries");
        res.json({
            'balances': ret
        });
    })
}


module.exports = {
    getBalances: getBalances
}
