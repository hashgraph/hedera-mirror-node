'use strict';

// Global constants
const globals = {
    MAX_LIMIT: 1000
}


/**
 * Parse the comparator symbols (i.e. gt, lt, etc.) and convert to SQL style query
 * @param {Array} fields Array of fields in the query (e.g. 'account.id' or 'timestamp')
 * @param {Array} req Array of values (e.g. 20 or gt:10)
 * @return {Object} {queryString, queryVals} Constructed SQL query string and values.
 */
const parseComparatorSymbol = function (fields, req) {
    let queryStr = '';
    let vals = [];

    const opsMap = {
        'lt': ' < ',
        'lte': ' <= ',
        'gt': ' > ',
        'gte': ' >= ',
        'eq': ' = ',
        'ne': ' != '
    };

    for (let item of req) {
        //Force a simple number to 'eq:number' form to have a consistent processing 
        if (!isNaN(Number(item))) {
            item = "eq:" + item;
        }
        // Split the gt:number into operation and value and create a SQL query string
        let splitItem = item.split(":");
        if (splitItem.length == 2) {
            let op = splitItem[0]
            let accountId = splitItem[1];
            if (!isNaN(accountId) && (op in opsMap)) {
                let fieldQueryStr = '';
                for (let f of fields) {
                    fieldQueryStr += (fieldQueryStr === '' ? '' : ' or ') +
                        f + ' ' + opsMap[op] + ' ?';
                    vals.push(accountId);
                }
                fieldQueryStr = '(' + fieldQueryStr + ')';

                queryStr += (queryStr === '' ? '' : ' and ') + fieldQueryStr;
            }
        }
    }

    return ({
        queryStr: '(' + queryStr + ')',
        queryVals: vals
    })
}

/**
 * Error/bound checking helper to get an integer parmeter from the query string
 * @param {String} param Value of the integer parameter as present in the query string
 * @param {Integer} limit Optional- max value
 * @return {String} Param value
 */
const getIntegerParam = function (param, limit = undefined) {
    if (param !== undefined && !isNaN(Number(param))) {
        if (limit !== undefined && param > limit) {
            param = limit;
        }
        return (param);
    }
    return ('');
}


/**
 * Parse the query filer parameter
 * @param {HTTPRequest} req HTTP Query request object
 * @param {String} queryField Query filter parameter name (e.g. account.id or timestamp) 
 * @param {Array of Strings} SQL table field names to construct the query
 * @return {Array} [query, params] Constructed SQL query fragment and corresponding values
 */
const parseParams = function (req, queryField, fields) {
    // Parse the timestamp filter parameters
    let query = '';
    let params = [];

    let reqQuery = req.query[queryField];
    if (reqQuery !== undefined) {
        // We either have a single entry of account filter, or an array (multiple entries)
        // Convert a single entry into an array to keep the processing consistent
        if (!Array.isArray(reqQuery)) {
            reqQuery = [reqQuery];
        }
        // Construct the SQL query fragment
        let qp = parseComparatorSymbol(fields, reqQuery)
        query = qp.queryStr;
        params = qp.queryVals;
    }
    return ([query, params]);
}

/**
 * Parse the type=[credit | debit | creditDebit] parameter
 * @param {HTTPRequest} req HTTP query request object
 * @return {String} Value of the credit/debit parameter
 */
const parseCreditDebitParams = function (req) {
    // Get the transaction type (credit, debit, or both)
    // By default, query for both credit and debit transactions
    let creditDebit = req.query.type;
    if (!['credit', 'debit'].includes(creditDebit)) {
        creditDebit = 'creditAndDebit';
    }
    return (creditDebit);
}

/**
 * Parse the result=[success | fail | all] parameter
 * @param {HTTPRequest} req HTTP query request object
 * @return {String} Value of the resultType parameter
 */
const parseResultParams = function (req)
{
    let resultType = req.query.result;
    let query = '';

    if (resultType === 'success') {
        query = '     and result=\'SUCCESS\'';
    } else if (resultType === 'fail') {
        query = '     and result != \'SUCCESS\'';
    }
    return (query);
//    return ((req.query.result === 'successful') ? 'successful' : 'all');
}

/**
 * Parse the pagination (limit/offset) and order parameters
 * @param {HTTPRequest} req HTTP query request object
 * @return {Object} {query, params, order} SQL query, values and order
 */
const parsePaginationAndOrderParams = function (req) {
    // Parse the pagination parameters (i.e. limit/offset)
    let limitOffsetQuery = '';
    let limitOffsetParams = [];
    let limitVal = getIntegerParam(req.query['limit'], globals.MAX_LIMIT);
    limitOffsetQuery = 'limit ? ';
    limitOffsetParams.push(limitVal === '' ? globals.MAX_LIMIT : limitVal);

    let offsetVal = getIntegerParam(req.query['offset']);
    if (offsetVal != '') {
        limitOffsetQuery += 'offset ? ';
        limitOffsetParams.push(offsetVal);
    }

    // Parse the order parameters (default; descending)
    let order = 'desc ';
    if (req.query['order'] === 'asc') {
        order = 'asc ';
    }

    return ({
        limitOffsetQuery: limitOffsetQuery,
        limitOffsetParams: limitOffsetParams,
        order: order
    });
}


/**
 * Convert the positional parameters from the MySql style query (?) to Postgres 
 * style positional parameters ($1, $2, etc)
 * @param {String} sqlQuery MySql style query
 * @param {Array of values} sqlParams Values of positional parameters
 * @return {String} SQL query with Postgres style positional parameters
 */
const convertMySqlStyleQueryToPostgress = function (sqlQuery, sqlParams) {
    let paramsCount = 0;
    let sqlQueryNonInject = sqlQuery.replace(/\?/g, function () {
        return '$' + ++paramsCount;
    });

    return (sqlQueryNonInject);
}

module.exports = {
    parseParams: parseParams,
    parseCreditDebitParams: parseCreditDebitParams,
    parsePaginationAndOrderParams: parsePaginationAndOrderParams,
    parseResultParams: parseResultParams,
    convertMySqlStyleQueryToPostgress: convertMySqlStyleQueryToPostgress
}


