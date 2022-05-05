/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
const {InvalidArgumentError} = require('../errors/invalidArgumentError');
const constants = require('../constants');

describe('EntityId isValidEntityId tests', () => {
  test('Verify invalid for null', () => {
    expect(EntityId.isValidEntityId(null)).toBe(false);
  });
  test('Verify invalid for empty input', () => {
    expect(EntityId.isValidEntityId('')).toBe(false);
  });
  test('Verify invalid for more than 3 "." separated number parts', () => {
    expect(EntityId.isValidEntityId('0.1.2.3')).toBe(false);
  });
  test('Verify invalid for negative shard', () => {
    expect(EntityId.isValidEntityId('-1.0.1')).toBe(false);
  });
  test('Verify invalid for negative realm', () => {
    expect(EntityId.isValidEntityId('0.-1.1')).toBe(false);
  });
  test('Verify invalid for negative entity_num', () => {
    expect(EntityId.isValidEntityId('0.0.-1')).toBe(false);
  });
  test('Verify invalid for shard too big', () => {
    expect(EntityId.isValidEntityId('100000.65535.000000001')).toBe(false);
  });
  test('Verify invalid for realm too big', () => {
    expect(EntityId.isValidEntityId('100000.000000001')).toBe(false);
  });
  test('Verify invalid for negative num', () => {
    expect(EntityId.isValidEntityId('-1')).toBe(false);
  });
  test('Verify invalid for float number', () => {
    expect(EntityId.isValidEntityId(123.321)).toBe(false);
  });
  test('Verify valid for entity_num only', () => {
    expect(EntityId.isValidEntityId('3')).toBe(true);
  });
  test('Verify valid for realm.num', () => {
    expect(EntityId.isValidEntityId('65535.000000001')).toBe(true);
  });
  test('Verify valid for full entity', () => {
    expect(EntityId.isValidEntityId('1.2.3')).toBe(true);
  });
  test('Verify valid for full entity 2', () => {
    expect(EntityId.isValidEntityId('0.2.3')).toBe(true);
  });
  test('Verify valid for integer number', () => {
    expect(EntityId.isValidEntityId(123)).toBe(true);
  });
  test('Verify valid for encoded ID', () => {
    expect(EntityId.isValidEntityId('2814792716779530')).toBe(true);
  });
  test('Verify valid for max encoded ID', () => {
    expect(EntityId.isValidEntityId(2n ** 63n - 1n)).toBe(true);
  });
});

describe('EntityId parse from entityId string', () => {
  const specs = [
    {
      entityIdStr: '0.0.0',
      expected: EntityId.of(0, 0, 0),
    },
    {
      entityIdStr: '0.0.4294967295',
      expected: EntityId.of(0, 0, 4294967295),
    },
    {
      entityIdStr: '32767.65535.4294967295',
      expected: EntityId.of(32767, 65535, 4294967295),
    },
    {
      entityIdStr: '0',
      expected: EntityId.of(0, 0, 0),
    },
    {
      entityIdStr: '10',
      expected: EntityId.of(0, 0, 10),
    },
    {
      entityIdStr: '4294967295',
      expected: EntityId.of(0, 0, 4294967295),
    },
    {
      entityIdStr: '24294967295',
      expected: EntityId.of(0, 5, 2820130815),
    },
    {
      entityIdStr: '0.1',
      expected: EntityId.of(0, 0, 1),
    },
    {
      entityIdStr: '0x0000000000000000000000000000000000000001',
      expected: EntityId.of(0, 0, 1),
      paramName: constants.filterKeys.FROM,
    },
    {
      entityIdStr: '0000000000000000000000000000000000000001',
      expected: EntityId.of(0, 0, 1),
      paramName: constants.filterKeys.CONTRACT_ID,
    },
    {
      entityIdStr: '0x0000000100000000000000020000000000000003',
      expected: EntityId.of(1, 2, 3),
      paramName: constants.filterKeys.FROM,
    },
    {
      entityIdStr: '0x00007fff000000000000ffff00000000ffffffff',
      expected: EntityId.of(32767, 65535, 4294967295),
      paramName: constants.filterKeys.FROM,
    },
    {
      entityIdStr: '0.0.000000000000000000000000000000000186Fb1b',
      expected: EntityId.of(0, 0, 25623323),
    },
    {
      entityIdStr: '0.000000000000000000000000000000000186Fb1b',
      expected: EntityId.of(0, 0, 25623323),
    },
    {
      entityIdStr: '000000000000000000000000000000000186Fb1b',
      expected: EntityId.of(0, 0, 25623323),
    },
    {
      entityIdStr: '0x000000000000000000000000000000000186Fb1b',
      expected: EntityId.of(0, 0, 25623323),
      paramName: constants.filterKeys.FROM,
    },
    {
      entityIdStr: null,
      isNullable: true,
      expected: EntityId.of(null, null, null),
    },
    {
      isNullable: true,
      expected: EntityId.of(null, null, null),
    },
    {
      entityIdStr: null,
      isNullable: false,
      expectErr: true,
    },
    {
      isNullable: false,
      expectErr: true,
    },
    {
      entityIdStr: '0.1.x',
      paramName: 'demo param',
      expectErr: true,
    },
    {
      entityIdStr: 'a',
      expectErr: true,
    },
    {
      entityIdStr: '0.1.2.3',
      expectErr: true,
    },
    {
      entityIdStr: 'a.b.c',
      expectErr: true,
    },
    {
      entityIdStr: '-1.-1.-1',
      expectErr: true,
    },
    {
      entityIdStr: '-1',
      expectErr: true,
    },
    {
      entityIdStr: '0x',
      expectErr: true,
      paramName: constants.filterKeys.FROM,
    },
    {
      entityIdStr: '0x010203',
      expectErr: true,
      paramName: constants.filterKeys.FROM,
    },
    {
      entityIdStr: '0x00000001000000000000000200000000000000034',
      expectErr: true,
      paramName: constants.filterKeys.FROM,
    },
    {
      entityIdStr: '0x2540be3f6001fffffffffffff001fffffffffffff', // 9999999990, Number.MAX_SAFE_INTEGER, Number.MAX_SAFE_INTEGER
      expectErr: true,
      paramName: constants.filterKeys.FROM,
    },
    {
      entityIdStr: '0x10000000000000000000000000000000000000000', // ffffffffffffffffffffffffffffffffffffffff + 1
      expectErr: true,
      paramName: constants.filterKeys.FROM,
    },
  ];

  for (const spec of specs) {
    const {entityIdStr, paramName, isNullable, expectErr, expected} = spec;
    test(`${entityIdStr}`, () => {
      if (!expectErr) {
        expect(EntityId.parse(entityIdStr, paramName, isNullable).toString()).toEqual(expected.toString());
      } else {
        expect(() => {
          EntityId.parse(entityIdStr, paramName, isNullable);
        }).toThrowErrorMatchingSnapshot();
      }
    });
  }
});

describe('EntityId cache', function () {
  test('cache hit from entity ID string', () => {
    const original = EntityId.parse('0.1.158');
    const cached = EntityId.parse('0.1.158');
    expect(cached).toBe(original);
  });

  test('cache hit from encoded entity ID', () => {
    const original = EntityId.parse(4294967454);
    const cached = EntityId.parse(4294967454);
    expect(cached).toBe(original);
  });

  test('cache hit from BigInt / number / string', () => {
    const original = EntityId.parse('2005');

    expect(EntityId.parse(2005n)).toBe(original);
    expect(EntityId.parse(2005)).toBe(original);
    expect(EntityId.parse('2005')).toBe(original);
  });
});

describe('EntityId parse from encoded entityId', () => {
  const specs = [
    {
      encodedId: 0,
      expected: EntityId.of(0, 0, 0),
    },
    {
      encodedId: 4294967295,
      expected: EntityId.of(0, 0, 4294967295),
    },
    {
      encodedId: 2814792716779530,
      expected: EntityId.of(10, 10, 10),
    },
    {
      encodedId: BigInt(0),
      expected: EntityId.of(0, 0, 0),
    },
    {
      encodedId: BigInt(4294967295),
      expected: EntityId.of(0, 0, 4294967295),
    },
    {
      encodedId: '4294967295',
      expected: EntityId.of(0, 0, 4294967295),
    },
    {
      encodedId: BigInt(2814792716779530),
      expected: EntityId.of(10, 10, 10),
    },
    {
      encodedId: 9223372036854775807n,
      expected: EntityId.of(32767, 65535, 4294967295),
    },
    {
      encodedId: 9223090561878065152n,
      expected: EntityId.of(32767, 0, 0),
    },
    {
      encodedId: 'a',
      expectErr: true,
    },
    {
      encodedId: true,
      expectErr: true,
    },
    {
      encodedId: 2n ** 63n,
      expectErr: true,
    },
    {
      encodedId: null,
      isNullable: true,
      expected: EntityId.of(null, null, null),
    },
    {
      isNullable: true,
      expected: EntityId.of(null, null, null),
    },
    {
      encodedId: null,
      expectErr: true,
    },
    {
      expectErr: true,
    },
  ];

  for (const spec of specs) {
    const {encodedId, isNullable, expectErr, expected} = spec;
    test(`${encodedId}`, () => {
      if (!expectErr) {
        expect(EntityId.parse(encodedId, '', isNullable).toString()).toEqual(expected.toString());
      } else {
        expect(() => {
          EntityId.parse(encodedId, isNullable);
        }).toThrowError(InvalidArgumentError);
      }
    });
  }
});

describe('EntityId toEvmAddress', () => {
  test('0.0.0', () => {
    expect(EntityId.of(0, 0, 0).toEvmAddress()).toEqual('0x0000000000000000000000000000000000000000');
  });

  test('0.0.7', () => {
    expect(EntityId.of(1, 2, 7).toEvmAddress()).toEqual('0x0000000100000000000000020000000000000007');
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.of(32767n, 65535n, 4294967295n, constants.EvmAddressType.NO_SHARD_REALM).toEvmAddress()).toEqual(
      '0x00007fff000000000000ffff00000000ffffffff'
    );
  });
});

describe('EntityId toString', () => {
  test('null', () => {
    expect(EntityId.of(null, null, null).toString()).toEqual(null);
  });

  test('0.0.0', () => {
    expect(EntityId.of(0n, 0n, 0n).toString()).toEqual(null);
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.of(32767, 65535, 4294967295).toString()).toEqual('32767.65535.4294967295');
  });
});

describe('EntityId encoding', () => {
  test('0.0.0', () => {
    expect(EntityId.parse('0.0.0').getEncodedId()).toBe(0);
  });

  test('0.0.10', () => {
    expect(EntityId.parse('0.0.10').getEncodedId()).toBe(10);
  });

  test('0.0.4294967295', () => {
    expect(EntityId.parse('0.0.4294967295').getEncodedId()).toBe(4294967295);
  });

  test('10.10.10', () => {
    expect(EntityId.parse('10.10.10').getEncodedId()).toBe(2814792716779530);
  });

  test('31.65535.4294967295', () => {
    expect(EntityId.parse('31.65535.4294967295').getEncodedId()).toBe(Number.MAX_SAFE_INTEGER);
  });

  test('32.0.0', () => {
    expect(EntityId.parse('32.0.0').getEncodedId()).toBe(2n ** 53n);
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.parse('32767.65535.4294967295').getEncodedId()).toBe(9223372036854775807n);
  });

  test('32767.0.0', () => {
    expect(EntityId.parse('32767.0.0').getEncodedId()).toBe(9223090561878065152n);
  });

  test('nullable', () => {
    expect(EntityId.parse(null, true).getEncodedId()).toBeNull();
  });
});
