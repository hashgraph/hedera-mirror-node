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

const validateDate = (date, _) => {
  if (!datePattern.test(date)) {
    program.error(`error: Invalid date ${date}. Expected YYYY-MM-DD`);
  }
  return date;
};

const now = new Date();
const today = now.toISOString().slice(0, 10);
now.setDate(now.getDate() + 1);
const tomorrow = now.toISOString().slice(0, 10);

program.command('report')
.description('Generate a report for the given accounts.')
.requiredOption('-a, --account <accountId...>', 'The accounts to include in the report')
.requiredOption('-f, --from-date <YYYY-MM-DD>', 'The day the report should start (inclusive)', validateDate, tomorrow)
.addOption(new Option('-n, --network <network>', 'The Hedera network to connect to')
.choices(['mainnet', 'testnet', 'previewnet'])
.default('mainnet')
.makeOptionMandatory())
.option('-s, --separate', 'Whether separate reports for each account or a combined report should be generated')
.requiredOption('-t, --to-date <YYYY-MM-DD>', 'The day the report should end (exclusive)', validateDate, today)
.showHelpAfterError()
.action(report);

program.parse();

