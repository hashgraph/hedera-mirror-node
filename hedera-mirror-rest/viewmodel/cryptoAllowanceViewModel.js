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

const EntityId = require('../entityId');
const utils = require('../utils');

/**
 * CryptoAllowance view model
 */
class CryptoAllowanceViewModel {
  /**
   * Constructs cryptoAllowance view model
   *
   * @param {CryptoAllowance} cryptoAllowance
   */
  constructor(cryptoAllowance) {
    this.amount = Number(cryptoAllowance.amount);
    this.owner = EntityId.parse(cryptoAllowance.owner).toString();
    this.payer_account_id = EntityId.parse(cryptoAllowance.payerAccountId).toString();
    this.spender = EntityId.parse(cryptoAllowance.spender).toString();
    this.timestamp = {
      from: utils.nsToSecNs(cryptoAllowance.timestampRange.begin),
      to: utils.nsToSecNs(cryptoAllowance.timestampRange.end),
    };
  }
}

module.exports = CryptoAllowanceViewModel;
