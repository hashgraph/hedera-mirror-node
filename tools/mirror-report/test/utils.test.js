/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
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
 */

import {toHbars, toIsoString} from '../src/utils.js';

describe('toHbars', () => {
  it.each([
    [undefined, ''],
    [null, ''],
    [0n, '0.0'],
    [1n, '0.00000001'],
    [-1n, '-0.00000001'],
    [10n, '0.00000010'],
    [99999999n, '0.99999999'],
    [-99999999n, '-0.99999999'],
    [100000000n, '1.0'],
    [-100000000n, '-1.0'],
    [10000000001n, '100.00000001'],
    [4999999999999999999n, '49999999999.99999999'],
    [-4999999999999999999n, '-49999999999.99999999'],
    [5000000000000000000n, '50000000000.0']
  ])("toHbars(%s) = '%s'", (tinybars, expected) => {
    expect(toHbars(tinybars)).toBe(expected);
  });
});

describe('toIsoString', () => {
  it.each([
    [undefined, ''],
    [null, ''],
    ['0.0', '1970-01-01T00:00:00.000000000Z'],
    ['0.', '1970-01-01T00:00:00.000000000Z'],
    ['0.000000001', '1970-01-01T00:00:00.000000001Z'],
    ['1.000000001', '1970-01-01T00:00:01.000000001Z'],
    ['1.1', '1970-01-01T00:00:01.000000001Z'],
    ['1733181006', '2024-12-02T23:10:06.000000000Z'],
    ['1733181006.483579814', '2024-12-02T23:10:06.483579814Z']
  ])("toIsoString(%s) = '%s'", (timestamp, expected) => {
    expect(toIsoString(timestamp)).toBe(expected);
  });
});