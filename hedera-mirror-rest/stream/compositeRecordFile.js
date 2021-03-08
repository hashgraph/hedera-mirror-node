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

const CompactRecordFile = require('./compactRecordFile');
const FullRecordFile = require('./fullRecordFile');
const RecordFile = require('./recordFile');

const delegates = [CompactRecordFile, FullRecordFile];

class CompositeRecordFile extends RecordFile {
  constructor(bufferOrObj) {
    super();

    const clazz = delegates.reduce((match, cls) => {
      return match || (cls._support(bufferOrObj) ? cls : null);
    }, null);
    if (!clazz) {
      throw new Error('Unsupported record file');
    }

    this.delegate = new clazz(bufferOrObj);
  }

  static canCompact(bufferOrObj) {
    return delegates.reduce((compactable, clazz) => {
      return compactable || clazz.canCompact(bufferOrObj);
    }, false);
  }

  containsTransaction(transactionId, scheduled = false) {
    return this.delegate.containsTransaction(transactionId, scheduled);
  }

  getFileHash() {
    return this.delegate.getFileHash();
  }

  getMetadataHash() {
    return this.delegate.getMetadataHash();
  }

  getTransactionMap() {
    return this.delegate.getTransactionMap();
  }

  getVersion() {
    return this.delegate.getVersion();
  }

  toCompactObject(transactionId, scheduled = false) {
    return this.delegate.toCompactObject(transactionId, scheduled);
  }
}

module.exports = CompositeRecordFile;
