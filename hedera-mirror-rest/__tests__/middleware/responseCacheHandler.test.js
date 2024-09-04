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

import {jest} from '@jest/globals';

import config from '../../config';
import {Cache} from '../../cache';
import {RedisContainer} from '@testcontainers/redis';
import {defaultBeforeAllTimeoutMillis} from '../integrationUtils';
import CachedApiResponse from '../../model/cachedApiResponse.js';
import {responseCacheCheckHandler, responseCacheUpdateHandler, setCache} from '../../middleware';
import {responseCacheKeyLabel} from '../../constants';

const CACHE_KEY_VALUE = 'cacheKey';

let redisContainer;
config.redis.enabled = true;
const cache = new Cache();
setCache(cache);

beforeAll(async () => {
  redisContainer = await new RedisContainer().withStartupTimeout(20000).start();
  config.redis.uri = `0.0.0.0:${redisContainer.getMappedPort(6379)}`;
  logger.info('Started Redis container');
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await redisContainer.stop({signal: 'SIGKILL', t: 5});
  logger.info('Stopped Redis container');
});

describe('Response cache check middleware', () => {
  let mockCacheKeyGenerator, mockNextMiddleware, mockRequest, mockResponse, responseData;

  beforeEach(() => {
    cache.clear();

    responseData = {accounts: [], links: {next: null}};
    mockNextMiddleware = jest.fn();
    mockRequest = {
      headers: [],
      ip: '127.0.0.1',
      method: 'GET',
      originalUrl: '/api/v1/accounts?account.id=gte:0.0.18&account.id=lt:0.0.21&limit=3',
      path: CACHE_KEY_VALUE,
      query: {'account.id': ['gte:0.0.18', 'lt:0.0.21'], limit: 3},
      requestStartTime: Date.now() - 5,
      route: {
        path: '/api/v1/accounts',
      },
    };
    mockResponse = {
      headers: [],
      locals: [],
      statusCode: 200,
      send: jest.fn(),
      set: jest.fn(),
      status: jest.fn(),
    };
  });

  test('Cache miss', async () => {
    await responseCacheCheckHandler(mockRequest, mockResponse, mockNextMiddleware);
    const cacheKey = mockResponse.locals[responseCacheKeyLabel];
    expect(cacheKey).toEqual(CACHE_KEY_VALUE);
    expect(await cache.getSingle(cacheKey)).toBeUndefined();
    expect(mockNextMiddleware).toBeCalled();
  });

  test('Cache hit', async () => {
    const body = {};

    const cachedResponse = new CachedApiResponse(mockResponse.status, mockResponse.headers, body);
    cache.setSingle(CACHE_KEY_VALUE, cachedResponse);
    await responseCacheCheckHandler(mockRequest, mockResponse, mockNextMiddleware);
    expect(mockNextMiddleware).not.toBeCalled();
  });
});
