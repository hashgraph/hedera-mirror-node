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

const BaseAllowanceViewModel = require('./baseAllowanceViewModel');
const EntityId = require('../entityId');

/**
 * TokenAllowance view model
 */
class TokenAllowanceViewModel extends BaseAllowanceViewModel {
  /**
   * Constructs tokenAllowance view model
   *
   * @param {TokenAllowance} tokenAllowance
   */
  constructor(tokenAllowance) {
    super(tokenAllowance);
    this.amount_granted = Number(tokenAllowance.amount);
    this.token_id = EntityId.parse(tokenAllowance.tokenId).toString();
  }
}

module.exports = TokenAllowanceViewModel;
