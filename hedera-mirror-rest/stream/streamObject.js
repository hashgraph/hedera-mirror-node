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

const {INT_SIZE, LONG_SIZE} = require('./constants');

// classId, classVersion
const STREAM_OBJECT_HEADER_SIZE = LONG_SIZE + INT_SIZE;

class StreamObject {
  /**
   * Reads stream object from buffer
   * @param {Buffer} buffer - The buffer to read the stream object from
   */
  constructor(buffer) {
    this.classId = buffer.readBigInt64BE();
    this.classVersion = buffer.readInt32BE(LONG_SIZE);

    this.bodyLength = this.readBody(buffer.slice(STREAM_OBJECT_HEADER_SIZE));
  }

  readBody(buffer) {
    return 0;
  }

  getLength() {
    return STREAM_OBJECT_HEADER_SIZE + this.bodyLength;
  }
}

module.exports = StreamObject;
