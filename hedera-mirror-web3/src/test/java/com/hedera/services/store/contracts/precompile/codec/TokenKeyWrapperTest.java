/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH;
import static org.hyperledger.besu.datatypes.Address.ALTBN128_MUL;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TokenKeyWrapperTest {

    private static final int ED25519_BYTE_LENGTH = 32;

    private byte[] contractAddress = ALTBN128_MUL.toArrayUnsafe();
    private ContractID contractId = ContractID.newBuilder()
            .setShardNum(Ints.fromByteArray(Arrays.copyOfRange(contractAddress, 0, 4)))
            .setRealmNum(Longs.fromByteArray(Arrays.copyOfRange(contractAddress, 4, 12)))
            .setContractNum(Longs.fromByteArray(Arrays.copyOfRange(contractAddress, 12, 20)))
            .build();

    @Test
    void createsExpectedTokenKeyWrapper() {
        var tokenKeyWrapper = new TokenKeyWrapper(
                2, new KeyValueWrapper(false, null, new byte[ED25519_BYTE_LENGTH], new byte[] {}, null));
        assertTrue(tokenKeyWrapper.isUsedForKycKey());

        tokenKeyWrapper = new TokenKeyWrapper(
                4,
                new KeyValueWrapper(
                        false,
                        null,
                        new byte[ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH],
                        new byte[ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH],
                        null));
        assertTrue(tokenKeyWrapper.isUsedForFreezeKey());

        tokenKeyWrapper = new TokenKeyWrapper(
                8, new KeyValueWrapper(false, null, new byte[ED25519_BYTE_LENGTH], new byte[1], contractId));
        assertTrue(tokenKeyWrapper.isUsedForWipeKey());

        tokenKeyWrapper = new TokenKeyWrapper(
                16,
                new KeyValueWrapper(
                        false, null, new byte[] {}, new byte[ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH - 1], contractId));
        assertTrue(tokenKeyWrapper.isUsedForSupplyKey());

        tokenKeyWrapper = new TokenKeyWrapper(
                32, new KeyValueWrapper(false, contractId, new byte[ED25519_BYTE_LENGTH], new byte[] {}, contractId));
        assertTrue(tokenKeyWrapper.isUsedForFeeScheduleKey());

        tokenKeyWrapper = new TokenKeyWrapper(
                64, new KeyValueWrapper(false, contractId, new byte[ED25519_BYTE_LENGTH], new byte[] {}, null));
        assertTrue(tokenKeyWrapper.isUsedForPauseKey());
    }
}
