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
const {InvalidArgumentError} = require('../errors/invalidArgumentError');

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
    expect(EntityId.isValidEntityId('1234567890.000000001')).toBe(true);
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
});

describe('EntityId fromString', () => {
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
      entityIdStr: '0.1',
      expected: EntityId.of(0, 0, 1),
    },
    {
      entityIdStr: null,
      isNullable: true,
      expected: EntityId.of(null, null, null),
    },
    {
      entityIdStr: null,
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
  ];

  for (const spec of specs) {
    const {entityIdStr, paramName, isNullable, expectErr, expected} = spec;
    test(`${entityIdStr}`, () => {
      if (!expectErr) {
        expect(EntityId.fromString(entityIdStr, paramName, isNullable)).toEqual(expected);
      } else {
        expect(() => {
          EntityId.fromString(entityIdStr, paramName, isNullable);
        }).toThrowErrorMatchingSnapshot();
      }
    });
  }
});

describe('EntityId toString', () => {
  test('0.0.0', () => {
    expect(EntityId.of(0, 0, 0).toString()).toEqual('0.0.0');
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.of(32767, 65535, 4294967295).toString()).toEqual('32767.65535.4294967295');
  });
});

describe('EntityId encoding', () => {
  test('0.0.0', () => {
    expect(EntityId.fromString('0.0.0').getEncodedId()).toBe('0');
  });

  test('0.0.10', () => {
    expect(EntityId.fromString('0.0.10').getEncodedId()).toBe('10');
  });

  test('0.0.4294967295', () => {
    expect(EntityId.fromString('0.0.4294967295').getEncodedId()).toBe('4294967295');
  });

  test('10.10.10', () => {
    expect(EntityId.fromString('10.10.10').getEncodedId()).toBe('2814792716779530');
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.fromString('32767.65535.4294967295').getEncodedId()).toBe('9223372036854775807');
  });

  test('32767.0.0', () => {
    expect(EntityId.fromString('32767.0.0').getEncodedId()).toBe('9223090561878065152');
  });

  test('nullable', () => {
    expect(EntityId.fromString(null, '', true).getEncodedId()).toBeNull();
  });
});

describe('EntityId fromEncodedId', () => {
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
      encodedId: BigInt('9223372036854775807'),
      expected: EntityId.of(32767, 65535, 4294967295),
    },
    {
      encodedId: 9223090561878065152,
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
      encodedId: null,
      expectErr: true,
    },
  ];

  for (const spec of specs) {
    const {encodedId, isNullable, expectErr, expected} = spec;
    test(`${encodedId}`, () => {
      if (!expectErr) {
        expect(EntityId.fromEncodedId(encodedId, isNullable)).toEqual(expected);
      } else {
        expect(() => {
          EntityId.fromEncodedId(encodedId, isNullable);
        }).toThrowError(InvalidArgumentError);
      }
    });
  }
});
