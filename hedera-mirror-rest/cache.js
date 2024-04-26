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
import {JSONParse, JSONStringify} from './utils.js';

export class Cache {
  constructor() {
    const enabled = config?.redis?.enabled;
    const uri = config?.redis?.uri;
    const uriSanitized = uri.replaceAll(RegExp('(?<=//).*:.+@', 'g'), '***:***@');
    this.ready = false;

    this.redis = new Redis(uri, {
      commandTimeout: config?.redis?.commandTimeout,
      connectTimeout: config?.redis?.connectTimeout,
      enableAutoPipelining: true,
      enableOfflineQueue: true,
      enableReadyCheck: true,
      keepAlive: 30000,
      lazyConnect: !enabled,
      maxRetriesPerRequest: config?.redis?.maxRetriesPerRequest,
      retryStrategy: (attempt) => {
        this.ready = false;

        if (!enabled) {
          return null;
        }

        return Math.min(attempt * 2000, config?.redis?.maxBackoff);
      },
    });

    this.redis.on('connect', () => logger.info(`Connected to ${uriSanitized}`));
    this.redis.on('error', (err) => logger.error(`Error connecting to ${uriSanitized}: ${err.message}`));
    this.redis.on('ready', () => {
      this.#setConfig('maxmemory', config?.redis?.maxMemory);
      this.#setConfig('maxmemory-policy', config?.redis?.maxMemoryPolicy);
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

    const buffers =
      (await this.redis
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

      this.redis.mset(newValues).catch((err) => logger.warn(`Redis error during mset: ${err.message}`));
    }

    if (logger.isDebugEnabled()) {
      const count = keys.length - missingKeys.length;
      logger.debug(`Redis returned ${count} of ${keys.length} keys`);
    }

    return values;
  }
}
