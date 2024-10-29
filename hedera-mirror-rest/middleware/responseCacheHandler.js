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
import {gzipSync, unzipSync} from 'zlib';
import {httpStatusCodes, requestStartTime, responseBodyLabel, responseCacheKeyLabel} from '../constants';
import config from '../config.js';
import crypto from 'crypto';

const CACHE_CONTROL_HEADER = 'cache-control';
const CONTENT_ENCODING_HEADER = 'content-encoding';
const ETAG_HEADER = 'etag';
const CONDITIONAL_HEADER = 'if-none-match';
const VARY_HEADER = 'vary';
const DEFAULT_REDIS_EXPIRY = 1;

let cache = new Cache();

// Response middleware that checks for and returns cached response.
const responseCacheCheckHandler = async (req, res, next) => {
  const startTime = res.locals[requestStartTime] || Date.now();
  const responseCacheKey = cacheKeyGenerator(req);
  const cachedTtlAndValue = await cache.getSingleWithTtl(responseCacheKey);

  if (cachedTtlAndValue) {
    const {ttl: redisTtl, value: redisValue} = cachedTtlAndValue;
    const cachedResponse = Object.assign(new CachedApiResponse(), redisValue);
    const conditionalHeader = req.get(CONDITIONAL_HEADER);
    const clientCached = conditionalHeader && conditionalHeader === cachedResponse.headers[ETAG_HEADER]; // 304
    const statusCode = clientCached ? httpStatusCodes.UNMODIFIED.code : cachedResponse.statusCode;
    const isHead = req.method === 'HEAD';

    cachedResponse.headers[CACHE_CONTROL_HEADER] = `public, max-age=${redisTtl}`;
    res.set(cachedResponse.headers);
    res.status(statusCode);

    if (isHead || clientCached) {
        res.set(cachedResponse.headers);
        res.status(statusCode);
        res.end();
    } else {
      if (cachedResponse.compressed) {
        cachedResponse.body = Buffer.from(cachedResponse.body);
        const acceptsGzip = req.get('accept-encoding')?.includes('gzip');
        if (!acceptsGzip) {
          cachedResponse.body = unzipSync(cachedResponse.body).toString();
        } else {
          cachedResponse.headers[CONTENT_ENCODING_HEADER] = 'gzip';
        }
      }
      res.send(cachedResponse.body);
    }

    const elapsed = Date.now() - startTime;
    logger.info(
      `${req.ip} ${req.method} ${req.originalUrl} from cache (ttl: ${redisTtl}) in ${elapsed} ms: ${statusCode}`
    );
  } else {
    res.locals[responseCacheKeyLabel] = responseCacheKey;
  }
};

// Response middleware that caches the completed response.
const responseCacheUpdateHandler = async (req, res, next) => {
  const compressionEnabled = config.cache.response.compress;
  const responseCacheKey = res.locals[responseCacheKeyLabel];
  const responseBody = res.locals[responseBodyLabel];
  const isSuccessfulResponse = res.statusCode === httpStatusCodes.UNMODIFIED.code || httpStatusCodes.isSuccess(res.statusCode);

  if (responseBody && responseCacheKey && isSuccessfulResponse) {
    const ttl = getCacheControlExpiryOrDefault(res.getHeaders()[CACHE_CONTROL_HEADER]);
    if (ttl > 0) {
      const headers = res.getHeaders();

      // Delete headers that will be re-computed when response later served by cache hit.
      delete headers[CACHE_CONTROL_HEADER];

      const body = compressionEnabled ? gzipSync(responseBody) : responseBody;
      const cachedResponse = new CachedApiResponse(res.locals.statusCode, headers, body, compressionEnabled);

      await cache.setSingle(responseCacheKey, ttl, cachedResponse);
    }
  }
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
  return crypto.createHash('md5').update(req.originalUrl).digest('hex');
};

const getCacheControlExpiryOrDefault = (headerValue) => {
  if (headerValue) {
    const maxAge = headerValue.match(/^.*max-age=(\d+)/);
    if (maxAge && maxAge.length === 2) {
      return parseInt(maxAge[1], 10);
    }
  }

  return DEFAULT_REDIS_EXPIRY;
};

// For testing
const setCache = (cacheToUse) => {
  cache = cacheToUse;
};

export {cacheKeyGenerator, responseCacheCheckHandler, responseCacheUpdateHandler, setCache};
