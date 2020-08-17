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
const {welcomeScreen} = require('./startUp');
const _ = require('lodash');
const {stateProofHandler} = require('./stateProofHandler');

// get user input
const {transactionId, url, sample} = welcomeScreen();

// instantiate stateProofHandler
const stateProofManager = new stateProofHandler(transactionId, url, sample);

// kick off stateProof flow
const validatedTransaction = stateProofManager.runStateProof();

console.log(`StateProof validation for ${transactionId} returned ${validatedTransaction}`);

// step 3 : store files locally and verify at least 1 addressBook, 3 signatures, 1 rcd file

// step 4: AddressBook
// q: what to do here? - what's the value of getting the addressBook chain here? What do we do with it?
// parse to AddressBook proto and pull out details of Nodes -> Id, publicKey
// For each node expect a matching entry in the signature_files

// step 5
// verify signature file using address book public key. -> See NodeSignatureVerifier.verifySignature()

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
