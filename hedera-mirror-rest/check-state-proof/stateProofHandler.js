/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
'uses strict';

// external libraries
const _ = require('lodash');
const {AddressBook} = require('./addressBook');
const {RecordFile} = require('./recordFile');
const {SignatureFile} = require('./signatureFile');
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
    console.log(`Parsing address books...`);
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
    console.log(`Parsing record file...`);
    const tmpRcdFile = base64StringToBuffer(recordFileString);
    storeFile(tmpRcdFile, `${this.transactionId}/recordFile`, 'rcd');

    const rcdFile = new RecordFile(tmpRcdFile);

    console.debug(`Parsed record, found ${Object.keys(rcdFile.transactionIdMap).length} transactions`);
    return rcdFile;
  }

  parseSignatureFiles(signatureFilesString) {
    console.log(`Parsing signature files...`);
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
    return _.map(this.signatureFiles, (signatureFileObject) => {
      return {
        nodeId: signatureFileObject.nodeId,
        signature: signatureFileObject.signature,
        hash: signatureFileObject.hash,
      };
    });
  }

  runStateProof() {
    const {nodeIdPublicKeyPairs} = _.last(this.addressBooks);

    // verify transactionId is in recordFile
    const transactionInRecordFile = this.recordFile.containsTransaction(this.transactionId);
    if (!transactionInRecordFile) {
      console.error(
        `transactionId ${this.transactionId} not present in recordFile. Transaction map is {${Object.keys(
          this.recordFile.transactionIdMap
        )}}`
      );
      return false;
    }
    console.log(`Matching transaction was found in record file`);

    const validatedTransaction = performStateProof(
      nodeIdPublicKeyPairs,
      this.getNodeSignatureMap(),
      this.recordFile.hash
    );

    return validatedTransaction;
  }
}

module.exports = {
  StateProofHandler,
};
