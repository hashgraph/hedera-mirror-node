/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import {getSequentialTestScenarios} from '../../lib/common.js';

// import test modules
import * as accountsIdTimestampLte from './accountsIdTimestampLte.js';
import * as balances from './balances.js';
import * as balancesAccount from './balancesAccount.js';
import * as balancesTimestamp from './balancesTimestamp.js';

// add test modules here
const tests = {
  accountsIdTimestampLte,
  balances,
  balancesAccount,
  balancesTimestamp,
};

const {funcs, options, requiredParameters, scenarioDurationGauge, scenarios} = getSequentialTestScenarios(tests);

export {funcs, options, requiredParameters, scenarioDurationGauge, scenarios};
