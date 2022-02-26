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
import {setupTestParameters} from "./bootstrapEnvParameters.js";

const urlTag = '/rosetta/construction/combine';

const {options, run} = new TestScenarioBuilder()
  .name('constructionCombine') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = testParameters.BASE_URL + urlTag;
    // the public key doesn't have to belong to the account in the payload since it's merely used to verify the signature
    const payload = JSON.stringify({
      network_identifier: testParameters.networkIdentifier,
      unsigned_transaction: testParameters.unsignedTransaction,
      signatures: [
        {
          signing_payload: {
            account_identifier: testParameters.accountIdentifier,
            hex_bytes: testParameters.signingTransaction,
            signature_type: testParameters.signatureType,
          },
          public_key: testParameters.publicKey,
          signature_type: testParameters.signatureType,
          hex_bytes: testParameters.transactionSignature,
        },
      ],
    });
    return http.post(url, payload);
  })
  .check('ConstructionCombine OK', (r) => r.status === 200)
  .build();

export {options, run};

export const setup = setupTestParameters;
