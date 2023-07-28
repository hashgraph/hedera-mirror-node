/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

class FixedFee {
  /**
   * Parses fixed_fee from element in custom_fee.fixed_fees jsonb column
   */
  constructor(fixedFee) {
    this.allCollectorsAreExempt = fixedFee.all_collectors_are_exempt;
    this.amount = fixedFee.amount;
    this.collectorAccountId = fixedFee.collector_account_id;
    this.denominatingTokenId = fixedFee.denominating_token_id;
  }

  static ALL_COLLECTORS_ARE_EXEMPT = 'all_collectors_are_exempt';
  static AMOUNT = `amount`;
  static COLLECTOR_ACCOUNT_ID = `collector_account_id`;
  static DENOMINATING_TOKEN_ID = `denominating_token_id`;
}

export default FixedFee;
