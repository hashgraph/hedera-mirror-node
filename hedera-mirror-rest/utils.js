/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */
'use strict';
const constants = require('./constants.js');
const math = require('mathjs');
const config = require('./config.js');
const ed25519 = require('./ed25519.js');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');

const ENTITY_TYPE_FILE = 3;
const TRANSACTION_RESULT_SUCCESS = 22;

const successValidationResponse = {isValid: true, code: 200, contents: 'OK'};

const opsMap = {
  lt: ' < ',
  lte: ' <= ',
  gt: ' > ',
  gte: ' >= ',
  eq: ' = ',
  ne: ' != ',
};

/**
 * Check if the given number is numeric
 * @param {String} n Number to test
 * @return {Boolean} true if n is numeric, false otherwise
 */
function isNumeric(n) {
  return !isNaN(parseFloat(n)) && isFinite(n);
}

const isValidTimestampParam = function (timestamp) {
  // Accepted forms: seconds or seconds.upto 9 digits
  return /^\d{1,10}$/.test(timestamp) || /^\d{1,10}\.\d{1,9}$/.test(timestamp);
};

const isValidEntityNum = (entity_num) => {
  return /^\d{1,10}\.\d{1,10}\.\d{1,10}$/.test(entity_num) || /^\d{1,10}$/.test(entity_num);
};

const isValidLimitNum = (limit) => {
  return /^\d{1,4}$/.test(limit) && limit > 0 && limit <= config.maxLimit;
};

const isValidNum = (num) => {
  return /^\d{1,16}$/.test(num) && num > 0 && num <= Number.MAX_SAFE_INTEGER;
};

const isValidOperatorQuery = (query) => {
  return /^(gte?|lte?|eq|ne)$/.test(query);
};

const isValidAccountBalanceQuery = (query) => {
  return /^\d{1,19}$/.test(query);
};

const isValidPublicKeyQuery = (query) => {
  return /^[0-9a-fA-F]{64}$/.test(query) || /^[0-9a-fA-F]{88}$/.test(query);
};

const isValidUtf8Encoding = (query) => {
  if (undefined == query) {
    return false;
  }
  query = query.toLowerCase();
  return /^(utf-?8)$/.test(query);
};

const isValidEncoding = (query) => {
  if (undefined == query) {
    return false;
  }
  query = query.toLowerCase();
  return query === constants.characterEncoding.BASE64 || isValidUtf8Encoding(query);
};

/**
 * Validate input parameters for the rest apis
 * @param {String} param Parameter to be validated
 * @param {String} opAndVal operator:value to be validated
 * @return {Boolean} true if the parameter is valid. false otherwise
 */
const paramValidityChecks = function (param, opAndVal) {
  let ret = false;
  let val = null;
  let op = null;

  if (opAndVal === undefined) {
    return ret;
  }

  const splitVal = opAndVal.split(':');

  if (splitVal.length == 1) {
    op = 'eq';
    val = splitVal[0];
  } else if (splitVal.length == 2) {
    op = splitVal[0];
    val = splitVal[1];
  } else {
    return ret;
  }

  return filterValidityChecks(param, op, val);
};

const filterValidityChecks = function (param, op, val) {
  let ret = false;

  if (op === undefined || val === undefined) {
    return ret;
  }

  // Validate operator
  if (!isValidOperatorQuery(op)) {
    return ret;
  }

  // Validate the value
  switch (param) {
    case constants.filterKeys.ACCOUNT_ID:
      // Accepted forms: shard.realm.num or num
      ret = isValidEntityNum(val);
      break;
    case constants.filterKeys.TIMESTAMP:
      ret = isValidTimestampParam(val);
      break;
    case constants.filterKeys.ACCOUNT_BALANCE:
      // Accepted forms: Upto 50 billion
      ret = isValidAccountBalanceQuery(val);
      break;
    case constants.filterKeys.ACCOUNT_PUBLICKEY:
      // Acceptable forms: exactly 64 characters or +12 bytes (DER encoded)
      ret = isValidPublicKeyQuery(val);
      break;
    case constants.filterKeys.LIMIT:
      // Acceptable forms: upto 4 digits
      ret = isValidLimitNum(val);
      break;
    case constants.filterKeys.ORDER:
      // Acceptable words: asc or desc
      ret = Object.values(constants.orderFilterValues).includes(val.toLowerCase());
      break;
    case constants.filterKeys.TYPE:
      // Acceptable words: credit or debit
      ret = Object.values(constants.cryptoTransferType).includes(val.toLowerCase());
      break;
    case constants.filterKeys.RESULT:
      // Acceptable words: success or fail
      ret = Object.values(constants.transactionResultFilter).includes(val.toLowerCase());
      break;
    case constants.filterKeys.SEQUENCE_NUMBER:
      // Acceptable range: 0 < x <= Number.MAX_SAFE_INTEGER
      ret = isValidNum(val);
      break;
    case constants.filterKeys.ENCODING:
      // Acceptable words: binary or text
      ret = isValidEncoding(val.toLowerCase());
      break;
    default:
      // Every parameter should be included here. Otherwise, it will not be accepted.
      ret = false;
  }

  return ret;
};

/**
 * Validate input http request object
 * @param {HTTPRequest} req HTTP request object
 * @return {Object} result of validity check, and return http code/contents
 */
const validateReq = function (req) {
  let badParams = [];
  // Check the validity of every query parameter
  for (const key in req.query) {
    if (Array.isArray(req.query[key])) {
      for (const val of req.query[key]) {
        if (!paramValidityChecks(key, val)) {
          badParams.push(key);
        }
      }
    } else {
      if (!paramValidityChecks(key, req.query[key])) {
        badParams.push(key);
      }
    }
  }

  if (badParams.length > 0) {
    throw InvalidArgumentError.forParams(badParams);
  }
};

/**
 * Split the account number into shard, realm and num fields.
 * @param {String} acc Either 0.0.1234 or just 1234
 * @return {Object} {accShard, accRealm, accNum} Parsed account number
 */
const parseEntityId = function (acc) {
  let ret = {
    shard: 0,
    realm: 0,
    num: 0,
  };

  const aSplit = acc.split('.');
  if (aSplit.length == 3) {
    if (isNumeric(aSplit[0]) && isNumeric(aSplit[1]) && isNumeric(aSplit[2])) {
      ret.shard = aSplit[0];
      ret.realm = aSplit[1];
      ret.num = aSplit[2];
    }
  } else if (aSplit.length == 1) {
    if (isNumeric(acc)) {
      ret.num = acc;
    }
  }
  return ret;
};

const parseTimestampParam = function (timestampParam) {
  // Expect timestamp input as (a) just seconds,
  // (b) seconds.mmm (3-digit milliseconds),
  // or (c) seconds.nnnnnnnnn (9-digit nanoseconds)
  // Convert all of these formats to (seconds * 10^9 + nanoseconds) format,
  // after validating that all characters are digits
  if (!timestampParam) {
    return '';
  }
  let tsSplit = timestampParam.split('.');
  if (tsSplit.length <= 0 || tsSplit.length > 2) {
    return '';
  }
  let seconds = /^(\d)+$/.test(tsSplit[0]) ? tsSplit[0] : 0;
  let nanos = tsSplit.length === 2 && /^(\d)+$/.test(tsSplit[1]) ? tsSplit[1] : 0;
  return '' + seconds + (nanos + '000000000').substring(0, 9);
};

/**
 * Parse the comparator symbols (i.e. gt, lt, etc.) and convert to SQL style query
 * @param {Array} fields Array of fields in the query (e.g. 'account.id' or 'timestamp')
 * @param {Array} valArr Array of values (e.g. 20 or gt:10)
 * @param {String} type Type of the field such as:
 *      'entityId': Could be just a number like 1234 that gets converted to 0.0.1234, or a full
 *          entity id in shard.realm.entityId form; or
 *      'timestamp': Could be just in seconds followed by optional decimal point and millis or nanos
 * @param {Function} valueTranslate Function(str)->str to apply to the query parameter's value (ie toLowerCase)
 *          this happens to the value _after_ operators removed and doesn't affect the operator
 * @return {Object} {queryString, queryVals} Constructed SQL query string and values.
 */
const parseComparatorSymbol = function (fields, valArr, type = null, valueTranslate = null) {
  let queryStr = '';
  let vals = [];

  for (let item of valArr) {
    // Split the gt:number into operation and value and create a SQL query string
    let splitItem = item.split(':');
    if (splitItem.length === 1 || splitItem.length === 2) {
      let op;
      let val;
      if (splitItem.length === 1) {
        // No operator specified. Just use "eq:"
        op = 'eq';
        val = splitItem[0];
      } else {
        op = splitItem[0];
        val = splitItem[1];
      }
      if (null !== valueTranslate) {
        val = valueTranslate(val);
      }

      let entity = null;

      if (type === 'entityId') {
        entity = parseEntityId(val);
      }

      if (op in opsMap) {
        let fieldQueryStr = '';
        for (let f of fields) {
          let fquery = '';
          if (type === 'entityId') {
            // add realm_num check once
            if (!queryStr.includes('realm_num = ?')) {
              fquery = f.realm + ' ' + opsMap['eq'] + ' ? and ';
            }

            fquery += f.num + ' ' + opsMap[op] + ' ? ';
            vals = vals.concat([entity.realm, entity.num]);
          } else if (type === 'timestamp_ns') {
            let ts = parseTimestampParam(val);
            fquery += '(' + f + ' ' + opsMap[op] + ' ?) ';
            vals.push(ts);
          } else if (type === 'balance') {
            if (isNumeric(val)) {
              fquery += '(' + f + ' ' + opsMap[op] + ' ?) ';
              vals.push(val);
            } else {
              fquery += '(1=1)';
            }
          } else if (type === 'publickey') {
            // If the supplied key is DER encoded, decode it
            const decodedKey = ed25519.derToEd25519(val);
            if (decodedKey != null) {
              val = decodedKey;
            }
            fquery += '(' + f + ' ' + opsMap[op] + ' ?) ';
            vals.push(val.toLowerCase());
          } else {
            fquery += '(' + f + ' ' + opsMap[op] + ' ?) ';
            vals.push(val);
          }
          fieldQueryStr += (fieldQueryStr === '' ? '' : ' or ') + fquery;
        }

        queryStr += (queryStr === '' ? '' : ' and ') + fieldQueryStr;
      }
    }
  }
  queryStr = queryStr === '' ? '1=1' : queryStr;

  return {
    queryStr: '(' + queryStr + ')',
    queryVals: vals,
  };
};

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
    return param;
  }
  return '';
};

/**
 * Parse the query filer parameter
 * @param {Request} req HTTP Query request object
 * @param {String} queryField Query filter parameter name (e.g. account.id or timestamp)
 * @param {Array of Strings} SQL table field names to construct the query
 * @param {String} type One of 'entityId' or 'timestamp' for special interpretation as
 *          an entity (shard.realm.entity format), or timestamp (ssssssssss.nnnnnnnnn)
 * @param {Function} valueTranslate Function(str)->str to apply to the query parameter's value (ie toLowerCase)
 *          this happens to the value _after_ operators removed and doesn't affect the operator
 * @return {Array} [query, params] Constructed SQL query fragment and corresponding values
 */
const parseParams = function (req, queryField, fields, type = null, valueTranslate = null) {
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
    let qp = parseComparatorSymbol(fields, reqQuery, type, valueTranslate);
    query = qp.queryStr;
    params = qp.queryVals;
  }
  return [query, params];
};

/**
 * Parse the type=[credit | debit | creditDebit] parameter
 * @param {Request} req HTTP query request object
 * @return {String} Value of the credit/debit parameter
 */
const parseCreditDebitParams = function (req) {
  // Get the transaction type (credit, debit, or both)
  // By default, query for both credit and debit transactions
  let creditDebit = req.query.type;
  if (!Object.values(constants.cryptoTransferType).includes(creditDebit)) {
    creditDebit = 'creditAndDebit';
  }
  return creditDebit;
};

/**
 * Parse the result=[success | fail | all] parameter
 * @param {HTTPRequest} req HTTP query request object
 * @return {String} Value of the resultType parameter
 */
const parseResultParams = function (req) {
  let resultType = req.query.result;
  let query = '';

  if (resultType === constants.transactionResultFilter.SUCCESS) {
    query = '     result=' + TRANSACTION_RESULT_SUCCESS;
  } else if (resultType === constants.transactionResultFilter.FAIL) {
    query = '     result != ' + TRANSACTION_RESULT_SUCCESS;
  }
  return query;
};

/**
 * Parse the pagination (limit) and order parameters
 * @param {HTTPRequest} req HTTP query request object
 * @param {String} defaultOrder Order of sorting (defaults to descending)
 * @return {Object} {query, params, order} SQL query, values and order
 */
const parseLimitAndOrderParams = function (req, defaultOrder = constants.orderFilterValues.DESC) {
  // Parse the limit parameter
  let limitQuery = '';
  let limitParams = [];
  let lVal = getIntegerParam(req.query[constants.filterKeys.LIMIT], config.maxLimit);
  let limitValue = lVal === '' ? config.maxLimit : lVal;
  limitQuery = `${constants.filterKeys.LIMIT} ? `;
  limitParams.push(limitValue);

  // Parse the order parameters (default: descending)
  let order = defaultOrder;
  if (Object.values(constants.orderFilterValues).includes(req.query[constants.filterKeys.ORDER])) {
    order = req.query[constants.filterKeys.ORDER];
  }

  return buildPgSqlObject(limitQuery, limitParams, order, limitValue);
};

const buildPgSqlObject = (query, params, order, limit) => {
  return {
    query: query,
    params: params,
    order: order,
    limit: Number(limit),
  };
};

/**
 * Convert the positional parameters from the MySql style query (?) to Postgres
 * style positional parameters ($1, $2, etc)
 * @param {String} sqlQuery MySql style query
 * @param {Array of values} sqlParams Values of positional parameters
 * @return {String} SQL query with Postgres style positional parameters
 */
const convertMySqlStyleQueryToPostgres = function (sqlQuery, sqlParams) {
  let paramsCount = 0;
  let sqlQueryNonInject = sqlQuery.replace(/\?/g, function () {
    return '$' + ++paramsCount;
  });

  return sqlQueryNonInject;
};

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
  let urlPrefix;
  if (config.port != undefined && config.includeHostInLink == 1) {
    urlPrefix = req.protocol + '://' + req.hostname + ':' + config.port;
  } else {
    urlPrefix = '';
  }

  var next = '';

  if (!isEnd) {
    const pattern = order === constants.orderFilterValues.ASC ? /gt[e]?:/ : /lt[e]?:/;
    const insertedPattern = order === constants.orderFilterValues.ASC ? 'gt' : 'lt';

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
        v.map((vv) => (next += (next === '' ? '?' : '&') + q + '=' + vv));
      } else {
        next += (next === '' ? '?' : '&') + q + '=' + v;
      }
    }
    next = urlPrefix + req.path + next;
  }
  return next === '' ? null : next;
};

/**
 * Converts nanoseconds since epoch to seconds.nnnnnnnnn format
 * @param {String} ns Nanoseconds since epoch
 * @return {String} Seconds since epoch (seconds.nnnnnnnnn format)
 */
const nsToSecNs = function (ns) {
  return math.divide(math.bignumber(ns), math.bignumber(1e9)).toFixed(9).toString();
};

/**
 * Converts nanoseconds since epoch to seconds-nnnnnnnnn format
 * @param {String} ns Nanoseconds since epoch
 * @return {String} Seconds since epoch (seconds-nnnnnnnnn format)
 */
const nsToSecNsWithHyphen = function (ns) {
  return nsToSecNs(ns).replace('.', '-');
};

/**
 * Converts seconds since epoch (seconds.nnnnnnnnn format) to  nanoseconds
 * @param {String} Seconds since epoch (seconds.nnnnnnnnn format)
 * @return {String} ns Nanoseconds since epoch
 */
const secNsToNs = function (secNs) {
  return math.multiply(math.bignumber(secNs), math.bignumber(1e9)).toString();
};

const secNsToSeconds = function (secNs) {
  return math.floor(Number(secNs));
};

/**
 * Returns the limit on how many result entries should be in the API
 * @param {String} type of API (e.g. transactions, balances, etc.). Currently unused.
 * @return {Number} limit Max # entries to be returned.
 */
const returnEntriesLimit = function (type) {
  return config.maxLimit;
};

/**
 * Converts the byte array returned by SQL queries into hex string
 * @param {ByteArray} byteArray Array of bytes to be converted to hex string
 * @return {hexString} Converted hex string
 */
const toHexString = function (byteArray) {
  return byteArray === null
    ? null
    : byteArray.reduce((output, elem) => output + ('0' + elem.toString(16)).slice(-2), '');
};

/**
 * Converts a key for returning in JSON output
 * @param {Array} key Byte array representing the key
 * @return {Object} Key object - with type decoration for ED25519, if detected
 */
const encodeKey = function (key) {
  let ret;

  if (key === null) {
    return null;
  }

  let hs = toHexString(key);
  const pattern = /^1220([A-Fa-f0-9]*)$/;
  const replacement = '$1';
  if (pattern.test(hs)) {
    ret = {
      _type: 'ED25519',
      key: hs.replace(pattern, replacement),
    };
  } else {
    ret = {
      _type: 'ProtobufEncoded',
      key: hs,
    };
  }
  return ret;
};

/**
 * Base64 encoding of a byte array for returning in JSON output
 * @param {Array} key Byte array to be encoded
 * @return {String} base64 encoded string
 */
const encodeBase64 = function (buffer) {
  return encodeBinary(buffer, constants.characterEncoding.BASE64);
};

/**
 * Base64 encoding of a byte array for returning in JSON output
 * @param {Array} key Byte array to be encoded
 * @return {String} utf-8 encoded string
 */
const encodeUtf8 = function (buffer) {
  return encodeBinary(buffer, constants.characterEncoding.UTF8);
};

const encodeBinary = function (buffer, encoding) {
  // default to base64 encoding
  let charEncoding = constants.characterEncoding.BASE64;
  if (isValidUtf8Encoding(encoding)) {
    charEncoding = constants.characterEncoding.UTF8;
  }

  return null === buffer ? null : buffer.toString(charEncoding);
};

/**
 *
 * @param {String} num Nullable number
 * @returns {Any} representation of math.bignumber value of parameter or null if null
 */
const getNullableNumber = function (num) {
  return num == null ? null : math.bignumber(num).toString();
};

/**
 * Construct a transaction id using format: shard.realm.num-sssssssssss-nnnnnnnnn
 * @param {String} shard shard number
 * @param {String} realm realm number
 * @param {String} num entity number
 * @param {String} validStartTimestamp valid start time
 * @returns {String} transactionId of format format: shard.realm.num-sssssssssss-nnnnnnnnn
 */
const createTransactionId = function (shard, realm, num, validStartTimestamp) {
  return shard + '.' + realm + '.' + num + '-' + nsToSecNsWithHyphen(validStartTimestamp);
};

/**
 * Given the req.query object build the filters object
 * @param filters
 */
const buildFilterObject = (filters) => {
  let filterObject = [];
  if (filters === null) {
    return null;
  }

  for (const [key, values] of Object.entries(filters)) {
    // for repeated params val will be an array
    if (Array.isArray(values)) {
      for (const val of values) {
        filterObject.push(buildComparatorFilter(key, val));
      }
    } else {
      filterObject.push(buildComparatorFilter(key, values));
    }
  }

  return filterObject;
};

const buildComparatorFilter = (name, filter) => {
  let splitVal = filter.split(':');
  let opVal = splitVal.length === 1 ? ['eq', filter] : splitVal;

  return {
    key: name,
    operator: opVal[0],
    value: opVal[1],
  };
};

/**
 * Verify param and filters meet expected format
 * Additionally update format to be persistence query compatible
 * @param filters
 * @returns {{code: number, contents: {_status: {messages: *}}, isValid: boolean}|{code: number, contents: string, isValid: boolean}}
 */
const validateAndParseFilters = (filters) => {
  let badParams = [];

  for (const filter of filters) {
    if (!filterValidityChecks(filter.key, filter.operator, filter.value)) {
      badParams.push(filter.key);
    } else {
      formatComparator(filter);
    }
  }

  if (badParams.length > 0) {
    throw InvalidArgumentError.forParams(badParams);
  }
};

const formatComparator = (comparator) => {
  if (comparator.operator in opsMap) {
    // update operator
    comparator.operator = opsMap[comparator.operator];

    // format value
    switch (comparator.key) {
      case constants.filterKeys.ACCOUNT_ID:
        // Accepted forms: shard.realm.num or num
        comparator.value = parseEntityId(comparator.value);
        break;
      case constants.filterKeys.TIMESTAMP:
        comparator.value = parseTimestampParam(comparator.value);
        break;
      case constants.filterKeys.ACCOUNT_PUBLICKEY:
        // Acceptable forms: exactly 64 characters or +12 bytes (DER encoded)
        comparator.value = ed25519.derToEd25519(comparator.value);
        break;
      // case 'type':
      //   // Acceptable words: credit or debit
      //   comparator.value = ;
      //   break;
      // case 'result':
      //   // Acceptable words: success or fail
      //   comparator.value = ;
      //   break;
      default:
    }
  }
};

module.exports = {
  buildFilterObject: buildFilterObject,
  buildComparatorFilter: buildComparatorFilter,
  buildPgSqlObject: buildPgSqlObject,
  createTransactionId: createTransactionId,
  convertMySqlStyleQueryToPostgres: convertMySqlStyleQueryToPostgres,
  encodeBase64: encodeBase64,
  encodeBinary,
  encodeUtf8,
  encodeKey: encodeKey,
  ENTITY_TYPE_FILE: ENTITY_TYPE_FILE,
  filterValidityChecks: filterValidityChecks,
  formatComparator: formatComparator,
  getNullableNumber: getNullableNumber,
  getPaginationLink: getPaginationLink,
  isValidEntityNum: isValidEntityNum,
  isValidLimitNum: isValidLimitNum,
  isValidNum: isValidNum,
  isValidTimestampParam: isValidTimestampParam,
  parseCreditDebitParams: parseCreditDebitParams,
  parseEntityId: parseEntityId,
  parseLimitAndOrderParams: parseLimitAndOrderParams,
  parseParams: parseParams,
  parseResultParams: parseResultParams,
  parseTimestampParam: parseTimestampParam,
  nsToSecNs: nsToSecNs,
  nsToSecNsWithHyphen: nsToSecNsWithHyphen,
  returnEntriesLimit: returnEntriesLimit,
  secNsToNs: secNsToNs,
  secNsToSeconds: secNsToSeconds,
  toHexString: toHexString,
  TRANSACTION_RESULT_SUCCESS: TRANSACTION_RESULT_SUCCESS,
  validateAndParseFilters: validateAndParseFilters,
  validateReq: validateReq,
};
