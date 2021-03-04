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

    const clazz = delegates.reduce((match, clazz) => {
      return match || (clazz._support(bufferOrObj) && clazz);
    }, undefined);

    if (!clazz) {
      throw new Error('Unsupported record file');
    }

    this.delegate = new clazz(bufferOrObj);
  }

  static _support(buffer) {
    return delegates.reduce((supported, clazz) => {
      return supported || clazz._support(buffer);
    }, false);
  }

  static canCompact(bufferOrObj) {
    return delegates.reduce((compactable, clazz) => {
      return compactable || clazz.canCompact(bufferOrObj);
    }, false);
  }

  toCompactObject(transactionId) {
    return this.delegate.toCompactObject(transactionId);
  }
}

module.exports = CompositeRecordFile;
