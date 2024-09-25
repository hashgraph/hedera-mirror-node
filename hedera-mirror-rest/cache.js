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

import Redis from 'ioredis';
import config from './config';
import _ from 'lodash';
import {JSONParse, JSONStringify} from './utils';

const {redis: redisConfig} = config;
const {enabled, sentinel, uri} = redisConfig;

const createRedisConnection = () => {
  let connectionReady = false;

  const sentinelOptions = sentinel.enabled
    ? {
        name: sentinel.name,
        sentinelPassword: sentinel.password,
        sentinels: [{host: sentinel.host, port: sentinel.port}],
      }
    : {};

  const options = {
    commandTimeout: redisConfig.commandTimeout,
    connectTimeout: redisConfig.connectTimeout,
    enableAutoPipelining: true,
    enableOfflineQueue: true,
    enableReadyCheck: true,
    keepAlive: 30000,
    lazyConnect: !enabled,
    maxRetriesPerRequest: redisConfig.maxRetriesPerRequest,
    retryStrategy: (attempt) => {
      connectionReady = false;

      if (!enabled) {
        return null;
      }

      return Math.min(attempt * 2000, redisConfig.maxBackoff);
    },
    ...sentinelOptions,
  };
  const uriSanitized = uri.replaceAll(RegExp('(?<=//).*:.+@', 'g'), '***:***@');

  const redis = new Redis(uri, options)
    .on('connect', () => logger.info(`Connected to ${uriSanitized}`))
    .on('error', (err) => logger.error(`Error connecting to ${uriSanitized}: ${err.message}`))
    .on('ready', () => {
      setConfig('maxmemory', redisConfig.maxMemory);
      setConfig('maxmemory-policy', redisConfig.maxMemoryPolicy);
      connectionReady = true;
    });

  const setConfig = function (key, value) {
    redis.config('SET', key, value).catch((e) => logger.warn(`Unable to set Redis ${key} to ${value}: ${e.message}`));
  };

  return {
    getRedis: () => redis,
    isReady: () => connectionReady,
    stop: async () => redis.quit(),
  };
};

let redisConnection;
export class Cache {
  constructor() {
    // Instead when createRedisConnection() is IIFE it initializes too early for tests to influence environment/config.
    if (redisConnection === undefined) {
      redisConnection = createRedisConnection();
    }
  }

  async clear() {
    return redisConnection.getRedis().flushall();
  }

  async getSingleWithTtl(key) {
    if (!redisConnection.isReady()) {
      return undefined;
    }

    let valueWithTtl = undefined; // Cache miss to caller
    await redisConnection
      .getRedis()
      .multi()
      .ttl(key)
      .get(key)
      .exec(function (err, result) {
        if (err) {
          logger.warn(`Redis error during ttl/get: ${err.message}`);
        } else {
          // result is [[null, ttl], [null, value]], with value === null on cache miss.
          const rawValue = result[1][1];
          if (rawValue) {
            valueWithTtl = {ttl: result[0][1], value: JSONParse(rawValue)};
          }
        }
      });

    return valueWithTtl;
  }

  async setSingle(key, expiry, value) {
    if (!redisConnection.isReady()) {
      return undefined;
    }

    return redisConnection
      .getRedis()
      .setex(key, expiry, JSONStringify(value))
      .catch((err) => logger.warn(`Redis error during set: ${err.message}`));
  }

  async get(keys, loader, keyMapper = (k) => (k ? k.toString() : k)) {
    if (_.isEmpty(keys)) {
      return [];
    }
    if (!redisConnection.isReady()) {
      return loader(keys);
    }

    const buffers =
      (await redisConnection
        .getRedis()
        .mgetBuffer(_.map(keys, keyMapper))
        .catch((err) => logger.warn(`Redis error during mget: ${err.message}`))) || new Array(keys.length);
    const values = buffers.map((t) => JSONParse(t));

    let i = 0;
    const missingKeys = keys.filter(() => _.isNil(values[i++]));

    if (missingKeys.length > 0) {
      const missing = await loader(missingKeys);
      const newValues = [];
      let j = 0;

      missing.forEach((value) => {
        // Update missing values in Redis array
        for (; j < values.length; j++) {
          if (_.isNil(values[j])) {
            values[j] = value;
            newValues.push(keyMapper(keys[j]));
            newValues.push(JSONStringify(value));
            break;
          }
        }
      });

      redisConnection
        .getRedis()
        .mset(newValues)
        .catch((err) => logger.warn(`Redis error during mset: ${err.message}`));
    }

    if (logger.isDebugEnabled()) {
      const count = keys.length - missingKeys.length;
      logger.debug(`Redis returned ${count} of ${keys.length} keys`);
    }

    return values;
  }

  // NOTE: Stops the connection for all instances of this class
  async stop() {
    return redisConnection.stop();
  }
}
