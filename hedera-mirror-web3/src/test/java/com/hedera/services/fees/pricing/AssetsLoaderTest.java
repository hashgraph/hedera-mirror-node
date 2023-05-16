/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.fees.pricing;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static com.hederahashgraph.api.proto.java.SubType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class AssetsLoaderTest {
    private final AssetsLoader subject = new AssetsLoader();

    @Test
    void canonicalPricesPassSpotChecks() throws IOException {
        // setup:
        final var nftXferPrice = BigDecimal.valueOf(1, 3);
        final var defaultScUpdPrice = BigDecimal.valueOf(26, 3);
        final var defaultTokenAssocPrice = BigDecimal.valueOf(5, 2);

        // given:
        final var prices = subject.loadCanonicalPrices();

        // then:
        assertEquals(nftXferPrice, prices.get(CryptoTransfer).get(TOKEN_NON_FUNGIBLE_UNIQUE));
        assertEquals(defaultScUpdPrice, prices.get(ContractUpdate).get(DEFAULT));
        assertEquals(defaultTokenAssocPrice, prices.get(TokenAssociateToAccount).get(DEFAULT));
    }

    static class RequiredPriceTypesTest {
        @Test
        void knowsTypedFunctions() {
            // expect:
            assertEquals(
                    EnumSet.of(
                            DEFAULT,
                            TOKEN_FUNGIBLE_COMMON,
                            TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES,
                            TOKEN_NON_FUNGIBLE_UNIQUE,
                            TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES),
                    RequiredPriceTypes.requiredTypesFor(CryptoTransfer));
            assertEquals(
                    EnumSet.of(TOKEN_FUNGIBLE_COMMON, TOKEN_NON_FUNGIBLE_UNIQUE),
                    RequiredPriceTypes.requiredTypesFor(TokenMint));
            assertEquals(
                    EnumSet.of(TOKEN_FUNGIBLE_COMMON, TOKEN_NON_FUNGIBLE_UNIQUE),
                    RequiredPriceTypes.requiredTypesFor(TokenBurn));
            assertEquals(
                    EnumSet.of(TOKEN_FUNGIBLE_COMMON, TOKEN_NON_FUNGIBLE_UNIQUE),
                    RequiredPriceTypes.requiredTypesFor(TokenAccountWipe));
            assertEquals(
                    EnumSet.of(
                            TOKEN_FUNGIBLE_COMMON, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES,
                            TOKEN_NON_FUNGIBLE_UNIQUE, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES),
                    RequiredPriceTypes.requiredTypesFor(TokenCreate));
            assertEquals(
                    EnumSet.of(DEFAULT, SCHEDULE_CREATE_CONTRACT_CALL),
                    RequiredPriceTypes.requiredTypesFor(ScheduleCreate));
        }

        @Test
        void isUninstantiable() {
            assertThrows(IllegalStateException.class, RequiredPriceTypes::new);
        }
    }
}
