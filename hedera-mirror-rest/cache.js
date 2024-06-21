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

export class Cache {
  constructor() {
    const {redis: redisConfig} = config;
    const {enabled, sentinel, uri} = redisConfig;
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
        this.ready = false;

        if (!enabled) {
          return null;
        }

        return Math.min(attempt * 2000, redisConfig.maxBackoff);
      },
      ...sentinelOptions,
    };
    const uriSanitized = uri.replaceAll(RegExp('(?<=//).*:.+@', 'g'), '***:***@');
    this.ready = false;

    this.redis = new Redis(uri, options)
      .on('connect', () => logger.info(`Connected to ${uriSanitized}`))
      .on('error', (err) => logger.error(`Error connecting to ${uriSanitized}: ${err.message}`))
      .on('ready', () => {
        this.#setConfig('maxmemory', redisConfig.maxMemory);
        this.#setConfig('maxmemory-policy', redisConfig.maxMemoryPolicy);
        this.ready = true;
      });
  }

  #setConfig(key, value) {
    this.redis
      .config('SET', key, value)
      .catch((e) => logger.warn(`Unable to set Redis ${key} to ${value}: ${e.message}`));
  }

  async clear() {
    return this.redis.flushall();
  }

  async get(keys, loader, keyMapper = (k) => (k ? k.toString() : k)) {
    if (!this.ready) {
      return loader(keys);
    }

    const mappedKeys = _.map(keys, keyMapper);
    const buffers = mappedKeys.length === 0 ? [] :
      (await this.redis
        .mgetBuffer(mappedKeys)
        .catch((err) => {
          logger.warn(`Redis error during mget: ${err.message}`)
        })) || new Array(keys.length);
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

      this.redis.mset(newValues).catch((err) => logger.warn(`Redis error during mset: ${err.message}`));
    }

    if (logger.isDebugEnabled()) {
      const count = keys.length - missingKeys.length;
      logger.debug(`Redis returned ${count} of ${keys.length} keys`);
    }

    return values;
  }

  async stop() {
    return this.redis.quit();
  }
}
