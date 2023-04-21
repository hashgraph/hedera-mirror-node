/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import {BYTE_SIZE, INT_SIZE} from './constants';
import StreamObject from './streamObject';
import {readLengthAndBytes} from './utils';

class SignatureObject extends StreamObject {
  // properties of SHA384WithRsa signature
  static SHA_384_WITH_RSA = {
    type: 1,
    maxLength: 384,
  };

  /**
   * Reads the body of the signature object
   * @param {Buffer} buffer
   * @returns {Number} The size of the body in bytes
   */
  _readBody(buffer) {
    const message = 'Error reading signature object';
    this.type = buffer.readInt32BE();
    if (this.type !== SignatureObject.SHA_384_WITH_RSA.type) {
      throw new Error(`${message}, expect type ${SignatureObject.SHA_384_WITH_RSA.type} got ${this.type}`);
    }

    const {length, bytes} = readLengthAndBytes(
      buffer.subarray(INT_SIZE),
      BYTE_SIZE,
      SignatureObject.SHA_384_WITH_RSA.maxLength,
      true
    );
    this.signature = bytes;

    return INT_SIZE + length;
  }
}

export default SignatureObject;
