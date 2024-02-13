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

import CachedToken from '../../model/cachedToken';

describe('CachedToken', () => {
  const defaultInput = {
    decimals: 2,
    freeze_default: 0,
    kyc_default: 0,
    token_id: 100,
  };
  const defaultExpected = {
    decimals: 2,
    freezeDefault: 0,
    kycDefault: 0,
    tokenId: 100,
  };

  test('default', () => {
    expect(new CachedToken(defaultInput)).toEqual(defaultExpected);
  });

  describe('freeze_default', () => {
    const specs = [
      {
        name: 'frozen',
        input: {
          ...defaultInput,
          freeze_default: 1,
        },
        expected: {
          ...defaultExpected,
          freezeDefault: 1,
        },
      },
      {
        name: 'unfrozen',
        input: {
          ...defaultInput,
          freeze_default: 2,
        },
        expected: {
          ...defaultExpected,
          freezeDefault: 2,
        },
      },
      {
        name: 'null freeze_key expect NOT_APPLICABLE',
        input: {
          ...defaultInput,
          freeze_key: null,
        },
        expected: {
          ...defaultExpected,
          freezeDefault: 0,
        },
      },
      {
        name: 'has freeze_key expect FROZEN',
        input: {
          ...defaultInput,
          freeze_default: true,
          freeze_key: [1, 1],
        },
        expected: {
          ...defaultExpected,
          freezeDefault: 1,
        },
      },
      {
        name: 'has freeze_key expect UNFROZEN',
        input: {
          ...defaultInput,
          freeze_default: false,
          freeze_key: [1, 1],
        },
        expected: {
          ...defaultExpected,
          freezeDefault: 2,
        },
      },
    ];

    test.each(specs)('$name', ({input, expected}) => {
      expect(new CachedToken(input)).toEqual(expected);
    });
  });

  describe('kyc_default', () => {
    const kycKeyDefaultInput = {...defaultInput, kyc_key: null};
    delete kycKeyDefaultInput.kyc_default;

    const specs = [
      {
        name: 'revoked',
        input: {
          ...defaultInput,
          kyc_default: 2,
        },
        expected: {
          ...defaultExpected,
          kycDefault: 2,
        },
      },
      {
        name: 'null kyc_key expect NOT_APPLICABLE',
        input: kycKeyDefaultInput,
        expected: {
          ...defaultExpected,
          kycDefault: 0,
        },
      },
      {
        name: 'has freeze_key expect REVOKED',
        input: {
          ...kycKeyDefaultInput,
          kyc_key: [1, 1],
        },
        expected: {
          ...defaultExpected,
          kycDefault: 2,
        },
      },
    ];

    test.each(specs)('$name', ({input, expected}) => {
      expect(new CachedToken(input)).toEqual(expected);
    });
  });
});
