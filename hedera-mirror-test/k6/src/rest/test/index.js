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
import * as accounts from './accounts.js';
import * as accountsBalanceFalse from './accountsBalanceFalse.js';
import * as accountsBalanceFalsePubkey from './accountsBalanceFalsePubkey.js';
import * as accountsBalanceGt0 from './accountsBalanceGt0.js';
import * as accountsBalanceGt0Pubkey from './accountsBalanceGte0Pubkey.js';
import * as accountsBalanceNe from './accountsBalanceNe.js';
import * as accountsCryptoAllowance from './accountsCryptoAllowance.js';
import * as accountsId from './accountsId.js';
import * as accountsIdNe from './accountsIdNe.js';

// add test modules here
const tests = {
  accounts,
  accountsBalanceFalse,
  accountsBalanceFalsePubkey,
  accountsBalanceGt0,
  accountsBalanceGt0Pubkey,
  accountsBalanceNe,
  accountsCryptoAllowance,
  accountsId,
  accountsIdNe,
};

const {funcs, options, requiredParameters, scenarioDurationGauge, scenarios} = getSequentialTestScenarios(tests);

export {funcs, options, requiredParameters, scenarioDurationGauge, scenarios};
