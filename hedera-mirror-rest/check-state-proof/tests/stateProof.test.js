/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

'use strict';

const StateProofHandler = require('../stateProofHandler');
const {readJSONFile} = require('../utils');

describe('stateproof sample test', () => {
  test('transaction 0.0.94139-1570800748-313194300 in v2 sample json', () => {
    const stateProofJson = readJSONFile('sample/v2/stateProofSample.json');
    const stateProofManager = new StateProofHandler(stateProofJson, '0.0.94139-1570800748-313194300');
    expect(stateProofManager.runStateProof()).toBe(true);
  });

  test('transaction 0.0.88-1614972043-671238000 in v5 sample json', () => {
    const stateProofJson = readJSONFile('sample/v5/stateProofSample.json');
    const stateProofManager = new StateProofHandler(stateProofJson, '0.0.88-1614972043-671238000');
    expect(stateProofManager.runStateProof()).toBe(true);
  });
});
