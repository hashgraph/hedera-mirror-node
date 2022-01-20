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

import http from "k6/http";

import {TestScenarioBuilder} from '../../lib/common.js';
import * as constants from './constants.js';

// the public key doesn't have to belong to the account in the payload since it's merely used to verify the signature
const payload = JSON.stringify({
  network_identifier: constants.networkIdentifier,
  unsigned_transaction: __ENV.ROSETTA_UNSIGNED_TRANSACTION,
  signatures: [
    {
      signing_payload: {
        account_identifier: constants.accountIdentifier,
        hex_bytes: __ENV.ROSETTA_SIGNING_PAYLOAD,
        signature_type: constants.signatureType,
      },
      public_key: constants.publicKey,
      signature_type: constants.signatureType,
      hex_bytes: __ENV.ROSETTA_TRANSACTION_SIGNATURE,
    },
  ],
});
const urlTag = '/construction/combine';
const url = __ENV.BASE_URL + urlTag;

const {options, run} = new TestScenarioBuilder()
  .name('constructionCombine') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request(() => http.post(url, payload))
  .check('ConstructionCombine OK', (r) => r.status === 200)
  .build();

export {options, run};
