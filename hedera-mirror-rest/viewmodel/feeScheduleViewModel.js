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

const _ = require('lodash');
const {
  proto: {HederaFunctionality},
} = require('@hashgraph/proto');
const utils = require('../utils');
const constants = require('../constants');

/**
 * Fee schedule view model
 */
class FeeScheduleViewModel {
  static currentLabel = 'current_';
  static nextLabel = 'next_';
  static enabledTxTypesMap = {
    [HederaFunctionality.ContractCall]: 'ContractCall',
    [HederaFunctionality.ContractCreate]: 'ContractCreate',
    [HederaFunctionality.EthereumTransaction]: 'EthereumTransaction',
  };
  /**
   * Constructs fee schedule view model
   *
   * @param {FeeSchedule} feeSchedule
   * @param {ExchangeRate} exchangeRate
   * @param {'asc'|'desc'} order
   * @param {boolean} current Default value is true
   */
  constructor(feeSchedule, exchangeRate, order, current = true) {
    const prefix = current ? FeeScheduleViewModel.currentLabel : FeeScheduleViewModel.nextLabel;
    const schedule = feeSchedule[`${prefix}feeSchedule`];
    const hbarRate = exchangeRate[`${prefix}hbar`];
    const centRate = exchangeRate[`${prefix}cent`];

    this.fees = schedule
      .filter(({hederaFunctionality}) =>
        _.keys(FeeScheduleViewModel.enabledTxTypesMap).includes(hederaFunctionality.toString())
      )
      .map(({fees, hederaFunctionality}) => {
        const fee = _.first(fees);
        const gasPrice = _.result(fee, 'servicedata.gas.toNumber');
        const tinyBars = utils.convertGasPriceToTinyBars(gasPrice, hbarRate, centRate);

        // make sure the gas price is converted successfully, otherwise something is wrong with gasPrice or exchange rate, so skip the current fee
        if (_.isNil(tinyBars)) {
          return null;
        }

        return {
          gas: tinyBars,
          transaction_type: FeeScheduleViewModel.enabledTxTypesMap[hederaFunctionality],
        };
      })
      .filter((f) => !_.isNil(f))
      .sort((curr, next) => {
        // localCompare by default sorts the array in ascending order, so when its multiplied by -1 the sort order is reversed
        const sortOrder = order.toLowerCase() === constants.orderFilterValues.ASC ? 1 : -1;
        return curr.transaction_type.localeCompare(next.transaction_type) * sortOrder;
      });

    this.timestamp = utils.nsToSecNs(feeSchedule.timestamp);
  }
}

module.exports = FeeScheduleViewModel;
