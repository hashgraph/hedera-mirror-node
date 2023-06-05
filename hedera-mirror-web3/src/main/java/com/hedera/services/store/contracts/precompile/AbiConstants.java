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

package com.hedera.services.store.contracts.precompile;

/** All ABI constants used by {@link Precompile} implementations, in one place for easy review. */
public class AbiConstants {

    private AbiConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // **** HIP-206 function selectors ****
    // cryptoTransfer(TokenTransferList[] memory tokenTransfers)
    public static final int ABI_ID_CRYPTO_TRANSFER = 0x189a554c;
    // cryptoTransfer(TransferList memory transferList, TokenTransferList[] memory tokenTransfers)
    public static final int ABI_ID_CRYPTO_TRANSFER_V2 = 0x0e71804f;
    // transferTokens(address token, address[] memory accountId, int64[] memory amount)
    public static final int ABI_ID_TRANSFER_TOKENS = 0x82bba493;
    // transferToken(address token, address sender, address recipient, int64 amount)
    public static final int ABI_ID_TRANSFER_TOKEN = 0xeca36917;
    // transferNFTs(address token, address[] memory sender, address[] memory receiver, int64[]
    // memory
    // serialNumber)
    public static final int ABI_ID_TRANSFER_NFTS = 0x2c4ba191;
    // transferNFT(address token,  address sender, address recipient, int64 serialNum)
    public static final int ABI_ID_TRANSFER_NFT = 0x5cfc9011;
}
