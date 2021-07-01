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
const TransactionTypeService = require('../service/transactionTypesService');

/**
 * Transaction transfer view model
 */
class TransactionViewModel {
  constructor(transactionModel) {
    const validStartTimestamp = transactionModel.validStartNs;
    this.bytes = utils.encodeBase64(transactionModel.transactionBytes);
    this.charged_tx_fee = Number(transactionModel.chargedTxFee);
    this.consensus_timestamp = utils.nsToSecNs(transactionModel.consensusNs);
    this.entity_id = EntityId.fromEncodedId(transactionModel.entityId, true).toString();
    this.max_fee = utils.getNullableNumber(transactionModel.maxFee);
    this.memo_base64 = utils.encodeBase64(transactionModel.memo); // base64 encode
    this.name = transactionModel.name;
    this.node = EntityId.fromEncodedId(transactionModel.nodeAccountId, true).toString();
    this.result = transactionModel.result;
    this.scheduled = transactionModel.scheduled;
    this.token_transfers = this.createTokenTransferList(transactionModel.tokenTransferList);
    this.transaction_hash = utils.encodeBase64(transactionModel.transactionHash);
    this.transaction_id = utils.createTransactionId(
      EntityId.fromEncodedId(transactionModel.payerAccountId).toString(),
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
