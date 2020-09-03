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

// external libraries
const _ = require('lodash');
const yargs = require('yargs'); //  simplify user input
const chalk = require('chalk'); //  pretty up request info
const boxen = require('boxen'); //  emphasize request info

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
    describe: 'Your Hedera Network Transaction Id e.g. 0.0.94139-1570800748-313194300',
    type: 'string',
    demandOption: true,
  })
  .option('f', {
    alias: 'file',
    describe: 'Absolute file path containing State Proof REST API response json',
    type: 'string',
    demandOption: false,
  })
  .option('h', {
    alias: 'host',
    describe: 'REST API host. Default is testnet',
    type: 'string',
    demandOption: false,
  })
  .option('e', {
    alias: 'env',
    describe: 'Your environment e.g. previewnet/testnet/mainnet',
    type: 'string',
    demandOption: false,
  }).argv;

const startUpScreen = () => {
  const greeting = chalk.bold(`Hedera Transaction State Proof Checker CLI!`);

  const msgBox = boxen(greeting, boxenOptions);
  console.log(msgBox);

  let host;
  // if host parameter was not passed then set host according to env.
  if (_.isUndefined(options.host)) {
    switch (options.env) {
      case 'previewnet':
        host = 'https://previewnet.mirrornode.hedera.com';
        break;
      case 'mainnet':
        host = 'https://mainnet.mirrornode.hedera.com';
        break;
      case 'testnet':
        host = 'https://testnet.mirrornode.hedera.com';
        break;
      default:
        host = 'http://localhost:5551';
    }
  } else {
    host = options.host;
  }

  // to:do sanitize transaction and env values
  const {transactionId} = options;
  const url = `${host}/api/v1/transactions/${transactionId}/stateproof`;
  const storedFile = options.file;
  const source = _.isUndefined(storedFile) ? url : storedFile;
  console.log(`Initializing state proof for transactionId: ${transactionId} from source: ${source}`);

  return {transactionId, url, storedFile};
};
module.exports = {
  startUpScreen,
};
