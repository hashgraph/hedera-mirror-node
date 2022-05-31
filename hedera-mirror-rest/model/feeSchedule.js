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
 *      http =//www.apache.org/licenses/LICENSE-2.0
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
const {proto} = require('@hashgraph/proto');
const {FileDecodeError} = require('../errors/fileDecodeError');

class FeeSchedule {
  static FEE_DIVISOR_FACTOR = 1000;
  /**
   * Parses fee schedule into object
   */
  constructor(feeSchedule) {
    let currentAndNextFeeSchedule = {};

    try {
      currentAndNextFeeSchedule = proto.CurrentAndNextFeeSchedule.decode(Buffer.from(feeSchedule.file_data, 'hex'));
    } catch (error) {
      throw new FileDecodeError(error.message);
    }

    this.current_feeSchedule = this._normalize(currentAndNextFeeSchedule.currentFeeSchedule);
    this.next_feeSchedule = this._normalize(currentAndNextFeeSchedule.nextFeeSchedule);
    this.timestamp = feeSchedule.consensus_timestamp;
  }

  _normalize = (feeSchedule) => {
    const transactionFeeSchedule = _.get(feeSchedule, 'transactionFeeSchedule') || [];
    return transactionFeeSchedule.map(({fees, hederaFunctionality}) => ({
      hederaFunctionality,
      fees: fees.map((fee) => ({
        servicedata: {
          gas: fee.servicedata.gas.low,
        },
        subType: fee.subType,
      })),
    }));
  };
}

module.exports = FeeSchedule;
