/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import _ from 'lodash';
import anonymize from 'ip-anonymize';
import crypto from 'crypto';
import JSONBigFactory from 'json-bigint';
import long from 'long';
import * as math from 'mathjs';
import pg from 'pg';
import pgRange from 'pg-range';
import util from 'util';

import * as constants from './constants';
import EntityId from './entityId';
import config from './config';
import ed25519 from './ed25519';
import {DbError, InvalidArgumentError, InvalidClauseError} from './errors';
import {FeeSchedule, TransactionResult, TransactionType} from './model';

const JSONBig = JSONBigFactory({useNativeBigInt: true});

const responseLimit = config.response.limit;
const resultSuccess = TransactionResult.getSuccessProtoId();

const opsMap = {
  lt: ' < ',
  lte: ' <= ',
  gt: ' > ',
  gte: ' >= ',
  eq: ' = ',
  ne: ' != ',
};

const gtGte = [opsMap.gt, opsMap.gte];
const ltLte = [opsMap.lt, opsMap.lte];

const gtLtPattern = /[gl]t[e]?:/;

const emptySet = new Set();

/**
 * Returns null if the value is equal to the default, otherwise value.
 *
 * @param value
 * @param defaultValue
 * @return {*|null}
 */
const asNullIfDefault = (value, defaultValue) => {
  return value === defaultValue ? null : value;
};

/**
 * Check if the given number is numeric
 * @param {String} n Number to test
 * @return {Boolean} true if n is numeric, false otherwise
 */
const isNumeric = (n) => {
  return !isNaN(parseFloat(n)) && isFinite(n);
};

// The max signed long has 19 digits
const positiveLongRegex = /^\d{1,19}$/;

/**
 * Validates that num is a positive long.
 * @param {number|string} num
 * @param {boolean} allowZero
 * @return {boolean}
 */
const isPositiveLong = (num, allowZero = false) => {
  const min = allowZero ? 0 : 1;
  return positiveLongRegex.test(num) && long.fromValue(num).greaterThanOrEqual(min);
};

/**
 * Strip the 0x prefix
 * @param val
 * @returns {*}
 */
const stripHexPrefix = (val) => {
  if (typeof val === 'string' && val.startsWith(hexPrefix)) {
    return val.substring(2);
  }

  return val;
};

/**
 * Validates that hex encoded num is a positive int.
 * @param num
 * @param allowZero
 * @returns {boolean}
 */
const isHexPositiveInt = (num, allowZero = false) => {
  if (typeof num === 'string' && num.startsWith(hexPrefix)) {
    num = parseInt(num, 16);
    return isPositiveLong(num, allowZero);
  }

  return false;
};

const nonNegativeInt32Regex = /^\d{1,10}$/;

/**
 * Validates that num is a non-negative int32.
 * @param num
 * @return {boolean}
 */
const isNonNegativeInt32 = (num) => {
  return nonNegativeInt32Regex.test(num) && Number(num) <= constants.MAX_INT32;
};

const isValidBooleanOpAndValue = (op, val) => {
  return op === constants.queryParamOperators.eq && /^(true|false)$/i.test(val);
};

const isValidTimestampParam = (timestamp) => {
  // Accepted forms: seconds or seconds.upto 9 digits
  return /^\d{1,10}$/.test(timestamp) || /^\d{1,10}\.\d{1,9}$/.test(timestamp);
};

const isValidOperatorQuery = (query) => {
  return /^(gte?|lte?|eq|ne)$/.test(query);
};

// Ed25519 has 64, ECDSA(secp256k1) has 66, and ED25519 DER encoded has 88 characters
const publicKeyPattern = /^(0x)?([0-9a-fA-F]{64}|[0-9a-fA-F]{66}|[0-9a-fA-F]{88})$/;
const isValidPublicKeyQuery = (query) => {
  return publicKeyPattern.test(query);
};

const contractTopicPattern = /^(0x)?[0-9A-Fa-f]{1,64}$/; // optional 0x followed by up to 64 hex digits
const isValidOpAndTopic = (op, query) => {
  return typeof query === 'string' && contractTopicPattern.test(query) && op === constants.queryParamOperators.eq;
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

const blockHashPattern = /^(0x)?([0-9A-Fa-f]{64}|[0-9A-Fa-f]{96})$/;
const isValidBlockHash = (query) => {
  if (query === undefined) {
    return false;
  }

  return blockHashPattern.test(query);
};

const ethHashPattern = /^(0x)?([0-9A-Fa-f]{64})$/;
const isValidEthHash = (hash) => {
  if (hash === undefined) {
    return false;
  }

  return ethHashPattern.test(hash);
};

const slotPattern = /^(0x)?[0-9A-Fa-f]{1,64}$/;
const isValidSlot = (slot) => slotPattern.test(slot);

const isValidValueIgnoreCase = (value, validValues) => validValues.includes(value.toLowerCase());

const addressBookFileIdPattern = ['101', '0.101', '0.0.101', '102', '0.102', '0.0.102'];
const isValidAddressBookFileIdPattern = (fileId) => {
  return addressBookFileIdPattern.includes(fileId);
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

const basicOperators = Object.values(constants.queryParamOperators).filter(
  (o) => o !== constants.queryParamOperators.ne
);

const filterValidityChecks = (param, op, val) => {
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
      ret = isPositiveLong(val, true);
      break;
    case constants.filterKeys.ACCOUNT_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case constants.filterKeys.ACCOUNT_PUBLICKEY:
      ret = isValidPublicKeyQuery(val);
      break;
    case constants.filterKeys.BALANCE:
      ret = isValidBooleanOpAndValue(op, val);
      break;
    case constants.filterKeys.BLOCK_HASH:
      ret = isValidBlockHash(val) && op === constants.queryParamOperators.eq;
      break;
    case constants.filterKeys.BLOCK_NUMBER:
      ret = (isPositiveLong(val, true) || isHexPositiveInt(val, true)) && _.includes(basicOperators, op);
      break;
    case constants.filterKeys.CONTRACT_ID:
      ret = isValidContractIdQueryParam(op, val);
      break;
    case constants.filterKeys.CREDIT_TYPE:
      // Acceptable words: credit or debit
      ret = isValidValueIgnoreCase(val, Object.values(constants.cryptoTransferType));
      break;
    case constants.filterKeys.ENCODING:
      // Acceptable words: binary or text
      ret = isValidEncoding(val.toLowerCase());
      break;
    case constants.filterKeys.ENTITY_PUBLICKEY:
      ret = isValidPublicKeyQuery(val);
      break;
    case constants.filterKeys.FILE_ID:
      ret =
        op === constants.queryParamOperators.eq &&
        EntityId.isValidEntityId(val) &&
        isValidAddressBookFileIdPattern(val);
      break;
    case constants.filterKeys.FROM:
      ret = EntityId.isValidEntityId(val, true, constants.EvmAddressType.NO_SHARD_REALM);
      break;
    case constants.filterKeys.INDEX:
      ret = isNumeric(val) && val >= 0;
      break;
    case constants.filterKeys.INTERNAL:
      ret = isValidBooleanOpAndValue(op, val);
      break;
    case constants.filterKeys.LIMIT:
      ret = isPositiveLong(val);
      break;
    case constants.filterKeys.NODE_ID:
      ret = isPositiveLong(val, true);
      break;
    case constants.filterKeys.NONCE:
      ret = op === constants.queryParamOperators.eq && isNonNegativeInt32(val);
      break;
    case constants.filterKeys.ORDER:
      // Acceptable words: asc or desc
      ret = isValidValueIgnoreCase(val, Object.values(constants.orderFilterValues));
      break;
    case constants.filterKeys.Q:
      ret = isValidValueIgnoreCase(val, Object.values(constants.networkSupplyQuery));
      break;
    case constants.filterKeys.RESULT:
      // Acceptable words: success or fail
      ret = isValidValueIgnoreCase(val, Object.values(constants.transactionResultFilter));
      break;
    case constants.filterKeys.SCHEDULED:
      ret = isValidBooleanOpAndValue(op, val);
      break;
    case constants.filterKeys.SCHEDULE_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case constants.filterKeys.SEQUENCE_NUMBER:
      ret = isPositiveLong(val);
      break;
    case constants.filterKeys.SERIAL_NUMBER:
      ret = isPositiveLong(val);
      break;
    case constants.filterKeys.SLOT:
      ret = isValidSlot(val) && _.includes(basicOperators, op);
      break;
    case constants.filterKeys.SPENDER_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case constants.filterKeys.TIMESTAMP:
      ret = isValidTimestampParam(val);
      break;
    case constants.filterKeys.TOKEN_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case constants.filterKeys.TOKEN_TYPE:
      ret = isValidValueIgnoreCase(val, Object.values(constants.tokenTypeFilter));
      break;
    case constants.filterKeys.TOPIC0:
    case constants.filterKeys.TOPIC1:
    case constants.filterKeys.TOPIC2:
    case constants.filterKeys.TOPIC3:
      ret = isValidOpAndTopic(op, val);
      break;
    case constants.filterKeys.TRANSACTION_INDEX:
      ret = isPositiveLong(val, true) && op === constants.queryParamOperators.eq;
      break;
    case constants.filterKeys.TRANSACTION_TYPE:
      // Accepted forms: valid transaction type string
      ret = TransactionType.isValid(val);
      break;
    default:
      // Every parameter should be included here. Otherwise, it will not be accepted.
      ret = false;
  }

  return ret;
};

/**
 * Validates the parameter dependencies
 * @param query
 */
const filterDependencyCheck = (query) => {
  const badParams = [];
  let containsBlockNumber = false;
  let containsBlockHash = false;
  let containsTransactionIndex = false;
  for (const key of Object.keys(query)) {
    if (key === constants.filterKeys.TRANSACTION_INDEX) {
      containsTransactionIndex = true;
    } else if (key === constants.filterKeys.BLOCK_NUMBER) {
      containsBlockNumber = true;
    } else if (key === constants.filterKeys.BLOCK_HASH) {
      containsBlockHash = true;
    }
  }

  if (containsTransactionIndex && !(containsBlockNumber || containsBlockHash)) {
    badParams.push({
      key: constants.filterKeys.TRANSACTION_INDEX,
      error: 'transaction.index requires block.number or block.hash filter to be specified',
      code: 'invalidParamUsage',
    });
  }

  if (containsBlockHash && containsBlockNumber) {
    badParams.push({
      key: constants.filterKeys.BLOCK_HASH,
      error: 'cannot combine block.number and block.hash',
      code: 'invalidParamUsage',
    });
  }

  if (badParams.length) {
    throw InvalidArgumentError.forRequestValidation(badParams);
  }
};

const isValidContractIdQueryParam = (op, val) => {
  if (EntityId.isValidEvmAddress(val, constants.EvmAddressType.OPTIONAL_SHARD_REALM)) {
    return op === constants.queryParamOperators.eq;
  }
  return EntityId.isValidEntityId(val, false);
};

/**
 * Validate input http request object
 * @param {Request} req HTTP request object
 * @param {Set} acceptedParameters List of valid parameters
 * @return {Object} result of validity check, and return http code/contents
 */
const validateReq = (req, acceptedParameters = emptySet) => {
  const badParams = [];
  // Check the validity of every query parameter
  for (const key in req.query) {
    if (!acceptedParameters.has(key)) {
      badParams.push({code: InvalidArgumentError.UNKNOWN_PARAM_USAGE, key});
      continue;
    }
    if (Array.isArray(req.query[key])) {
      if (!isRepeatedQueryParameterValidLength(req.query[key])) {
        badParams.push({
          code: InvalidArgumentError.PARAM_COUNT_EXCEEDS_MAX_CODE,
          key,
          count: req.query[key].length,
          max: config.query.maxRepeatedQueryParameters,
        });
        continue;
      }
      for (const val of req.query[key]) {
        if (!paramValidityChecks(key, val)) {
          badParams.push({code: InvalidArgumentError.INVALID_ERROR_CODE, key});
        }
      }
    } else if (!paramValidityChecks(key, req.query[key])) {
      badParams.push({code: InvalidArgumentError.INVALID_ERROR_CODE, key});
    }
  }

  if (badParams.length > 0) {
    throw InvalidArgumentError.forRequestValidation(badParams);
  }
};

const isRepeatedQueryParameterValidLength = (values) => values.length <= config.query.maxRepeatedQueryParameters;

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
 * Gets the limit param value, if not exists, return the default; otherwise cap it at max. Note if values is an array,
 * the last one is honored.
 * @param {string[]|string} values Values of the limit param
 * @return {number}
 */
const getLimitParamValue = (values) => {
  let ret = responseLimit.default;
  if (values !== undefined) {
    const value = Array.isArray(values) ? values[values.length - 1] : values;
    const parsed = long.fromValue(value);
    ret = parsed.greaterThan(responseLimit.max) ? responseLimit.max : parsed.toNumber();
  }
  return ret;
};

/**
 * Parse the query filter parameter
 * @param paramValues Value of the query param after parsing by ExpressJS
 * @param {Function} processValue function to extract sql params using comparator and value
 *          in the query param.
 * @param {Function} processOpAndValue function to compute partial sql clause using comparator and value
 *          in the query param.
 * @param {Boolean} allowMultiple whether the sql clause should build multiple = ops as an IN() clause
 * @return {Array} [query, params] Constructed SQL query fragment and corresponding values
 */
const parseParams = (paramValues, processValue, processQuery, allowMultiple) => {
  if (paramValues === undefined) {
    return ['', []];
  }
  // Convert paramValues to a set to remove duplicates
  if (!Array.isArray(paramValues)) {
    paramValues = new Set([paramValues]);
  } else {
    paramValues = new Set(paramValues);
  }
  const partialQueries = [];
  const values = [];
  // Iterate for each value of param. For a url '..?q=val1&q=val2', paramValues for 'q' are [val1, val2].
  const equalValues = new Set();
  for (const paramValue of paramValues) {
    const opAndValue = parseOperatorAndValueFromQueryParam(paramValue);
    if (_.isNil(opAndValue)) {
      continue;
    }
    const processedValue = processValue(opAndValue.value);
    // Equal ops have to be processed in bulk at the end to format the IN() correctly.
    if (opAndValue.op === opsMap.eq && allowMultiple) {
      equalValues.add(processedValue);
    } else {
      const queryAndValues = processQuery(opAndValue.op, processedValue);
      if (!_.isNil(queryAndValues)) {
        partialQueries.push(queryAndValues[0]);
        if (queryAndValues[1]) {
          values.push(...queryAndValues[1]);
        }
      }
    }
  }
  if (equalValues.size !== 0) {
    const queryAndValues = processQuery(opsMap.eq, Array.from(equalValues));
    partialQueries.push(queryAndValues[0]);
    values.push(...queryAndValues[1]);
  }
  const fullClause = partialQueries.join(' and ');
  validateClauseAndValues(fullClause, values);
  return [partialQueries.join(' and '), values];
};

const validateClauseAndValues = (clause, values) => {
  if ((clause.match(/\?/g) || []).length !== values.length) {
    throw new InvalidClauseError(`Invalid clause produced after parsing query parameters: number of replacement
    parameters does not equal number of values: clause: \"${clause}\", values: ${values}`);
  }
};

const parseAccountIdQueryParam = (parsedQueryParams, columnName) => {
  return parseParams(
    parsedQueryParams[constants.filterKeys.ACCOUNT_ID],
    (value) => EntityId.parse(value).getEncodedId(),
    (op, value) => {
      return Array.isArray(value)
        ? [`${columnName} IN (?`.concat(', ?'.repeat(value.length - 1)).concat(')'), value]
        : [`${columnName}${op}?`, [value]];
    },
    true
  );
};

const parseBalanceQueryParam = (parsedQueryParams, columnName) => {
  return parseParams(
    parsedQueryParams[constants.filterKeys.ACCOUNT_BALANCE],
    (value) => value,
    (op, value) => (isNumeric(value) ? [`${columnName}${op}?`, [value]] : null),
    false
  );
};

/**
 * Parse the type=[credit | debit] parameter
 */
const parseCreditDebitParams = (parsedQueryParams, columnName) => {
  return parseParams(
    parsedQueryParams[constants.filterKeys.CREDIT_TYPE],
    (value) => value,
    (op, value) => {
      if (value === 'credit') {
        return [`${columnName} > ?`, [0]];
      }
      if (value === 'debit') {
        return [`${columnName} < ?`, [0]];
      }
      return null;
    },
    false
  );
};

/**
 * Parses the integer string into a Number if it's safe or otherwise a BigInt
 *
 * @param {string} str
 * @returns {Number|BigInt}
 */
const parseInteger = (str) => {
  const num = Number(str);
  return Number.isSafeInteger(num) ? num : BigInt(str);
};

/**
 * Parse the pagination (limit) and order parameters
 * @param {HTTPRequest} req HTTP query request object
 * @param {String} defaultOrder Order of sorting (defaults to descending)
 * @return {Object} {query, params, order} SQL query, values and order
 */
const parseLimitAndOrderParams = (req, defaultOrder = constants.orderFilterValues.DESC) => {
  // Parse the limit parameter
  const limitQuery = `${constants.filterKeys.LIMIT} ? `;
  const limitValue = getLimitParamValue(req.query[constants.filterKeys.LIMIT]);

  // Parse the order parameters (default: descending)
  let order = defaultOrder;
  const value = req.query[constants.filterKeys.ORDER];
  if (value === constants.orderFilterValues.ASC || value === constants.orderFilterValues.DESC) {
    order = value;
  }

  return buildPgSqlObject(limitQuery, [limitValue], order, limitValue);
};

const parsePublicKey = (publicKey) => {
  const publicKeyNoPrefix = publicKey ? publicKey.replace('0x', '').toLowerCase() : publicKey;
  const decodedKey = ed25519.derToEd25519(publicKeyNoPrefix);
  return decodedKey == null ? publicKeyNoPrefix : decodedKey;
};

const parsePublicKeyQueryParam = (parsedQueryParams, columnName) => {
  return parseParams(
    parsedQueryParams[constants.filterKeys.ACCOUNT_PUBLICKEY],
    (value) => {
      return parsePublicKey(value);
    },
    (op, value) => [`${columnName}${op}?`, [value]],
    false
  );
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
    query = `${columnName} = ${resultSuccess}`;
  } else if (resultType === constants.transactionResultFilter.FAIL) {
    query = `${columnName} != ${resultSuccess}`;
  }
  return query;
};

const parseTimestampQueryParam = (parsedQueryParams, columnName, opOverride = {}) => {
  return parseParams(
    parsedQueryParams[constants.filterKeys.TIMESTAMP],
    (value) => parseTimestampParam(value),
    (op, value) => [`${columnName}${op in opOverride ? opOverride[op] : op}?`, [value]],
    false
  );
};

const buildPgSqlObject = (query, params, order, limit) => {
  return {
    query,
    params,
    order,
    limit: Number(limit),
  };
};

const parseBooleanValue = (value) => {
  return value.toLowerCase() === 'true';
};

/**
 * Convert the positional parameters from the MySql style query (?) to Postgres style positional parameters
 * ($1, $2, etc); named parameters of the format \?([a-zA-Z][a-zA-Z0-9]*)? will get the same positional index.
 *
 * @param {String} sqlQuery MySql style query
 * @return {String} SQL query with Postgres style positional parameters
 */
const convertMySqlStyleQueryToPostgres = (sqlQuery, startIndex = 1) => {
  let paramsCount = startIndex;
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
 * @param {{string: {value: string, inclusive: boolean}}} lastValueMap Map of key value pairs representing last values
 *   of columns that may be filtered on
 * @param {String} order Order of sorting the results
 * @return {String} next Fully formed link to the next page
 */
const getPaginationLink = (req, isEnd, lastValueMap, order) => {
  let urlPrefix;
  if (config.port !== undefined && config.response.includeHostInLink) {
    urlPrefix = `${req.protocol}://${req.hostname}:${config.port}`;
  } else {
    urlPrefix = '';
  }

  let next = '';

  if (!isEnd) {
    next = getNextParamQueries(order, req.query, lastValueMap);

    // remove the '/' at the end of req.path
    const path = req.path.endsWith('/') ? req.path.slice(0, -1) : req.path;
    next = urlPrefix + req.baseUrl + path + next;
  }
  return next === '' ? null : next;
};

/**
 * Construct the query string from the query object
 * @param {Object} reqQuery request query object
 * @returns url string
 */
const constructStringFromUrlQuery = (reqQuery) => {
  let next = '';
  for (const [q, v] of Object.entries(reqQuery)) {
    if (Array.isArray(v)) {
      v.forEach((vv) => (next += `${(next === '' ? '?' : '&') + q}=${vv}`));
    } else {
      next += `${(next === '' ? '?' : '&') + q}=${v}`;
    }
  }

  return next;
};

/**
 * Go through the query parameters, and if there is a 'field=gt:xxxx' (asc order)
 * or 'field=lt:xxxx' (desc order) fields, then remove that, to be replaced by the new continuation value
 */
const updateReqQuery = (reqQuery, field, pattern, insertValue) => {
  const fieldValues = reqQuery[field];
  const patternMatch = pattern.test(fieldValues);
  if (Array.isArray(fieldValues)) {
    reqQuery[field] = fieldValues.filter((value) => !pattern.test(value));
  } else if (patternMatch) {
    delete reqQuery[field];
  }

  if (field in reqQuery) {
    if (gtLtPattern.test(fieldValues)) {
      reqQuery[field] = [].concat(reqQuery[field]).concat(insertValue);
    }
  } else {
    reqQuery[field] = insertValue;
  }
};

const operatorPatterns = {
  [constants.orderFilterValues.ASC]: /gt[e]?:/,
  [constants.orderFilterValues.DESC]: /lt[e]?:/,
};

const getNextParamQueries = (order, reqQuery, lastValueMap) => {
  const pattern = operatorPatterns[order];
  const newPattern = order === constants.orderFilterValues.ASC ? 'gt' : 'lt';

  for (const [field, lastValue] of Object.entries(lastValueMap)) {
    let value = lastValue;
    let inclusive = false;
    if (typeof value === 'object' && 'value' in lastValue) {
      value = lastValue.value;
      inclusive = lastValue.inclusive;
    }
    const insertValue = inclusive ? `${newPattern}e:${value}` : `${newPattern}:${value}`;
    updateReqQuery(reqQuery, field, pattern, insertValue);
  }

  return constructStringFromUrlQuery(reqQuery);
};

/**
 * Merges params arrays. Pass [] as initial if the params arrays should stay unmodified. Note every params should be
 * an array.
 * @param {any[]} initial
 * @param {any[]} params
 */
const mergeParams = (initial, ...params) => {
  return params.reduce((previous, current) => {
    previous.push(...current);
    return previous;
  }, initial);
};

/**
 * Converts nanoseconds since epoch to seconds.nnnnnnnnn format
 *
 * @param {BigInt|Number|String} ns Nanoseconds since epoch
 * @param {String} sep separator between seconds and nanos, default is '.'
 * @return {String} Seconds since epoch (seconds.nnnnnnnnn format)
 */
const nsToSecNs = (ns, sep = '.') => {
  if (_.isNil(ns)) {
    return null;
  }

  ns = `${ns}`;
  const secs = ns.substring(0, ns.length - 9).padStart(1, '0');
  const nanos = ns.slice(-9).padStart(9, '0');
  return `${secs}${sep}${nanos}`;
};

/**
 * Converts nanoseconds since epoch to seconds-nnnnnnnnn format
 * @param {String} ns Nanoseconds since epoch
 * @return {String} Seconds since epoch (seconds-nnnnnnnnn format)
 */
const nsToSecNsWithHyphen = (ns) => {
  return nsToSecNs(ns, '-');
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

/**
 * Increment timestamp (nnnnnnnnnnnnnnnnnnn format) by 1 day
 * @return {String} (seconds.nnnnnnnnn format)
 */
const incrementTimestampByOneDay = (ns) => {
  if (_.isNil(ns)) {
    return null;
  }

  const result = BigInt(ns) + constants.ONE_DAY_IN_NS;
  return nsToSecNs(result);
};

const randomBytesAsync = util.promisify(crypto.randomBytes);

const randomString = async (length) => {
  const bytes = await randomBytesAsync(Math.max(2, length) / 2);
  return bytes.toString('hex');
};

const hexPrefix = '0x';
const addHexPrefix = (hexData) => {
  if (_.isEmpty(hexData)) {
    return hexPrefix;
  }

  const hexString = typeof hexData === 'string' ? hexData : Buffer.from(hexData).toString();
  return hexString.substring(0, 2) === hexPrefix ? hexString : `${hexPrefix}${hexString}`;
};

/**
 * Pads all non-null arrays to 0x-prefixed 64 characters hex string and pass the null values as null
 * @param val
 * @returns {String|null}
 */
const toUint256 = (val) => {
  if (_.isNil(val)) {
    return null;
  }

  if (!val.length) {
    return constants.ZERO_UINT256;
  }

  return toHexString(val, true, 64);
};

/**
 * Converts the byte array returned by SQL queries into hex string
 * Logic conforms with ETH hex value encoding, therefore nill and empty return '0x'
 * @param {Array} byteArray Array of bytes to be converted to hex string
 * @param {boolean} addPrefix Whether to add the '0x' prefix to the hex string
 * @param {Number} padLength The length to left pad the result hex string
 * @return {String} Converted hex string
 */
const toHexString = (byteArray, addPrefix = false, padLength = undefined) => {
  if (_.isEmpty(byteArray)) {
    return hexPrefix;
  }

  const modifiers = [];
  if (padLength !== undefined) {
    modifiers.push((s) => s.padStart(padLength, '0'));
  }

  if (addPrefix) {
    modifiers.push(addHexPrefix);
  }

  const encoded = Buffer.from(byteArray, 'utf8').toString('hex');
  return modifiers.reduce((v, f) => f(v), encoded);
};

const toHexStringQuantity = (byteArray) => {
  let hex = toHexString(byteArray, true);
  if (hex.length > 3) {
    hex = hex.replace('0x0', '0x');
  }

  return hex;
};

const toHexStringNonQuantity = (byteArray) => {
  return toHexString(byteArray, true, 2);
};

// These match protobuf encoded hex strings. The prefixes listed check if it's a primitive key, a key list with one
// primitive key, or a 1/1 threshold key, respectively.
const PATTERN_ECDSA = /^(3a21|32250a233a21|2a29080112250a233a21)([A-Fa-f0-9]{66})$/;
const PATTERN_ED25519 = /^(1220|32240a221220|2a28080112240a221220)([A-Fa-f0-9]{64})$/;

/**
 * Converts a key for returning in JSON output
 * @param {Array} key Byte array representing the key
 * @return {Object} Key object - with type decoration for primitive keys, if detected
 */
const encodeKey = (key) => {
  if (_.isNil(key)) {
    return null;
  }

  // check for empty case to support differentiation between empty and null keys
  const keyHex = _.isEmpty(key) ? '' : toHexString(key);
  const ed25519Key = keyHex.match(PATTERN_ED25519);
  if (ed25519Key) {
    return {
      _type: constants.keyTypes.ED25519,
      key: ed25519Key[2],
    };
  }

  const ecdsa = keyHex.match(PATTERN_ECDSA);
  if (ecdsa) {
    return {
      _type: constants.keyTypes.ECDSA_SECP256K1,
      key: ecdsa[2],
    };
  }

  return {
    _type: constants.keyTypes.PROTOBUF,
    key: keyHex,
  };
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

  return _.isNil(buffer) ? null : buffer.toString(charEncoding);
};

/**
 *
 * @param {String} num Nullable number
 * @returns {Any} representation of math.bignumber value of parameter or null if null
 */
const getNullableNumber = (num) => {
  return _.isNil(num) ? null : `${num}`;
};

/**
 * @returns {String} transactionId of format shard.realm.num-sssssssssss-nnnnnnnnn
 */
const createTransactionId = (entityStr, validStartTimestamp) => {
  return `${entityStr}-${nsToSecNsWithHyphen(validStartTimestamp)}`;
};

/**
 * Builds the filters from HTTP request query, validates and parses the filters.
 *
 * @param query
 * @param {Set} acceptedParameters
 * @param {function(string, string, string)} filterValidator
 * @param {function(array)} filterDependencyChecker
 * @return {[]}
 */
const buildAndValidateFilters = (
  query,
  acceptedParameters,
  filterValidator = filterValidityChecks,
  filterDependencyChecker = filterDependencyCheck
) => {
  const {badParams, filters} = buildFilters(query);
  const {invalidParams, unknownParams} = validateAndParseFilters(filters, filterValidator, acceptedParameters);
  badParams.push(...invalidParams);
  badParams.push(...unknownParams);
  if (badParams.length > 0) {
    throw InvalidArgumentError.forRequestValidation(badParams);
  }

  if (filterDependencyChecker) {
    filterDependencyChecker(query);
  }

  return filters;
};

/**
 * Build the filters from the HTTP request query
 *
 * @param query
 */
const buildFilters = (query) => {
  const badParams = [];
  const filters = [];

  for (const [key, values] of Object.entries(query)) {
    // for repeated params val will be an array
    if (Array.isArray(values)) {
      if (!isRepeatedQueryParameterValidLength(values)) {
        badParams.push({
          code: InvalidArgumentError.PARAM_COUNT_EXCEEDS_MAX_CODE,
          key,
          count: values.length,
          max: config.query.maxRepeatedQueryParameters,
        });
        continue;
      }

      for (const val of values) {
        filters.push(buildComparatorFilter(key, val));
      }
    } else {
      filters.push(buildComparatorFilter(key, values));
    }
  }

  return {badParams, filters};
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
 * Calculates the expiryTimestamp.
 * @param {int} autoRenewPeriod: seconds format
 * @param {BigInt} createdTimestamp: nnnnnnnnnnnnnnnnnnn format
 * @param {BigInt} expirationTimestamp: nnnnnnnnnnnnnnnnnnn format
 * @returns {BigInt} nnnnnnnnnnnnnnnnnnn format
 */
const calculateExpiryTimestamp = (autoRenewPeriod, createdTimestamp, expirationTimestamp) => {
  return _.isNil(expirationTimestamp) && !_.isNil(createdTimestamp) && !_.isNil(autoRenewPeriod)
    ? BigInt(createdTimestamp) + BigInt(autoRenewPeriod) * constants.AUTO_RENEW_PERIOD_MULTIPLE
    : expirationTimestamp;
};

/**
 * Verify param and filters meet expected format
 *
 * @param filters
 * @param filterValidator
 * @param {Set} acceptedParameters
 * @returns {string[], []{}} bad parameters, unknown parameters
 */
const validateFilters = (filters, filterValidator, acceptedParameters) => {
  const invalidParams = [];
  const unknownParams = [];
  for (const filter of filters) {
    if (!acceptedParameters.has(filter.key)) {
      unknownParams.push({key: filter.key, code: InvalidArgumentError.UNKNOWN_PARAM_USAGE});
      continue;
    }
    if (!filterValidator(filter.key, filter.operator, filter.value)) {
      invalidParams.push(filter.key);
    }
  }

  return {invalidParams, unknownParams};
};

/**
 * Update format to be persistence query compatible
 * @param filters
 */
const formatFilters = (filters) => {
  for (const filter of filters) {
    formatComparator(filter);
  }
};

const zeroPaddingRegex = /^0+(?=\d)0/;

/**
 * Update slot format to be persistence query compatible
 * @param slot
 */
const formatSlot = (slot, leftPad = false) => {
  if (leftPad) {
    const formatedSlot = stripHexPrefix(slot).replace(zeroPaddingRegex, '0');
    return Buffer.from(formatedSlot === '0' ? '' : formatedSlot, 'hex');
  }
  return Buffer.from(stripHexPrefix(slot).padStart(64, 0), 'hex');
};

/**
 * Verify param and filters meet expected format
 * Additionally update format to be persistence query compatible
 *
 * @param filters
 * @param filterValidator
 * @param {Set} acceptedParameters
 * @returns {string[], []{}} bad parameters, unknown parameters
 */
const validateAndParseFilters = (filters, filterValidator, acceptedParameters) => {
  const {invalidParams, unknownParams} = validateFilters(filters, filterValidator, acceptedParameters);
  if (invalidParams.length === 0 && unknownParams.length === 0) {
    formatFilters(filters);
  }
  return {invalidParams, unknownParams};
};

const formatComparator = (comparator) => {
  if (comparator.operator in opsMap) {
    // update operator
    comparator.operator = opsMap[comparator.operator];

    // format value
    switch (comparator.key) {
      case constants.filterKeys.ACCOUNT_ID:
        // Accepted forms: shard.realm.num or encoded ID string
        comparator.value = EntityId.parse(comparator.value).getEncodedId();
        break;
      case constants.filterKeys.ACCOUNT_PUBLICKEY:
        comparator.value = parsePublicKey(comparator.value);
        break;
      case constants.filterKeys.BLOCK_HASH:
        if (comparator.value.startsWith(hexPrefix)) {
          comparator.value = comparator.value.slice(hexPrefix.length);
        }
        break;
      case constants.filterKeys.BLOCK_NUMBER:
        if (comparator.value.startsWith(hexPrefix)) {
          comparator.value = parseInt(comparator.value, 16);
        }
        break;
      case constants.filterKeys.FILE_ID:
        // Accepted forms: shard.realm.num or encoded ID string
        comparator.value = EntityId.parse(comparator.value).getEncodedId();
        break;
      case constants.filterKeys.ENTITY_PUBLICKEY:
        comparator.value = parsePublicKey(comparator.value);
        break;
      case constants.filterKeys.FROM:
        comparator.value = EntityId.parse(comparator.value, {
          evmAddressType: constants.EvmAddressType.NO_SHARD_REALM,
          paramName: comparator.key,
        }).getEncodedId();
        break;
      case constants.filterKeys.INTERNAL:
        comparator.value = parseBooleanValue(comparator.value);
        break;
      case constants.filterKeys.LIMIT:
        comparator.value = math.min(Number(comparator.value), responseLimit.max);
        break;
      case constants.filterKeys.NODE_ID:
      case constants.filterKeys.NONCE:
        comparator.value = Number(comparator.value);
        break;
      case constants.filterKeys.SCHEDULED:
        comparator.value = parseBooleanValue(comparator.value);
        break;
      case constants.filterKeys.SCHEDULE_ID:
        // Accepted forms: shard.realm.num or num
        comparator.value = EntityId.parse(comparator.value).getEncodedId();
        break;
      case constants.filterKeys.SPENDER_ID:
        // Accepted forms: shard.realm.num or num
        comparator.value = EntityId.parse(comparator.value).getEncodedId();
        break;
      case constants.filterKeys.TIMESTAMP:
        comparator.value = parseTimestampParam(comparator.value);
        break;
      case constants.filterKeys.TOKEN_ID:
        // Accepted forms: shard.realm.num or num
        comparator.value = EntityId.parse(comparator.value).getEncodedId();
        break;
      case constants.filterKeys.TOKEN_TYPE:
        // db requires upper case matching for enum
        comparator.value = comparator.value.toUpperCase();
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
  if (_.isNil(tokenBalances)) {
    return [];
  }

  return tokenBalances
    .filter((x) => !_.isNil(x.token_id))
    .map((tokenBalance) => {
      const {token_id: tokenId, balance} = tokenBalance;
      return {
        token_id: EntityId.parse(tokenId).toString(),
        balance,
      };
    });
};

const parseTransactionTypeParam = (parsedQueryParams) => {
  if (_.isNil(parsedQueryParams)) {
    return '';
  }

  const transactionType = parsedQueryParams[constants.filterKeys.TRANSACTION_TYPE];
  if (_.isNil(transactionType)) {
    return '';
  }

  const transactionTypes = !_.isArray(transactionType) ? [transactionType] : transactionType;
  const protoIds = transactionTypes
    .map((t) => TransactionType.getProtoId(t))
    .reduce((result, protoId) => {
      result.add(protoId);
      return result;
    }, new Set());

  return `${constants.transactionColumns.TYPE} in (${Array.from(protoIds)})`;
};

const isTestEnv = () => process.env.NODE_ENV === 'test';

/**
 * Masks the given IP based on Google Analytics standards
 * @param {String} ip the IP address from the req object.
 * @returns {String} The masked IP address
 */
const ipMask = (ip) => {
  return anonymize(ip, 24, 48);
};

/**
 * Gets the pool class with queryQuietly
 *
 * @param {boolean} mock
 */
const getPoolClass = async (mock = false) => {
  const Pool = mock ? (await import('./__tests__/mockPool')).default : pg.Pool;
  Pool.prototype.queryQuietly = async function (query, params = [], preQueryHint = undefined) {
    let client;
    let result;
    params = Array.isArray(params) ? params : [params];
    const clientErrorCallback = (error) => {
      logger.error(`error event emitted on pg pool. ${error.stack}`);
    };
    try {
      if (!preQueryHint) {
        result = await this.query(query, params);
      } else {
        client = await this.connect();
        client.on('error', clientErrorCallback);
        await client.query(`begin; ${preQueryHint}`);
        result = await client.query(query, params);
        await client.query('commit');
      }

      return result;
    } catch (err) {
      if (client !== undefined) {
        await client.query('rollback');
      }
      throw new DbError(err.message);
    } finally {
      if (client !== undefined) {
        client.off('error', clientErrorCallback);
        client.release();
      }
    }
  };

  return Pool;
};

/**
 * Gets the staking period view model object for the given staking period timestamp.
 *
 * @param {number} stakingPeriod
 * @returns {Object|null}
 */
const getStakingPeriod = (stakingPeriod) => {
  if (_.isNil(stakingPeriod)) {
    return null;
  } else {
    const stakingPeriodStart = BigInt(stakingPeriod) + 1n;
    return {
      from: nsToSecNs(stakingPeriodStart),
      to: incrementTimestampByOneDay(stakingPeriodStart),
    };
  }
};

/**
 * Checks that the timestamp filters contains either a valid range (greater than and less than filters that do not span
 * beyond a configured limit), or a set of equals operators within the same limit.
 *
 * @param {[]}timestampFilters an array of timestamp filters
 */
const checkTimestampRange = (timestampFilters) => {
  //No timestamp params provided
  if (timestampFilters.length === 0) {
    throw new InvalidArgumentError('No timestamp range or eq operator provided');
  }

  const valuesByOp = {};
  Object.values(opsMap).forEach((k) => (valuesByOp[k] = []));
  timestampFilters.forEach((filter) => valuesByOp[filter.operator].push(filter.value));

  const gtGteLength = valuesByOp[opsMap.gt].length + valuesByOp[opsMap.gte].length;
  const ltLteLength = valuesByOp[opsMap.lt].length + valuesByOp[opsMap.lte].length;

  if (valuesByOp[opsMap.ne].length > 0) {
    // Don't allow ne
    throw new InvalidArgumentError('Not equals operator not supported for timestamp param');
  }

  if (gtGteLength > 1) {
    //Don't allow multiple gt/gte
    throw new InvalidArgumentError('Multiple gt or gte operators not permitted for timestamp param');
  }

  if (ltLteLength > 1) {
    //Don't allow multiple lt/lte
    throw new InvalidArgumentError('Multiple lt or lte operators not permitted for timestamp param');
  }

  if (valuesByOp[opsMap.eq].length > 0 && (gtGteLength > 0 || ltLteLength > 0)) {
    //Combined eq with other operator
    throw new InvalidArgumentError('Cannot combine eq with gt, gte, lt, or lte for timestamp param');
  }

  if (valuesByOp[opsMap.eq].length > 0) {
    //Only eq provided, no range needed
    return;
  }

  if (gtGteLength === 0 || ltLteLength === 0) {
    //Missing range
    throw new InvalidArgumentError('Timestamp range must have gt (or gte) and lt (or lte)');
  }

  // there should be exactly one gt/gte and one lt/lte at this point
  const earliest =
    valuesByOp[opsMap.gt].length > 0 ? BigInt(valuesByOp[opsMap.gt][0]) + 1n : BigInt(valuesByOp[opsMap.gte][0]);
  const latest =
    valuesByOp[opsMap.lt].length > 0 ? BigInt(valuesByOp[opsMap.lt][0]) - 1n : BigInt(valuesByOp[opsMap.lte][0]);
  const difference = latest - earliest + 1n;

  const {maxTimestampRange, maxTimestampRangeNs} = config.query;
  if (difference > maxTimestampRangeNs || difference <= 0n) {
    throw new InvalidArgumentError(`Timestamp lower and upper bounds must be positive and within ${maxTimestampRange}`);
  }
};

/**
 * Intended to be used when it is possible for different API routes to have conflicting paths
 * and only one of them needs to be executed. E.g:
 * /contracts/results
 * /contracts/:contractId
 *
 * @param req
 * @param paramName
 * @param possibleConflicts
 * @returns {boolean}
 */
const conflictingPathParam = (req, paramName, possibleConflicts = []) => {
  if (!Array.isArray(possibleConflicts)) {
    possibleConflicts = [possibleConflicts];
  }
  return req.params[paramName] && possibleConflicts.indexOf(req.params[paramName]) !== -1;
};

(function () {
  // config pg bigint parsing
  const pgTypes = pg.types;
  pgTypes.setTypeParser(20, parseInteger); // int8
  const parseBigIntArray = pgTypes.getTypeParser(1016); // int8[]
  pgTypes.setTypeParser(1016, (a) => parseBigIntArray(a).map(parseInteger));

  pgTypes.setTypeParser(114, JSONBig.parse); // json
  pgTypes.setTypeParser(3802, JSONBig.parse); // jsonb

  //  install pg-range
  pgRange.install(pg);
})();

/**
 * Converts gas price into tiny bars
 *
 * @param {number} gasPrice
 * @param {number} hbarPerTinyCent
 * @param {number} centsPerHbar
 * @returns {number|null} `null` if the gasPrice cannot be converted. The minimum `number` returned is 1
 */
const convertGasPriceToTinyBars = (gasPrice, hbarsPerTinyCent, centsPerHbar) => {
  if ([gasPrice, hbarsPerTinyCent, centsPerHbar].some((n) => !_.isNumber(n))) {
    return null;
  }
  const tinyCentsBN = math.bignumber(gasPrice / FeeSchedule.FEE_DIVISOR_FACTOR);
  const hbarMultiplierBN = math.bignumber(hbarsPerTinyCent);
  const centsDivisorBN = math.bignumber(centsPerHbar);
  const hbarCentsBN = math.multiply(tinyCentsBN, hbarMultiplierBN);

  const tinyBars = math.divide(hbarCentsBN, centsDivisorBN).toNumber();
  return Math.round(Math.max(tinyBars, 1));
};

const JSONParse = JSONBig.parse;
const JSONStringify = JSONBig.stringify;

export {
  JSONParse,
  JSONStringify,
  addHexPrefix,
  asNullIfDefault,
  buildAndValidateFilters,
  buildComparatorFilter,
  buildFilters,
  buildPgSqlObject,
  calculateExpiryTimestamp,
  checkTimestampRange,
  conflictingPathParam,
  convertGasPriceToTinyBars,
  convertMySqlStyleQueryToPostgres,
  createTransactionId,
  encodeBase64,
  encodeBinary,
  encodeKey,
  encodeUtf8,
  filterDependencyCheck,
  filterValidityChecks,
  formatComparator,
  formatFilters,
  formatSlot,
  getLimitParamValue,
  getNextParamQueries,
  getNullableNumber,
  getPaginationLink,
  getPoolClass,
  getStakingPeriod,
  gtGte,
  incrementTimestampByOneDay,
  ipMask,
  isNonNegativeInt32,
  isPositiveLong,
  isRepeatedQueryParameterValidLength,
  isTestEnv,
  isValidBlockHash,
  isValidEthHash,
  isValidOperatorQuery,
  isValidPublicKeyQuery,
  isValidSlot,
  isValidTimestampParam,
  isValidValueIgnoreCase,
  ltLte,
  mergeParams,
  nsToSecNs,
  nsToSecNsWithHyphen,
  opsMap,
  parseAccountIdQueryParam,
  parseBalanceQueryParam,
  parseBooleanValue,
  parseCreditDebitParams,
  parseInteger,
  parseLimitAndOrderParams,
  parseParams,
  parsePublicKey,
  parsePublicKeyQueryParam,
  parseResultParams,
  parseTimestampParam,
  parseTimestampQueryParam,
  parseTokenBalances,
  parseTransactionTypeParam,
  randomString,
  resultSuccess,
  secNsToNs,
  secNsToSeconds,
  toHexString,
  toHexStringNonQuantity,
  toHexStringQuantity,
  validateAndParseFilters,
  validateFilters,
  validateReq,
  stripHexPrefix,
  toUint256,
};
