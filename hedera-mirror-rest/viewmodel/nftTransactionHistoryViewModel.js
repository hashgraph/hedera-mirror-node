/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import _ from 'lodash';

import EntityId from '../entityId';
import {createTransactionId, nsToSecNs} from '../utils';
import {TransactionType} from '../model';

/**
 * Nft transaction history transfer view model
 */
class NftTransactionHistoryViewModel {
  constructor(nftTransferModel, transactionModel) {
    this.consensus_timestamp = nsToSecNs(nftTransferModel.consensusTimestamp);
    this.is_approval = _.isNil(nftTransferModel.isApproval) ? false : nftTransferModel.isApproval;
    this.nonce = transactionModel.nonce;
    this.receiver_account_id = EntityId.parse(nftTransferModel.receiverAccountId, {isNullable: true}).toString();
    this.sender_account_id = EntityId.parse(nftTransferModel.senderAccountId, {isNullable: true}).toString();
    this.transaction_id = createTransactionId(
      EntityId.parse(transactionModel.payerAccountId).toString(),
      transactionModel.validStartNs
    );
    this.type = TransactionType.getName(transactionModel.type);
  }
}

export default NftTransactionHistoryViewModel;
