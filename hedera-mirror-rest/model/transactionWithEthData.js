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

const Transaction = require('./transaction');

class TransactionWithEthData extends Transaction {
  /**
   * Parses ethereumTransaction table columns into the transaction object
   */
  constructor(txWithEthData) {
    super(txWithEthData);
    this.access_list = txWithEthData.access_list;
    this.chain_id = txWithEthData.chain_id;
    this.gas_price = txWithEthData.gas_price;
    this.max_fee_per_gas = txWithEthData.max_fee_per_gas;
    this.max_priority_fee_per_gas = txWithEthData.max_priority_fee_per_gas;
    this.signature_r = txWithEthData.signature_r;
    this.signature_s = txWithEthData.signature_s;
    this.recovery_id = txWithEthData.recovery_id;
    this.value = txWithEthData.value;
    this.ethType = txWithEthData.ethtype;
    this.ethHash = txWithEthData.ethhash;
  }
}

module.exports = TransactionWithEthData;
