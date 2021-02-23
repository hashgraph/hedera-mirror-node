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

const EntityId = require('../entityId');
const utils = require('../utils.js');
const constants = require('../constants.js');
const config = require('../config.js');

describe('utils buildComparatorFilter tests', () => {

  test('Verify buildComparatorFilter for scheduled=true', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SCHEDULED, 'true', {
      key: constants.filterKeys.SCHEDULED,
      operator: 'eq',
      value: 'true',
    });
  });

  test('Verify buildComparatorFilter for scheduled=false', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SCHEDULED, 'false', {
      key: constants.filterKeys.SCHEDULED,
      operator: 'eq',
      value: 'false',
    });
  });

  test('Verify buildComparatorFilter for sequencenumber=lt:2', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, 'lt:2', {
      key: constants.filterKeys.SEQUENCE_NUMBER,
      operator: 'lt',
      value: '2',
    });
  });

  test('Verify buildComparatorFilter for sequencenumber=lte:3', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, 'lte:3', {
      key: constants.filterKeys.SEQUENCE_NUMBER,
      operator: 'lte',
      value: '3',
    });
  });

  test('Verify buildComparatorFilter for sequencenumber=4', () => {
    verifyBuildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, '4', {
      key: constants.filterKeys.SEQUENCE_NUMBER,
      operator: 'eq',
      value: '4',
    });
  });

  test('Verify buildComparatorFilter for timestamp=gte:1234567890.000000004', () => {
    verifyBuildComparatorFilter(constants.filterKeys.TIMESTAMP, 'gte:1234567890.000000005', {
      key: constants.filterKeys.TIMESTAMP,
      operator: 'gte',
      value: '1234567890.000000005',
    });
  });

  test('Verify buildComparatorFilter for timestamp=gt:6', () => {
    verifyBuildComparatorFilter(constants.filterKeys.TIMESTAMP, 'gt:1234567890.000000006', {
      key: constants.filterKeys.TIMESTAMP,
      operator: 'gt',
      value: '1234567890.000000006',
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
  test('Verify buildComparatorFilter for /api/v1/topic/7/messages?sequencenumber=2', () => {
    const filters = {
      sequencenumber: '2',
    };

    const formattedFilters = utils.buildFilterObject(filters);

    expect(formattedFilters).toHaveLength(1);
    verifyFilter(formattedFilters[0], constants.filterKeys.SEQUENCE_NUMBER, 'eq', '2');
  });

  test('Verify buildComparatorFilter for /api/v1/topic/7/messages?timestamp=1234567890.000000004', () => {
    const filters = {
      timestamp: '1234567890.000000004',
    };

    const formattedFilters = utils.buildFilterObject(filters);

    expect(formattedFilters).toHaveLength(1);
    verifyFilter(formattedFilters[0], constants.filterKeys.TIMESTAMP, 'eq', '1234567890.000000004');
  });

  test('Verify buildComparatorFilter for /api/v1/topic/7/messages?sequencenumber=lt:2&sequencenumber=gte:3&timestamp=1234567890.000000004&order=desc&limit=5', () => {
    const filters = {
      sequencenumber: ['lt:2', 'gte:3'],
      timestamp: '1234567890.000000004',
      limit: '5',
      order: 'desc',
    };

    const formattedFilters = utils.buildFilterObject(filters);

    expect(formattedFilters).toHaveLength(5);
    verifyFilter(formattedFilters[0], constants.filterKeys.SEQUENCE_NUMBER, 'lt', '2');
    verifyFilter(formattedFilters[1], constants.filterKeys.SEQUENCE_NUMBER, 'gte', '3');
    verifyFilter(formattedFilters[2], constants.filterKeys.TIMESTAMP, 'eq', '1234567890.000000004');
    verifyFilter(formattedFilters[3], constants.filterKeys.LIMIT, 'eq', '5');
    verifyFilter(formattedFilters[4], constants.filterKeys.ORDER, 'eq', 'desc');
  });

  test('Verify buildComparatorFilter for /api/v1/transactions/0.0.3-1234567890-000000123/stateproof?scheduled=true', () => {
    const filters = {
      scheduled: 'true',
    };

    const formattedFilters = utils.buildFilterObject(filters);

    expect(formattedFilters).toHaveLength(1);
    verifyFilter(formattedFilters[0], constants.filterKeys.SCHEDULED, 'eq', 'true');
  });

  test('Verify buildComparatorFilter for /api/v1/transactions/0.0.3-1234567890-000000123/stateproof?scheduled=true&scheduled=false', () => {
    const filters = {
      scheduled: ['true', 'false'],
    };

    const formattedFilters = utils.buildFilterObject(filters);

    expect(formattedFilters).toHaveLength(2);
    verifyFilter(formattedFilters[0], constants.filterKeys.SCHEDULED, 'eq', 'true');
    verifyFilter(formattedFilters[1], constants.filterKeys.SCHEDULED, 'eq', 'false');
  });

  test('Verify buildComparatorFilter for /api/v1/schedules?account.id=0.0.1024&schedule.id=gte:4000&order=desc&limit=10', () => {
    const filters = {
      'account.id': 'lt:0.0.1024',
      'schedule.id': 'gte:4000',
      order: 'desc',
      limit: '10',
    };

    const formattedFilters = utils.buildFilterObject(filters);

    expect(formattedFilters).toHaveLength(4);
    verifyFilter(formattedFilters[0], constants.filterKeys.ACCOUNT_ID, 'lt', '0.0.1024');
    verifyFilter(formattedFilters[1], constants.filterKeys.SCHEDULE_ID, 'gte', '4000');
    verifyFilter(formattedFilters[2], constants.filterKeys.ORDER, 'eq', 'desc');
    verifyFilter(formattedFilters[3], constants.filterKeys.LIMIT, 'eq', '10');
  });
});

describe('utils formatComparator tests', () => {
  test('Verify formatComparator for sequencenumber=lt:2', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.SEQUENCE_NUMBER, 'lt:2');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.SEQUENCE_NUMBER, ' < ', '2');
  });

  test('Verify formatComparator for timestamp=lte:1234567890.000000004', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, 'lte:1234567890.000000004');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.TIMESTAMP, ' <= ', '1234567890000000004');
  });

  test('Verify formatComparator for account.id=5', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, '5');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.ACCOUNT_ID, ' = ', '5');
  });

  test('Verify formatComparator for account.id=0.2.5', () => {
    const entityIdStr = '0.2.5';
    const entityId = EntityId.fromString(entityIdStr);
    const filter = utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, entityIdStr);
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.ACCOUNT_ID, ' = ', entityId.getEncodedId());
  });

  test('Verify formatComparator for timestamp=1234567890.000000004', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, '1234567890.000000005');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.TIMESTAMP, ' = ', '1234567890000000005');
  });

  test('Verify formatComparator for account.id=gte:6', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.ACCOUNT_ID, 'gte:6');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.ACCOUNT_ID, ' >= ', '6');
  });

  test('Verify formatComparator for timestamp=gt:1234567890.000000004', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.TIMESTAMP, 'gt:1234567890.000000007');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.TIMESTAMP, ' > ', '1234567890000000007');
  });

  test('Verify formatComparator for scheduled=true', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.SCHEDULED, 'true');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.SCHEDULED, ' = ', true);
  });

  test('Verify formatComparator for scheduled=false', () => {
    const filter = utils.buildComparatorFilter(constants.filterKeys.SCHEDULED, 'false');
    utils.formatComparator(filter);
    verifyFilter(filter, constants.filterKeys.SCHEDULED, ' = ', false);
  });

});

const verifyInvalidFilters = async (filters) => {
  await expect(utils.validateAndParseFilters(filters)).rejects.toThrowErrorMatchingSnapshot();
};

const validateAndParseFiltersNoExMessage = 'Verify validateAndParseFilters for valid filters does not throw exception';
const verifyValidAndInvalidFilters = async (invalidFilters, validFilters) => {
  invalidFilters.forEach((filter) => {
    const filterString = Array.isArray(filter)
      ? `${JSON.stringify(filter[0])} ${filter.length} times`
      : `${JSON.stringify(filter)}`;
    test(`Verify validateAndParseFilters for invalid ${filterString}`, async () => {
      await verifyInvalidFilters([filter]);
    });
  });

  test(`${validateAndParseFiltersNoExMessage}`, async () => {
    await utils.validateAndParseFilters(validFilters);
  });
};

describe('utils validateAndParseFilters boolean key tests', () => {
  const booleanFilterKeys = [constants.filterKeys.SCHEDULED];
  booleanFilterKeys.forEach((key) => {
    const invalidFilters = [
      // erroneous data
      utils.buildComparatorFilter(key, 'lt:true'),
      utils.buildComparatorFilter(key, 'lte:true'),
      utils.buildComparatorFilter(key, 'ne:true'),
      utils.buildComparatorFilter(key, 'gte:true'),
      utils.buildComparatorFilter(key, 'gt:true'),
      // invalid format
      utils.buildComparatorFilter(key, 'invalid'),
    ];

    const filters = [
      utils.buildComparatorFilter(key, 'true'),
      utils.buildComparatorFilter(key, 'false'),
      utils.buildComparatorFilter(key, 'True'),
      utils.buildComparatorFilter(key, 'False'),
    ];

    verifyValidAndInvalidFilters(invalidFilters, filters);
  });
});

describe('utils validateAndParseFilters timestamp key tests', () => {
  const key = constants.filterKeys.TIMESTAMP;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, 'lte:today'),
    // invalid format
    utils.buildComparatorFilter(key, 'lte:23456789012345678901234'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, '1234567890.000000003'),
    utils.buildComparatorFilter(key, 'eq:1234567890.000000003'),
    utils.buildComparatorFilter(key, 'gt:1234567890.000000003'),
    utils.buildComparatorFilter(key, 'gte:1234567890.000000003'),
    utils.buildComparatorFilter(key, 'lt:1234567890.000000003'),
    utils.buildComparatorFilter(key, 'lte:1234567890.000000003'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters order key tests', () => {
  const key = constants.filterKeys.ORDER;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, 'chronological'),
  ];

  const filters = [utils.buildComparatorFilter(key, 'asc'), utils.buildComparatorFilter(key, 'desc')];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters limit key tests', () => {
  const key = constants.filterKeys.ORDER;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
    // invalid format
    utils.buildComparatorFilter(key, '2.3'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, 'asc'),
    utils.buildComparatorFilter(key, 'desc'),
    utils.buildComparatorFilter(key, 'ASC'),
    utils.buildComparatorFilter(key, 'DESC'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters entity key tests', () => {
  const booleanFilterKeys = [
    constants.filterKeys.ACCOUNT_ID,
    constants.filterKeys.TOKEN_ID,
    constants.filterKeys.SCHEDULE_ID,
  ];
  booleanFilterKeys.forEach((key) => {
    const invalidFilters = [
      // erroneous data
      utils.buildComparatorFilter(key, 'lt:-1'),
      // invalid format
      utils.buildComparatorFilter(key, 'lt:0.1.23456789012345'),
      utils.buildMultipleComparatorFilter(constants.filterKeys.ACCOUNT_ID, '0.0.3', config.queryParams[constants.filterKeys.ACCOUNT_ID].max + 1),
    ];

    const filters = [
      utils.buildComparatorFilter(key, '123'),
      utils.buildComparatorFilter(key, '1.2.3'),
      utils.buildComparatorFilter(key, '0.0.2000'),
      utils.buildComparatorFilter(key, 'eq:1234567890'),
      utils.buildComparatorFilter(key, 'gt:1234567890'),
      utils.buildComparatorFilter(key, 'gte:1234567890'),
      utils.buildComparatorFilter(key, 'lt:1234567890'),
      utils.buildComparatorFilter(key, 'lte:1234567890'),
    ];

    verifyValidAndInvalidFilters(invalidFilters, filters);
  });
});

describe('utils validateAndParseFilters integer key tests', () => {
  const booleanFilterKeys = [constants.filterKeys.SEQUENCE_NUMBER];
  booleanFilterKeys.forEach((key) => {
    const invalidFilters = [
      // erroneous data
      utils.buildComparatorFilter(key, 'test'),
      // invalid format
      utils.buildComparatorFilter(key, '0.45678901234'),
      utils.buildComparatorFilter(key, '<=:2'),
    ];

    const filters = [
      utils.buildComparatorFilter(key, '1'),
      utils.buildComparatorFilter(key, '9007199254740991'), // MAX_SAFE_INTEGER
    ];

    verifyValidAndInvalidFilters(invalidFilters, filters);
  });
});

describe('utils validateAndParseFilters credit type key tests', () => {
  const key = constants.filterKeys.CREDIT_TYPE;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
    // invalid format
    utils.buildComparatorFilter(key, 'cred'),
    utils.buildComparatorFilter(key, 'deb'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, 'credit'),
    utils.buildComparatorFilter(key, 'debit'),
    utils.buildComparatorFilter(key, 'CREDIT'),
    utils.buildComparatorFilter(key, 'DEBIT'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters result type key tests', () => {
  const key = constants.filterKeys.RESULT;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
    // invalid format
    utils.buildComparatorFilter(key, 'y'),
    utils.buildComparatorFilter(key, 'n'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, 'success'),
    utils.buildComparatorFilter(key, 'fail'),
    utils.buildComparatorFilter(key, 'SUCCESS'),
    utils.buildComparatorFilter(key, 'FAIL'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters account balance key tests', () => {
  const key = constants.filterKeys.ACCOUNT_BALANCE;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
    // invalid format
    utils.buildComparatorFilter(key, 'y'),
    utils.buildComparatorFilter(key, '23456789012345678901234'),
  ];

  const filters = [
    utils.buildComparatorFilter(key, '0'),
    utils.buildComparatorFilter(key, '1000000000'),
    utils.buildComparatorFilter(key, '1234567890123456789'),
  ];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters encoding key tests', () => {
  const key = constants.filterKeys.ENCODING;
  const invalidFilters = [
    // erroneous data
    utils.buildComparatorFilter(key, 'encrypt'),
    // invalid format
    utils.buildComparatorFilter(key, 'utf'),
    utils.buildComparatorFilter(key, 'b64'),
  ];

  const filters = [utils.buildComparatorFilter(key, 'utf-8'), utils.buildComparatorFilter(key, 'base64')];

  verifyValidAndInvalidFilters(invalidFilters, filters);
});

describe('utils validateAndParseFilters crypto key tests', () => {
  const booleanFilterKeys = [constants.filterKeys.ACCOUNT_PUBLICKEY, constants.filterKeys.ENTITY_PUBLICKEY];
  booleanFilterKeys.forEach((key) => {
    const invalidFilters = [
      // erroneous data
      utils.buildComparatorFilter(key, 'key'),
      // invalid format
      utils.buildComparatorFilter(key, '3c3d546321ff6f63d701d2ec5c2'),
    ];

    const filters = [
      utils.buildComparatorFilter(key, '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be'),
    ];

    verifyValidAndInvalidFilters(invalidFilters, filters);
  });
});
