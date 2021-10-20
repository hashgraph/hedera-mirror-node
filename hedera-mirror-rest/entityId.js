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

const _ = require('lodash');
const {shard: systemShard} = require('./config');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');

// format: |0|15-bit shard|16-bit realm|32-bit num|
const numBits = 32n;
const numMask = 2n ** numBits - 1n;
const realmBits = 16n;
const realmMask = 2n ** realmBits - 1n;
const shardOffset = numBits + realmBits;
const maxEncodedId = 2n ** 63n - 1n;

class EntityId {
  constructor(shard, realm, num) {
    this.shard = shard;
    this.realm = realm;
    this.num = num;
  }

  /**
   * @returns {string} encoded id corresponding to this EntityId.
   */
  getEncodedId() {
    return this.num === null ? null : ((this.shard << shardOffset) | (this.realm << numBits) | this.num).toString();
  }

  /**
   * Converts the entity id to the 20-byte solidity address in hex with '0x' prefix
   */
  toSolidityAddress() {
    // shard, realm, and num take 4, 8, and 8 bytes respectively from the left
    return [
      '0x',
      this.shard.toString(16).padStart(8, '0'),
      this.realm.toString(16).padStart(16, '0'),
      this.num.toString(16).padStart(16, '0'),
    ].join('');
  }

  toString() {
    return this.num === null ? null : `${this.shard}.${this.realm}.${this.num}`;
  }
}

const isValidEntityId = (entityId) => {
  // Accepted forms: num, realm.num, or shard.realm.num
  return (typeof entityId === 'string' && /^(\d{1,10}\.){0,2}\d{1,10}$/.test(entityId)) || /^\d{1,10}$/.test(entityId);
};

/**
 * Creates EntityId from shard, realm, and num.
 *
 * @param {BigInt} shard
 * @param {BigInt} realm
 * @param {BigInt} num
 * @return {EntityId}
 */
const of = (shard, realm, num) => {
  return new EntityId(shard, realm, num);
};

/**
 * Converts encoded entity ID (BigInt, int, or string) to EntityId object.
 *
 * @param {BigInt|int|string} id
 * @param {boolean} isNullable
 * @return {EntityId}
 */
const fromEncodedId = (id, isNullable = false) => {
  if (_.isNil(id)) {
    if (isNullable) {
      return of(null, null, null);
    }

    throw new InvalidArgumentError('Null entity ID');
  }

  // Javascript's precision limit is 2^53 - 1. Precision needed to handle encoded ids is 2^63 - 1 (highest number
  // for java's long and postgres' bigint). Limit use of BigInt types to this function, everything returned by this
  // function should be normal JS number for compatibility.
  const message = `Invalid entity ID "${id}"`;
  if (!/^\d+$/.test(id)) {
    throw new InvalidArgumentError(message);
  }

  const encodedId = BigInt(id);
  if (encodedId < 0n || encodedId > maxEncodedId) {
    throw new InvalidArgumentError(message);
  }

  const num = encodedId & numMask;
  const shardRealm = encodedId >> numBits;
  const realm = shardRealm & realmMask;
  const shard = shardRealm >> realmBits;
  return of(shard, realm, num);
};

/**
 * Converts entity ID string to EntityId object. Supports 'shard.realm.num', 'realm.num', and 'num'.
 *
 * @param {string} entityIdStr
 * @param {string} paramName
 * @param {boolean} isNullable
 * @return {EntityId}
 */
const fromString = (entityIdStr, paramName = '', isNullable = false) => {
  const error = (message) =>
    paramName ? InvalidArgumentError.forParams(paramName) : new InvalidArgumentError(message);

  if (_.isNil(entityIdStr)) {
    if (isNullable) {
      return of(null, null, null);
    }

    throw error('Null entity ID');
  }

  if (!isValidEntityId(entityIdStr)) {
    throw error(`invalid entity ID string "${entityIdStr}"`);
  }

  const defaultShardRealm = [systemShard, '0'];
  const parts = entityIdStr.split('.');
  if (parts.length < 3) {
    parts.unshift(...defaultShardRealm.slice(0, 3 - parts.length));
  }

  return of(
    ...parts.map((part) => {
      const num = BigInt(part);
      if (num < 0n) {
        throw error(`invalid entity ID string "${entityIdStr}"`);
      }
      return num;
    })
  );
};

module.exports = {
  isValidEntityId,
  of,
  fromEncodedId,
  fromString,
};
