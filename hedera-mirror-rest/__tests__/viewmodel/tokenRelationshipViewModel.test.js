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

import {TokenFreezeStatus, TokenKycStatus} from '../../model';
import {TokenRelationshipViewModel} from '../../viewmodel';

describe('TokenRelationshipViewModel', () => {
  const specs = [
    {
      name: 'simple',
      input: {
        automaticAssociation: false,
        balance: 10,
        createdTimestamp: 100111222333,
        decimals: 2,
        freezeStatus: 0,
        kycStatus: 0,
        tokenId: 100,
      },
      expected: {
        automatic_association: false,
        balance: 10,
        created_timestamp: '100.111222333',
        decimals: 2,
        freeze_status: new TokenFreezeStatus(0),
        kyc_status: new TokenKycStatus(0),
        token_id: '0.0.100',
      },
    },
    {
      name: 'nullable fields',
      input: {
        automaticAssociation: null,
        balance: 0,
        createdTimestamp: null,
        decimals: null,
        freezeStatus: null,
        kycStatus: null,
        tokenId: 100,
      },
      expected: {
        automatic_association: null,
        balance: 0,
        created_timestamp: null,
        decimals: null,
        freeze_status: null,
        kyc_status: null,
        token_id: '0.0.100',
      },
    },
  ];

  test.each(specs)('$name', ({input, expected}) => {
    expect(new TokenRelationshipViewModel(input)).toEqual(expected);
  });
});
