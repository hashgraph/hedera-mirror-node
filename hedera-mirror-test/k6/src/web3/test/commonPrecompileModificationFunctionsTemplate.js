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

import {SharedArray} from 'k6/data';
import {ContractCallTestScenarioBuilder} from './common.js';

function PrecompileModificationTestTemplate(key, shouldRevert) {
  const data = new SharedArray(key, () => {
    return JSON.parse(open('../resources/modificationFunctions.json'))[key];
  });

  const {options, run} = new ContractCallTestScenarioBuilder()
    .name(key)
    .vuData(data)
    .shouldRevert(shouldRevert)
    .build();

  return {options, run};
}

export {PrecompileModificationTestTemplate};
