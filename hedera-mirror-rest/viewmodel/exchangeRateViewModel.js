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

'use strict';

const utils = require('../utils');

/**
 * Exchange rate view model
 */
class ExchangeRateViewModel {
  /**
   * Constructs exchange rate view model
   *
   * @param {ExchangeRate} exchangeRate
   */
  constructor(exchangeRate, prefix) {
    this.cent_equivalent = exchangeRate[`${prefix}cent`];
    this.expiration_time = exchangeRate[`${prefix}expiration`];
    this.hbar_equivalent = exchangeRate[`${prefix}hbar`];
  }
}

module.exports = ExchangeRateViewModel;
