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
const {addressBook} = require('./addressBook');
const {recordFile} = require('./recordFile');
const {signatureFile} = require('./signatureFile');
const {base64StringToBuffer, makeStateProofDir, storeFile} = require('./utils');
const {performStateProof} = require('./transactionValidator');
const {replaceSpecialCharsWithUnderScores} = require('./utils');

// responsible for parsing response data to valid AddressBook, recordFile and SignFiles objects
class stateProofHandler {
  constructor(transactionId, stateProofJson) {
    this.transactionId = replaceSpecialCharsWithUnderScores(transactionId);
    this.setStateProofComponents(stateProofJson);
  }

  setStateProofComponents(stateProofJson) {
    makeStateProofDir(this.transactionId);
    this.addressBooks = this.parseAddressBooks(stateProofJson.address_books);
    this.recordFile = this.parseRecordFile(stateProofJson.record_file);
    this.signatureFiles = this.parseSignatureFiles(stateProofJson.signature_files);
  }

  parseAddressBooks(addressBooksString) {
    console.log(`Parsing address books...`);
    let addBooks = [];
    _.forEach(addressBooksString, (book, index) => {
      let tmpAddrBook = base64StringToBuffer(book);
      storeFile(tmpAddrBook, `${this.transactionId}/addressBook-${index + 1}`, 'txt');
      addBooks.push(new addressBook(tmpAddrBook));
    });

    console.debug(`Parsed ${addBooks.length} address books`);
    return addBooks;
  }

  parseRecordFile(recordFileString) {
    console.log(`Parsing record file...`);
    const tmpRcdFile = base64StringToBuffer(recordFileString);
    storeFile(tmpRcdFile, `${this.transactionId}/recordFile`, 'rcd');

    const rcdFile = new recordFile(tmpRcdFile);

    console.debug(`Parsed record file ${rcdFile.hash}`);
    return rcdFile;
  }

  parseSignatureFiles(signatureFilesString) {
    console.log(`Parsing signature files...`);
    let sigFiles = [];
    _.forEach(signatureFilesString, (sigFilesString, nodeId) => {
      let tmpSigFile = base64StringToBuffer(sigFilesString);
      storeFile(tmpSigFile, `${this.transactionId}/signatureFile-${nodeId}`, 'rcd_sig');
      sigFiles.push(new signatureFile(tmpSigFile, nodeId));
    });

    console.debug(`Parsed ${sigFiles.length} signature files`);
    return sigFiles;
  }

  getNodeSignatureMap() {
    return _.map(this.signatureFiles, (signatureFile) => {
      return {nodeId: signatureFile.nodeId, signature: signatureFile.signature, hash: signatureFile.hash};
    });
  }

  runStateProof() {
    const nodeIdPublicKeyPairs = _.last(this.addressBooks).nodeIdPublicKeyPairs;

    // verify transactionId is in recordFile
    const transactionInRecordFile = this.recordFile.containsTransaction(this.transactionId);
    if (!transactionInRecordFile) {
      console.error(
        `transactionId not present in recordFile. Transaction map is ${Object.keys(this.recordFile.transactionIdMap)}`
      );
      return false;
    }

    const validatedTransaction = performStateProof(
      nodeIdPublicKeyPairs,
      this.getNodeSignatureMap(),
      this.recordFile.hash
    );

    return validatedTransaction;
  }
}

module.exports = {
  stateProofHandler,
};
