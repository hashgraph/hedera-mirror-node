/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const _ = require('lodash');
const crypto = require('crypto');
const math = require('mathjs');
const constants = require('./constants');
const EntityId = require('./entityId');
const config = require('./config');
const ed25519 = require('./ed25519');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');
const transactionTypes = require('./transactionTypes');

const ENTITY_TYPE_ACCOUNT = 1;
const ENTITY_TYPE_FILE = 3;
const TRANSACTION_RESULT_SUCCESS = 22;

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
const isNumeric = (n) => {
  return !isNaN(parseFloat(n)) && isFinite(n);
};

const isValidTimestampParam = (timestamp) => {
  // Accepted forms: seconds or seconds.upto 9 digits
  return /^\d{1,10}$/.test(timestamp) || /^\d{1,10}\.\d{1,9}$/.test(timestamp);
};

const isValidEntityNum = (entityNum) => {
  return /^\d{1,10}\.\d{1,10}\.\d{1,10}$/.test(entityNum) || /^\d{1,10}$/.test(entityNum);
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
  if (!query) {
    return false;
  }
  return /^(utf-?8)$/.test(query.toLowerCase());
};

const isValidEncoding = (query) => {
  if (query === undefined) {
    return false;
  }
  query = query.toLowerCase();
  return query === constants.characterEncoding.BASE64 || isValidUtf8Encoding(query);
};

const isValidTransactionType = async (transactionType) => {
  return _.isString(transactionType) && (await transactionTypes.get(transactionType)) !== undefined;
};

/**
 * Validate input parameters for the rest apis
 * @param {String} param Parameter to be validated
 * @param {String} opAndVal operator:value to be validated
 * @return {Boolean} true if the parameter is valid. false otherwise
 */
const paramValidityChecks = (param, opAndVal) => {
  const ret = false;
  let val = null;
  let op = null;

  if (opAndVal === undefined) {
    return ret;
  }

  const splitVal = opAndVal.split(':');

  if (splitVal.length === 1) {
    op = 'eq';
    val = splitVal[0];
  } else if (splitVal.length === 2) {
    op = splitVal[0];
    val = splitVal[1];
  } else {
    return ret;
  }

  return filterValidityChecks(param, op, val);
};

const filterValidityChecks = async (param, op, val) => {
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
    case constants.filterKeys.ACCOUNT_BALANCE:
      // Accepted forms: Upto 50 billion
      ret = isValidAccountBalanceQuery(val);
      break;
    case constants.filterKeys.ACCOUNT_ID:
      // Accepted forms: shard.realm.num or num
      ret = isValidEntityNum(val);
      break;
    case constants.filterKeys.ACCOUNT_PUBLICKEY:
      // Acceptable forms: exactly 64 characters or +12 bytes (DER encoded)
      ret = isValidPublicKeyQuery(val);
      break;
    case constants.filterKeys.ENCODING:
      // Acceptable words: binary or text
      ret = isValidEncoding(val.toLowerCase());
      break;
    case constants.filterKeys.ENTITY_PUBLICKEY:
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
    case constants.filterKeys.RESULT:
      // Acceptable words: success or fail
      ret = Object.values(constants.transactionResultFilter).includes(val.toLowerCase());
      break;
    case constants.filterKeys.SEQUENCE_NUMBER:
      // Acceptable range: 0 < x <= Number.MAX_SAFE_INTEGER
      ret = isValidNum(val);
      break;
    case constants.filterKeys.TIMESTAMP:
      ret = isValidTimestampParam(val);
      break;
    case constants.filterKeys.TOKEN_ID:
      // Accepted forms: shard.realm.num or num
      ret = isValidEntityNum(val);
      break;
    case constants.filterKeys.CREDIT_TYPE:
      // Acceptable words: credit or debit
      ret = Object.values(constants.cryptoTransferType).includes(val.toLowerCase());
      break;
    case constants.filterKeys.TRANSACTION_TYPE:
      // Accepted forms: valid transaction type string
      ret = await isValidTransactionType(val);
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
const validateReq = async (req) => {
  const badParams = [];
  // Check the validity of every query parameter
  for (const key in req.query) {
    if (Array.isArray(req.query[key])) {
      for (const val of req.query[key]) {
        if (!(await paramValidityChecks(key, val))) {
          badParams.push(key);
        }
      }
    } else if (!(await paramValidityChecks(key, req.query[key]))) {
      badParams.push(key);
    }
  }

  if (badParams.length > 0) {
    throw InvalidArgumentError.forParams(badParams);
  }
};

const parseTimestampParam = (timestampParam) => {
  // Expect timestamp input as (a) just seconds,
  // (b) seconds.mmm (3-digit milliseconds),
  // or (c) seconds.nnnnnnnnn (9-digit nanoseconds)
  // Convert all of these formats to (seconds * 10^9 + nanoseconds) format,
  // after validating that all characters are digits
  if (!timestampParam) {
    return '';
  }
  const tsSplit = timestampParam.split('.');
  if (tsSplit.length <= 0 || tsSplit.length > 2) {
    return '';
  }
  const seconds = /^(\d)+$/.test(tsSplit[0]) ? tsSplit[0] : 0;
  let nanos = tsSplit.length === 2 && /^(\d)+$/.test(tsSplit[1]) ? tsSplit[1] : 0;
  nanos = `${nanos}000000000`.substring(0, 9);
  return `${seconds}${nanos}`;
};

/**
 * @returns {Object} null if paramValue has invalid operator, or not correctly formatted.
 */
const parseOperatorAndValueFromQueryParam = (paramValue) => {
  // Split the op:value into operation and value and create a SQL query string
  const splitItem = paramValue.split(':');
  if (splitItem.length === 1) {
    // No operator specified. Just use "eq:"
    return {op: opsMap.eq, value: splitItem[0]};
  }
  if (splitItem.length === 2) {
    if (!(splitItem[0] in opsMap)) {
      return null;
    }
    return {op: opsMap[splitItem[0]], value: splitItem[1]};
  }
  return null;
};

/**
 * Error/bound checking helper to get an integer parmeter from the query string
 * @param {String} param Value of the integer parameter as present in the query string
 * @param {Integer} limit Optional- max value
 * @return {String} Param value
 */
const getIntegerParam = (param, limit = undefined) => {
  if (param !== undefined && !Number.isNaN(Number(param))) {
    if (limit !== undefined && param > limit) {
      param = limit;
    }
    return param;
  }
  return '';
};

/**
 * Parse the query filer parameter
 * @param paramValues Value of the query param after parsing by ExpressJS
 * @param {Function} processOpAndValue function to compute partial sql clause and sql params using comparator and value
 *          in the query param.
 * @return {Array} [query, params] Constructed SQL query fragment and corresponding values
 */
const parseParams = (paramValues, processOpAndValue) => {
  if (paramValues === undefined) {
    return ['', []];
  }
  // We either have a single entry of account filter, or an array (multiple entries)
  // Convert a single entry into an array to keep the processing consistent
  if (!Array.isArray(paramValues)) {
    paramValues = [paramValues];
  }
  const partialQueries = [];
  let values = [];
  // Iterate for each value of param. For a url '..?q=val1&q=val2', paramValues for 'q' are [val1, val2].
  for (const paramValue of paramValues) {
    const opAndValue = parseOperatorAndValueFromQueryParam(paramValue);
    if (opAndValue === null) {
      continue;
    }
    const queryAndValues = processOpAndValue(opAndValue.op, opAndValue.value);
    if (queryAndValues !== null) {
      partialQueries.push(queryAndValues[0]);
      values = values.concat(queryAndValues[1]);
    }
  }
  return [partialQueries.join(' and '), values];
};

const parseAccountIdQueryParam = (parsedQueryParams, columnName) => {
  return parseParams(parsedQueryParams[constants.filterKeys.ACCOUNT_ID], (op, value) => {
    const accountId = EntityId.fromString(value);
    return [`${columnName} ${op} ?`, [accountId.getEncodedId()]];
  });
};

const parseTimestampQueryParam = (parsedQueryParams, columnName, opOverride = {}) => {
  return parseParams(parsedQueryParams[constants.filterKeys.TIMESTAMP], (op, value) => {
    return [`${columnName} ${op in opOverride ? opOverride[op] : op} ?`, [parseTimestampParam(value)]];
  });
};

const parseBalanceQueryParam = (parsedQueryParams, columnName) => {
  return parseParams(parsedQueryParams[constants.filterKeys.ACCOUNT_BALANCE], (op, value) => {
    if (isNumeric(value)) {
      return [`${columnName} ${op} ?`, [value]];
    }
    return null;
  });
};

const parsePublicKeyQueryParam = (parsedQueryParams, columnName) => {
  return parseParams(parsedQueryParams[constants.filterKeys.ACCOUNT_PUBLICKEY], (op, value) => {
    let key = value.toLowerCase();
    // If the supplied key is DER encoded, decode it
    const decodedKey = ed25519.derToEd25519(key);
    if (decodedKey != null) {
      key = decodedKey;
    }
    return [`${columnName} ${op} ?`, [key]];
  });
};

/**
 * Parse the type=[credit | debit | creditDebit] parameter
 */
const parseCreditDebitParams = (parsedQueryParams, columnName) => {
  return parseParams(parsedQueryParams[constants.filterKeys.CREDIT_TYPE], (op, value) => {
    if (value === 'credit') {
      return [`${columnName} > 0`, []];
    }
    if (value === 'debit') {
      return [`${columnName} < 0`, []];
    }
  });
};

/**
 * Parse the result=[success | fail | all] parameter
 * @param {Request} req HTTP query request object
 * @param {String} columnName Column name for the transaction result
 * @return {String} Value of the resultType parameter
 */
const parseResultParams = (req, columnName) => {
  const resultType = req.query.result;
  let query = '';

  if (resultType === constants.transactionResultFilter.SUCCESS) {
    query = `${columnName} = ${TRANSACTION_RESULT_SUCCESS}`;
  } else if (resultType === constants.transactionResultFilter.FAIL) {
    query = `${columnName} != ${TRANSACTION_RESULT_SUCCESS}`;
  }
  return query;
};

/**
 * Parse the pagination (limit) and order parameters
 * @param {HTTPRequest} req HTTP query request object
 * @param {String} defaultOrder Order of sorting (defaults to descending)
 * @return {Object} {query, params, order} SQL query, values and order
 */
const parseLimitAndOrderParams = (req, defaultOrder = constants.orderFilterValues.DESC) => {
  // Parse the limit parameter
  let limitQuery = '';
  const limitParams = [];
  const lVal = getIntegerParam(req.query[constants.filterKeys.LIMIT], config.maxLimit);
  const limitValue = lVal === '' ? config.maxLimit : lVal;
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
    query,
    params,
    order,
    limit: Number(limit),
  };
};

/**
 * Convert the positional parameters from the MySql style query (?) to Postgres style positional parameters
 * ($1, $2, etc); named parameters of the format \?([a-zA-Z][a-zA-Z0-9]*)? will get the same positional index.
 *
 * @param {String} sqlQuery MySql style query
 * @return {String} SQL query with Postgres style positional parameters
 */
const convertMySqlStyleQueryToPostgres = (sqlQuery) => {
  let paramsCount = 1;
  const namedParamIndex = {};
  return sqlQuery.replace(/\?([a-zA-Z][a-zA-Z0-9]*)?/g, (s) => {
    let index = namedParamIndex[s];
    if (index === undefined) {
      index = paramsCount;
      paramsCount += 1;

      if (s.length > 1) {
        namedParamIndex[s] = index;
      }
    }

    return `$${index}`;
  });
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
const getPaginationLink = (req, isEnd, field, lastValue, order) => {
  let urlPrefix;
  if (config.port !== undefined && config.includeHostInLink === 1) {
    urlPrefix = `${req.protocol}://${req.hostname}:${config.port}`;
  } else {
    urlPrefix = '';
  }

  let next = '';

  if (!isEnd) {
    const pattern = order === constants.orderFilterValues.ASC ? /gt[e]?:/ : /lt[e]?:/;
    const insertedPattern = order === constants.orderFilterValues.ASC ? 'gt' : 'lt';

    // Go through the query parameters, and if there is a 'field=gt:xxxx' (asc order)
    // or 'field=lt:xxxx' (desc order) fields, then remove that, to be replaced by the
    // new continuation value
    for (const [q, v] of Object.entries(req.query)) {
      if (Array.isArray(v)) {
        for (const vv of v) {
          if (q === field && pattern.test(vv)) {
            req.query[q] = req.query[q].filter(function (value, index, arr) {
              return value != vv;
            });
          }
        }
      } else if (q === field && pattern.test(v)) {
        delete req.query[q];
      }
    }

    // And add back the continuation value as 'field=gt:x' (asc order) or
    // 'field=lt:x' (desc order)
    if (field in req.query) {
      req.query[field] = [].concat(req.query[field]).concat(`${insertedPattern}:${lastValue}`);
    } else {
      req.query[field] = `${insertedPattern}:${lastValue}`;
    }

    // Reconstruct the query string
    for (const [q, v] of Object.entries(req.query)) {
      if (Array.isArray(v)) {
        v.forEach((vv) => (next += `${(next === '' ? '?' : '&') + q}=${vv}`));
      } else {
        next += `${(next === '' ? '?' : '&') + q}=${v}`;
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
const nsToSecNs = (ns) => {
  return math.divide(math.bignumber(ns), math.bignumber(1e9)).toFixed(9).toString();
};

/**
 * Converts nanoseconds since epoch to seconds-nnnnnnnnn format
 * @param {String} ns Nanoseconds since epoch
 * @return {String} Seconds since epoch (seconds-nnnnnnnnn format)
 */
const nsToSecNsWithHyphen = (ns) => {
  return nsToSecNs(ns).replace('.', '-');
};

/**
 * Converts seconds since epoch (seconds.nnnnnnnnn format) to  nanoseconds
 * @param {String} Seconds since epoch (seconds.nnnnnnnnn format)
 * @return {String} ns Nanoseconds since epoch
 */
const secNsToNs = (secNs) => {
  return math.multiply(math.bignumber(secNs), math.bignumber(1e9)).toString();
};

const secNsToSeconds = (secNs) => {
  return math.floor(Number(secNs));
};

const randomString = (length) => {
  return crypto.randomBytes(Math.max(2, length) / 2).toString('hex');
};

/**
 * Returns the limit on how many result entries should be in the API
 * @param {String} type of API (e.g. transactions, balances, etc.). Currently unused.
 * @return {Number} limit Max # entries to be returned.
 */
const returnEntriesLimit = (type) => {
  return config.maxLimit;
};

/**
 * Converts the byte array returned by SQL queries into hex string
 * @param {ByteArray} byteArray Array of bytes to be converted to hex string
 * @return {hexString} Converted hex string
 */
const toHexString = (byteArray) => {
  return byteArray === null ? null : byteArray.reduce((output, elem) => output + `0${elem.toString(16)}`.slice(-2), '');
};

/**
 * Converts a key for returning in JSON output
 * @param {Array} key Byte array representing the key
 * @return {Object} Key object - with type decoration for ED25519, if detected
 */
const encodeKey = (key) => {
  let ret;

  if (key === null) {
    return null;
  }

  const hs = toHexString(key);
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
const encodeBase64 = (buffer) => {
  return encodeBinary(buffer, constants.characterEncoding.BASE64);
};

/**
 * Base64 encoding of a byte array for returning in JSON output
 * @param {Array} key Byte array to be encoded
 * @return {String} utf-8 encoded string
 */
const encodeUtf8 = (buffer) => {
  return encodeBinary(buffer, constants.characterEncoding.UTF8);
};

const encodeBinary = (buffer, encoding) => {
  // default to base64 encoding
  let charEncoding = constants.characterEncoding.BASE64;
  if (isValidUtf8Encoding(encoding)) {
    charEncoding = constants.characterEncoding.UTF8;
  }

  return buffer === null ? null : buffer.toString(charEncoding);
};

/**
 *
 * @param {String} num Nullable number
 * @returns {Any} representation of math.bignumber value of parameter or null if null
 */
const getNullableNumber = (num) => {
  return num == null ? null : math.bignumber(num).toString();
};

/**
 * @returns {String} transactionId of format shard.realm.num-sssssssssss-nnnnnnnnn
 */
const createTransactionId = (entityStr, validStartTimestamp) => {
  return `${entityStr}-${nsToSecNsWithHyphen(validStartTimestamp)}`;
};

/**
 * Given the req.query object build the filters object
 * @param filters
 */
const buildFilterObject = (filters) => {
  const filterObject = [];
  if (filters === null) {
    return null;
  }

  for (const key in filters) {
    const values = filters[key];
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
  const splitVal = filter.split(':');
  const opVal = splitVal.length === 1 ? ['eq', filter] : splitVal;

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
const validateAndParseFilters = async (filters) => {
  const badParams = [];

  for (const filter of filters) {
    if (!(await filterValidityChecks(filter.key, filter.operator, filter.value))) {
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
        // Accepted forms: shard.realm.num or encoded ID string
        comparator.value = EntityId.fromString(comparator.value).getEncodedId();
        break;
      case constants.filterKeys.ACCOUNT_PUBLICKEY:
        // Acceptable forms: exactly 64 characters or +12 bytes (DER encoded)
        comparator.value = parsePublicKey(comparator.value);
        break;
      case constants.filterKeys.ENTITY_PUBLICKEY:
        // Acceptable forms: exactly 64 characters or +12 bytes (DER encoded)
        comparator.value = parsePublicKey(comparator.value);
        break;
      case constants.filterKeys.TIMESTAMP:
        comparator.value = parseTimestampParam(comparator.value);
        break;
      case constants.filterKeys.TOKEN_ID:
        // Accepted forms: shard.realm.num or num
        comparator.value = EntityId.fromString(comparator.value).getEncodedId();
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

/**
 * Parses tokenBalances into an array of {token_id: string, balance: Number} objects
 *
 * @param {{token_id: string, balance: Number}[]} tokenBalances array of token balance objects
 * @return {[]|{token_id: string, balance: Number}[]}
 */
const parseTokenBalances = (tokenBalances) => {
  return tokenBalances
    ? tokenBalances.map((tokenBalance) => {
        const {token_id: tokenId, balance} = tokenBalance;
        return {
          token_id: EntityId.fromString(tokenId).toString(),
          balance,
        };
      })
    : [];
};

const parsePublicKey = (publicKey) => {
  const decodedKey = ed25519.derToEd25519(publicKey);
  return decodedKey == null ? publicKey : decodedKey;
};

const getTransactionTypeQuery = async (parsedQueryParams) => {
  if (_.isNil(parsedQueryParams)) {
    return '';
  }

  const transactionType = parsedQueryParams[constants.filterKeys.TRANSACTION_TYPE];
  if (_.isNil(transactionType)) {
    return '';
  }

  const protoId = await transactionTypes.get(transactionType);
  if (protoId !== undefined) {
    return `${constants.transactionColumns.TYPE}${opsMap.eq}${protoId}`;
  }

  // throw error if transactionType filter was provided but invalid
  throw new InvalidArgumentError(`Invalid transactionType value '${transactionType}'`);
};

module.exports = {
  buildFilterObject,
  buildComparatorFilter,
  buildPgSqlObject,
  createTransactionId,
  convertMySqlStyleQueryToPostgres,
  encodeBase64,
  encodeBinary,
  encodeUtf8,
  encodeKey,
  ENTITY_TYPE_ACCOUNT,
  ENTITY_TYPE_FILE,
  filterValidityChecks,
  formatComparator,
  getNullableNumber,
  getPaginationLink,
  getTransactionTypeQuery,
  isValidEntityNum,
  isValidLimitNum,
  isValidNum,
  isValidTimestampParam,
  isValidTransactionType,
  parseCreditDebitParams,
  parseLimitAndOrderParams,
  parseBalanceQueryParam,
  parsePublicKey,
  parsePublicKeyQueryParam,
  parseAccountIdQueryParam,
  parseTimestampQueryParam,
  parseResultParams,
  parseTimestampParam,
  nsToSecNs,
  nsToSecNsWithHyphen,
  randomString,
  returnEntriesLimit,
  secNsToNs,
  secNsToSeconds,
  toHexString,
  TRANSACTION_RESULT_SUCCESS,
  validateAndParseFilters,
  validateReq,
  parseTokenBalances,
  opsMap,
};
