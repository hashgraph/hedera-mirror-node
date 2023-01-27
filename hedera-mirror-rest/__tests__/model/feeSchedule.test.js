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

import {FileDecodeError} from '../../errors';

// models
import {FeeSchedule} from '../../model';

describe('fee schedule proto parse', () => {
  const input = {
    file_data: Buffer.from(
      '0a280a0a08541a061a04408888340a0a08061a061a0440889d2d0a0a08071a061a0440b0b63c120208011200',
      'hex'
    ),
    consensus_timestamp: 1653644164591111113,
  };

  const expectedOutput = {
    current_feeSchedule: [
      {
        fees: [{servicedata: {gas: {low: 853000}}}],
        hederaFunctionality: 84,
      },
      {
        fees: [{servicedata: {gas: {low: 741000}}}],
        hederaFunctionality: 6,
      },
      {
        fees: [{servicedata: {gas: {low: 990000}}}],
        hederaFunctionality: 7,
      },
    ],
    next_feeSchedule: [],
    timestamp: 1653644164591111113,
  };

  test('valid update', () => {
    expect(new FeeSchedule(input)).toMatchObject(expectedOutput);
  });

  test('invalid contents', () => {
    expect(() => new FeeSchedule({file_data: '123456', consensus_timestamp: 1})).toThrowError(FileDecodeError);
    expect(() => new FeeSchedule({file_data: Buffer.from('123456', 'hex'), consensus_timestamp: 1})).toThrowError(
      FileDecodeError
    );
  });
});
