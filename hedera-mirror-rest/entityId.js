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
const memoize = require('memoizee');

const {
  cache: {entityId: entityIdCacheConfig},
  shard: systemShard,
} = require('./config');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');

// format: |0|15-bit shard|16-bit realm|32-bit num|
const numBits = 32n;
const numMask = 2n ** numBits - 1n;
const maxNum = 2n ** numBits - 1n;

const realmBits = 16n;
const realmMask = 2n ** realmBits - 1n;
const maxRealm = 2n ** realmBits - 1n;

const shardBits = 15n;
const shardOffset = numBits + realmBits;
const maxShard = 2n ** shardBits - 1n;

const maxEncodedId = 2n ** 63n - 1n;

class EntityId {
  constructor(shard, realm, num) {
    this.shard = shard;
    this.realm = realm;
    this.num = num;
  }

  /**
   * @returns {string|null} encoded id corresponding to this EntityId.
   */
  getEncodedId() {
    if (this.encodedId === undefined) {
      this.encodedId =
        this.num === null ? null : ((this.shard << shardOffset) | (this.realm << numBits) | this.num).toString();
    }
    return this.encodedId;
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
  // Accepted forms: shard.realm.num, realm.num, or encodedId
  return (typeof entityId === 'string' && /^(\d{1,5}\.){1,2}\d{1,10}$/.test(entityId)) || /^\d{1,19}$/.test(entityId);
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

const nullEntityId = of(null, null, null);
const nullEntityIdError = new InvalidArgumentError('Null entity ID');

/**
 * Checks if the id is null. When null, returns the nullEntityId if allowed, otherwise throws error; When not
 * null, returns undefined
 * @param {BigInt|int|string} id
 * @param {boolean} isNullable
 * @return {EntityId}
 */
const checkNullId = (id, isNullable) => {
  let entityId;
  if (_.isNil(id)) {
    if (!isNullable) {
      throw nullEntityIdError;
    }
    entityId = nullEntityId;
  }

  return entityId;
};

/**
 * Parses shard, realm, num from encoded ID string.
 * @param {string} id
 * @param {Error} error
 * @return {bigint[3]}
 */
const parseFromEncodedId = (id, error) => {
  // encoded ID string
  const encodedId = BigInt(id);
  if (encodedId > maxEncodedId) {
    throw error;
  }

  const num = encodedId & numMask;
  const shardRealm = encodedId >> numBits;
  const realm = shardRealm & realmMask;
  const shard = shardRealm >> realmBits;
  return [shard, realm, num];
};

/**
 * Parses shard, realm, num from entity ID string, can be shard.realm.num or realm.num.
 * @param {string} id
 * @return {bigint[3]}
 */
const parseFromString = (id) => {
  const parts = id.split('.');
  if (parts.length < 3) {
    parts.unshift(systemShard);
  }

  return parts.map((part) => BigInt(part));
};

const parseMemoized = memoize(
  /**
   * Parses entity ID string, can be shard.realm.num, realm.num, or the encoded entity ID.
   * @param {string} id
   * @param {Error} error
   * @return {EntityId}
   */
  (id, error) => {
    if (!isValidEntityId(id)) {
      throw error;
    }

    const [shard, realm, num] = id.includes('.') ? parseFromString(id) : parseFromEncodedId(id, error);
    if (num > maxNum || realm > maxRealm || shard > maxShard) {
      throw error;
    }

    return of(shard, realm, num);
  },
  {
    max: entityIdCacheConfig.maxSize,
    maxAge: entityIdCacheConfig.maxAge * 1000, // in millis
    normalizer: (args) => args[0],
  }
);

/**
 * Parses entity ID string. The entity ID string can be shard.realm.num, realm.num, or the encoded entity ID string.
 * If there are at 3 or more arguments, the second is the param name string, and the third is isNullable. If there are
 * 2 arguments, the second is the param name string if its type is string, or isNullable if its type is boolean.
 *
 * @param id
 * @param rest
 * @return {EntityId}
 */
const parse = (id, ...rest) => {
  let paramName = '';
  let isNullable = false;
  if (rest.length >= 2) {
    paramName = rest[0];
    isNullable = rest[1];
  } else if (rest.length === 1) {
    if (typeof rest[0] === 'string') {
      paramName = rest[0];
    } else if (typeof rest[0] === 'boolean') {
      isNullable = rest[0];
    }
  }

  const error = paramName
    ? InvalidArgumentError.forParams(paramName)
    : new InvalidArgumentError(`Invalid entity ID "${id}"`);

  return checkNullId(id, isNullable) || parseMemoized(`${id}`, error);
};

module.exports = {
  isValidEntityId,
  of,
  parse,
};
