'use strict';
const utils = require('./utils.js');

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
    // timestamp and pagination (limit/offset)
    const creditDebit = utils.parseCreditDebitParams(req);
    const [accountQuery, accountParams] = 
        utils.parseParams(req, 'account.id',
            creditDebit === 'credit' ? ['eto.entity_num'] :
                creditDebit === 'debit' ? ['efrom.entity_num'] :
                    ['eto.entity_num', 'efrom.entity_num']);
    const [tsQuery, tsParams] = 
        utils.parseParams(req, 'timestamp', ['t.seconds']);
    const resultTypeQuery = utils.parseResultParams(req);
    const { limitOffsetQuery, limitOffsetParams, order } = 
        utils.parsePaginationAndOrderParams(req);



    let sqlQuery = 
	'select transaction_id\n' +
	'     ,enode.entity_num as node_account\n' +
        '     ,memo\n' +
        '     ,seconds\n' +
        '     ,nanos\n' +
        '     ,efrom.entity_num as from_account\n' +
        '     ,eto.entity_num as to_account\n' +
        '     ,ct.amount\n' +
        '     ,result\n' +
	' from t_transactions t\n' +
        '     ,t_cryptotransfers ct\n' +
        '     ,t_transfer_types tt\n' +
        '     ,t_entities enode\n' +
        '     ,t_entities efrom\n' +
        '     ,t_entities eto\n' +
        ' where t.id = ct.tx_id\n' +
        '     and ct.payment_type_id = tt.id\n' +
        '     and enode.id = t.node_account_id\n' +
        '     and efrom.id = ct.from_account_id\n' +
        '     and eto.id = ct.to_account_id\n' +
        (accountQuery === '' ? '' : '     and ') + accountQuery + '\n' +
        (tsQuery === '' ? '' : '     and ') + tsQuery + '\n' +
        resultTypeQuery + '\n' +
        '     order by seconds ' + order + '\n' +
        '     , t.id ' + order + '\n' +
        '     ' + limitOffsetQuery;
    let sqlParams = accountParams.concat(tsParams)
        .concat(limitOffsetParams);

    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getTransactions query: " + 
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query
    pool.query(pgSqlQuery, sqlParams, (error, results) => {
        if (error) {
            logger.error("getTransactions error: " +
                JSON.stringify(error, Object.getOwnPropertyNames(error)));
            res.end(JSON.stringify({
                'transactions': []
            }));
            return;
        }
        logger.debug("getTransactions returning " + 
            results.rows.length + " entries");
        res.json({
            'transactions': results.rows
        });
    })
}

/**
 * Handler function for /transactions API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getOneTransaction = function (req, res) {
    logger.debug("--------------------  getTransactions --------------------");
    logger.debug("Client: [" + req.ip + "] URL: " + req.originalUrl);

    const accountQuery = 'transaction_id = ?\n';
    const sqlParams = [req.params.id];

    let sqlQuery = 
	'select transaction_id\n' +
	'     ,enode.entity_num as node_account\n' +
        '     ,memo\n' +
        '     ,seconds\n' +
        '     ,nanos\n' +
        '     ,efrom.entity_num as from_account\n' +
        '     ,eto.entity_num as to_account\n' +
        '     ,ct.amount\n' +
        '     ,result\n' +
	' from t_transactions t\n' +
        '     ,t_cryptotransfers ct\n' +
        '     ,t_transfer_types tt\n' +
        '     ,t_entities enode\n' +
        '     ,t_entities efrom\n' +
        '     ,t_entities eto\n' +
        ' where t.id = ct.tx_id\n' +
        '     and ct.payment_type_id = tt.id\n' +
        '     and enode.id = t.node_account_id\n' +
        '     and efrom.id = ct.from_account_id\n' +
        '     and eto.id = ct.to_account_id\n' +
        (accountQuery === '' ? '' : '     and ') + accountQuery;

    const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(
        sqlQuery, sqlParams);

    logger.debug("getTransactions query: " + 
        pgSqlQuery + JSON.stringify(sqlParams));

    // Execute query
    pool.query(pgSqlQuery, sqlParams, (error, results) => {
        if (error) {
            logger.error("getOneTransaction error: " +
                JSON.stringify(error, Object.getOwnPropertyNames(error)));
            res.end(JSON.stringify({
                'transactions': []
            }));
            return;
        }
        logger.debug("getOneTransaction returning " + 
            results.rows.length + " entries");
        if (results.rows.length === 1) {
            res.json(
                results.rows[0]
            );
        } else {
            res.status(404)  
                .send('Not found');
        }
    })
}


module.exports = {
    getTransactions: getTransactions,
    getOneTransaction: getOneTransaction
}
