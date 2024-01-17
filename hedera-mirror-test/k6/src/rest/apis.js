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

import exec from 'k6/execution';
import {textSummary} from 'https://jslib.k6.io/k6-summary/0.0.3/index.js';

import {getTestReportFilename, markdownReport} from '../lib/common.js';
import {funcs, getUrlFuncs, options, requiredParameters, scenarioDurationGauge, scenarios} from './test/index.js';
import {setupTestParameters} from './libex/parameters.js';

function handleSummary(data) {
  return {
    stdout: textSummary(data, {indent: ' ', enableColors: true}),
    [getTestReportFilename()]: markdownReport(data, true, funcs, scenarios, getUrlFuncs),
  };
}

function run(testParameters) {
  const scenario = exec.scenario;
  funcs[scenario.name](testParameters);
  scenarioDurationGauge.add(Date.now() - scenario.startTime, {scenario: scenario.name});
}

export {handleSummary, options, run};

export const setup = () => setupTestParameters(requiredParameters);
