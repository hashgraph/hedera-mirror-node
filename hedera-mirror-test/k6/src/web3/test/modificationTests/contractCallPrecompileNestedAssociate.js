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

import {ContractCallTestScenarioBuilder} from '../common.js';
import {PrecompileModificationTestTemplate} from '../commonPrecompileModificationFunctionsTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const account = __ENV.ACCOUNT_ADDRESS;
const token = __ENV.TOKEN_ADDRESS;
const selector = '0x437dffd5'; //nestedAssociateTokenExternal
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'contractCallPrecompileNestedAssociate';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} = runMode==="false"
    ? new PrecompileModificationTestTemplate(testName, true)
    : new ContractCallTestScenarioBuilder().name(testName) // use unique scenario name among all tests
    .selector(selector)
    .args([account, token])
    .to(contract)
    .shouldRevert(true)
    .build();

export {options, run};
