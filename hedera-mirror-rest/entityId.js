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

const of = function (shard, realm, num) {
  return new EntityId(shard, realm, num);
};

const fromString = function (entityStr) {
  let tokens = entityStr.split('.');
  return of(parseInt(tokens[0]), parseInt(tokens[1]), parseInt(tokens[2]));
};

// Javascript's precision limit is 2^53 - 1. Precision needed to handle encoded ids is 2^63 - 1 (highest +ve number
// for java's long and postgres' bigint). Limit use of BigInt types to this function, everything returned by this
// function should be normal JS number for compatibility.
/**
 * @returns {EntityId} computed from decoding of given encodedId.
 */
const fromEncodedId = function (encodedIdStr) {
  let encodedId = BigInt(encodedIdStr);
  let shard = encodedId / shardOffset; // quotient is shard
  encodedId = encodedId % shardOffset; // realm and num remains
  let realm = encodedId / realmOffset;
  let num = encodedId % realmOffset;
  return of(parseInt(shard), parseInt(realm), parseInt(num)); // convert from BitInt to number
};

module.exports = {
  of,
  fromEncodedId,
  fromString,
};
