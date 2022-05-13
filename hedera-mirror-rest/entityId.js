/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
const mem = require('mem');
const quickLru = require('quick-lru');

const {
  cache: {entityId: entityIdCacheConfig},
  shard: systemShard,
} = require('./config');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');
const {EvmAddressType} = require('./constants');

// format: |0|15-bit shard|16-bit realm|32-bit num|
const numBits = 32n;
const numMask = 2n ** numBits - 1n;
const maxNum = 2n ** numBits - 1n;

const realmBits = 16n;
const realmMask = 2n ** realmBits - 1n;
const realmScale = 2 ** Number(numBits);
const maxRealm = 2n ** realmBits - 1n;

const shardBits = 15n;
const shardOffset = numBits + realmBits;
const shardScale = 2 ** Number(shardOffset);
const maxShard = 2n ** shardBits - 1n;
const maxSafeShard = 2 ** 5 - 1;

const maxEncodedId = 2n ** 63n - 1n;

const entityIdRegex = /^(\d{1,5}\.){1,2}\d{1,10}$/;
const encodedEntityIdRegex = /^\d{1,19}$/;
const evmAddressShardRealmRegex = /^(\d{1,10}\.){0,2}[A-Fa-f0-9]{40}$/;
const evmAddressRegex = /^(0x)?[A-Fa-f0-9]{40}$/;

class EntityId {
  /**
   * Creates an EntityId instance
   *
   * @param {Number|null} shard
   * @param {Number|null} realm
   * @param {Number|null} num
   * @param {string|null} evmAddress The hex encoded non-parsable evm address, without 0x prefix
   */
  constructor(shard, realm, num, evmAddress) {
    this.shard = shard;
    this.realm = realm;
    this.num = num;
    this.evmAddress = evmAddress;
  }

  /**
   * Encodes the shard.realm.num entity id into an integer. Returns null if num is null; returns a
   * number if the encoded integer is not larger than Number.MAX_SAFE_INTEGER; returns a BigInt otherwise.
   *
   * @returns {Number|BigInt|null} encoded id corresponding to this EntityId.
   */
  getEncodedId() {
    if (this.encodedId === undefined) {
      if (this.num === null) {
        this.encodedId = null;
      } else {
        this.encodedId =
          this.shard <= maxSafeShard
            ? this.shard * shardScale + this.realm * realmScale + this.num
            : (BigInt(this.shard) << shardOffset) | (BigInt(this.realm) << numBits) | BigInt(this.num);
      }
    }
    return this.encodedId;
  }

  isAllZero() {
    return this.shard === 0 && this.realm === 0 && this.num === 0;
  }
  /**
   * Converts the entity id to the 20-byte EVM address in hex with '0x' prefix
   */
  toEvmAddress() {
    if (this.evmAddress) {
      return `0x${this.evmAddress}`;
    }

    // shard, realm, and num take 4, 8, and 8 bytes respectively from the left
    return this.num === null
      ? null
      : [
          '0x',
          toHex(this.shard).padStart(8, '0'),
          toHex(this.realm).padStart(16, '0'),
          toHex(this.num).padStart(16, '0'),
        ].join('');
  }

  toString() {
    if (this.isAllZero()) {
      return null;
    }

    if (this.num === null && this.evmAddress === null) {
      return null;
    }

    return [this.shard, this.realm, this.num, this.evmAddress].filter((x) => x !== null).join('.');
  }
}

const toHex = (num) => {
  return num.toString(16);
};

const isValidEvmAddress = (address, evmAddressType = EvmAddressType.ANY) => {
  if (typeof address !== 'string') {
    return false;
  }

  if (evmAddressType === EvmAddressType.ANY) {
    return evmAddressRegex.test(address) || evmAddressShardRealmRegex.test(address);
  }
  if (evmAddressType === EvmAddressType.NO_SHARD_REALM) {
    return evmAddressRegex.test(address);
  }
  return evmAddressShardRealmRegex.test(address);
};

const isValidEntityId = (entityId, allowEvmAddress = true, evmAddressType = EvmAddressType.ANY) => {
  if ((typeof entityId === 'string' && entityIdRegex.test(entityId)) || encodedEntityIdRegex.test(entityId)) {
    // Accepted forms: shard.realm.num, realm.num, or encodedId
    return true;
  }

  return allowEvmAddress && isValidEvmAddress(entityId, evmAddressType);
};

const isCreate2EvmAddress = (evmAddress) => {
  if (!isValidEvmAddress(evmAddress)) {
    return false;
  }
  const idPartsFromEvmAddress = parseFromEvmAddress(evmAddress);
  return (
    idPartsFromEvmAddress[0] > maxShard || idPartsFromEvmAddress[1] > maxRealm || idPartsFromEvmAddress[2] > maxNum
  );
};

/**
 * Creates EntityId from shard, realm, and num.
 *
 * @param {BigInt|Number} shard
 * @param {BigInt|Number} realm
 * @param {BigInt|Number} num
 * @param {string|null} evmAddress The hex encoded non-parsable evm address
 * @return {EntityId}
 */
const of = (shard, realm, num, evmAddress = null) => {
  const toNumber = (val) => (typeof val === 'bigint' ? Number(val) : val);
  return new EntityId(toNumber(shard), toNumber(realm), toNumber(num), evmAddress);
};

const nullEntityId = of(null, null, null, null);
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

// without and with 0x prefix
const isValidEvmAddressLength = (len) => len === 40 || len === 42;

/**
 * Parses shard, realm, num from encoded ID string.
 * @param {string} id
 * @param {Function} error
 * @return {[BigInt, BigInt, BigInt, null]}
 */
const parseFromEncodedId = (id, error) => {
  const encodedId = BigInt(id);
  if (encodedId > maxEncodedId) {
    throw error();
  }

  const num = encodedId & numMask;
  const shardRealm = encodedId >> numBits;
  const realm = shardRealm & realmMask;
  const shard = shardRealm >> realmBits;
  return [shard, realm, num, null];
};

/**
 * Parses shard, realm, num from EVM address string.
 * @param {string} evmAddress
 * @return {bigint[3]}
 */
const parseFromEvmAddress = (evmAddress) => {
  // extract shard from index 0->8, realm from 8->23, num from 24->40 and parse from hex to decimal
  const hexDigits = _.last(evmAddress.split('.')).replace('0x', '');
  return [
    BigInt('0x' + hexDigits.slice(0, 8)), // shard
    BigInt('0x' + hexDigits.slice(8, 24)), // realm
    BigInt('0x' + hexDigits.slice(24, 40)), // num
  ];
};

/**
 * Parses entity id string, accepts the following formats:
 *   - shard.realm.num
 *   - realm.num
 *   - shard.realm.evmAddress
 *   - evmAddress with or without 0x prefix
 *
 * @param {string} id
 * @param {Function} error The error function
 * @return {[BigInt|null, BigInt|null, BigInt|null, string|null]}
 */
const parseFromString = (id, error) => {
  const parts = id.split('.');
  const numOrEvmAddress = parts[parts.length - 1];
  if (isValidEvmAddressLength(numOrEvmAddress.length)) {
    const evmAddress = numOrEvmAddress.replace('0x', '');
    let [shard, realm, num] = parseFromEvmAddress(numOrEvmAddress);
    if (shard > maxShard || realm > maxRealm || num > maxNum) {
      // non-parsable evm address
      shard = parts.length === 3 ? BigInt(parts[0]) : null;
      realm = parts.length === 3 ? BigInt(parts[1]) : null;
      return [shard, realm, null, evmAddress];
    } else {
      if (parts.length === 3 && (parts[0] !== `${shard}` || parts[1] !== `${realm}`)) {
        throw error();
      }
      return [shard, realm, num, null];
    }
  }

  // it's either shard.realm.num or realm.num
  if (parts.length < 3) {
    parts.unshift(systemShard);
  }

  return [BigInt(parts[0]), BigInt(parts[1]), BigInt(parts[2]), null];
};

const computeContractIdPartsFromContractIdValue = (contractId) => {
  const idPieces = contractId.split('.');
  idPieces.unshift(...[null, null].slice(0, 3 - idPieces.length));
  const contractIdParts = {
    shard: idPieces[0] !== null ? BigInt(idPieces[0]) : null,
    realm: idPieces[1] !== null ? BigInt(idPieces[1]) : null,
  };
  if (isCreate2EvmAddress(idPieces[2])) {
    contractIdParts.create2_evm_address = idPieces[2];
  } else {
    contractIdParts.num = idPieces[2];
  }
  return contractIdParts;
};

const entityIdCacheOptions = {
  cache: new quickLru({
    maxSize: entityIdCacheConfig.maxSize,
  }),
  cacheKey: (args) => `${args[0]}_${args[1]}_${args[2]}`,
  maxAge: entityIdCacheConfig.maxAge * 1000, // in millis
};

const parseMemoized = mem(
  /**
   * Parses entity ID string, can be shard.realm.num, realm.num, the encoded entity ID or an evm address.
   * @param {string} id
   * @param {boolean} allowEvmAddress
   * @param {number} evmAddressType
   * @param {Function} error
   * @return {EntityId}
   */
  (id, allowEvmAddress, evmAddressType, error) => {
    if (!isValidEntityId(id, allowEvmAddress, evmAddressType)) {
      throw error();
    }

    const [shard, realm, num, evmAddress] =
      id.includes('.') || isValidEvmAddressLength(id.length)
        ? parseFromString(id, error)
        : parseFromEncodedId(id, error);
    if (evmAddress === null && (num > maxNum || realm > maxRealm || shard > maxShard)) {
      throw error();
    }

    return of(shard, realm, num, evmAddress);
  },
  entityIdCacheOptions
);

/**
 * Parses entity ID string. The entity ID string can be shard.realm.num, realm.num, shard.realm.evm_address, evm_address,
 * or the encoded entity ID string.
 *
 * @param id
 * @param options
 * @return {EntityId}
 */
const parse = (id, {allowEvmAddress, evmAddressType, isNullable, paramName} = {}) => {
  // defaults
  allowEvmAddress = allowEvmAddress === undefined ? true : allowEvmAddress;
  evmAddressType = evmAddressType === undefined ? EvmAddressType.ANY : evmAddressType;
  isNullable = isNullable === undefined ? false : isNullable;
  paramName = paramName === undefined ? '' : paramName;

  // lazily create error object
  const error = () =>
    paramName ? InvalidArgumentError.forParams(paramName) : new InvalidArgumentError(`Invalid entity ID "${id}"`);
  return checkNullId(id, isNullable) || parseMemoized(`${id}`, allowEvmAddress, evmAddressType, error);
};

module.exports = {
  isValidEntityId,
  isValidEvmAddress,
  computeContractIdPartsFromContractIdValue,
  of,
  parse,
};
