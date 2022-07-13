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

import crypto from 'crypto';
import _ from 'lodash';

import {INT_SIZE} from './constants.js';
import HashObject from './hashObject.js';
import RecordFile from './recordFile.js';
import RecordStreamObject from './recordStreamObject.js';
import calculateRunningHash from './runningHash.js';

const COMPACT_OBJECT_FIELDS = [
  'head',
  'startRunningHashObject',
  'hashesBefore',
  'recordStreamObject',
  'hashesAfter',
  'endRunningHashObject',
];

const {SHA_384} = HashObject;

// version, hapi version major/minor/patch, object stream version
const V5_START_HASH_OFFSET = INT_SIZE + (INT_SIZE + INT_SIZE + INT_SIZE) + INT_SIZE;

class CompactRecordFile extends RecordFile {
  constructor(bufferOrObj) {
    super();

    if (!CompactRecordFile._support(bufferOrObj)) {
      throw new Error('Unsupported record file version, expect 5');
    }

    this._version = 5;

    if (Buffer.isBuffer(bufferOrObj)) {
      this._parseFromBuffer(bufferOrObj);
    } else {
      this._parseFromObj(bufferOrObj);
    }
  }

  static _support(bufferOrObj) {
    const buffer = Buffer.isBuffer(bufferOrObj) ? bufferOrObj : bufferOrObj.head;
    return this._readVersion(buffer) === 5;
  }

  static canCompact(buffer) {
    return Buffer.isBuffer(buffer) && this._support(buffer);
  }

  toCompactObject(transactionId, nonce = 0, scheduled = false) {
    if (!this.containsTransaction(transactionId, nonce, scheduled)) {
      throw new Error(`Transaction ${transactionId} not found in the successful transactions map`);
    }

    if (this._recordStreamObjects) {
      // parsed from a full record file v5
      const transactionKey = RecordFile._getTransactionKey(transactionId, nonce, scheduled);
      const index = this._transactionMap[transactionKey];
      this.recordStreamObject = this._recordStreamObjects[index];

      this._hashes.forEach((value, current) => {
        if (!value && current !== index) {
          // calculate and cache the hash if not found and this is not the transaction of interest
          this._hashes[current] = crypto.createHash(SHA_384.name).update(this._recordStreamObjects[current]).digest();
        }
      });
      this.hashesBefore = this._hashes.slice(0, index);
      this.hashesAfter = this._hashes.slice(index + 1);
    }

    return _.pick(this, COMPACT_OBJECT_FIELDS);
  }

  _parseFromBuffer(buffer) {
    // only the REST service parses full record file v5 data and only the fields in the compact format response are
    // needed, so don't calculate full file hash and metadata hash

    // skip the bytes before the start hash object to read a list of stream objects organized as follows:
    //
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |  Start Object Running Hash  |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |    Record Stream Object     |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |    ...                      |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |    Record Stream Object     |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |   End Object Running Hash   |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //
    // Note the start object running hash and the end object running hash are of the same type HashObject and
    // they have the same classId an classVersion.
    this.head = buffer.slice(0, V5_START_HASH_OFFSET);

    buffer = buffer.slice(V5_START_HASH_OFFSET);
    const startHashObject = new HashObject(buffer);
    this.startRunningHashObject = buffer.slice(0, startHashObject.getLength());

    buffer = buffer.slice(startHashObject.getLength());
    this._recordStreamObjects = []; // store the record stream object raw buffer
    while (buffer.readBigInt64BE() !== startHashObject.classId) {
      // record stream objects are between the start hash object and the end hash object
      const recordStreamObject = new RecordStreamObject(buffer);
      this._addTransaction(recordStreamObject.record, this._recordStreamObjects.length);
      this._recordStreamObjects.push(buffer.slice(0, recordStreamObject.getLength()));
      buffer = buffer.slice(recordStreamObject.getLength());
    }

    this._hashes = new Array(this._recordStreamObjects.length).fill(undefined);
    const endHashObject = new HashObject(buffer);
    this.endRunningHashObject = buffer.slice(0, endHashObject.getLength());
    if (buffer.length !== endHashObject.getLength()) {
      throw new Error('Extra data discovered in record file');
    }
  }

  _parseFromObj(obj) {
    Object.assign(this, _.pick(obj, COMPACT_OBJECT_FIELDS));

    this._verifyEndRunningHash();

    // add the transaction to the map
    const recordStreamObject = new RecordStreamObject(this.recordStreamObject);
    this._addTransaction(recordStreamObject.record);

    // calculate metadata hash
    this._metadataHash = crypto
      .createHash(SHA_384.name)
      .update(this.head)
      .update(this.startRunningHashObject)
      .update(this.endRunningHashObject)
      .digest();
  }

  _verifyEndRunningHash() {
    const startHashObject = new HashObject(this.startRunningHashObject);
    const endHashObject = new HashObject(this.endRunningHashObject);
    const hashes = [
      startHashObject.hash,
      ...this.hashesBefore,
      crypto.createHash(SHA_384.name).update(this.recordStreamObject).digest(),
      ...this.hashesAfter,
    ];

    // when calculating running hash, classId and classVersion are digested in little-endian
    const header = startHashObject.getHeaderLE();
    const actualHash = hashes.reduce((runningHash, nextHash) =>
      calculateRunningHash(
        {
          header,
          hash: runningHash,
        },
        {
          header,
          hash: nextHash,
        },
        SHA_384.name
      )
    );
    if (!actualHash.equals(endHashObject.hash)) {
      throw new Error('End object running hash mismatch');
    }
  }
}

export default CompactRecordFile;
