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

const accountIdentifier = {
  address: `0.0.${__ENV.ROSETTA_ACCOUNT_NUM != null ? __ENV.ROSETTA_ACCOUNT_NUM : 98}`,
  metadata: {},
};


const currencyHbar = {
  symbol: 'HBAR',
  decimals: 8,
  metadata: {
    issuer: 'Hedera',
  },
};

const networkIdentifier = {
  blockchain: 'Hedera',
  network: __ENV.ROSETTA_NETWORK != null ? __ENV.ROSETTA_NETWORK : 'mainnet',
  sub_network_identifier: {
    network: 'shard 0 realm 0',
  }
};

const publicKey = {
  hex_bytes: __ENV.ROSETTA_ACCOUNT_PUBLIC_KEY,
  curve_type: 'edwards25519',
};

export {
  accountIdentifier,
  currencyHbar,
  networkIdentifier,
  publicKey
};
