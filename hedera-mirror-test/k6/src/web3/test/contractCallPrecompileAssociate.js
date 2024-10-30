/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import {PrecompileModificationTestTemplate} from './commonPrecompileModificationFunctionsTemplate.js';
import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const account = __ENV.ACCOUNT_ADDRESS;
const token = __ENV.TOKEN_ADDRESS;
const runMode = __ENV.RUN_WITH_VARIABLES
const selector = '0xd91cfc95'; //associateTokenExternal
const testName = 'contractCallPrecompileAssociate'

//If RUN_WITH_VARIABLES=true will run tests from the __ENV variables
const {options, run} = runMode==="true"
    ? new ContractCallTestScenarioBuilder().name(testName) // use unique scenario name among all tests
    .selector(selector)
    .args([account, token])
    .to(contract)
    .build()
    : new PrecompileModificationTestTemplate(testName, false);

export {options, run};