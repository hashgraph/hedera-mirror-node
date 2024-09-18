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

import {Range} from 'pg-range';

import {orderFilterValues} from './constants';
import config from './config';
import {isTestEnv, nowInNs, nsToSecNs} from './utils';

const queryConfig = config.query;

/**
 * If enabled in config, ensure the returned timestamp range is fully bound; contains both a begin
 * and end timestamp value. The provided Range is not modified. If changes are made a copy is returned.
 *
 * @param {Range} range timestamp range, typically based on query parameters. Note the bounds should be '[]'
 * @param {string} order the order in the http request
 * @return {Object} fully bound timestamp range and next timestamp
 */
const bindTimestampRange = async (range, order) => {
  if (!queryConfig.bindTimestampRange) {
    return {range};
  }

  const {maxTransactionsTimestampRangeNs} = queryConfig;
  const boundRange = Range(range?.begin ?? (await getFirstTransactionTimestamp()), range?.end ?? nowInNs(), '[]');
  if (boundRange.end - boundRange.begin + 1n <= maxTransactionsTimestampRangeNs) {
    return {range: boundRange};
  }

  let next;
  if (order === orderFilterValues.DESC) {
    next = boundRange.end - maxTransactionsTimestampRangeNs;
    boundRange.begin = next + 1n;
  } else {
    next = boundRange.begin + maxTransactionsTimestampRangeNs;
    boundRange.end = next - 1n;
  }

  return {range: boundRange, next: nsToSecNs(next)};
};

/**
 * Get the first transaction's consensus timestamp from the database. Note the db query runs once and the timestamp is
 * cached for subsequent calls.
 *
 * @return {Promise<bigint>} the first transaction's consensus timestamp
 */
const getFirstTransactionTimestamp = (() => {
  let timestamp;

  const func = async () => {
    if (timestamp === undefined) {
      const {rows} = await pool.queryQuietly(`select consensus_timestamp
                                              from transaction
                                              order by consensus_timestamp
                                              limit 1`);
      if (rows.length !== 1) {
        return 0n; // fallback to 0
      }

      timestamp = rows[0].consensus_timestamp;
      logger.info(`First transaction's consensus timestamp is ${timestamp}`);
    }

    return BigInt(timestamp);
  };

  if (isTestEnv()) {
    func.reset = () => (timestamp = undefined);
  }

  return func;
})();

export {bindTimestampRange};

const testExports = {};
if (isTestEnv()) {
  testExports.getFirstTransactionTimestamp = getFirstTransactionTimestamp;
}

export default testExports;
