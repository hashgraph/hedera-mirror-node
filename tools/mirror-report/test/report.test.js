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

import fetchMock, {manageFetchMockGlobally} from '@fetch-mock/jest';
import {jest} from '@jest/globals';
import console from 'console';
import {report} from '../src/report';

manageFetchMockGlobally(jest);

global.console = console;

describe('report', () => {
  test('single account', async () => {
    fetchMock.get("https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1000", 200, JSON.stringify({}));

    const json = await report({account: "0.0.1000", date: "2024-12-17", network: "testnet"});
    expect(fetchMock).toHaveFetched("https://testnet.mirrornode.hedera.com/api/v1/accounts/0.0.1000");
    expect(Array.isArray(json)).toEqual(true);
    expect(json.length).toEqual(0);
  });
});
