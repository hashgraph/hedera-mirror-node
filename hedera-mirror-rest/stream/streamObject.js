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

import {INT_SIZE, LONG_SIZE} from './constants';

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

    this.bodyLength = this._readBody(buffer.subarray(STREAM_OBJECT_HEADER_SIZE));
  }

  _readBody(buffer) {
    return 0;
  }

  /**
   * Gets the serialized header with fields in little-endian order.
   *
   * @return {Buffer}
   */
  getHeaderLE() {
    const header = Buffer.alloc(STREAM_OBJECT_HEADER_SIZE);
    header.writeBigInt64LE(this.classId);
    header.writeInt32LE(this.classVersion, LONG_SIZE);
    return header;
  }

  getHeaderLength() {
    return STREAM_OBJECT_HEADER_SIZE;
  }

  getLength() {
    return STREAM_OBJECT_HEADER_SIZE + this.bodyLength;
  }
}

export default StreamObject;
