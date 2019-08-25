'use strict';
const math = require('mathjs');
const config = require('./config.js');


/**
 * Split the account number into shard, realm and num fields. 
 * @param {String} acc Either 0.0.1234 or just 1234
 * @return {Object} {accShard, accRealm, accNum} Parsed account number
 */
const parseEntityId = function (acc) {
    let ret = {
        shard: 0,
        realm: 0,
        num: 0
    }

    const aSplit = acc.split(".");
    if (aSplit.length == 3) {
        ret.shard = aSplit[0];
        ret.realm = aSplit[1];
        ret.num = aSplit[2]
    } else if (aSplit.length == 1) {
        ret.num = acc;
    }
    return (ret);
}


/**
 * Parse the comparator symbols (i.e. gt, lt, etc.) and convert to SQL style query
 * @param {Array} fields Array of fields in the query (e.g. 'account.id' or 'timestamp')
 * @param {Array} valArr Array of values (e.g. 20 or gt:10)
 * @param {String} type One of 'entityId' or 'timestamp' for special interpretation as 
 *          an entity (shard.realm.entity format), or timestamp_ns (ssssssssss.nnnnnnnnn)
 * @return {Object} {queryString, queryVals} Constructed SQL query string and values.
 */
const parseComparatorSymbol = function (fields, valArr, type = null) {
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

    for (let item of valArr) {
        //Force a simple account number (e.g. 1234 or 0.0.1234) to 'eq:0.0.1234' form 
        // to allow for consistent processing 
        const pattern = type === 'hexstring' ? /^([A-Fa-f0-9])+$/ : /^(\d)+$/;
        if ((pattern.test(item)) ||
            (/^(\d)+\.(\d)+\.(\d)+$/.test(item))) {
            item = "eq:" + item;
        }

        // Split the gt:number into operation and value and create a SQL query string
        let splitItem = item.split(":");
        if (splitItem.length == 2) {
            let op = splitItem[0]
            let val = splitItem[1];

            let entity = null;

            if (type === 'entityId') {
                entity = parseEntityId(val);
            }

            if (((type === 'entityId' && entity.num !== 0) ||
                (type === 'hexstring') ||
                (type !== 'entityId' && !isNaN(val))) &&
                (op in opsMap)) {

                let fieldQueryStr = '';
                for (let f of fields) {
                    let fquery = '';
                    if (type === 'entityId') {
                        const shardRealmOp =
                            ['gt', 'lt'].includes(op) ? (op + 'e') : op;

                        fquery = '(' +
                            f.shard + ' ' + opsMap[shardRealmOp] + ' ? and ' +
                            f.realm + ' ' + opsMap[shardRealmOp] + ' ? and ' +
                            f.num + ' ' + opsMap[op] + ' ? ' +
                            ')';
                        vals = vals.concat([entity.shard, entity.realm, entity.num]);
                    } else if (type === 'timestamp_ns') {
                        // Expect timestamp input as (a) just seconds, 
                        // (b) seconds.mmm (3-digit milliseconds), 
                        // or (c) seconds.nnnnnnnnn (9-digit nanoseconds)
                        // Convert all of these formats to (seconds * 10^9 + nanoseconds) format, 
                        // after validating that all characters are digits
                        let tsSplit = val.split('.');
                        let seconds = /^(\d)+$/.test(tsSplit[0]) ? tsSplit[0] : 0;
                        let nanos = (tsSplit.length == 2 && /^(\d)+$/.test(tsSplit[1])) ? tsSplit[1] : 0;
                        let ts = '' + seconds + (nanos + '000000000').substring(0, 9);
                        fquery += '(' + f + ' ' + opsMap[op] + ' ?) ';
                        vals.push(ts);
                    } else {
                        fquery += '(' + f + ' ' + opsMap[op] + ' ?) ';
                        vals.push((type === 'hexstring' ? '\\x' : '') + val);
                    }
                    fieldQueryStr += (fieldQueryStr === '' ? '' : ' or ') +
                        fquery;
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
 * @param {Request} req HTTP Query request object
 * @param {String} queryField Query filter parameter name (e.g. account.id or timestamp) 
 * @param {Array of Strings} SQL table field names to construct the query
 * @param {String} type One of 'entityId' or 'timestamp' for special interpretation as 
 *          an entity (shard.realm.entity format), or timestamp (ssssssssss.nnnnnnnnn)
 * @return {Array} [query, params] Constructed SQL query fragment and corresponding values
 */
const parseParams = function (req, queryField, fields, type = null) {
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
        let qp = parseComparatorSymbol(fields, reqQuery, type)
        query = qp.queryStr;
        params = qp.queryVals;
    }
    return ([query, params]);
}

/**
 * Parse the type=[credit | debit | creditDebit] parameter
 * @param {Request} req HTTP query request object
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
}


/**
 * Parse the pagination (limit) and order parameters
 * @param {HTTPRequest} req HTTP query request object
 * @param {String} defaultOrder Order of sorting (defaults to descending)
 * @return {Object} {query, params, order} SQL query, values and order
 */
const parseLimitAndOrderParams = function (req, defaultOrder = 'desc') {
    // Parse the limit parameter
    let limitQuery = '';
    let limitParams = [];
    let lVal = getIntegerParam(req.query['limit'], config.limits.RESPONSE_ROWS);
    let limitValue = lVal === '' ? config.limits.RESPONSE_ROWS : lVal;
    limitQuery = 'limit ? ';
    limitParams.push(limitValue);

    // Parse the order parameters (default: descending)
    let order = defaultOrder;
    if (['asc', 'desc'].includes(req.query['order'])) {
        order = req.query['order'];
    }

    return ({
        limitQuery: limitQuery,
        limitParams: limitParams,
        order: order,
        limit: Number(limitValue)
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
 * @param {String} field The query parameter field name
 * @param {Any} lastValue THe last val for the 'next' queries in the pagination. 
 * @param {String} order Order of sorting the results
 * @return {String} next Fully formed link to the next page
 */
const getPaginationLink = function (req, isEnd, field, lastValue, order) {
    const port = process.env.PORT;
    const portquery = (Number(port) === 80) ? '' : (':' + port);

    var next = '';

    if (!isEnd) {
        const pattern = (order === 'asc') ? /gt[e]?:/ : /lt[e]?:/;
        const insertedPattern = (order === 'asc') ? 'gt' : 'lt';

        // Go through the query parameters, and if there is a 'field=gt:xxxx' (asc order)
        // or 'field=lt:xxxx' (desc order) fields, then remove that, to be replaced by the
        // new continuation value
        for (const [q, v] of Object.entries(req.query)) {
            if (Array.isArray(v)) {
                for (let vv of v) {
                    if (q === field && pattern.test(vv)) {
                        req.query[q] = req.query[q].filter(function (value, index, arr) {
                            return value != vv;
                        });
                    }
                }
            } else {
                if (q === field && pattern.test(v)) {
                    delete req.query[q];
                }
            }
        }

        // And add back the continuation value as 'field=gt:x' (asc order) or 
        // 'field=lt:x' (desc order)
        if (field in req.query) {
            req.query[field] = [].concat(req.query[field]).concat(insertedPattern + ':' + lastValue);
        } else {
            req.query[field] = insertedPattern + ':' + lastValue;
        }

        // Reconstruct the query string
        for (const [q, v] of Object.entries(req.query)) {
            if (Array.isArray(v)) {
                v.map(vv => (next += (next === '' ? '?' : '&') + q + '=' + vv));
            } else {
                next += (next === '' ? '?' : '&') + q + '=' + v;
            }
        }
        next = req.protocol + '://' + req.hostname + portquery + req.path + next;
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
 * @return {Integer} anchorSecNs consensus seconds of the query result of 
 *          the call that started pagination
 * @return {Request} req Updated HTTP request object with inserted pageanchor parameter
 */
const getTimeQueryForPagination = function (req, order, anchorSecNs) {
    //  if descending
    //      if query has anchorSecNs:
    //          then just use that
    //      else:
    //          add anchorSecNs = anchorSecNs
    //
    if (order === 'desc') {
        if (anchorSecNs !== undefined && !req.query.pageanchor) {
            req.query.pageanchor = anchorSecNs;
        }
    }
    return (req);
}

/**
* Converts nanoseconds since epoch to seconds.nnnnnnnnn format
* @param {String} ns Nanoseconds since epoch
* @return {String} Seconds since epoch (seconds.nnnnnnnnn format) 
*/
const nsToSecNs = function (ns) {
    return (math.divide(math.bignumber(ns), math.bignumber(1e9)).toFixed(9).toString());
}

/**
* Converts seconds since epoch (seconds.nnnnnnnnn format) to  nanoseconds
* @param {String} Seconds since epoch (seconds.nnnnnnnnn format) 
* @return {String} ns Nanoseconds since epoch
*/
const secNsToNs = function (secNs) {
    return (math.multiply(math.bignumber(secNs), math.bignumber(1e9)).toString());
}

const secNsToSeconds = function (secNs) {
    return (math.floor(Number(secNs)));
}

/**
* Returns the limit on how many result entries should be in the API
* @param {String} type of API (e.g. transactions, balances, etc.). Currently unused.
* @return {Number} limit Max # entries to be returned.
*/
const returnEntriesLimit = function (type) {
    return (config.limits.RESPONSE_ROWS);
}

/**
* Converts the byte array returned by SQL queries into hex string
* @param {ByteArray} byteArray Array of bytes to be converted to hex string
* @return {hexString} Converted hex string
*/
const toHexString = function (byteArray) {
    return (byteArray === null ? null :
        (byteArray.reduce((output, elem) =>
            (output + ('0' + elem.toString(16)).slice(-2)), ''))
    );
}

/**
* Converts a key for returning in JSON output
* @param {Array} key Byte array representing the key
* @return {Object} Key object - with type decoration for ED25519, if detected
*/
const encodeKey = function (key) {
    let ret;

    if (key === null) {
        return (null);
    }

    let hs = toHexString(key);
    const pattern = /^1220([A-Fa-f0-9]*)$/;
    const replacement = "$1";
    if (pattern.test(hs)) {
        ret = {
            '_type': 'ED25519',
            'key': hs.replace(pattern, replacement)
        }
    } else {
        ret = {
            '_type': 'ProtobufEncoded',
            'key': hs
        }
    }
    return (ret);
}

/**
* Base64 encoding of a byte array for returning in JSON output
* @param {Array} key Byte array to be encoded
* @return {String} base64 encoded string
*/
const encodeBase64 = function (buffer) {
    return (buffer.toString('base64'));
}

module.exports = {
    parseParams: parseParams,
    parseCreditDebitParams: parseCreditDebitParams,
    parseLimitAndOrderParams: parseLimitAndOrderParams,
    parseResultParams: parseResultParams,
    convertMySqlStyleQueryToPostgress: convertMySqlStyleQueryToPostgress,
    getPaginationLink: getPaginationLink,
    parseEntityId: parseEntityId,
    nsToSecNs: nsToSecNs,
    secNsToNs: secNsToNs,
    secNsToSeconds: secNsToSeconds,
    returnEntriesLimit: returnEntriesLimit,
    toHexString: toHexString,
    encodeKey: encodeKey,
    encodeBase64: encodeBase64
}
