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
const {AddressBook} = require('./addressBook');
const {RecordFile} = require('../stream/recordFile');
const {SignatureFile} = require('../stream/signatureFile');
const {base64StringToBuffer, makeStateProofDir, storeFile} = require('./utils');
const {performStateProof} = require('./transactionValidator');

// responsible for parsing response data to valid AddressBook, recordFile and SignFiles objects
class StateProofHandler {
  constructor(transactionId, stateProofJson) {
    this.transactionId = makeStateProofDir(transactionId, stateProofJson);
    this.setStateProofComponents(stateProofJson);
  }

  setStateProofComponents(stateProofJson) {
    this.addressBooks = this.parseAddressBooks(stateProofJson.address_books);
    this.recordFile = this.parseRecordFile(stateProofJson.record_file);
    this.signatureFiles = this.parseSignatureFiles(stateProofJson.signature_files);
  }

  parseAddressBooks(addressBooksString) {
    const addressBooks = [];
    _.forEach(addressBooksString, (book, index) => {
      const tmpAddrBook = base64StringToBuffer(book);
      storeFile(tmpAddrBook, `${this.transactionId}/addressBook-${index + 1}`, 'txt');
      addressBooks.push(new AddressBook(tmpAddrBook));
    });

    console.debug(`Parsed ${addressBooks.length} address books`);
    return addressBooks;
  }

  parseRecordFile(recordFileString) {
    const tmpRcdFile = base64StringToBuffer(recordFileString);
    storeFile(tmpRcdFile, `${this.transactionId}/recordFile`, 'rcd');

    const rcdFile = new RecordFile(tmpRcdFile);

    console.debug(`Parsed record and found ${Object.keys(rcdFile.transactionIdMap).length} transactions`);
    return rcdFile;
  }

  parseSignatureFiles(signatureFilesString) {
    const sigFiles = [];
    _.forEach(signatureFilesString, (sigFilesString, nodeId) => {
      const tmpSigFile = base64StringToBuffer(sigFilesString);
      storeFile(tmpSigFile, `${this.transactionId}/signatureFile-${nodeId}`, 'rcd_sig');
      sigFiles.push(new SignatureFile(tmpSigFile, nodeId));
    });

    console.debug(`Parsed ${sigFiles.length} signature files`);
    return sigFiles;
  }

  getNodeSignatureMap() {
    return this.signatureFiles;
  }

  runStateProof() {
    const {nodeIdPublicKeyPairs} = _.last(this.addressBooks);

    // verify transactionId is in recordFile
    const transactionInRecordFile = this.recordFile.containsTransaction(this.transactionId);
    if (!transactionInRecordFile) {
      console.error(
        `Transaction ID ${this.transactionId} not present in record file. Available transaction IDs: ${Object.keys(
          this.recordFile.transactionIdMap
        )}`
      );
      return false;
    }
    console.log(`Matching transaction was found in record file`);

    const validatedTransaction = performStateProof(
      nodeIdPublicKeyPairs,
      this.getNodeSignatureMap(),
      this.recordFile.fileHash
    );

    return validatedTransaction;
  }
}

module.exports = {
  StateProofHandler,
};
