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

import EntityId from '../entityId';

/**
 * Assessed custom fee view model
 */
class AssessedCustomFeeViewModel {
  /**
   * Constructs the assessed custom fee view model
   *
   * @param {AssessedCustomFee} assessedCustomFee
   */
  constructor(assessedCustomFee) {
    this.amount = assessedCustomFee.amount;
    this.collector_account_id = EntityId.parse(assessedCustomFee.collectorAccountId).toString();
    this.token_id = EntityId.parse(assessedCustomFee.tokenId, {isNullable: true}).toString();

    if (assessedCustomFee.effectivePayerAccountIds != null) {
      this.effective_payer_account_ids = assessedCustomFee.effectivePayerAccountIds.map((payer) =>
        EntityId.parse(payer).toString()
      );
    } else {
      this.effective_payer_account_ids = [];
    }
  }
}

export default AssessedCustomFeeViewModel;
