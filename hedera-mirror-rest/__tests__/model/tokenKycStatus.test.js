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

import {TokenKycStatus} from '../../model';

describe('TokenKycStatus', () => {
  describe('invalid id', () => {
    [-1, 3].forEach((value) =>
      test(`${value}`, () => {
        expect(() => new TokenKycStatus(value)).toThrowErrorMatchingSnapshot();
      })
    );
  });

  test('toJSON', () => {
    const input = [new TokenKycStatus(0), new TokenKycStatus(1), new TokenKycStatus(2)];
    expect(JSON.stringify(input)).toEqual(JSON.stringify(['NOT_APPLICABLE', 'GRANTED', 'REVOKED']));
  });
});
