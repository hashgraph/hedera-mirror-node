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

// external libraries
const _ = require('lodash');
const log4js = require('log4js');
const AddressBook = require('./addressBook');
const {CompositeRecordFile, SignatureFile} = require('../stream');
const TransactionId = require('../transactionId');
const {performStateProof} = require('./transactionValidator');
const {makeStateProofDir, storeFile} = require('./utils');

const logger = log4js.getLogger();

// responsible for parsing response data to valid AddressBook, recordFile and SignFiles objects
class StateProofHandler {
  constructor(stateProofJson, transactionId, scheduled = false) {
    this.transactionId = TransactionId.fromString(transactionId);
    this.scheduled = scheduled;
    makeStateProofDir(transactionId, stateProofJson);
    this.setStateProofComponents(stateProofJson);
  }

  setStateProofComponents(stateProofJson) {
    const stateProof = this.responseJsonToObj(stateProofJson);
    this.addressBooks = this.parseAddressBooks(stateProof.addressBooks);
    this.recordFile = this.parseRecordFile(stateProof.recordFile);
    this.signatureFileMap = this.parseSignatureFiles(stateProof.signatureFiles);
  }

  responseJsonToObj(json) {
    // change keys to camelCase
    const camelCasify = (obj) => {
      const ret = _.mapKeys(obj, (v, k) => _.camelCase(k));
      if (ret.recordFile && _.isPlainObject(ret.recordFile)) {
        ret.recordFile = _.mapKeys(ret.recordFile, (v, k) => _.camelCase(k));
      }
      return ret;
    };

    const base64Decode = (obj) => {
      for (const [k, v] of Object.entries(obj)) {
        if (typeof v === 'string') {
          obj[k] = Buffer.from(v, 'base64');
        } else {
          obj[k] = base64Decode(v);
        }
      }
      return obj;
    };

    return base64Decode(camelCasify(json));
  }

  parseAddressBooks(addressBookBuffers) {
    const addressBooks = addressBookBuffers.map((addressBookBuffer, index) => {
      storeFile(addressBookBuffer, `${this.transactionId}/addressBook-${index + 1}`, 'txt');
      return new AddressBook(addressBookBuffer);
    });

    logger.debug(`Parsed ${addressBooks.length} address books`);
    return addressBooks;
  }

  parseRecordFile(recordFileBufferOrObj) {
    storeFile(recordFileBufferOrObj, `${this.transactionId}/recordFile`, 'rcd');

    const rcdFile = new CompositeRecordFile(recordFileBufferOrObj);

    logger.debug(`Parsed record and found ${Object.keys(rcdFile.getTransactionMap()).length} transactions`);
    return rcdFile;
  }

  parseSignatureFiles(signatureFiles) {
    const signatureFileMap = Object.fromEntries(
      _.map(signatureFiles, (signatureFileBuffer, nodeAccountId) => {
        storeFile(signatureFileBuffer, `${this.transactionId}/signatureFile-${nodeAccountId}`, 'rcd_sig');
        return [nodeAccountId, new SignatureFile(signatureFileBuffer)];
      })
    );
    logger.debug(`Parsed ${Object.keys(signatureFileMap).length} signature files`);
    return signatureFileMap;
  }

  runStateProof() {
    const {nodeAccountIdPublicKeyPairs} = _.last(this.addressBooks);

    // verify transactionId is in recordFile
    const transactionInRecordFile = this.recordFile.containsTransaction(this.transactionId, this.scheduled);
    if (!transactionInRecordFile) {
      logger.error(
        `Transaction ID ${this.transactionId} not present in record file. Available transaction IDs: ${Object.keys(
          this.recordFile.getTransactionMap()
        )}`
      );
      return false;
    }
    logger.info(`Matching transaction was found in record file`);

    return performStateProof(nodeAccountIdPublicKeyPairs, this.signatureFileMap, {
      fileHash: this.recordFile.getFileHash(),
      metadataHash: this.recordFile.getMetadataHash(),
    });
  }
}

module.exports = StateProofHandler;
