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
const {getAPIResponse, readJSONFile} = require('./utils');

// get user input
const {transactionId, url, sample} = welcomeScreen();

const getStateProofJson = async (url, test) => {
  console.log(`Use sample data : ${test}`);
  return test ? readJSONFile('stateProofSample.json') : getAPIResponse(url);
};

getStateProofJson(url, sample).then((stateProofJson) => {
  // instantiate stateProofHandler which will parse files and extract needed data
  const stateProofManager = new stateProofHandler(transactionId, stateProofJson);

  // kick off stateProof flow
  const validatedTransaction = stateProofManager.runStateProof();

  console.log(`StateProof validation for ${transactionId} returned ${validatedTransaction}`);
});
