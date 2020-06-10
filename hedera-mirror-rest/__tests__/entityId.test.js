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

describe('EntityId fromString', () => {
  test('0.0.0', () => {
    expect(EntityId.fromString('0.0.0')).toEqual(EntityId.of(0, 0, 0));
  });

  test('0.0.4294967295', () => {
    expect(EntityId.fromString('0.0.4294967295')).toEqual(EntityId.of(0, 0, 4294967295));
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.fromString('32767.65535.4294967295')).toEqual(EntityId.of(32767, 65535, 4294967295));
  });
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

describe('EntityId from encoded id', () => {
  test('0.0.0', () => {
    expect(EntityId.fromEncodedId('0')).toEqual(EntityId.fromString('0.0.0'));
  });

  test('0.0.10', () => {
    expect(EntityId.fromEncodedId('10')).toEqual(EntityId.fromString('0.0.10'));
  });

  test('0.0.4294967295', () => {
    expect(EntityId.fromEncodedId('4294967295')).toEqual(EntityId.fromString('0.0.4294967295'));
  });

  test('10.10.10', () => {
    expect(EntityId.fromEncodedId('2814792716779530')).toEqual(EntityId.fromString('10.10.10'));
  });

  test('32767.65535.4294967295', () => {
    expect(EntityId.fromEncodedId('9223372036854775807')).toEqual(EntityId.fromString('32767.65535.4294967295'));
  });

  test('32767.0.0', () => {
    expect(EntityId.fromEncodedId('9223090561878065152')).toEqual(EntityId.fromString('32767.0.0'));
  });
});
