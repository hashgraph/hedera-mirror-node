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
 *      http =//www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

'use strict';

const {proto} = require('@hashgraph/proto');
const {FileDecodeError} = require('../errors/fileDecodeError');

class ExchangeRate {
  /**
   * Parses exchange rate into object
   * Currently from proto, eventually from exchange_rate table
   */
  constructor(exchangeRate) {
    let exchangeRateSet = {};

    try {
      exchangeRateSet = proto.ExchangeRateSet.decode(Buffer.from(exchangeRate.file_data, 'hex'));
    } catch (error) {
      throw new FileDecodeError(`${error.message}`);
    }

    this.current_cent = exchangeRateSet.currentRate.centEquiv;
    this.current_expiration = exchangeRateSet.currentRate.expirationTime.seconds.low;
    this.current_hbar = exchangeRateSet.currentRate.hbarEquiv;
    this.next_cent = exchangeRateSet.nextRate.centEquiv;
    this.next_expiration = exchangeRateSet.nextRate.expirationTime.seconds.low;
    this.next_hbar = exchangeRateSet.nextRate.hbarEquiv;
    this.timestamp = exchangeRate.consensus_timestamp;
  }
}

module.exports = ExchangeRate;
