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
const utils = require('../utils');
const TransactionTypeService = require('../services/transactionTypesService');
const TransactionModel = require('../models/transactionModel');

/**
 * Transaction transfer view model
 */
class TransactionViewModel {
  constructor(transactionModel) {
    const validStartTimestamp = transactionModel.valid_start_ns;
    this.bytes = utils.encodeBase64(transactionModel.transaction_bytes);
    this.charged_tx_fee = Number(transactionModel.charged_tx_fee);
    this.consensus_timestamp = utils.nsToSecNs(transactionModel.consensus_ns);
    this.entity_id = EntityId.fromEncodedId(transactionModel.entity_id).toString();
    this.id = EntityId.fromEncodedId(transactionModel.id).toString();
    this.max_fee = utils.getNullableNumber(transactionModel.max_fee);
    this.memo_base64 = utils.encodeKey(transactionModel.memo); // base64 encode
    this.name = transactionModel.name;
    this.node = transactionModel.node_account_id;
    this.result = transactionModel.result;
    this.scheduled = transactionModel.scheduled;
    this.token_transfers = this.createTokenTransferList(transactionModel.token_transfer_list);
    this.transaction_hash = utils.encodeBase64(transactionModel.transaction_hash);
    this.transaction_id = utils.createTransactionId(
      EntityId.fromEncodedId(transactionModel.payer_account_id).toString(),
      validStartTimestamp
    );
    this.transfers = [];
    this.type = TransactionTypeService.getName(transactionModel.type);
    this.valid_duration_seconds = utils.getNullableNumber(transactionModel.valid_duration_seconds);
    this.valid_start_timestamp = utils.nsToSecNs(validStartTimestamp);
  }

  /**
   * Creates token transfer list from aggregated array of JSON objects in the query result
   *
   * @param tokenTransferList token transfer list string
   * @return {undefined|{amount: Number, account: string, token_id: string}[]}
   */
  createTokenTransferList(tokenTransferList) {
    if (!tokenTransferList) {
      return undefined;
    }

    return tokenTransferList.map((transfer) => {
      const {token_id: tokenId, account_id: accountId, amount} = transfer;
      return {
        token_id: EntityId.fromEncodedId(tokenId).toString(),
        account: EntityId.fromEncodedId(accountId).toString(),
        amount,
      };
    });
  }
}

module.exports = TransactionViewModel;
