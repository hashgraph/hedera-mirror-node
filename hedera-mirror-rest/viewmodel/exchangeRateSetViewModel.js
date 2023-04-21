/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import ExchangeRateViewModel from './exchangeRateViewModel';
import {nsToSecNs} from '../utils';

/**
 * Exchange rate set view model
 */
class ExchangeRateSetViewModel {
  static currentLabel = 'current_';
  static nextLabel = 'next_';

  /**
   * Constructs exchange rate set view model
   *
   * @param {ExchangeRateSet} exchangeRateSet
   */
  constructor(exchangeRateSet) {
    this.current_rate = new ExchangeRateViewModel(exchangeRateSet, ExchangeRateSetViewModel.currentLabel);
    this.next_rate = new ExchangeRateViewModel(exchangeRateSet, ExchangeRateSetViewModel.nextLabel);
    this.timestamp = nsToSecNs(exchangeRateSet.timestamp);
  }
}

export default ExchangeRateSetViewModel;
