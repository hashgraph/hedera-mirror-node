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

import http from 'k6/http';
import {check} from 'k6';
import * as utils from '../../lib/common.js';
import { sleep } from 'k6';

const resultField = 'result';

function isNonErrorResponse(response) {
  //instead of doing multiple type checks,
  //lets just do the normal path and return false,
  //if an exception happens.
  try {
    if (response.status !== 200) {
      return false;
    }
    const body = JSON.parse(response.body);
    return body.hasOwnProperty(resultField);
  } catch (e) {
    return false;
  }
}

const jsonPost = (url, payload) =>
  http.post(url, payload, {
    headers: {
      'Content-Type': 'application/json',
    },
  });

function ContractCallTestScenarioBuilder() {
  this._name = null;
  this._selector = null;
  this._args = null;
  this._to = null;
  this._scenario = null;
  this._tags = {};
  this._url = `${__ENV.BASE_URL}/api/v1/contracts/call`;

  this.build = function () {
    const that = this;
    return {
      options: utils.getOptionsWithScenario(that._name, that._scenario, that._tags),
      run: function (testParameters) {
        const response = jsonPost(
          that._url,
          JSON.stringify({
            to: that._to,
            data: that._selector + that._args,
          })
        );
        check(response, {[`${that._name}`]: (r) => isNonErrorResponse(r)});
      },
    };
  };

  this.name = function (name) {
    this._name = name;
    return this;
  };

  this.selector = function (selector) {
    this._selector = selector;
    return this;
  };

  this.args = function (args) {
    this._args = args.join('');
    return this;
  };

  this.to = function (to) {
    this._to = to;
    return this;
  };

  this.scenario = function (scenario) {
    this._scenario = scenario;
    return this;
  };

  this.tags = function (tags) {
    this._tags = tags;
    return this;
  };

  return this;
}

function ContractCallScenarioBuilder() {
  this._name = null;
  this._selector = null;
  this._args = null;
  this._to = null;
  this._scenario = null;
  this._tags = {};
  this._sleep = 0;

  this._block = null;
  this._data = null;
  this._gas = null;
  this._from = null;
  this._value = null;
  this._estimate = null;

  this._url = `${__ENV.BASE_URL}/api/v1/contracts/call`;

  this.build = function () {
    const that = this;
    return {
      options: utils.getOptionsWithScenario(that._name, that._scenario, that._tags),
      run: function (testParameters) {
        const payload = {
          to: that._to,
          estimate: that._estimate || true, // Set default to true
        };

        if (that._selector && that._args) {
          payload.data = that._selector + that._args;
        } else {
          Object.assign(payload, {
            block: that._block,
            data: that._data,
            gas: that._gas,
            from: that._from,
            value: that._value,
          });
        }
        const response = jsonPost(that._url, JSON.stringify(payload));
        check(response, {[`${that._name}`]: (r) => isNonErrorResponse(r)});
        if (that._sleep > 0) {
          sleep(that._sleep);
        }
      },
    };
  };

  // Common methods
  this.name = function (name) {
    this._name = name;
    return this;
  };

  this.to = function (to) {
    this._to = to;
    return this;
  };

  this.scenario = function (scenario) {
    this._scenario = scenario;
    return this;
  };

  this.tags = function (tags) {
    this._tags = tags;
    return this;
  };

  // Methods specific to ContractCallTestScenarioBuilder
  this.selector = function (selector) {
    this._selector = selector;
    return this;
  };

  this.args = function (args) {
    this._args = args.join('');
    return this;
  };

  // Methods specific to ContractCallEstimateTestScenarioBuilder
  this.block = function (block) {
    this._block = block;
    return this;
  };

  this.data = function (data) {
    this._data = data;
    return this;
  };

  this.gas = function (gas) {
    this._gas = gas;
    return this;
  };

  this.from = function (from) {
    this._from = from;
    return this;
  };

  this.value = function (value) {
    this._value = value;
    return this;
  };

  this.estimate = function (estimate) {
    this._estimate = estimate;
    return this;
  };

  this.sleep = function (sleep) {
    this._sleep = sleep;
    return this;
  }

  return this;
}


export {isNonErrorResponse, jsonPost, ContractCallTestScenarioBuilder, ContractCallScenarioBuilder};
