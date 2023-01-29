/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import exec from 'k6/execution';
import {textSummary} from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

import {getTestReportFilename, markdownReport} from '../lib/common.js';
import {funcs, options, scenarioDurationGauge, scenarios} from './test/index.js';

function handleSummary(data) {
  return {
    stdout: textSummary(data, {indent: ' ', enableColors: true}),
    [getTestReportFilename()]: markdownReport(data, false, scenarios),
  };
}

function run() {
  const scenario = exec.scenario;
  funcs[scenario.name]();
  scenarioDurationGauge.add(Date.now() - scenario.startTime, {scenario: scenario.name});
}

export {handleSummary, options, run};
