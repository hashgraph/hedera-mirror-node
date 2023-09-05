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

import {buildScenario} from './common.js';
import {SharedArray} from 'k6/data';

const allData = new SharedArray('estimateCreateNFT', function () {
  return JSON.parse(open('./data/estimate.json')).estimateCreateNFTWithCustomFees;
});

const data = allData[__VU % allData.length];

const params = {
  BLOCK: data.block,
  DATA: data.data,
  TO: data.to,
  GAS: data.gas,
  FROM: data.from,
  VALUE: data.value,
  NAME: data.name,
  SLEEP: data.sleep,
};

const {options, run} = buildScenario(params);

export {options, run};
