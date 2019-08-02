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
const parseResultParams = function (req) {
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
const parsePaginationAndOrderParams = function (req, defaultOrder = 'desc') {
    // Parse the pagination parameters (i.e. limit/offset)
    let limitOffsetQuery = '';
    let limitOffsetParams = [];
    let lVal = getIntegerParam(req.query['limit'], globals.MAX_LIMIT);
    let limitValue = lVal === '' ? globals.MAX_LIMIT : lVal;
    limitOffsetQuery = 'limit ? ';
    limitOffsetParams.push(limitValue);

    let oVal = getIntegerParam(req.query['offset']);
    let offsetValue = 0;
    if (oVal != '') {
        limitOffsetQuery += 'offset ? ';
        limitOffsetParams.push(oVal);
        offsetValue = oVal;
    }

    // Parse the order parameters (default; descending)
    let order = defaultOrder;
    if (['asc', 'desc'].includes(req.query['order'])) {
        order = req.query['order'];
    }

    return ({
        limitOffsetQuery: limitOffsetQuery,
        limitOffsetParams: limitOffsetParams,
        order: order,
        limit: Number(limitValue),
        offset: Number(offsetValue)
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


/**
 * Create pagination (next) link
 * @param {HTTPRequest} req HTTP query request object
 * @param {Boolean} isEnd Is the next link valid or not
 * @return {Integer} limit Limit value
 * @return {Integer} offset Offset value
 * @return {String} next Fully formed link to the next page
 */
const getPaginationLink = function (req, isEnd, limit, offset, order, anchorSeconds) {
    const port = process.env.PORT;
    const portquery = (Number(port) === 80) ? '' : (':' + port);
    req = getTimeQueryForPagination(req, order, anchorSeconds);
    var next = '';
    if (!isEnd) {
        // Remove the limit and offset parameters from the current query
        for (const [q, v] of Object.entries(req.query)) {
            if (q === 'limit' || q === 'offset') {
                delete req.query[q];
            }
        }

        // Reconstruct the query string without the limit and offset parameters
        for (const [q, v] of Object.entries(req.query)) {
            if (Array.isArray(v)) {
                v.map(vv => (next += (next === '' ? '?' : '&') + q + '=' + vv));
            } else {
                next += (next === '' ? '?' : '&') + q + '=' + v;
            }
        }

        // And add back the new limit and offset values
        next = req.protocol + '://' + req.hostname + portquery + req.path + next +
            (next === '' ? '?' : '&') +
            'limit=' + limit + '&offset=' + (offset + limit);
    }
    return (next === '' ? null : next);
}

/**
 * Create an additional timestamp based query to ensure the integrity of 
 * paginated links results. The challege is that between consecutive paginated 
 * calls, the database could have received more recent entries, and a subsequent 
 * call could receive inconsistent data as a result.
 * This is handled by anchoring the queries on the page anchor (consensus seconds)
 * parameter.
 * @param {Request} req HTTP query request object
 * @param {String} order Order ('asc' or 'desc')
 * @return {Integer} anchorSeconds consensus seconds of the query result of 
 *          the call that started pagination
 * @return {Request} req Updated HTTP request object with inserted pageanchor parameter
 */
const getTimeQueryForPagination = function (req, order, anchorSeconds) {
    //  if descending
    //      if query has anchorSeconds:
    //          then just use that
    //      else:
    //          add anchorSeconds = anchorSeconds
    //
    if (order === 'desc') {
        if (anchorSeconds !== undefined && !req.query.pageanchor) {
            req.query.pageanchor = anchorSeconds;
        }
    }
    return (req);
}

module.exports = {
    parseParams: parseParams,
    parseCreditDebitParams: parseCreditDebitParams,
    parsePaginationAndOrderParams: parsePaginationAndOrderParams,
    parseResultParams: parseResultParams,
    convertMySqlStyleQueryToPostgress: convertMySqlStyleQueryToPostgress,
    getPaginationLink: getPaginationLink
}


