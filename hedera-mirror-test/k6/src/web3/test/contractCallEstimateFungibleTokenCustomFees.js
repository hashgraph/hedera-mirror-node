/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import {SharedArray} from 'k6/data';
import {ContractCallTestScenarioBuilder} from './common.js';

const k6name = 'contractCallEstimateFungibleTokenCustomFees';
const allData = new SharedArray(k6name, () => {
  return JSON.parse(open('./resources/estimate.json')).estimateCreateFungibleTokenWithCustomFees;
});

const data = allData[__VU % allData.length];

const {options, run} = new ContractCallTestScenarioBuilder()
  .name(k6name)
  .estimate(true)
  .block(data.block)
  .data(data.data)
  .gas(data.gas)
  .from(data.from)
  .value(data.value)
  .to(data.to)
  .sleep(data.sleep)
  .build();

export {options, run};
