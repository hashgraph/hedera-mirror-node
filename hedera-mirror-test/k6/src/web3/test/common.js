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

import {check, sleep} from 'k6';
import {vu} from 'k6/execution';
import http from 'k6/http';
import * as utils from '../../lib/common.js';

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
  this._args = null;
  this._name = null;
  this._selector = null;
  this._scenario = null;
  this._tags = {};
  this._to = null;
  this._vuData = null;

  this._block = 'latest';
  this._data = null;
  this._estimate = null;
  this._from = null;
  this._gas = 15000000;
  this._value = null;

  this._url = `${__ENV.BASE_URL_PREFIX}/contracts/call`;

  this.build = function () {
    const that = this;
    return {
      options: utils.getOptionsWithScenario(that._name, that._scenario, that._tags),
      run: function (testParameters) {
        let sleepSecs = 0;
        const payload = {
          to: that._to,
          estimate: that._estimate || false, // Set default to false
        };

        if (that._selector && that._args) {
          payload.data = that._selector + that._args;
        } else {
          const {_vuData: vuData} = that;
          const data = vuData
            ? Object.assign({}, vuData[vu.idInTest % vuData.length])
            : {
                block: that._block,
                data: that._data,
                gas: that._gas,
                from: that._from,
                value: that._value,
              };
          sleepSecs = data.sleep;
          delete data.sleep;

          Object.assign(payload, data);
        }

        const response = jsonPost(that._url, JSON.stringify(payload));
        check(response, {[`${that._name}`]: (r) => isNonErrorResponse(r)});
        if (sleepSecs > 0) {
          sleep(sleepSecs);
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

  // Methods specific to eth_call
  this.selector = function (selector) {
    this._selector = selector;
    return this;
  };

  this.args = function (args) {
    this._args = args.join('');
    return this;
  };

  // Methods specific to eth_estimateGas
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

  this.vuData = function (vuData) {
    this._vuData = vuData;
    return this;
  };

  return this;
}

export {isNonErrorResponse, jsonPost, ContractCallTestScenarioBuilder};
