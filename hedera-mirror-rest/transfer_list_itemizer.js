/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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
const math = require('mathjs');
const config = require('./config.js');
const utils = require('./utils.js');

const TREASURY_ACCOUNT_ID = utils.TREASURY_ACCOUNT_ID;
const ZERO = math.bignumber(0);

/**
 * Keep track of 2 sets of data during itemization of a transfer list:
 * <ul>
 *   <li>The list of itemized transfers (ie - the results/output)</li>
 *   <li>The set of as-of-yet-unallocated (to the itemized list) transfer amounts per account</li>
 * </ul>
 */
class ItemizationState {
  constructor() {
    this.resultTransfers = [];
    this.unallocatedTransferAmounts = {};
  }

  /**
   * Update the unallocatedTransferAmounts map to ADD _value_ to the unallocated amount for _accountId_
   * @param {String} accountId
   * @param {math.bignumber} value
   */
  addToMap(accountId, value) {
    let l = this.unallocatedTransferAmounts[accountId];
    if (undefined === l) {
      this.unallocatedTransferAmounts[accountId] = value;
    } else {
      this.unallocatedTransferAmounts[accountId] = value.plus(l);
    }
  }

  /**
   * Append {account: accountId, amount: value} to the resultTransfers list.
   * @param {String} accountId
   * @param {math.bignumber} value
   */
  appendTransfer(accountId, value) {
    if (!value.isZero()) {
      this.resultTransfers.push({account: accountId, amount: Number(value)});
    }
  }

  /**
   * Extract an <account, amount> from the unallocatedTransferAmounts map into the resultTransfers list.
   * For example:
   *   if the map has {accountA: 12000}
   *   extract(accountA, 12000) will:
   *     append {account: accountA, amount: 12000} to the resultTransfers list
   *     subtract that 12000 from the unallocatedTransferAmounts, resulting in it being {accountA: 0}
   */
  extract(accountId, value) {
    this.appendTransfer(accountId, value);
    this.addToMap(accountId, ZERO.minus(value));
  }

  toString() {
    return (
      '{unallocated: [' +
      Object.keys(this.unallocatedTransferAmounts)
        .map(k => k + ' ' + this.unallocatedTransferAmounts[k])
        .join(' , ') +
      '], transfers: [' +
      this.resultTransfers.map(transfer => transfer.account + ' ' + transfer.amount).join(' , ') +
      ']}'
    );
  }
}

class TransferListItemizer {
  /**
   * @param transaction the (output/json)-style transaction object (must contain charged_tx_fee, node, transaction_id,
   * and transfers).
   * @param nonFeeTransfers a list of non-fee transfers for this transaction
   */
  constructor(transaction, nonFeeTransfers) {
    this.transaction = transaction;
    this.nonFeeTransfers = nonFeeTransfers || [];
  }

  /**
   * Update this.transaction.transfers to be itemized.
   */
  itemize() {
    let state = new ItemizationState();

    const payerAccountId = this.transaction['transaction_id'].split('-')[0];
    const nodeAccountId = this.transaction['node'];
    const chargedTxFee = math.bignumber(this.transaction['charged_tx_fee']);

    // Step 1 - aggregate all transfers from the TransactionRecord (transaction result) into
    // state.unallocatedTransferAmounts.
    state.addToMap(nodeAccountId, ZERO);
    state.addToMap(TREASURY_ACCOUNT_ID, ZERO);
    state.addToMap(payerAccountId, ZERO);
    this.transaction.transfers.forEach(transfer => {
      state.addToMap(transfer.account, math.bignumber(transfer.amount));
    });

    // Step 2 - extract non_fee_transfers from unallocatedTransferAmounts
    this.nonFeeTransfers.forEach(transfer => {
      let accountId = config.shard + '.' + transfer.realm_num + '.' + transfer.entity_num;
      state.extract(accountId, math.bignumber(transfer.amount));
    });

    // Step 3 - extract threshold records
    Object.keys(state.unallocatedTransferAmounts).forEach(accountId => {
      if (accountId != TREASURY_ACCOUNT_ID && accountId != nodeAccountId) {
        // Transfers that aren't for the treasury or node contain threshold record amounts and, for the payer,
        // additionally the (-)charged_tx_fee.
        let amount = state.unallocatedTransferAmounts[accountId];
        if (accountId == payerAccountId) {
          amount = amount.plus(chargedTxFee); // 0 or else the payer's threshold record fee (ie -500)
        }
        if (amount != 0) {
          // (negative) Threshold record fee for this account.
          state.extract(accountId, amount);
          state.extract(TREASURY_ACCOUNT_ID, ZERO.minus(amount));
        }
      }
    });

    // Step 4 - extract node and network+service fees.
    let nodeFee, netServiceFee;
    if (payerAccountId == nodeAccountId) {
      netServiceFee = state.unallocatedTransferAmounts[TREASURY_ACCOUNT_ID];
      nodeFee = chargedTxFee.minus(netServiceFee);
    } else {
      nodeFee = state.unallocatedTransferAmounts[nodeAccountId];
      netServiceFee = chargedTxFee.minus(nodeFee);
    }
    state.extract(payerAccountId, ZERO.minus(nodeFee));
    state.extract(nodeAccountId, nodeFee);
    state.extract(payerAccountId, ZERO.minus(netServiceFee));
    state.extract(TREASURY_ACCOUNT_ID, netServiceFee);

    // Step 5 - Sanity check. The unallocated map should be all zeroes. If not, add those amounts to the resultTransfers
    // list.
    Object.keys(state.unallocatedTransferAmounts).forEach(accountId => {
      let amount = state.unallocatedTransferAmounts[accountId];
      if (!amount.isZero()) {
        state.extract(accountId, amount);
      }
    });

    return state.resultTransfers.sort(utils.compareTransfers);
  }
}

module.exports = TransferListItemizer;
