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

const request = require('supertest');
const utils = require('../utils.js');
const constants = require('../constants.js');

describe('Utils getNullableNumber tests', () => {
  test('Verify getNullableNumber returns correct result for 0', () => {
    var val = utils.getNullableNumber(0);
    expect(val).toBe('0');
  });

  test('Verify getNullableNumber returns correct result for null', () => {
    var val = utils.getNullableNumber(null);
    expect(val).toBe(null);
  });

  test('Verify getNullableNumber returns correct result for undefined', () => {
    var val = utils.getNullableNumber(undefined);
    expect(val).toBe(null);
  });

  test('Verify getNullableNumber returns correct result for valid number', () => {
    var validNumber = 10;
    var val = utils.getNullableNumber(validNumber);
    expect(val).toBe(validNumber.toString());
  });
});

describe('Utils nsToSecNs tests', () => {
  var validStartNs = '9223372036854775837';
  test('Verify nsToSecNs returns correct result for valid validStartNs', () => {
    var val = utils.nsToSecNs(validStartNs);
    expect(val).toBe('9223372036.854775837');
  });

  test('Verify nsToSecNs returns correct result for 0 validStartNs', () => {
    var val = utils.nsToSecNs(0);
    expect(val).toBe('0.000000000');
  });

  test('Verify nsToSecNs returns correct result for null validStartNs', () => {
    var val = utils.nsToSecNs(null);
    expect(val).toBe('0.000000000');
  });

  test('Verify nsToSecNsWithHyphen returns correct result for valid validStartNs', () => {
    var val = utils.nsToSecNsWithHyphen(validStartNs);
    expect(val).toBe('9223372036-854775837');
  });

  test('Verify nsToSecNsWithHyphen returns correct result for 0 validStartNs', () => {
    var val = utils.nsToSecNsWithHyphen(0);
    expect(val).toBe('0-000000000');
  });

  test('Verify nsToSecNsWithHyphen returns correct result for null validStartNs', () => {
    var val = utils.nsToSecNsWithHyphen(null);
    expect(val).toBe('0-000000000');
  });
});

describe('Utils createTransactionId tests', () => {
  var validStartNs = '9223372036854775837';
  var shard = 1;
  var realm = 2;
  var num = 995;
  test('Verify createTransactionId returns correct result for valid inputs', () => {
    var val = utils.createTransactionId(shard, realm, num, validStartNs);
    expect(val).toBe(`${shard}.${realm}.${num}-` + '9223372036-854775837');
  });

  test('Verify nsToSecNs returns correct result for 0 inputs', () => {
    var val = utils.createTransactionId(0, 0, 0, 0);
    expect(val).toBe('0.0.0-0-000000000');
  });

  test('Verify nsToSecNs returns correct result for null inputs', () => {
    var val = utils.createTransactionId(0, 0, 0, null);
    expect(val).toBe('0.0.0-0-000000000');
  });
});

describe('Utils isValidTimestampParam tests', () => {
  test('Verify invalid for null', () => {
    expect(utils.isValidTimestampParam(null)).toBe(false);
  });
  test('Verify invalid for empty input', () => {
    expect(utils.isValidTimestampParam('')).toBe(false);
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isValidTimestampParam('0.0.1')).toBe(false);
  });
  test('Verify invalid for invalid seconds', () => {
    expect(utils.isValidTimestampParam('12345678901')).toBe(false);
  });
  test('Verify invalid for invalid nanoseconds', () => {
    expect(utils.isValidTimestampParam('1234567890.0000000012')).toBe(false);
  });
  test('Verify valid for seconds only', () => {
    expect(utils.isValidTimestampParam('1234567890')).toBe(true);
  });
  test('Verify valid for seconds and nanoseconds', () => {
    expect(utils.isValidTimestampParam('1234567890.000000001')).toBe(true);
  });
});

describe('Utils makeValidationResponse tests', () => {
  test('Verify success response for null', () => {
    const val = utils.makeValidationResponse(null);
    expect(val).toStrictEqual({isValid: true, code: utils.httpStatusCodes.OK, contents: 'OK'});
  });
  test('Verify success response for empty array', () => {
    const val = utils.makeValidationResponse([]);
    expect(val).toStrictEqual({isValid: true, code: utils.httpStatusCodes.OK, contents: 'OK'});
  });
  test('Verify failure response for null', () => {
    const message = {message: 'Invalid parameter: consensusTimestamp'};
    const val = utils.makeValidationResponse(message);
    expect(val).toStrictEqual({
      isValid: false,
      code: utils.httpStatusCodes.BAD_REQUEST,
      contents: {_status: {messages: message}}
    });
  });
});

describe('Utils parseTimestampParam tests', () => {
  test('Verify empty response for null', () => {
    expect(utils.parseTimestampParam(null)).toBe('');
  });
  test('Verify empty response for empty input', () => {
    expect(utils.parseTimestampParam('')).toBe('');
  });
  test('Verify empty response for invalid input', () => {
    expect(utils.parseTimestampParam('0.0.1')).toBe('');
  });
  test('Verify valid response for seconds only', () => {
    expect(utils.parseTimestampParam('1234567890')).toBe('1234567890000000000');
  });
  test('Verify valid response for seconds and nanoseconds', () => {
    expect(utils.parseTimestampParam('1234567890.000000001')).toBe('1234567890000000001');
  });
});

describe('Utils isValidEntityNum tests', () => {
  test('Verify invalid for null', () => {
    expect(utils.isValidEntityNum(null)).toBe(false);
  });
  test('Verify invalid for empty input', () => {
    expect(utils.isValidEntityNum('')).toBe(false);
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isValidEntityNum('1234567890.000000001')).toBe(false);
  });
  test('Verify invalid for negative shard', () => {
    expect(utils.isValidEntityNum('-1.0.1')).toBe(false);
  });
  test('Verify invalid for negative realm', () => {
    expect(utils.isValidEntityNum('0.-1.1')).toBe(false);
  });
  test('Verify invalid for negative entity_num', () => {
    expect(utils.isValidEntityNum('0.0.-1')).toBe(false);
  });
  test('Verify invalid for negative num', () => {
    expect(utils.isValidEntityNum('-1')).toBe(false);
  });
  test('Verify valid for entity_num only', () => {
    expect(utils.isValidEntityNum('3')).toBe(true);
  });
  test('Verify valid for full entity', () => {
    expect(utils.isValidEntityNum('1.2.3')).toBe(true);
  });
  test('Verify valid for full entity 2', () => {
    expect(utils.isValidEntityNum('0.2.3')).toBe(true);
  });
});

describe('utils buildComparatorFilter tests', () => {
  test('Verify buildComparatorFilter for seqnum=lt:2', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, 'lt:2', {
      key: constants.filterKeys.SEQUENCE_NUMBER,
      operator: 'lt',
      value: '2'
    });
  });

  test('Verify buildComparatorFilter for seqnum=lte:3', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, 'lte:3', {
      key: constants.filterKeys.SEQUENCE_NUMBER,
      operator: 'lte',
      value: '3'
    });
  });

  test('Verify buildComparatorFilter for seqnum=4', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, '4', {
      key: constants.filterKeys.SEQUENCE_NUMBER,
      operator: 'eq',
      value: '4'
    });
  });

  test('Verify buildComparatorFilter for timestamp=gte:1234567890.000000004', () => {
    verifyBuildComparatorFilter(constants.filterKeys.TIMESTAMP, 'gte:1234567890.000000005', {
      key: constants.filterKeys.TIMESTAMP,
      operator: 'gte',
      value: '1234567890.000000005'
    });
  });

  test('Verify buildComparatorFilter for timestamp=gt:6', () => {
    verifyBuildComparatorFilter(constants.filterKeys.TIMESTAMP, 'gt:1234567890.000000006', {
      key: constants.filterKeys.TIMESTAMP,
      operator: 'gt',
      value: '1234567890.000000006'
    });
  });
});

const verifyBuildComparatorFilter = (key, val, expectedFilter) => {
  let filter = utils.buildComparatorFilter(key, val);

  verifyFilter(filter, expectedFilter.key, expectedFilter.operator, expectedFilter.value);
};

const verifyFilter = (filter, key, op, val) => {
  expect(filter.key).toStrictEqual(key);
  expect(filter.operator).toStrictEqual(op);
  expect(filter.value).toStrictEqual(val);
};

describe('utils buildFilterObject tests', () => {
  test('Verify buildComparatorFilter for /api/v1/topic/7?seqnum=2', () => {
    const filters = {
      seqnum: '2'
    };

    let formattedFilters = utils.buildFilterObject(filters);

    expect(formattedFilters.length).toBe(1);
    verifyFilter(formattedFilters[0], constants.filterKeys.SEQUENCE_NUMBER, 'eq', '2');
  });

  test('Verify buildComparatorFilter for /api/v1/topic/7?timestamp=1234567890.000000004', () => {
    const filters = {
      timestamp: '1234567890.000000004'
    };

    let formattedFilters = utils.buildFilterObject(filters);

    expect(formattedFilters.length).toBe(1);
    verifyFilter(formattedFilters[0], constants.filterKeys.TIMESTAMP, 'eq', '1234567890.000000004');
  });

  test('Verify buildComparatorFilter for /api/v1/topic/7?seqnum=2&seqnum=gte:3&timestamp=1234567890.000000004&order=desc&limit=5', () => {
    const filters = {
      seqnum: ['lt:2', 'gte:3'],
      timestamp: '1234567890.000000004',
      limit: '5',
      order: 'desc'
    };

    let builtFilters = utils.buildFilterObject(filters);

    expect(builtFilters.length).toBe(5);
    verifyFilter(builtFilters[0], constants.filterKeys.SEQUENCE_NUMBER, 'lt', '2');
    verifyFilter(builtFilters[1], constants.filterKeys.SEQUENCE_NUMBER, 'gte', '3');
    verifyFilter(builtFilters[2], constants.filterKeys.TIMESTAMP, 'eq', '1234567890.000000004');
    verifyFilter(builtFilters[3], constants.filterKeys.LIMIT, 'eq', '5');
    verifyFilter(builtFilters[4], constants.filterKeys.ORDER, 'eq', 'desc');
  });
});

describe('utils formatComparator tests', () => {
  test('Verify formatComparator for seqnum=2', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, 'lt:2');

    let formattedFilter = utils.formatComparator(filter);

    expect(formattedFilter).toBe(undefined);
  });

  test('Verify formatComparator for timestamp=lte:1234567890.000000004', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, 'lte:1234567890.000000004');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.TIMESTAMP, ' <= ', '1234567890000000004');
  });

  test('Verify formatComparator for account.id=5', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, '5');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.ACCOUNT_ID, ' = ', {num: '5', realm: 0, shard: 0});
  });

  test('Verify formatComparator for timestamp=1234567890.000000004', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, '1234567890.000000005');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.TIMESTAMP, ' = ', '1234567890000000005');
  });

  test('Verify formatComparator for account.id=gte:6', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, 'gte:6');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.ACCOUNT_ID, ' >= ', {num: '6', realm: 0, shard: 0});
  });

  test('Verify formatComparator for timestamp=lte:1234567890.000000004', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, 'gt:1234567890.000000007');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.TIMESTAMP, ' > ', '1234567890000000007');
  });
});

describe('utils validateAndParseFilters tests', () => {
  test('Verify validateAndParseFilters for seqnum=2', () => {
    const filters = [
      utils.buildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, '<:2'),
      utils.buildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, '<=:2')
    ];

    let validationResponse = utils.validateAndParseFilters(filters);

    verifyInvalidFilters(validationResponse, [
      constants.filterKeys.SEQUENCE_NUMBER,
      constants.filterKeys.SEQUENCE_NUMBER
    ]);
  });

  test('Verify validateAndParseFilters for erroneous data', () => {
    const filters = [
      utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, 'lt:-1'),
      utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, 'lte:today'),
      utils.buildComparatorFilter(constants.filterKeys.ORDER, 'chronological'),
      utils.buildComparatorFilter(constants.filterKeys.LIMIT, '-1'),
      utils.buildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, '-1'),
      utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_PUBLICKEY, 'key'),
      utils.buildComparatorFilter(constants.filterKeys.RESULT, 'good'),
      utils.buildComparatorFilter(constants.filterKeys.TYPE, 'all'),
      utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_BALANCE, '-1')
    ];

    let validationResponse = utils.validateAndParseFilters(filters);

    verifyInvalidFilters(validationResponse, [
      constants.filterKeys.ACCOUNT_ID,
      constants.filterKeys.TIMESTAMP,
      constants.filterKeys.ORDER,
      constants.filterKeys.LIMIT,
      constants.filterKeys.SEQUENCE_NUMBER,
      constants.filterKeys.ACCOUNT_PUBLICKEY,
      constants.filterKeys.RESULT,
      constants.filterKeys.TYPE,
      constants.filterKeys.ACCOUNT_BALANCE
    ]);
  });

  test('Verify validateAndParseFilters for invalid format', () => {
    const filters = [
      utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, 'lt:0.1.23456789012345'),
      utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, 'lte:23456789012345678901234'),
      utils.buildComparatorFilter(constants.filterKeys.LIMIT, '2.3'),
      utils.buildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, '3.4'),
      utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_PUBLICKEY, '3c3d546321ff6f63d701d2ec5c2'),
      utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_BALANCE, '23456789012345678901234')
    ];

    let validationResponse = utils.validateAndParseFilters(filters);

    verifyInvalidFilters(validationResponse, [
      constants.filterKeys.ACCOUNT_ID,
      constants.filterKeys.TIMESTAMP,
      constants.filterKeys.LIMIT,
      constants.filterKeys.SEQUENCE_NUMBER,
      constants.filterKeys.ACCOUNT_PUBLICKEY,
      constants.filterKeys.ACCOUNT_BALANCE
    ]);
  });

  test('Verify validateAndParseFilters for valid filters', () => {
    const filters = [
      utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, 'lt:2'),
      utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, 'lte:1234567890.000000003'),
      utils.buildComparatorFilter(constants.filterKeys.ORDER, 'desc'),
      utils.buildComparatorFilter(constants.filterKeys.LIMIT, 'gte:4'),
      utils.buildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, '5'),
      utils.buildComparatorFilter(
        constants.filterKeys.ACCOUNT_PUBLICKEY,
        '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be'
      ),
      utils.buildComparatorFilter(constants.filterKeys.RESULT, 'success'),
      utils.buildComparatorFilter(constants.filterKeys.RESULT, 'fail'),
      utils.buildComparatorFilter(constants.filterKeys.TYPE, 'credit'),
      utils.buildComparatorFilter(constants.filterKeys.TYPE, 'debit'),
      utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_BALANCE, '45000')
    ];

    let validationResponse = utils.validateAndParseFilters(filters);

    verifyValidFilters(validationResponse);
  });
});

const verifyValidFilters = val => {
  expect(val).toStrictEqual(utils.successValidationResponse);
};

const verifyInvalidFilters = (val, columns) => {
  let invalidObjects = [];
  for (let col of columns) {
    invalidObjects.push(utils.getInvalidParameterMessageObject(col));
  }

  expect(val).toStrictEqual(utils.makeValidationResponse(invalidObjects));
};
