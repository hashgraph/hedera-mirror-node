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

const TransferListItemizer = require('../transfer_list_itemizer.js');
const PAYER = '0.0.123';
const TRANSACTION_ID = PAYER + '-1580123456-123456789';
const NODE = '0.0.3';
const NODE_PAYER_TRANSACTION_ID = NODE + '-1580123456-123456789';
const TREASURY = '0.0.98';
const SENDER = '0.0.11111';
const RECIPIENT1 = '0.0.98765';
const RECIPIENT2 = '0.0.98766';

const NODE_FEE = 20;
const SERVICE_FEE = 70;
const NETWORK_FEE = 10;
const CHARGED_TX_FEE = NODE_FEE + SERVICE_FEE + NETWORK_FEE;
const THRESHOLD_RECORD_FEE = 1;

const TRANSFER_AMOUNT1 = 1000;
const TRANSFER_AMOUNT2 = 50;

/**
 * Map an array of [account_id_string, amount] to [{amount: number, account: string}].
 * @param arr
 * @returns {{amount: number, account: string}}
 */
const toTransfer = function(arr) {
  return {account: arr[0], amount: +arr[1]};
};

/**
 * Map an array of [account_id_string, amount] to DB results [{amount, entity_num, realm_num}].
 * @param arr
 * @returns {{amount: number, entity_num: number, realm_num: number}}
 */
const toNonFeeTransfer = function(arr) {
  const parts = arr[0].split('.');
  return {entity_num: +parts[2], realm_num: +parts[1], amount: +arr[1]};
};

describe('TransferListItemizer', () => {
  test('no transfers', () => {
    const cut = createTestInput(TRANSACTION_ID, NODE, 0, [], []);
    const itemizedList = cut.itemize();
    expect(itemizedList).toStrictEqual([]);
  });

  test('100 fee, no transfer, aggregated transferlist, no threshold records', () => {
    const expected = [
      [NODE, NODE_FEE],
      [TREASURY, NETWORK_FEE + SERVICE_FEE],
      [PAYER, 0 - NETWORK_FEE - SERVICE_FEE],
      [PAYER, 0 - NODE_FEE]
    ];
    const cut = createTestInput(
      TRANSACTION_ID,
      NODE,
      CHARGED_TX_FEE,
      [
        [PAYER, 0 - CHARGED_TX_FEE],
        [NODE, NODE_FEE],
        [TREASURY, NETWORK_FEE + SERVICE_FEE]
      ],
      []
    );
    const itemizedList = cut.itemize();
    expect(itemizedList).toStrictEqual(expected.map(toTransfer));
  });

  test('100 fee, no transfer, R3 pre-itemized transferlist, no threshold records', () => {
    const expected = [
      [NODE, NODE_FEE],
      [TREASURY, NETWORK_FEE + SERVICE_FEE],
      [PAYER, 0 - NETWORK_FEE - SERVICE_FEE],
      [PAYER, 0 - NODE_FEE]
    ];
    // R3 had a different breakout.
    // payer rows for service+node (-90) and network (-10), treasury for network and service.
    const cut = createTestInput(
      TRANSACTION_ID,
      NODE,
      CHARGED_TX_FEE,
      [
        [PAYER, 0 - NODE_FEE - SERVICE_FEE],
        [PAYER, 0 - NETWORK_FEE],
        [NODE, NODE_FEE],
        [TREASURY, SERVICE_FEE],
        [TREASURY, NETWORK_FEE]
      ],
      []
    );
    const itemizedList = cut.itemize();
    expect(itemizedList).toStrictEqual(expected.map(toTransfer));
  });

  test('100 fee, payer=sender, 1 payer transfer, aggregated transferlist, no threshold records', () => {
    const expected = [
      [NODE, NODE_FEE],
      [TREASURY, SERVICE_FEE + NETWORK_FEE],
      [PAYER, 0 - TRANSFER_AMOUNT1],
      [PAYER, 0 - SERVICE_FEE - NETWORK_FEE],
      [PAYER, 0 - NODE_FEE],
      [RECIPIENT1, TRANSFER_AMOUNT1]
    ];
    const cut = createTestInput(
      TRANSACTION_ID,
      NODE,
      CHARGED_TX_FEE,
      [
        [PAYER, 0 - CHARGED_TX_FEE - TRANSFER_AMOUNT1],
        [RECIPIENT1, TRANSFER_AMOUNT1],
        [NODE, NODE_FEE],
        [TREASURY, SERVICE_FEE + NETWORK_FEE]
      ],
      [
        [PAYER, 0 - TRANSFER_AMOUNT1],
        [RECIPIENT1, TRANSFER_AMOUNT1]
      ]
    );
    const itemizedList = cut.itemize();
    expect(itemizedList).toStrictEqual(expected.map(toTransfer));
  });

  test('100 fee, payer != sender, 2 transfers, aggregated transferlist, sender threshold record', () => {
    const expected = [
      [NODE, NODE_FEE],
      [TREASURY, THRESHOLD_RECORD_FEE],
      [TREASURY, NETWORK_FEE + SERVICE_FEE],
      [PAYER, 0 - NETWORK_FEE - SERVICE_FEE],
      [PAYER, 0 - NODE_FEE],
      [SENDER, 0 - TRANSFER_AMOUNT1 - TRANSFER_AMOUNT2],
      [SENDER, 0 - THRESHOLD_RECORD_FEE],
      [RECIPIENT1, TRANSFER_AMOUNT1],
      [RECIPIENT2, TRANSFER_AMOUNT2]
    ];
    const cut = createTestInput(
      TRANSACTION_ID,
      NODE,
      CHARGED_TX_FEE,
      [
        [PAYER, 0 - CHARGED_TX_FEE],
        [SENDER, 0 - TRANSFER_AMOUNT1 - TRANSFER_AMOUNT2 - THRESHOLD_RECORD_FEE],
        [RECIPIENT1, TRANSFER_AMOUNT1],
        [RECIPIENT2, TRANSFER_AMOUNT2],
        [NODE, NODE_FEE],
        [TREASURY, SERVICE_FEE + NETWORK_FEE + THRESHOLD_RECORD_FEE]
      ],
      [
        [SENDER, 0 - TRANSFER_AMOUNT1 - TRANSFER_AMOUNT2],
        [RECIPIENT1, TRANSFER_AMOUNT1],
        [RECIPIENT2, TRANSFER_AMOUNT2]
      ]
    );
    const itemizedList = cut.itemize();
    expect(itemizedList).toStrictEqual(expected.map(toTransfer));
  });

  test('100 fee, payer = node, 1 transfer, aggregated transferlist, recipient threshold record', () => {
    const expected = [
      [NODE, 0 - TRANSFER_AMOUNT1],
      [NODE, 0 - NETWORK_FEE - SERVICE_FEE],
      [NODE, 0 - NODE_FEE],
      [NODE, NODE_FEE],
      [TREASURY, THRESHOLD_RECORD_FEE],
      [TREASURY, NETWORK_FEE + SERVICE_FEE],
      [RECIPIENT1, 0 - THRESHOLD_RECORD_FEE],
      [RECIPIENT1, TRANSFER_AMOUNT1]
    ];
    const cut = createTestInput(
      NODE_PAYER_TRANSACTION_ID,
      NODE,
      CHARGED_TX_FEE,
      [
        [RECIPIENT1, TRANSFER_AMOUNT1 - THRESHOLD_RECORD_FEE],
        [NODE, 0 - TRANSFER_AMOUNT1 - NETWORK_FEE - SERVICE_FEE],
        [TREASURY, NETWORK_FEE + SERVICE_FEE + THRESHOLD_RECORD_FEE]
      ],
      [
        [NODE, 0 - TRANSFER_AMOUNT1],
        [RECIPIENT1, TRANSFER_AMOUNT1]
      ]
    );
    const itemizedList = cut.itemize();
    expect(itemizedList).toStrictEqual(expected.map(toTransfer));
  });
});

const createTestInput = function(transactionId, nodeAccountId, chargedTransactionFee, transferList, nonFeeTransfers) {
  const transaction = {
    charged_tx_fee: chargedTransactionFee,
    node: nodeAccountId,
    transaction_id: transactionId,
    transfers: transferList.map(toTransfer)
  };
  return new TransferListItemizer(transaction, nonFeeTransfers.map(toNonFeeTransfer));
};
