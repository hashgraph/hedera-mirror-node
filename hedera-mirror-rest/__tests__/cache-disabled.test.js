/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import config from '../config';
import {Cache} from '../cache';
import {defaultBeforeAllTimeoutMillis} from './integrationUtils';

let cache;

beforeAll(async () => {
  config.redis.enabled = false;
  logger.info('Redis disabled');
}, defaultBeforeAllTimeoutMillis);

beforeEach(async () => {
  cache = new Cache();
});

const loader = (keys) => keys.map((key) => `v${key}`);
const keyMapper = (key) => `k${key}`;

describe('Redis disabled', () => {
  test('get', async () => {
    const values = await cache.get(['1', '2', '3'], loader, keyMapper);
    expect(values).toEqual(['v1', 'v2', 'v3']);
  });

  test('getSingleWithTtl', async () => {
    const key = 'myKey';
    const value = await cache.getSingleWithTtl(key);
    expect(value).toBeUndefined();
    const setResult = await cache.setSingle(key, 5, 'someValue');
    expect(setResult).toBeUndefined();
  });
});
