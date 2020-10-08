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

const EntityId = require('../entityId');
const {InvalidArgumentError} = require('../errors/invalidArgumentError');

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
      entityIdStr: '2814792716779530',
      expected: EntityId.of(10, 10, 10),
    },
    {
      entityIdStr: '9223372036854775807',
      expected: EntityId.of(32767, 65535, 4294967295),
    },
    {
      entityIdStr: '9223090561878065152',
      expected: EntityId.of(32767, 0, 0),
    },
    {
      entityIdStr: '0.1.x',
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
      entityIdStr: '0.1',
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
    const {entityIdStr, expectErr, expected} = spec;
    test(entityIdStr, () => {
      if (!expectErr) {
        expect(EntityId.fromString(entityIdStr)).toEqual(expected);
      } else {
        expect(() => {
          EntityId.fromString(entityIdStr);
        }).toThrowError(InvalidArgumentError);
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
});
