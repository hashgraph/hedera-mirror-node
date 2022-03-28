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
 * TokenAllowance view model
 */
class TokenAllowanceViewModel {
  /**
   * Constructs tokenAllowance view model
   *
   * @param {TokenAllowance} tokenAllowance
   */
  constructor(tokenAllowance) {
    this.amount = Number(tokenAllowance.amount);
    this.owner = EntityId.parse(tokenAllowance.owner).toString();
    this.spender = EntityId.parse(tokenAllowance.spender).toString();
    this.token_id = EntityId.parse(tokenAllowance.tokenId).toString();
    this.timestamp = {
      from: utils.nsToSecNs(tokenAllowance.timestampRange.begin),
      to: utils.nsToSecNs(tokenAllowance.timestampRange.end),
    };
  }
}

module.exports = TokenAllowanceViewModel;
