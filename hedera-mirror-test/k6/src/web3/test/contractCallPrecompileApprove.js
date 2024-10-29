/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const selector = '0xceda64c4'; //approveExternal
const token = __ENV.TOKEN_ADDRESS;
const spender = __ENV.ACCOUNT_ADDRESS;
const amount = __ENV.AMOUNT;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallPrecompileApprove') // use unique scenario name among all tests
  .selector(selector)
  .args([token, spender, amount])
  .to(contract)
  .build();

export {options, run};
