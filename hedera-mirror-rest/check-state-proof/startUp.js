#!/usr/bin/env node
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
    describe: 'REST API host. Default is testnet. This overrides the value of the env if also provided.',
    type: 'string',
    demandOption: false,
  })
  .option('e', {
    alias: 'env',
    describe: 'Your environment e.g. local|mainnet|previewnet|testnet',
    type: 'string',
    demandOption: false,
  }).argv;

const startUpScreen = () => {
  const greeting = chalk.bold(`Hedera Transaction State Proof Checker CLI`);
  const msgBox = boxen(greeting, boxenOptions);
  console.log(msgBox);

  const {transactionId} = options;
  const storedFile = options.file;
  let source = storedFile; // default source to filePath
  let url;

  // update source creating a url based on env and or host if no filePath provided
  if (_.isUndefined(storedFile)) {
    let host;
    // if host parameter was not passed then set host according to env.
    if (_.isUndefined(options.host)) {
      switch (options.env) {
        case 'local':
          host = 'http://localhost:5551';
          break;
        case 'mainnet':
          host = 'https://mainnet.mirrornode.hedera.com';
          break;
        case 'previewnet':
          host = 'https://previewnet.mirrornode.hedera.com';
          break;
        case 'testnet':
          host = 'https://testnet.mirrornode.hedera.com';
          break;
        default:
          throw Error(`Invalid env value provided: ${options.env}. Check --help option to see correct usage`);
      }
    } else {
      host = options.host;
    }

    url = `${host}/api/v1/transactions/${transactionId}/stateproof`;
    source = url;
  }

  console.log(`Initializing state proof for transaction ID ${transactionId} from source: ${source}`);
  return {transactionId, url, storedFile};
};
module.exports = {
  startUpScreen,
};
