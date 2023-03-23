/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.utils;

import static com.hedera.services.utils.EntityIdUtils.parseAccount;
import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdUtilsTest {


    @ParameterizedTest
    @CsvSource({
        "0,Cannot parse '0' due to only 0 dots",
        "0.a.0,Argument 'literal=0.a.0' is not an account",
        "...,Argument 'literal=...' is not an account",
        "1.2.3.4,Argument 'literal=1.2.3.4' is not an account",
        "1.2.three,Argument 'literal=1.2.three' is not an account",
        "1.2.333333333333333333333,Cannot parse '1.2.333333333333333333333' due to overflow"
    })
    void rejectsInvalidAccountLiterals(final String badLiteral, final String desiredMsg) {
        final var e = assertThrows(IllegalArgumentException.class, () -> parseAccount(badLiteral));
        assertEquals(desiredMsg, e.getMessage());
    }

    @Test
    void asSolidityAddressBytesWorksProperly() {
        final var id = AccountID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setAccountNum(3)
                .build();

        final var result = EntityIdUtils.asEvmAddress(id);

        final var expectedBytes = new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3};

        assertArrayEquals(expectedBytes, result);
    }

    @Test
    void asSolidityAddressBytesFromToken() {
        final var id = TokenID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setTokenNum(3)
                .build();

        final var result = EntityIdUtils.asEvmAddress(id);

        final var expectedBytes = new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3};

        assertArrayEquals(expectedBytes, result);
    }

    @Test
    void asContractWorks() {
        final var expected = ContractID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setContractNum(3)
                .build();
        final var id = AccountID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setAccountNum(3)
                .build();

        final var cid = EntityIdUtils.asContract(id);

        assertEquals(expected, cid);
    }
}
