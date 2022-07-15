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

import {FileDecodeError} from '../../errors';

// models
import {ExchangeRate} from '../../model';

describe('exchange rate proto parse', () => {
  const input = {
    file_data: Buffer.from('0a1008b0ea0110cac1181a0608a0a1d09306121008b0ea0110e18e191a0608b0bdd09306', 'hex'),
    consensus_timestamp: 1651770056616171000,
  };

  const expectedOutput = {
    current_cent: 401610,
    current_expiration: 1651773600,
    current_hbar: 30000,
    next_cent: 411489,
    next_expiration: 1651777200,
    next_hbar: 30000,
    timestamp: 1651770056616171000,
  };

  test('valid update', () => {
    expect(new ExchangeRate(input)).toEqual(expectedOutput);
  });

  test('invalid contents', () => {
    expect(() => new ExchangeRate({file_data: Buffer.from('123456', 'hex'), consensus_timestamp: 1})).toThrowError(
      FileDecodeError
    );
  });
});
