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

const {InvalidArgumentError} = require('./errors/invalidArgumentError');

const realmOffset = 2n ** 32n; // realm is followed by 32 bits entity_num
const shardOffset = 2n ** 48n; // shard is followed by 16 bits realm and 32 bits entity_num

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
    return (BigInt(this.num) + BigInt(this.realm) * realmOffset + BigInt(this.shard) * shardOffset).toString();
  }

  toString() {
    return `${this.shard}.${this.realm}.${this.num}`;
  }
}

const of = (shard, realm, num) => {
  return new EntityId(shard, realm, num);
};

/**
 * Converts entity ID string to EntityId object. Supports both 'shard.realm.num' and encoded entity ID string.
 *
 * @param entityStr
 * @return {EntityId}
 */
const fromString = (entityStr) => {
  if (entityStr.includes('.')) {
    const parts = entityStr.split('.');
    if (parts.length === 3) {
      return of(
        ...parts.map((part) => {
          const num = Number(part);
          if (Number.isNaN(num) || num < 0) {
            throw new InvalidArgumentError(`invalid entity ID string "${entityStr}"`);
          }
          return num;
        })
      );
    }
  } else {
    // Javascript's precision limit is 2^53 - 1. Precision needed to handle encoded ids is 2^63 - 1 (highest number
    // for java's long and postgres' bigint). Limit use of BigInt types to this function, everything returned by this
    // function should be normal JS number for compatibility.
    let encodedId;
    try {
      encodedId = BigInt(entityStr);
    } catch (err) {
      throw new InvalidArgumentError(`invalid entity ID string "${entityStr}"`);
    }

    if (encodedId < 0n) {
      throw new InvalidArgumentError(`invalid entity ID string "${entityStr}"`);
    }

    const shard = encodedId / shardOffset; // quotient is shard
    const encodedRealmNum = encodedId % shardOffset; // realm and num remains
    const realm = encodedRealmNum / realmOffset;
    const num = encodedRealmNum % realmOffset;
    return of(Number(shard), Number(realm), Number(num)); // convert from BigInt to number
  }

  throw new InvalidArgumentError(`invalid entity ID string "${entityStr}"`);
};

module.exports = {
  of,
  fromString,
};
