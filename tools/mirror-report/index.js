#!/usr/bin/env node --experimental-specifier-resolution=node

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
import {report} from './src/report';

const today = new Date().toISOString().slice(0, 10);

program.command('report')
.description('Generate a report for the given accounts.')
.requiredOption('-a, --account <accountId...>', 'The accounts to include in the report')
.requiredOption('-d, --date <YYYY-MM-DD>', 'The day the report should cover', today)
.addOption(new Option('-n, --network <network>', 'The Hedera network to connect to')
.choices(['mainnet', 'testnet', 'previewnet'])
.default('mainnet')
.makeOptionMandatory())
.action(report);

program.parse();

