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

const {INT_SIZE} = require('./constants');

// the sum of the length field and the checksum field
const SIMPLE_SUM = 101;

/**
 * Reads the length field, an optional checksum, and the byte array from buffer
 * @param {Buffer} buffer - The buffer to read data from
 * @param {Number} minLength - The minimum allowed length
 * @param {Number} maxLength - The maxinum allowed length
 * @param {boolean} hasChecksum - If there is a checksum field
 * @return {Object} The length read and the bytes
 */
const readLengthAndBytes = (buffer, minLength, maxLength, hasChecksum) => {
  const message = 'Error reading length and bytes';
  const length = buffer.readInt32BE();
  let offset = INT_SIZE;
  if (minLength === maxLength) {
    if (length !== minLength) {
      throw new Error(`${message}, expect length ${minLength} got ${length}`);
    }
  } else if (length < minLength || length > maxLength) {
    throw new Error(`${message}, expect length ${minLength} within [${minLength}, ${maxLength}]`);
  }

  if (hasChecksum) {
    const checksum = buffer.readInt32BE(offset);
    const expected = SIMPLE_SUM - length;
    offset += INT_SIZE;
    if (checksum !== expected) {
      throw new Error(`${message}, expect checksum ${checksum} to be ${expected}`);
    }
  }

  return {
    length: offset + length,
    bytes: readNBytes(buffer.slice(offset), length),
  };
};

/**
 * Reads a byte array from buffer
 * @param {Buffer} buffer
 * @param {Number} length
 * @return {Buffer}
 */
const readNBytes = (buffer, length) => {
  if (buffer.length < length) {
    throw new Error(`Error reading byte array, expect ${length}-byte data got ${buffer.length}-byte`);
  }

  return buffer.slice(0, length);
};

module.exports = {
  readLengthAndBytes,
  readNBytes,
};
