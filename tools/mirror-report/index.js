#!/usr/bin/env node

/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import {Option, program} from 'commander';
import {report} from './src/report.js';

const datePattern = /^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])$/;
const accountIdPattern = /^(\d+\.\d+\.)(\d+)(-(\d+\.\d+\.)(\d+))?$/;

const validateDate = (date, _) => {
  if (!datePattern.test(date)) {
    program.error(`error: Invalid date ${date}. Expected YYYY-MM-DD`);
  }
  return date;
};

const parseAccount = (accountId, previous) => {
  const match = accountIdPattern.exec(accountId);

  if (!match) {
    program.error(
      `error: Invalid account ID ${accountId}. Expected a single account like 0.0.2 or a range like 0.0.2-0.0.3`);
  }

  let accountIds = previous instanceof Set ? previous : new Set();

  // Not a range-based account
  if (!match[3]) {
    accountIds.add(accountId);
  } else {
    const prefix = match[1];
    const start = parseInt(match[2]);
    const end = parseInt(match[5]);

    if (end <= start) {
      program.error(`error: Account ID range end ${end} is not after start ${start}`);
    }

    if (prefix !== match[4]) {
      program.error(`error: Account ID range start and end have a different shard and realm prefix`);
    }

    for (let i = start; i <= end; ++i) {
      accountIds.add(`${prefix}${i}`);
    }
  }

  return accountIds;
};

const now = new Date();
const today = now.toISOString().slice(0, 10);
now.setDate(now.getDate() + 1);
const tomorrow = now.toISOString().slice(0, 10);

program.command('report')
.description('Generate a report for the given accounts.')
.requiredOption('-a, --account <accountId...>',
  'The accounts to include in the report. Can be single account or range (e.g. 0.0.3-0.0.9)', parseAccount)
.option('-c, --combined',
  'Whether a single combined report should be generated for all accounts. By default it produces separate reports')
.requiredOption('-f, --from-date <YYYY-MM-DD>', 'The day the report should start (inclusive)', validateDate, today)
.addOption(new Option('-n, --network <network>', 'The Hedera network to connect to')
.choices(['mainnet', 'testnet', 'previewnet'])
.default('mainnet')
.makeOptionMandatory())
.requiredOption('-t, --to-date <YYYY-MM-DD>', 'The day the report should end (exclusive)', validateDate, tomorrow)
.showHelpAfterError()
.action(report);

program.parse();
