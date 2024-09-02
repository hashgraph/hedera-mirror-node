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
import CachedApiResponse from '../model/cachedApiResponse.js';
import {requestStartTime, responseBodyLabel, responseCacheKeyLabel} from '../constants.js';

const AGE_HEADER_NAME = 'age';
const CACHE_BYPASS_HEADER_VALUE = 'no-cache';
const CACHE_CONTROL_HEADER_NAME = 'cache-control';
const PRAGMA_HEADER_NAME = 'pragma';

let cache = new Cache();
let cacheKeyGenerator = (req) => {
  return req.path; // Interact with Edwin's code
};

// Response middleware that checks for and returns cached response.
const responseCacheCheckHandler = async (req, res, next) => {
  const cacheControl = req.headers[CACHE_CONTROL_HEADER_NAME];
  const pragma = req.headers[PRAGMA_HEADER_NAME];
  if (pragma === CACHE_BYPASS_HEADER_VALUE || cacheControl === CACHE_BYPASS_HEADER_VALUE) {
    logger.warn(`${req.ip} ${req.method} ${req.originalUrl} attempted cache bypass`);
  }

  const responseCacheKey = cacheKeyGenerator(req);
  const rawCachedResponse = await cache.getSingle(responseCacheKey);

  if (rawCachedResponse) {
    logger.debug(`Cache hit: ${responseCacheKey}`);

    const cachedResponse = Object.assign(new CachedApiResponse(), rawCachedResponse);
    const age = Date.now() - cachedResponse.cacheTime;
    const code = cachedResponse.status;
    res.set(cachedResponse.headers);
    res.set(AGE_HEADER_NAME, age);
    res.status(code);
    res.send(cachedResponse.body);

    const startTime = res.locals[requestStartTime];
    const elapsed = startTime ? Date.now() - startTime : 0;
    logger.info(`${req.ip} ${req.method} ${req.originalUrl} from cache in ${elapsed} ms: ${code}`);
  } else {
    logger.debug(`Cache miss: ${responseCacheKey}`);
    res.locals[responseCacheKeyLabel] = responseCacheKey;
    next();
  }
};

// Response middleware that caches the completed response.
const responseCacheUpdateHandler = async (req, res, next) => {
  const responseCacheKey = res.locals[responseCacheKeyLabel];
  if (responseCacheKey) {
    const cacheControlHeader = res.getHeaders()[CACHE_CONTROL_HEADER_NAME];
    const expiry = getCacheControlExpiry(cacheControlHeader);
    if (expiry) {
      const cachedResponse = new CachedApiResponse(res.status, res.getHeaders(), res.locals[responseBodyLabel]);
      cache.setSingle(responseCacheKey, cachedResponse, expiry);
    }
  }

  next();
};

// For testing
const setCache = (cacheToUse) => {
  cache = cacheToUse;
};

const setCacheKeyGenerator = (generatorFunction) => {
  cacheKeyGenerator = generatorFunction;
};

// TODO don't need this no more
const getCacheControlExpiry = (headerValue) => {
  if (headerValue) {
    const maxAge = headerValue.match(/^.*max-age=(\d+)/);
    if (maxAge && maxAge.length === 2) {
      return parseInt(maxAge[1], 10);
    }
  }
  return undefined;
};

export {responseCacheCheckHandler, responseCacheUpdateHandler, setCache, setCacheKeyGenerator};
