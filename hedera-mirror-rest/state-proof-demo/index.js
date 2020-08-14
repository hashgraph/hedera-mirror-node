#!/usr/bin/env node
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

// responsible for 1. Getting input, 2. Calling API, passing response to validator

// external libraries
const yargs = require('yargs'); //  simplify user input
const chalk = require('chalk'); //  pretty up request info
const boxen = require('boxen'); //  emphasize request info
const fetch = require('node-fetch');
const fs = require('fs');
const _ = require('lodash');
const {mirrorStateProofResponseHandler} = require('./mirrorStateProofResponseHandler');
const {getAPIResponse, readJSONFile} = require('./utils');
const {stateProofSample} = require('./stateProofSample');
const {sampleAddressBooks} = require('./stateProofSample').address_books;
const {sampleRecordFile} = require('./stateProofSample').record_file;
const {sampleSignatureFiles} = require('./stateProofSample').signature_files;

// const greeting = chalk.white.bold("Welcome to your on demand Hedera Transaction State Proof CLI!!!");
const boxenOptions = {
  padding: 1,
  margin: 1,
  borderStyle: 'round',
  borderColor: 'green',
  backgroundColor: '#555555',
};

const options = yargs
  .usage('Usage: -t <transactionId> -e <env>')
  .option('t', {
    alias: 'transactionId',
    describe: 'Your Hedera Network Transaction Id e.g. 0.0.94139-11965562-313194',
    type: 'string',
    demandOption: true,
  })
  .option('s', {alias: 'sample', describe: 'Use sample data', type: 'boolean', demandOption: false})
  .option('e', {alias: 'env', describe: 'Your environment e.g. test / main', type: 'string', demandOption: true}).argv;

const welcome = `Welcome to your on demand Hedera Transaction State Proof CLI!!!, ${options.transactionId} on ${options.env}!`;
const greeting = chalk.bold(`Welcome to your on demand Hedera Transaction State Proof CLI!!!, ${options.name}!`);

const msgBox = boxen(greeting, boxenOptions);
console.log(msgBox);

// step 1 :
// Obtain user input for network and transactionId e.g testnet & 0.0.94139-11965562-313194
// get host url https://mainnet.mirrornode.hedera.com or  https://testnet.mirrornode.hedera.com or localhost:5551 by default
// "url": "/api/v1/transactions/0.0.94139-11965562-313194/stateproof"

let host;
switch (options.env) {
  case 'test':
    host = 'testnet';
    break;
  case 'main':
    host = 'mainnet';
    break;
  default:
    host = 'localhost:5551';
}

// to:do sanitize transaction and env values
const sample = options.sample === true;
const transactionId = options.transactionId;
const url = `${host}/api/v1/transactions/${transactionId}/stateproof`;
console.log(`Env: ${host}, transactionId: ${transactionId} url: ${url}, sample: ${sample}`);

// step 2: make stateproof call
// const stateProofJson = getAPIResponse(url);
const badJson = {
  record_file: 'd204eae8c41027b039dba4841f8ef22d',
  address_books: ['8b9f6e7d1916344785d6d718ea3e884f'],
  signature_files: {
    '0.0.3': '5e5bd7171318a2d5cc3596f24f30b053',
    '0.0.4': 'c523080e35a76792a18a9ea0f43737c0',
    '0.0.5': 'a6a851d4a205e47b52b35ada6dfce366',
    '0.0.6': 'bd566edbcb0fb29fc1a50befd7b3977b',
    '0.0.7': 'bd566edbcb0fb29fc1a50befd7b3977b',
    '0.0.8': '5e5bd7171318a2d5cc3596f24f30b053',
  },
};

const stateProofJson = sample ? readJSONFile('stateProofSample.json') : badJson;
// const stateProofJson = stateProofSample;
console.log(
  `Retrieved state proof files. Checking for emptiness - address_books: ${
    stateProofJson.address_books !== undefined
  }, record_file: ${stateProofJson.record_file !== undefined}, signature_files: ${
    stateProofJson.signature_files !== undefined
  }`
);

// step 3 : store files locally and verify at least 1 addressBook, 3 signatures, 1 rcd file
// const recordFileData = Buffer.from(stateProofJson.record_file, 'base64'); // base64 string
// const addressBooksData = Buffer.from(stateProofJson.address_books, 'base64'); // base64 string array
// const signatureFilesData = Buffer.from(stateProofJson.signature_files, 'base64'); // base64 string array
const stateProofResponseHandler = new mirrorStateProofResponseHandler(
  stateProofJson.record_file,
  stateProofJson.address_books,
  stateProofJson.signature_files,
  transactionId
);

console.log(
  `Parsed state proof files. ${stateProofResponseHandler.addressBooks.length} address_books, record_file '${stateProofResponseHandler.recordFile.consensusStart}', ${stateProofResponseHandler.signatureFiles.length} signature_files`
);

// step 4: AddressBook
// q: what to do here? - what's the value of getting the addressBook chain here? What do we do with it?
// parse to AddressBook proto and pull out details of Nodes -> Id, publicKey
// For each node expect a matching entry in the signature_files

const nodeIdPublicKeyPairs = _.last(stateProofResponseHandler.addressBooks).getNodeIdPublicKeyPairs;
console.log(`${nodeIdPublicKeyPairs} will be used to verify signatures and transaction`);

// step 5
// verify signature file using address book public key. -> See NodeSignatureVerifier.verifySignature()
// check if it's signed by corresponding node's PublicKey

// step 6 verify consensus on signature files
// for valid signature files verify that at least 1/3 of them have the matching hash.

// step 7
// verify rcd has matching hash of consensus signature files - See RecordFileDownloader.verifyDataFile()
// that hash of data file matches the verified hash and that data file is next in line based on previous file
// For now assume rcd file is 0.0.3

// step 8
// parse rcd file looking for transaction with matching consensus time stamp from transaction id

// step 9
// confirm all verifications pass. At earliest fail report invalidTransaction
// Q: what's the output here?
