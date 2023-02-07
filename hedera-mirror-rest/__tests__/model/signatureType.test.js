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

import {SignatureType} from '../../model';

describe('getName', () => {
  test('Valid', () => {
    expect(SignatureType.getName(2)).toEqual('CONTRACT');
    expect(SignatureType.getName(3)).toEqual('ED25519');
    expect(SignatureType.getName(4)).toEqual('RSA_3072');
    expect(SignatureType.getName(5)).toEqual('ECDSA_384');
    expect(SignatureType.getName(6)).toEqual('ECDSA_SECP256K1');
  });

  test('Unknown', () => {
    expect(SignatureType.getName(null)).toEqual('UNKNOWN');
    expect(SignatureType.getName(undefined)).toEqual('UNKNOWN');
    expect(SignatureType.getName(-2)).toEqual('UNKNOWN');
    expect(SignatureType.getName(1)).toEqual('UNKNOWN');
    expect(SignatureType.getName(9999999)).toEqual('UNKNOWN');
    expect(SignatureType.getName('sig')).toEqual('UNKNOWN');
  });
});
