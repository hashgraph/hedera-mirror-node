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

const utils = require('../utils');
const EntityId = require('../entityId');
const NftModel = require('../models/nftModel');

/**
 * NFT view model
 */
class NftViewModel {
  constructor(nftModel) {
    this.account_id = EntityId.fromEncodedId(nftModel.account_id).toString();
    this.created_timestamp = utils.nsToSecNs(nftModel.created_timestamp);
    this.deleted = nftModel.deleted;
    this.metadata = utils.encodeBase64(nftModel.metadata);
    this.modified_timestamp = utils.nsToSecNs(nftModel.modified_timestamp);
    this.serial_number = Number(nftModel.serial_number);
    this.token_id = EntityId.fromEncodedId(nftModel.token_id).toString();
  }

  static fromDb(dbRow) {
    const nftTransferModel = new NftModel(dbRow);
    return new NftViewModel(nftTransferModel);
  }
}

module.exports = NftViewModel;
