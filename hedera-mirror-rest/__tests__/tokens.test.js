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

const request = require('supertest');
const server = require('../server');
const {runTestSuite} = require('./balancesCommon');

beforeAll(async () => {
  jest.setTimeout(1000);
});

afterAll(() => {});

const getTokenSupplyDistributionEndpoint = (tokenId) => `/api/v1/tokens/${tokenId}/balances`;

describe('Token supply distribution', () => {
  runTestSuite(getTokenSupplyDistributionEndpoint('0.20.1'), 'Token supply distribution');

  describe('Invalid token ID', () => {
    const expectedErrorMessage = {
      _status: {
        messages: [{message: 'Invalid parameter: token.id'}],
      },
    };

    ['-1', 'a', '0.0', '0.0.0.1', '0 0 1'].forEach((badTokenId) => {
      test(badTokenId, async () => {
        const response = await request(server).get(getTokenSupplyDistributionEndpoint(badTokenId));

        expect(response.status).toEqual(400);
        expect(JSON.parse(response.text)).toEqual(expectedErrorMessage);
      });
    });
  });
});
