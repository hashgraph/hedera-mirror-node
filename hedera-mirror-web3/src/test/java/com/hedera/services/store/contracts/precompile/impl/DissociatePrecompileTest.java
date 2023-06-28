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

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKEN;
import static com.hedera.services.utils.EntityIdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.HrcParams;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DissociatePrecompileTest {

    private final TokenID tokenID = asToken("0.0.777");

    private DissociatePrecompile dissociatePrecompile;

    @Mock
    private HrcParams hrcParams;

    @Mock
    private PrecompilePricingUtils pricingUtils;

    private final Address callerAccountAddress = Address.fromHexString("0x000000000000000000000000000000000000077e");

    @BeforeEach
    void setup() {
        dissociatePrecompile = new DissociatePrecompile(pricingUtils);
    }

    @Test
    void testBodyWithHrcParams() {
        final Bytes dissociateToken = Bytes.of(Integers.toBytes(ABI_ID_DISSOCIATE_TOKEN));
        given(hrcParams.token()).willReturn(tokenID);
        given(hrcParams.senderAddress()).willReturn(callerAccountAddress);

        final var accountID =
                EntityIdUtils.accountIdFromEvmAddress(Objects.requireNonNull(callerAccountAddress.toArray()));
        final var expected = dissociatePrecompile.createDissociate(Dissociation.singleDissociation(accountID, tokenID));
        final var result = dissociatePrecompile.body(dissociateToken, a -> a, hrcParams);

        assertEquals(expected.getTokenDissociate(), result.getTokenDissociate());
    }
}
