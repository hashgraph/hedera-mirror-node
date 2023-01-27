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

import asn1js from 'asn1js';

const BLOCK_NAME = 'ObjectIdentifierValueBlock';
const ID = '1.3.101.112'; // per RFC 8410 https://tools.ietf.org/html/rfc8410#section-9

const derToEd25519 = function (der) {
  try {
    const buf = new Uint8Array(Buffer.from(der, 'hex')).buffer;
    const asn = asn1js.fromBER(buf);
    if (asn.offset === -1) {
      return null; // Not a valid DER/BER format
    }

    const asn1Result = asn.result.toJSON();

    // Check if it is a ED25519 key
    if (asn1Result.valueBlock.value.length < 1 || asn1Result.valueBlock.value[0].valueBlock.value.length < 1) {
      return null;
    }
    const {valueBlock} = asn1Result.valueBlock.value[0].valueBlock.value[0];
    if (valueBlock.blockName === BLOCK_NAME && valueBlock.value === ID) {
      const ed25519Key = asn1Result.valueBlock.value[1].valueBlock.valueHex;
      return ed25519Key.toLowerCase();
    }
    return null;
  } catch (err) {
    return null;
  }
};

export default {
  derToEd25519,
};
