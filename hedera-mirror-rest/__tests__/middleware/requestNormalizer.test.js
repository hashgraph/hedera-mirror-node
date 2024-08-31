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

import {normalizeRequestQueryParams} from '../../middleware/requestNormalizer.js';

describe('request normalizer', () => {
  const testSpecs = [
    {
      input: {
        path: '/api/v1/blocks',
        query: {},
      },
      expected: '/api/v1/blocks?limit=25&order=desc',
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
      expected: '/api/v1/blocks?limit=5&order=asc&unknown=3',
    },
    {
      input: {
        path: '/api/v1/accounts',
        query: {
          'account.id': ['gt:0.0.20', 'lt:0.0.21'],
          limit: '3',
        },
      },
      expected: '/api/v1/accounts?account.id=gt:0.0.20&account.id=lt:0.0.21&balance=true&limit=3&order=asc',
    },
    {
      input: {
        path: undefined,
        query: {
          'outOfOrder:': '1',
          'account.id': ['lt:0.0.21', 'gt:0.0.20'],
        },
      },
      // Query parameters that are arrays are not sorted if no path is provided
      expected: 'undefined?account.id=lt:0.0.21&account.id=gt:0.0.20&outOfOrder:=1',
    },
    {
      input: {
        path: undefined,
        query: undefined,
      },
      expected: undefined,
    },
    {
      input: {
        path: '/api/v1/accounts',
        query: {
          limit: ['2', '4'],
        },
      },
      expected: '/api/v1/accounts?balance=true&limit=2&limit=4&order=asc',
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
      expected: '/api/v1/contracts/results/logs?index=lt:1&limit=25&order=desc&timestamp=1639010141.000000000&topic0=A',
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
      expected:
        '/api/v1/accounts/0.0.1001/nfts?limit=25&order=asc&serialnumber=gte:2&spender.id=gte:2004&token.id=gte:1500',
    },
    {
      input: {
        path: '/api/v1/tokens/1500/nfts/2/transactions',
        query: {
          timestamp: 'gte:1234567890.000000005',
          order: 'asc',
        },
      },
      expected: '/api/v1/tokens/1500/nfts/2/transactions?limit=25&order=asc&timestamp=gte:1234567890.000000005',
    },
    {
      input: {
        path: '/api/v1/tokens/1500/nfts/2/transactions',
        query: {},
      },
      expected: '/api/v1/tokens/1500/nfts/2/transactions?limit=25&order=desc',
    },
    {
      input: {
        path: '/api/v1/tokens/0.0.1500/nfts/2/transactions',
        query: {},
      },
      expected: '/api/v1/tokens/0.0.1500/nfts/2/transactions?limit=25&order=desc',
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
      expected:
        '/api/v1/accounts/0.0.1001/nfts?limit=25&order=desc&serialnumber=gte:2&spender.id=gte:2004&token.id=gte:1500',
    },
    {
      input: {
        path: '/api/v1/contracts/62cf9068fed962cf9aaabbb962cf9068fed9dddd',
        query: {},
      },
      expected: '/api/v1/contracts/62cf9068fed962cf9aaabbb962cf9068fed9dddd',
    },
    {
      input: {
        path: '/api/v1/contracts/62cf9068fed962cf9aaabbb962cf9068fed9dddd/results',
        query: {
          limit: '3',
        },
      },
      expected: '/api/v1/contracts/62cf9068fed962cf9aaabbb962cf9068fed9dddd/results?internal=false&limit=3&order=desc',
    },
    {
      input: {
        path: '/api/v1/topics/7/messages',
        query: {},
      },
      expected: '/api/v1/topics/7/messages?limit=25&order=desc',
    },
  ];

  testSpecs.forEach((spec) => {
    test(spec.input.path, () => {
      expect(normalizeRequestQueryParams(spec.input.path, spec.input.query)).toEqual(spec.expected);
    });
  });
});
