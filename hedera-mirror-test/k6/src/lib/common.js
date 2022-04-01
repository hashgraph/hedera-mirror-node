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

import {check} from "k6";
import {Gauge} from 'k6/metrics';
import {setDefaultValuesForEnvParameters} from "./parameters.js";

setDefaultValuesForEnvParameters();

const SCENARIO_DURATION_METRIC_NAME = 'scenario_duration';

const options = {
  thresholds: {
    checks: [`rate>=${__ENV['DEFAULT_PASS_RATE']}`], // at least 95% should pass the checks,
    http_req_duration: [`p(95)<${__ENV['DEFAULT_MAX_DURATION']}`], // 95% requests should receive response in less than 500ms
  },
  insecureSkipTLSVerify: true,
  noConnectionReuse: true,
  noVUConnectionReuse: true,
};

const scenario = {
  duration: __ENV.DEFAULT_DURATION,
  exec: 'run',
  executor: 'constant-vus',
  gracefulStop: (__ENV.DEFAULT_GRACEFUL_STOP != null && __ENV.DEFAULT_GRACEFUL_STOP) || '5s',
  vus: __ENV.DEFAULT_VUS,
};

function getMetricNameWithTags(name, ...tags) {
  return tags.length === 0 ? name : `${name}{${tags}}`;
}

const timeRegex = /^[0-9]+s$/;

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

  return `${parseInt(startTime) + parseInt(duration) + parseInt(gracefulStop)}s`;
}

function getOptionsWithScenario(name, tags = {}) {
  return Object.assign({}, options, {
    scenarios: {
      [name]: Object.assign({}, scenario, {tags}),
    },
  });
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
    for (const [scenarioName, testScenario] of Object.entries(testScenarios)) {
      const scenario = Object.assign({}, testScenario);
      funcs[scenarioName] = testModule[scenario.exec];
      scenarios[scenarioName] = scenario;

      // update the scenario's startTime, so scenarios run in sequence
      scenario.startTime = getNextStartTime(startTime, duration, gracefulStop);
      startTime = scenario.startTime;
      duration = scenario.duration;
      gracefulStop = scenario.gracefulStop;

      // thresholds
      const tag = `scenario:${scenarioName}`;
      for (const [name, threshold] of Object.entries(testThresholds)) {
        if (name === 'http_req_duration') {
          thresholds[getMetricNameWithTags(name, tag, 'expected_response:true')] = threshold;
        } else {
          thresholds[getMetricNameWithTags(name, tag)] = threshold;
        }
      }
      thresholds[getMetricNameWithTags('http_reqs', tag)] = ['count>0'];
      thresholds[getMetricNameWithTags(SCENARIO_DURATION_METRIC_NAME, tag)] = ['value>0'];
    }
  }

  const testOptions = Object.assign({}, options, {scenarios, thresholds});

  return {funcs, options: testOptions, scenarioDurationGauge: new Gauge(SCENARIO_DURATION_METRIC_NAME)};
}

const checksRegex = /^checks{.*scenario:.*}$/;
const httpReqDurationRegex = /^http_req_duration{.*scenario:.*}$/;
const httpReqsRegex = /^http_reqs{.*scenario:.*}$/;
const scenarioDurationRegex = /^scenario_duration{.*scenario:.*}$/;
const scenarioRegex = /scenario:([^,}]+)/;

function getScenario(metricKey) {
  const match = scenarioRegex.exec(metricKey);
  return match[1];
}

function defaultMetrics() {
  return {
    "checks": {
      "values": {
        "rate": 0
      },
    },
    "http_req_duration": {
      "values": {
        "avg": 0
      }
    },
    "http_reqs": {
      "values": {
        "count": 0
      },
    },
    "scenario_duration": {
      "values": {
        "value": 0
      }
    }
  };
}

function markdownReport(data, isFirstColumnUrl, scenarios) {
  const firstColumnName = isFirstColumnUrl ? 'URL' : 'Scenario';
  const header = `| ${firstColumnName} | VUS | Pass% | RPS | Pass RPS | Avg. Req Duration | Comment |
|----------|-----|-------|-----|----------|-------------------|---------|`;

  // collect the metrics
  const {metrics} = data;
  const scenarioMetrics = {};

  for (const [key, value] of Object.entries(metrics)) {
    let name;
    if (checksRegex.test(key)) {
      name = 'checks';
    } else if (httpReqDurationRegex.test(key)) {
      name = 'http_req_duration';
    } else if (httpReqsRegex.test(key)) {
      name = 'http_reqs';
    } else if (scenarioDurationRegex.test(key)) {
      name = 'scenario_duration';
    } else {
      continue;
    }

    const scenario = getScenario(key);
    const existingMetrics = scenarioMetrics[scenario] || defaultMetrics();
    scenarioMetrics[scenario] = Object.assign(existingMetrics, {[name]: value});
  }

  const scenarioUrls = {};
  if (isFirstColumnUrl) {
    for (const [name, scenario] of Object.entries(scenarios)) {
      scenarioUrls[name] = scenario.tags.url;
    }
  }

  // Generate the markdown report
  let markdown = `${header}\n`;
  for (const scenario of Object.keys(scenarioMetrics).sort()) {
    try {
      const scenarioMetric = scenarioMetrics[scenario];
      const passPercentage = (scenarioMetric['checks'].values.rate * 100.0).toFixed(2);
      const httpReqs = scenarioMetric['http_reqs'].values.count;
      const duration = scenarioMetric['scenario_duration'].values.value; // in ms
      const rps = ((httpReqs * 1.0 / duration) * 1000).toFixed(2);
      const passRps = (rps * passPercentage / 100.0).toFixed(2);
      const httpReqDuration = scenarioMetric['http_req_duration'].values.avg.toFixed(2);

      const firstColumn = isFirstColumnUrl ? scenarioUrls[scenario] : scenario;
      markdown += `| ${firstColumn} | ${__ENV.DEFAULT_VUS} | ${passPercentage} | ${rps}/s | ${passRps}/s | ${httpReqDuration}ms | |\n`;
    } catch (err) {
      console.error(`Unable to render report for scenario ${scenario}`);
    }
  }

  return markdown;
}

function TestScenarioBuilder() {
  this._checks = {};
  this._name = null;
  this._request = null;
  this._tags = {};

  this.build = function () {
    const that = this;
    return {
      options: getOptionsWithScenario(that._name, that._tags),
      run: function (testParameters) {
        const response = that._request(testParameters);
        check(response, that._checks);
      },
    };
  }

  this.check = function (name, func) {
    this._checks[name] = func;
    return this;
  }

  this.name = function (name) {
    this._name = name;
    return this;
  }

  this.request = function (func) {
    this._request = func;
    return this;
  }

  this.tags = function (tags) {
    this._tags = tags;
    return this;
  }

  return this;
}

export {getSequentialTestScenarios, markdownReport, TestScenarioBuilder};
