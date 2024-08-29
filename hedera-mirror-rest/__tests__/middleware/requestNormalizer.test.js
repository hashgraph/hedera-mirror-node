/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import {normalizeRequestQueryParams} from '../../middleware/requestNormalizer.js';

describe('request normalizer', () => {
  const testSpecs = [
    {
      input: {
        path: '/api/v1/blocks',
        query: {},
      },
      expected: 'limit=25&order=desc',
    },
    {
      input: {
        path: '/api/v1/blocks',
        query: {
          order: 'asc',
          unknown: '3',
          limit: '5',
        },
      },
      expected: 'limit=5&order=asc&unknown=3',
    },
    {
      input: {
        path: '/api/v1/accounts',
        query: {
          'account.id': ['gt:0.0.20', 'lt:0.0.21'],
          limit: '3',
        },
      },
      expected: 'account.id=gt:0.0.20&account.id=lt:0.0.21&balance=true&limit=3&order=asc',
    },
    {
      input: {
        path: undefined,
        query: {
          'outOfOrder:': '1',
          'account.id': ['lt:0.0.21', 'gt:0.0.20'],
        },
      },
      expected: 'account.id=gt:0.0.20&account.id=lt:0.0.21&outOfOrder:=1',
    },
    {
      input: {
        path: '/api/v1/accounts',
        query: {
          limit: ['2', '4'],
        },
      },
      expected: 'balance=true&limit=2&limit=4&order=asc',
    },
    {
      input: {
        path: '/api/v1/contracts/results/logs',
        query: {
          topic0: 'A',
          timestamp: '1639010141.000000000',
          index: 'lt:1',
        },
      },
      expected: 'index=lt:1&limit=25&order=desc&timestamp=1639010141.000000000&topic0=A',
    },
    {
      input: {
        path: '/api/v1/accounts/0.0.1001/nfts',
        query: {
          'token.id': 'gte:1500',
          serialnumber: 'gte:2',
          'spender.id': 'gte:2004',
          order: 'asc',
        },
      },
      expected: 'limit=25&order=asc&serialnumber=gte:2&spender.id=gte:2004&token.id=gte:1500',
    },
    {
      input: {
        path: '/api/v1/tokens/1500/nfts/2/transactions',
        query: {
          timestamp: 'gte:1234567890.000000005',
          order: 'asc',
        },
      },
      expected: 'limit=25&order=asc&timestamp=gte:1234567890.000000005',
    },
    {
      input: {
        path: '/api/v1/tokens/1500/nfts/2/transactions',
        query: {},
      },
      expected: 'limit=25&order=desc',
    },
    {
      input: {
        path: '/api/v1/tokens/0.0.1500/nfts/2/transactions',
        query: {},
      },
      expected: 'limit=25&order=desc',
    },
    {
      input: {
        path: '/api/v1/accounts/0.0.1001/nfts',
        query: {
          'token.id': 'gte:1500',
          serialnumber: 'gte:2',
          'spender.id': 'gte:2004',
        },
      },
      expected: 'limit=25&order=desc&serialnumber=gte:2&spender.id=gte:2004&token.id=gte:1500',
    },
  ];

  testSpecs.forEach((spec) => {
    test(spec.input.path, () => {
      expect(normalizeRequestQueryParams(spec.input.path, spec.input.query)).toEqual(spec.expected);
    });
  });
});
