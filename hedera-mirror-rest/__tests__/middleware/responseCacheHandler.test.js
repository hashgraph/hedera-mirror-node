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
import {cacheKeyGenerator, responseCacheCheckHandler, responseCacheUpdateHandler, setCache} from '../../middleware';
import {responseCacheKeyLabel} from '../../constants';
import {JSONStringify} from '../../utils.js';

let cache;
let redisContainer;

beforeAll(async () => {
  config.redis.enabled = true;
  redisContainer = await new RedisContainer().withStartupTimeout(20000).start();
  logger.info('Started Redis container');
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await redisContainer.stop({signal: 'SIGKILL', t: 5});
  logger.info('Stopped Redis container');
});

beforeEach(async () => {
  config.redis.uri = `0.0.0.0:${redisContainer.getMappedPort(6379)}`;
  cache = new Cache();
  setCache(cache);
  await cache.clear();
});

afterEach(async () => {
  await cache.stop();
});

describe('Response cache middleware', () => {
  let mockNextMiddleware, mockRequest, mockResponse, responseData;

  beforeEach(() => {
    responseData = {accounts: [], links: {next: null}};
    mockNextMiddleware = jest.fn();
    mockRequest = {
      headers: {'accept-encoding': 'gzip'},
      ip: '127.0.0.1',
      method: 'GET',
      originalUrl: '/api/v1/accounts?account.id=gte:0.0.18&account.id=lt:0.0.21&limit=3',
      query: {'account.id': ['gte:0.0.18', 'lt:0.0.21'], limit: 3},
      requestStartTime: Date.now() - 5,
      route: {
        path: '/api/v1/accounts',
      },
    };
    mockResponse = {
      headers: [],
      locals: [],
      removeHeader: jest.fn(),
      send: jest.fn(),
      set: jest.fn(),
      status: jest.fn(),
    };
  });

  describe('Cache check front-end', () => {
    test('Cache miss', async () => {
      // The cache is empty, thus a cache miss is expected.
      await responseCacheCheckHandler(mockRequest, mockResponse, mockNextMiddleware);

      // Middleware must provide cache key in locals[] to be utilized downstream.
      const expectedCacheKey = cacheKeyGenerator(mockRequest);
      const cacheKey = mockResponse.locals[responseCacheKeyLabel];
      expect(cacheKey).toEqual(expectedCacheKey);

      // Middleware must not have handled the response directly.
      expect(mockResponse.removeHeader).not.toBeCalled();
      expect(mockResponse.send).not.toBeCalled();
      expect(mockResponse.set).not.toBeCalled();
      expect(mockResponse.status).not.toBeCalled();

      // Middleware must invoke provided next() function to continue normal API request processing.
      expect(mockNextMiddleware).toBeCalled();
    });

    test('Cache miss with bypass attempts', async () => {
      const logSpy = jest.spyOn(logger, 'debug');
      logger.level = 'debug';

      mockRequest.headers['pragma'] = 'no-cache';

      // The cache is empty, thus a cache miss is expected.
      await responseCacheCheckHandler(mockRequest, mockResponse, mockNextMiddleware);

      mockRequest.headers['pragma'] = undefined;
      mockRequest.headers['cache-control'] = 'no-cache';

      // The cache is empty, thus a cache miss is expected.
      await responseCacheCheckHandler(mockRequest, mockResponse, mockNextMiddleware);

      expect(logSpy).toHaveBeenNthCalledWith(1, expect.stringContaining('attempted cache bypass'));
      expect(logSpy).toHaveBeenNthCalledWith(2, expect.stringContaining('attempted cache bypass'));
      logSpy.mockRestore();
    });

    test('Cache hit', async () => {
      const cachedBody = JSONStringify({a: 'b'});
      const cachedHeaders = {
        'cache-control': 'public, max-age=60',
        'content-encoding': 'gzip',
        etag: 'W/"5c35-Gld7z2oXbbJUpCcZpRCrhYePM04',
        vary: 'accept-encoding',
      };
      const cachedStatusCode = 200;

      // Place the expected response in the cache.
      const cachedResponse = new CachedApiResponse(cachedStatusCode, cachedHeaders, cachedBody);
      const cacheKey = cacheKeyGenerator(mockRequest);
      await cache.setSingle(cacheKey, 30, cachedResponse);

      // Cache hit is expected, and the middleware must handle the response.
      await responseCacheCheckHandler(mockRequest, mockResponse, mockNextMiddleware);
      expect(mockResponse.removeHeader).toBeCalledWith('content-encoding');
      expect(mockResponse.send).toBeCalledWith(cachedBody);
      expect(mockResponse.set).toHaveBeenNthCalledWith(1, cachedHeaders);
      expect(mockResponse.set).toHaveBeenNthCalledWith(2, 'cache-control', expect.stringContaining('public, max-age='));
      expect(mockResponse.status).toBeCalledWith(cachedStatusCode);

      // Middleware must invoke provided next() function to continue normal API request processing.
      expect(mockNextMiddleware).toBeCalled();
    });
  });

  describe('Cache update back-end', () => {
    test('No cache key in locals', async () => {
      const cacheKey = cacheKeyGenerator(mockRequest);

      // No cache key in locals means don't cache the response
      await responseCacheUpdateHandler(mockRequest, mockResponse, mockNextMiddleware);
      const cachedResponse = await cache.getSingleWithTtl(cacheKey);
      expect(cachedResponse).toBeUndefined();

      // Middleware must invoke provided next() function to continue normal API request processing.
      expect(mockNextMiddleware).toBeCalled();
    });

    test('Do not cache negative results', async () => {
      const cacheKey = cacheKeyGenerator(mockRequest);
      mockResponse.locals[responseCacheKeyLabel] = cacheKey;
      mockResponse.statusCode = 503;

      await responseCacheUpdateHandler(mockRequest, mockResponse, mockNextMiddleware);
      const cachedResponse = await cache.getSingleWithTtl(cacheKey);
      expect(cachedResponse).toBeUndefined();

      // Middleware must invoke provided next() function to continue normal API request processing.
      expect(mockNextMiddleware).toBeCalled();
    });
  });
});
