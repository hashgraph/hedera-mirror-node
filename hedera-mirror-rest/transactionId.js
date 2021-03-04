/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

'use strict';

const long = require('long');
const EntityId = require('./entityId');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');
const {TransactionId: SdkTransactionId} = require('@hashgraph/sdk');

class TransactionId {
  constructor(entityId, validStartSeconds, validStartNanos) {
    this.entityId = entityId;
    this.validStartSeconds = validStartSeconds;
    this.validStartNanos = validStartNanos;
  }

  /**
   * @returns {EntityId} entityId of the transaction ID
   */
  getEntityId() {
    return this.entityId;
  }

  /**
   * @returns {string} validStartNs of the transaction ID
   */
  getValidStartNs() {
    return `${this.validStartSeconds}${String(this.validStartNanos).padStart(9, '0')}`;
  }

  /**
   * Convert to hedera-sdk-js TransactionId
   * @returns {SdkTransactionId}
   */
  toSdkTransactionId() {
    return SdkTransactionId.fromString(`${this.entityId.toString()}@${this.validStartSeconds}.${this.validStartNanos}`);
  }

  /**
   * Convert the transaction ID to a string in the format of "shard.realm.num-ssssssssss-nnnnnnnnn"
   * @returns {string}
   */
  toString() {
    return `${this.entityId.toString()}-${this.validStartSeconds}-${this.validStartNanos}`;
  }
}

/**
 * Construct transaction ID from string. The string must be in the format of "shard.realm.num-ssssssssss-nnnnnnnnn"
 * @param {string} transactionIdStr
 */
const fromString = (transactionIdStr) => {
  const txIdMatches = transactionIdStr.match(/^(\d+)\.(\d+)\.(\d+)-(\d{1,19})-(\d{1,9})$/);
  const message =
    'Invalid Transaction id. Please use "shard.realm.num-sss-nnn" format where sss are seconds and nnn are nanoseconds';
  if (txIdMatches === null || txIdMatches.length !== 6) {
    throw new InvalidArgumentError(message);
  }

  const entityId = EntityId.fromString(`${txIdMatches[1]}.${txIdMatches[2]}.${txIdMatches[3]}`);
  const seconds = long.fromString(txIdMatches[4]);
  const nanos = parseInt(txIdMatches[5], 10);
  if (seconds.lessThan(0)) {
    throw new InvalidArgumentError(message);
  }

  return new TransactionId(entityId, seconds, nanos);
};

module.exports = {
  fromString,
};
