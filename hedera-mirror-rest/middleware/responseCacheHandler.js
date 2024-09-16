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

import {Cache} from '../cache';
import CachedApiResponse from '../model/cachedApiResponse';
import {responseBodyLabel, responseCacheKeyLabel} from '../constants';
import _ from 'lodash';

const CACHE_CONTROL_HEADER = 'cache-control';
const CONTENT_ENCODING_HEADER = 'content-encoding';
const ETAG_HEADER = 'etag';
const VARY_HEADER = 'vary';
const DEFAULT_REDIS_EXPIRY = 1;

let cache = new Cache('apiResponse:');

// Response middleware that checks for and returns cached response.
const responseCacheCheckHandler = async (req, res, next) => {
  const startTime = Date.now();
  const responseCacheKey = cacheKeyGenerator(req);
  const cachedTtlAndValue = await cache.getSingleWithTtl(responseCacheKey);

  if (cachedTtlAndValue) {
    const {ttl: redisTtl, value: redisValue} = cachedTtlAndValue;
    const cachedResponse = Object.assign(new CachedApiResponse(), redisValue);
    const statusCode = cachedResponse.status;

    res.set(cachedResponse.headers);
    res.set(CACHE_CONTROL_HEADER, `public, max-age=${redisTtl}`);
    res.status(statusCode);
    res.send(cachedResponse.body);

    const elapsed = Date.now() - startTime;
    logger.info(
      `${req.ip} ${req.method} ${req.originalUrl} from cache (ttl: ${redisTtl}) in ${elapsed} ms: ${statusCode}`
    );
  } else {
    res.locals[responseCacheKeyLabel] = responseCacheKey;
  }
  next();
};

// Response middleware that caches the completed response.
const responseCacheUpdateHandler = async (req, res, next) => {
  const responseCacheKey = res.locals[responseCacheKeyLabel];
  // Cache only positive outcomes
  if (responseCacheKey && res.statusCode === 200) {
    const cacheControlHeaderExpiry = getCacheControlExpiry(res.getHeaders()[CACHE_CONTROL_HEADER]);
    const redisExpiry = _.isNull(cacheControlHeaderExpiry) ? DEFAULT_REDIS_EXPIRY : cacheControlHeaderExpiry;
    if (redisExpiry > 0) {
      const headers = res.getHeaders();
      // Delete headers that will be re-computed when response later served by cache hit.
      delete headers[CACHE_CONTROL_HEADER];
      delete headers[CONTENT_ENCODING_HEADER];
      delete headers[ETAG_HEADER];
      delete headers[VARY_HEADER];

      const cachedResponse = new CachedApiResponse(res.statusCode, headers, res.locals[responseBodyLabel]);
      await cache.setSingle(responseCacheKey, redisExpiry, cachedResponse);
    }
  }

  next();
};

/*
 * Generate the cache key to access Redis. While Accept-Encoding is specified in the API response Vary
 * header, and therefore that request header value should be used as part of the cache key, the cache
 * implementation stores the response body as the original JSON object without any encoding applied. Thus it
 * is the same regardless of the accept encoding specified, and chosen by the compression middleware.
 *
 * Current key format:
 *
 *   path?query - In the future, this will utilize Edwin's request normalizer (9113).
 */
const cacheKeyGenerator = (req) => {
  return req.originalUrl;
};

const getCacheControlExpiry = (headerValue) => {
  if (headerValue) {
    const maxAge = headerValue.match(/^.*max-age=(\d+)/);
    if (maxAge && maxAge.length === 2) {
      return parseInt(maxAge[1], 10);
    }
  }
  return undefined;
};

// For testing
const setCache = (cacheToUse) => {
  cache = cacheToUse;
};

export {cacheKeyGenerator, responseCacheCheckHandler, responseCacheUpdateHandler, setCache};
