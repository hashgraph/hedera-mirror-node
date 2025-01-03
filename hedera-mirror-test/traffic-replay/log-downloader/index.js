/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
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
 */

import {hideBin} from 'yargs/helpers';
import parseDuration from 'parse-duration';
import yargs from 'yargs';

import Downloader from './downloader.js';
import GoReplayConverter from './converter.js';

global.log = (msg) => {
  const timestamp = new Date().toISOString();
  console.log(`${timestamp} ${msg}`);
};

const main = async () => {
  const args = yargs(hideBin(process.argv))
    .option('duration', {
      alias: 'd',
      default: '10s',
      demandOption: true,
      description: 'Duration to download logs, e.g., 1s, 10m, 1h',
      type: 'string',
    })
    .option('filter', {
      demandOption: true,
      description: 'Google Cloud logging filter for the resource to get logs from',
      type: 'string',
    })
    .option('from', {
      alias: 'f',
      demandOption: true,
      description: 'From time to download logs, in ISO 8601 format, e.g. 2024-10-01T10:05:30Z',
      type: 'string',
    })
    .option('output-file', {
      alias: 'o',
      demandOption: true,
      description: 'Output file',
      type: 'string',
    })
    .option('project', {
      alias: 'p',
      demandOption: true,
      description: 'Google Cloud project id',
      type: 'string',
    })
    .parse();

  const fromDate = new Date(args.from);
  const toDate = new Date(fromDate.getTime() + parseDuration(args.duration));

  const filter = [
    args.filter,
    `resource.labels.project_id="${args.project}"`,
    `timestamp>="${fromDate.toISOString()}"`,
    `timestamp<="${toDate.toISOString()}"`,
  ].join(' ');

  const converter = new GoReplayConverter(args.outputFile);
  const downloader = new Downloader(filter, args.project, converter);

  await downloader.download();
};

await main();
