/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import { Gauge } from 'k6/metrics';

const timeRegex = /^[0-9]+s$/;

const SCENARIO_DURATION_METRIC_NAME = 'scenario_duration';

function getNextStartTime(startTime, duration, gracefulStop) {
  if (!timeRegex.test(startTime)) {
    throw new Error(`Invalid startTime ${startTime}`);
  }

  if (!timeRegex.test(duration)) {
    throw new Error(`Invalid duration ${duration}`);
  }

  if (!timeRegex.test(gracefulStop)) {
    throw new Error(`Invalid gracefulStop ${gracefulStop}`);
  }

  return `${parseInt(startTime) + parseInt(gracefulStop) + parseInt(duration)}s`;
}

function getOptionsWithScenarios(...names) {
  const scenarios = names.reduce((res, name) => {
    res[name] = Object.assign({}, scenario);
    return res;
  }, {});
  return Object.assign({}, options, {scenarios});
}

function getSequentialTestScenarios(tests) {
  let startTime = '0s';
  let duration = '0s';
  let gracefulStop = '0s';

  const funcs = {};
  const scenarios = {};
  const thresholds = {};
  for (const testName of Object.keys(tests).sort()) {
    const testModule = tests[testName];
    const testScenarios = testModule.options.scenarios;
    const testThresholds = testModule.options.thresholds;
    for (const scenarioName in  testScenarios) {
      const scenario = Object.assign({}, testScenarios[scenarioName]);
      funcs[scenarioName] = testModule[scenario.exec];
      scenarios[scenarioName] = scenario;

      // update the scenario's startTime, so scenarios run in sequence
      scenario.startTime = getNextStartTime(startTime, duration, gracefulStop);
      startTime = scenario.startTime;
      duration = scenario.duration;
      gracefulStop = scenario.gracefulStop;

      // thresholds
      const tag = `scenario:${scenarioName}`;
      for (const name in testThresholds) {
        if (name === 'http_req_duration') {
          thresholds[`${name}{${tag},expected_response:true}`] = testThresholds[name];
        } else {
          thresholds[`${name}{${tag}}`] = testThresholds[name];
        }
      }
      thresholds[`http_reqs{${tag}}`] = ['count>0'];
      thresholds[`${SCENARIO_DURATION_METRIC_NAME}{${tag}}`] = ['value>0'];
    }
  }

  const testOptions = Object.assign({}, options, {scenarios, thresholds});

  return {funcs, options: testOptions, scenarioDurationGauge: new Gauge(SCENARIO_DURATION_METRIC_NAME)};
}

const options = {
  thresholds: {
    checks: ['rate>=0.95'], // at least 95% should pass the checks,
    http_req_duration: ['p(95)<500'], // 95% requests should receive response in less than 500ms
  },
  insecureSkipTLSVerify: true,
  noConnectionReuse: true,
  noVUConnectionReuse: true,
};

const scenario = {
  duration: __ENV.DEFAULT_DURATION,
  exec: 'run',
  executor: 'constant-vus',
  gracefulStop: '15s',
  vus: __ENV.DEFAULT_VUS,
};

export {getNextStartTime, getOptionsWithScenarios, getSequentialTestScenarios, options, scenario};
