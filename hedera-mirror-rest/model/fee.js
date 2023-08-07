/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

class Fee {
  constructor(fee) {
    this.allCollectorsAreExempt = fee.all_collectors_are_exempt;
    this.collectorAccountId = fee.collector_account_id;
  }

  static ALL_COLLECTORS_ARE_EXEMPT = 'all_collectors_are_exempt';
  static COLLECTOR_ACCOUNT_ID = `collector_account_id`;
}

export default Fee;
