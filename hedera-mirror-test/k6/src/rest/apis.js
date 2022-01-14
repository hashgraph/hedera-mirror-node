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

import exec from 'k6/execution';
import {textSummary} from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

import {funcs, options, scenarioDurationGauge} from './test/index.js';

const checksRegex = /^checks{.*scenario:.*}$/;
const httpReqDurationRegex = /^http_req_duration{.*scenario:.*}$/;
const httpReqsRegex = /^http_reqs{.*scenario:.*}$/;
const scenarioDurationRegex = /^scenario_duration{.*scenario:.*}$/;
const scenarioRegex = /scenario:([^,}]+)/;

function getScenario(metricKey) {
  const match = scenarioRegex.exec(metricKey);
  return match[1];
}

function markdownReport(data) {
  const header = `| URL | VUE | pass% | RPS | http_req_duration | Comment |
|----------|-----|-------|-----|-------------------|---------|`;

  // collect the metrics
  const {metrics} = data;
  const scenarioMetrics = {};
  for (const key in metrics) {
    let metric;
    if (checksRegex.test(key)) {
      metric = 'checks';
    } else if (httpReqDurationRegex.test(key)) {
      metric = 'http_req_duration';
    } else if (httpReqsRegex.test(key)) {
      metric = 'http_reqs';
    } else if (scenarioDurationRegex.test(key)) {
      metric = 'scenario_duration';
    } else {
      continue;
    }

    const scenario = getScenario(key);
    scenarioMetrics[scenario] = Object.assign(scenarioMetrics[scenario] || {}, {[metric]: metrics[key]});
  }

  const scenarioUrls = {};
  for (const name of Object.keys(options.scenarios)) {
    scenarioUrls[name] = options.scenarios[name].tags.url;
  }

  // generate the markdown report
  let markdown = `${header}\n`;
  for (const scenario of Object.keys(scenarioMetrics).sort()) {
    const scenarioMetric = scenarioMetrics[scenario];
    const passPercentage = (scenarioMetric['checks'].values.rate * 100.0).toFixed(2);
    const httpReqs = scenarioMetric['http_reqs'].values.count;
    const duration = scenarioMetric['scenario_duration'].values.value; // in ms
    const rps = ((httpReqs * 1.0 / duration) * 1000).toFixed(2);
    const httpReqDuration = scenarioMetric['http_req_duration'].values.avg.toFixed(2);

    markdown += `| ${scenarioUrls[scenario]} | ${__ENV.DEFAULT_VUS} | ${passPercentage} | ${rps}/s | ${httpReqDuration}ms | |\n`;
  }

  return markdown;
}

function handleSummary(data) {
  return {
    'stdout': textSummary(data, {indent: '  ', enableColors: true}),
    'report.md': markdownReport(data),
  };
}

function run() {
  const scenario = exec.scenario;
  funcs[scenario.name]();
  scenarioDurationGauge.add(Date.now() - scenario.startTime, {scenario: scenario.name});
}

export {options, handleSummary, run};
