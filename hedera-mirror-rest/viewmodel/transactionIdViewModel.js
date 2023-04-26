/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */

import {proto} from '@hashgraph/proto';

import EntityId from '../entityId.js';
import {nsToSecNs} from '../utils.js';

/**
 * TransactionId view model
 */
class TransactionIdViewModel {
  /**
   * Constructs transactionId view model from proto transaction id or TransactionId model
   *
   * @param {TransactionId|TransactionID} transactionId
   */
  constructor(transactionId) {
    if (transactionId instanceof proto.TransactionID) {
      // handle proto format
      const {accountID, transactionValidStart, nonce, scheduled} = transactionId;
      this.account_id = EntityId.of(accountID.shardNum, accountID.realmNum, accountID.accountNum).toString();
      this.nonce = nonce;
      this.scheduled = scheduled;
      this.transaction_valid_start = `${transactionValidStart.seconds}.${transactionValidStart.nanos
        .toString()
        .padStart(9, '0')}`;
    } else {
      // handle db format. Handle nil case for nonce and scheduled
      this.account_id = EntityId.parse(transactionId.payerAccountId).toString();
      this.nonce = transactionId.nonce;
      this.scheduled = transactionId.scheduled;
      this.transaction_valid_start = nsToSecNs(transactionId.validStartTimestamp);
    }
  }
}

export default TransactionIdViewModel;
