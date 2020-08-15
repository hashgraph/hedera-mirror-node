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

// responsible for parsing response data to valid AddressBook, recordFile and SignFiles objects

// external libraries
const _ = require('lodash');
const {addressBook} = require('./addressBook');
const {recordFile} = require('./recordFile');
const {signatureFile} = require('./signatureFile');
const {base64StringToBuffer, makeStateProofDir, storeFile} = require('./utils');

class mirrorStateProofResponseHandler {
  constructor(recordFileData, addressBooksData, signatureFilesData, transactionId) {
    this.transactionId = transactionId;
    // this.storeDir = makeStateProofDir(transactionId);
    this.addressBooks = this.parseAddressBooks(addressBooksData);
    this.recordFile = this.parseRecordFile(recordFileData);
    this.signatureFiles = this.parseSignatureFiles(signatureFilesData);
  }

  parseAddressBooks(addressBooksString) {
    console.log(`Parsing address books`);
    let addBooks = [];
    _.forEach(addressBooksString, (book, index) => {
      let tmpAddrBook = base64StringToBuffer(book);
      // storeFile(tmpAddrBook, `${this.storeDir}/addressBook-${index + 1}`, 'txt');
      addBooks.push(new addressBook(tmpAddrBook));
    });

    console.log(`Parsed ${addBooks.length} address books`);
    return addBooks;
  }

  parseRecordFile(recordFileString) {
    console.log(`Parsing record file`);
    const tmpRcdFile = base64StringToBuffer(recordFileString);
    // storeFile(tmpRcdFile, `${this.storeDir}/recordFile.rcd`, 'rcd');

    const rcdFile = new recordFile(tmpRcdFile);

    console.log(`Parsed record file ${rcdFile.consensusStart}`);
    return rcdFile;
  }

  parseSignatureFiles(signatureFilesString) {
    console.log(`Parsing signature files`);
    let sigFiles = [];
    _.forEach(signatureFilesString, (sigFilesString, nodeId) => {
      let tmpSigFile = base64StringToBuffer(sigFilesString);
      // storeFile(tmpSigFile, `${this.storeDir}/signatureFile-${nodeId}`, 'rcd_sig');
      sigFiles.push(new signatureFile(tmpSigFile, nodeId));
    });

    console.log(`Parsed ${sigFiles.length} signature files`);
    return sigFiles;
  }

  getNodeSignatureMap() {
    return _.map(this.signatureFiles, (signatureFile) => {
      return {nodeId: signatureFile.nodeId, signature: signatureFile.signature, hash: signatureFile.hash};
    });
  }
}

// store files, allow for future checks without callingAPI
// const storeStateProofFiles = (recordFile, addressBooks, signatureFiles, transId) => {
//   // store record
//   const rcdFile = base64StringToBuffer(recordFile);
//   storeFile(recordFile, `${transId}-recordFile.rcd`);
//
//   // store addressBooks
//   let addBooks = [];
//   _.forEach(addressBooks, (addressBook, index) => {
//     let tmpAddrBook = base64StringToBuffer(addressBook);
//     storeFile(addressBook, `${transId}-addressBook-${index + 1}.rcd`);
//     addBooks.push(tmpAddrBook);
//   });
//
//   // store signatureFiles
//   let sigFiles = [];
//   _.forEach(signatureFiles, (signatureFile, index) => {
//     let tmpSigFile = base64StringToBuffer(signatureFile);
//     storeFile(tmpSigFile, `${transId}-signatureFile-${index + 1}.rcd`);
//   });
//
//   return [rcdFile, addBooks, sigFiles];
// };

module.exports = {
  mirrorStateProofResponseHandler,
};
