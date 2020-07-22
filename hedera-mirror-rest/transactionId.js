/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

const EntityId = require('./entityId');
const {InvalidArgumentError} = require("./errors/invalidArgumentError");

class TransactionId {
  constructor(entityId, validStartNs) {
    this.entityId = entityId;
    this.validStartNs = validStartNs;
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
    return this.validStartNs;
  }

  /**
   * Convert the transaction ID to a string in the format of "shard.realm.num-ssssssssss-nnnnnnnnn"
   * @returns {string}
   */
  toString() {
    const seconds = this.validStartNs.slice(0, 10);
    const nanos = this.validStartNs.slice(10);
    return `${this.entityId.toString()}-${seconds}-${nanos}`;
  }
}

/**
 * Construct transaction ID from string. The string must be in the format of "shard.realm.num-ssssssssss-nnnnnnnnn"
 * @param {string} transactionIdStr
 */
const fromString = transactionIdStr => {
    let txIdMatches = transactionIdStr.match(/(\d+)\.(\d+)\.(\d+)-(\d{10})-(\d{9})/);
    if (txIdMatches === null || txIdMatches.length !== 6) {
      logger.error(`TransactionId.fromString, invalid transaction ID string ${transactionIdStr}`);
      let message =
        'Invalid Transaction id. Please use "shard.realm.num-ssssssssss-nnnnnnnnn" ' +
        'format where ssss are 10 digits seconds and nnn are 9 digits nanoseconds';
      throw new InvalidArgumentError(message);
    }

    const entityId = EntityId.fromString(`${txIdMatches[1]}.${txIdMatches[2]}.${txIdMatches[3]}`);
    const validStartNs = `${txIdMatches[4]}${txIdMatches[5]}`;

    return new TransactionId(entityId, validStartNs);
};

module.exports = {
  fromString,
};
