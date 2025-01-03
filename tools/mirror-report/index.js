#!/usr/bin/env node

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

import {Option, program} from 'commander';
import {report} from './src/report.js';
import {parseAccount, validateDate} from './src/utils.js';

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
