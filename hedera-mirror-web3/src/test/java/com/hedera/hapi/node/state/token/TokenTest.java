/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.hapi.node.state.token;

import static com.hedera.pbj.runtime.ProtoTestTools.BOOLEAN_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.BYTES_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.INTEGER_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.STRING_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.generateListArguments;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class TokenTest extends AbstractStateTest {

    @SuppressWarnings("EqualsWithItself")
    @Test
    void testTestEqualsAndHashCode() {
        if (ARGUMENTS.size() >= 3) {
            final var item1 = ARGUMENTS.get(0);
            final var item2 = ARGUMENTS.get(1);
            final var item3 = ARGUMENTS.get(2);
            assertEquals(item1, item1);
            assertEquals(item2, item2);
            assertEquals(item3, item3);
            assertNotEquals(item1, item2);
            assertNotEquals(item2, item3);
            final var item1HashCode = item1.hashCode();
            final var item2HashCode = item2.hashCode();
            final var item3HashCode = item3.hashCode();
            assertNotEquals(item1HashCode, item2HashCode);
            assertNotEquals(item2HashCode, item3HashCode);
        }
    }

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<Token> ARGUMENTS;

    static {
        final var tokenIdList = TokenIDTest.ARGUMENTS;
        final var nameList = STRING_TESTS_LIST;
        final var symbolList = STRING_TESTS_LIST;
        final var decimalsList = INTEGER_TESTS_LIST;
        final var totalSupplyList = LONG_TESTS_LIST;
        final var treasuryAccountIdList = AccountIDTest.ARGUMENTS;
        final var adminKeyList = KeyTest.ARGUMENTS;
        final var kycKeyList = KeyTest.ARGUMENTS;
        final var freezeKeyList = KeyTest.ARGUMENTS;
        final var wipeKeyList = KeyTest.ARGUMENTS;
        final var supplyKeyList = KeyTest.ARGUMENTS;
        final var feeScheduleKeyList = KeyTest.ARGUMENTS;
        final var pauseKeyList = KeyTest.ARGUMENTS;
        final var lastUsedSerialNumberList = LONG_TESTS_LIST;
        final var deletedList = BOOLEAN_TESTS_LIST;
        final var tokenTypeList = Arrays.asList(TokenType.values());
        final var supplyTypeList = Arrays.asList(TokenSupplyType.values());
        final var autoRenewAccountIdList = AccountIDTest.ARGUMENTS;
        final var autoRenewSecondsList = LONG_TESTS_LIST;
        final var expirationSecondList = LONG_TESTS_LIST;
        final var memoList = STRING_TESTS_LIST;
        final var maxSupplyList = LONG_TESTS_LIST;
        final var pausedList = BOOLEAN_TESTS_LIST;
        final var accountsFrozenByDefaultList = BOOLEAN_TESTS_LIST;
        final var accountsKycGrantedByDefaultList = BOOLEAN_TESTS_LIST;
        final var customFeesList = generateListArguments(CUSTOM_FEE_ARGUMENTS);
        final var metadataList = BYTES_TESTS_LIST;
        final var metadataKeyList = KeyTest.ARGUMENTS;

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(
                        tokenIdList.size(),
                        nameList.size(),
                        symbolList.size(),
                        decimalsList.size(),
                        totalSupplyList.size(),
                        treasuryAccountIdList.size(),
                        adminKeyList.size(),
                        kycKeyList.size(),
                        freezeKeyList.size(),
                        wipeKeyList.size(),
                        supplyKeyList.size(),
                        feeScheduleKeyList.size(),
                        pauseKeyList.size(),
                        lastUsedSerialNumberList.size(),
                        deletedList.size(),
                        tokenTypeList.size(),
                        supplyTypeList.size(),
                        autoRenewAccountIdList.size(),
                        autoRenewSecondsList.size(),
                        expirationSecondList.size(),
                        memoList.size(),
                        maxSupplyList.size(),
                        pausedList.size(),
                        accountsFrozenByDefaultList.size(),
                        accountsKycGrantedByDefaultList.size(),
                        customFeesList.size(),
                        metadataList.size(),
                        metadataKeyList.size())
                .max()
                .getAsInt();
        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new Token(
                        tokenIdList.get(Math.min(i, tokenIdList.size() - 1)),
                        nameList.get(Math.min(i, nameList.size() - 1)),
                        symbolList.get(Math.min(i, symbolList.size() - 1)),
                        decimalsList.get(Math.min(i, decimalsList.size() - 1)),
                        totalSupplyList.get(Math.min(i, totalSupplyList.size() - 1)),
                        treasuryAccountIdList.get(Math.min(i, treasuryAccountIdList.size() - 1)),
                        adminKeyList.get(Math.min(i, adminKeyList.size() - 1)),
                        kycKeyList.get(Math.min(i, kycKeyList.size() - 1)),
                        freezeKeyList.get(Math.min(i, freezeKeyList.size() - 1)),
                        wipeKeyList.get(Math.min(i, wipeKeyList.size() - 1)),
                        supplyKeyList.get(Math.min(i, supplyKeyList.size() - 1)),
                        feeScheduleKeyList.get(Math.min(i, feeScheduleKeyList.size() - 1)),
                        pauseKeyList.get(Math.min(i, pauseKeyList.size() - 1)),
                        lastUsedSerialNumberList.get(Math.min(i, lastUsedSerialNumberList.size() - 1)),
                        deletedList.get(Math.min(i, deletedList.size() - 1)),
                        tokenTypeList.get(Math.min(i, tokenTypeList.size() - 1)),
                        supplyTypeList.get(Math.min(i, supplyTypeList.size() - 1)),
                        autoRenewAccountIdList.get(Math.min(i, autoRenewAccountIdList.size() - 1)),
                        autoRenewSecondsList.get(Math.min(i, autoRenewSecondsList.size() - 1)),
                        expirationSecondList.get(Math.min(i, expirationSecondList.size() - 1)),
                        memoList.get(Math.min(i, memoList.size() - 1)),
                        maxSupplyList.get(Math.min(i, maxSupplyList.size() - 1)),
                        pausedList.get(Math.min(i, pausedList.size() - 1)),
                        accountsFrozenByDefaultList.get(Math.min(i, accountsFrozenByDefaultList.size() - 1)),
                        accountsKycGrantedByDefaultList.get(Math.min(i, accountsKycGrantedByDefaultList.size() - 1)),
                        customFeesList.get(Math.min(i, customFeesList.size() - 1)),
                        metadataList.get(Math.min(i, metadataList.size() - 1)),
                        metadataKeyList.get(Math.min(i, metadataKeyList.size() - 1))))
                .toList();
    }
}
