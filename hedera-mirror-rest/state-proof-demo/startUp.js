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
const yargs = require('yargs'); //  simplify user input
const chalk = require('chalk'); //  pretty up request info
const boxen = require('boxen'); //  emphasize request info

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

const welcomeScreen = () => {
  // const welcome = `Welcome to your on demand Hedera Transaction State Proof CLI!!!, ${options.transactionId} on ${options.env}!`;
  const greeting = chalk.bold(`Welcome to your on demand Hedera Transaction State Proof CLI!!!, ${options.name}!`);

  const msgBox = boxen(greeting, boxenOptions);
  console.log(msgBox);

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

  return {transactionId, url, sample};
};
module.exports = {
  welcomeScreen,
};
