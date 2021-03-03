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
const StreamObject = require('./streamObject');
const {readLengthAndBytes} = require('./utils');

class HashObject extends StreamObject {
  // properties of SHA-384 hash algorithm
  static SHA_384 = {
    encoding: 'hex',
    length: 48,
    name: 'sha384',
  };

  /**
   * Reads the body of the hash object
   * @param {Buffer} buffer
   * @returns {Number} The size of the body in bytes
   */
  readBody(buffer) {
    // always SHA-384
    const hashLength = HashObject.SHA_384.length;
    this.digestType = buffer.readInt32BE();
    const {length, bytes} = readLengthAndBytes(buffer.slice(INT_SIZE), hashLength, hashLength, false);
    this.hash = bytes;

    return INT_SIZE + length;
  }
}

module.exports = HashObject;
