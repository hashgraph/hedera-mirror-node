/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const _ = require('lodash');

const NftModel = require('../model/nft');
const utils = require('../utils');

/**
 * Nft business model
 */
class NftService {
  constructor() {}

  static nftByIdQuery = 'select * from nft where token_id = $1 and serial_number = $2';

  async getNft(tokenId, serialNumber) {
    const {rows} = await utils.queryQuietly(NftService.nftByIdQuery, tokenId, serialNumber);
    return _.isEmpty(rows) ? null : new NftModel(rows[0]);
  }
}

module.exports = new NftService();
