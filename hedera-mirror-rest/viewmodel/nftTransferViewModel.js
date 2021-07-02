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

const EntityId = require('../entityId');
const {NftTransfer} = require('../model');

/**
 * Nft transfer view model
 */
class NftTransferViewModel {
  constructor(nftTransferModel) {
    this.receiver_account_id = EntityId.fromEncodedId(nftTransferModel.receiverAccountId, true).toString();
    this.sender_account_id = EntityId.fromEncodedId(nftTransferModel.senderAccountId, true).toString();
    this.serial_number = nftTransferModel.serialNumber;
    this.token_id = EntityId.fromEncodedId(nftTransferModel.tokenId).toString();
  }

  static fromDb(dbRow) {
    const nftTransferModel = new NftTransfer(dbRow);
    return new NftTransferViewModel(nftTransferModel);
  }
}

module.exports = NftTransferViewModel;
