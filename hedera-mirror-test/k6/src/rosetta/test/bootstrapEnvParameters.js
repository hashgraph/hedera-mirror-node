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


import {
  computeAccountParameters,
  computeBlockFromNetwork,
  computeTransactionFromBlock,
  computeNetworkInfo,
  setDefaultValuesForEnvParameters
} from "../../lib/parameters.js";
import {currencyHbar} from "./constants.js";
import {urlPrefix} from "../../lib/constants.js";

const setupTestParameters = () => {
  setDefaultValuesForEnvParameters();

  const baseUrl = __ENV['BASE_URL'];
  const networkName = computeNetworkInfo(baseUrl).name;
  const accountParameters = computeAccountParameters({baseApiUrl: `${baseUrl}${urlPrefix}`});

  const blockIdentifier = computeBlockFromNetwork(baseUrl, networkName);
  const networkIdentifier = {
    blockchain: 'Hedera',
    network: networkName,
    sub_network_identifier: {
      network: 'shard 0 realm 0',
    }
  };
  const transactionIdentifier = computeTransactionFromBlock(baseUrl, networkIdentifier, blockIdentifier);
  const accountIdentifier = {
    address: `0.0.${accountParameters.DEFAULT_ACCOUNT_ID}`,
    metadata: {},
  };
  return {
    baseUrl,
    accountIdentifier,
    blockIdentifier,
    networkIdentifier,
    operations: [
      {
        operation_identifier: {index: 0},
        type: 'CRYPTOTRANSFER',
        account: accountIdentifier,
        amount: {
          value: '-100',
          currency: currencyHbar,
        }
      },
      {
        operation_identifier: {index: 1},
        type: 'CRYPTOTRANSFER',
        account: accountIdentifier,
        amount: {
          value: '100',
          currency: currencyHbar,
        }
      }
    ],
    publicKey: {
      hex_bytes: __ENV['ROSETTA_ACCOUNT_PUBLIC_KEY'] || 'eba8cc093a83a4ca5e813e30d8c503babb35c22d57d34b6ec5ac0303a6aaba77',
      curve_type: 'edwards25519'
    },
    signatureType: 'ed25519',
    signingTransaction: __ENV['ROSETTA_SIGNING_PAYLOAD'] || '967f26876ad492cc27b4c384dc962f443bcc9be33cbb7add3844bc864de047340e7a78c0fbaf40ab10948dc570bbc25edb505f112d0926dffb65c93199e6d507',
    signedTransaction: __ENV['ROSETTA_SIGNED_TRANSACTION'] || '0x0aaa012aa7010a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f12660a640a20eba8cc093a83a4ca5e813e30d8c503babb35c22d57d34b6ec5ac0303a6aaba771a40793de745bc19dd8fe8e817891f51b8fe1e259c2e6428bd7fa075b181585a2d40e3666a7c9a1873abb5433ffe1414502836d8d37082eaf94a648b530e9fa78108',
    transactionIdentifier,
    transactionSignature: __ENV['ROSETTA_TRANSACTION_SIGNATURE'] || '793de745bc19dd8fe8e817891f51b8fe1e259c2e6428bd7fa075b181585a2d40e3666a7c9a1873abb5433ffe1414502836d8d37082eaf94a648b530e9fa78108',
    unsignedTransaction: __ENV['ROSETTA_UNSIGNED_TRANSACTION'] || '0a432a410a3d0a140a0c08feafcb840610ae86c0db03120418d8c307120218041880c2d72f2202087872180a160a090a0418d8c30710cf0f0a090a0418fec40710d00f1200'
  };
};

export {setupTestParameters};
