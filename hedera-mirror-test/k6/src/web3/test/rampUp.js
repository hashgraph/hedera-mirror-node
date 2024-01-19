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

import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.HTS_CONTRACT_ADDRESS;
const selector = '0xbff9834f';
const token = __ENV.TOKEN_ADDRESS;

// call isToken to ramp up
const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallRampUp') // use unique scenario name among all tests
  .args([token])
  .selector(selector)
  .scenario({
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      {
        duration: __ENV.DEFAULT_RAMPUP_DURATION || __ENV.DEFAULT_DURATION,
        target: __ENV.DEFAULT_RAMPUP_VUS || __ENV.DEFAULT_VUS,
      },
    ],
    gracefulRampDown: '0s',
  })
  .to(contract)
  .build();

export {options, run};
