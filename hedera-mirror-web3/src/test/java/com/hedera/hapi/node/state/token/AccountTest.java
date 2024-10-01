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
import static com.hedera.pbj.runtime.ProtoTestTools.UNSIGNED_INTEGER_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.UNSIGNED_LONG_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.generateListArguments;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.test.NoToStringWrapper;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class AccountTest {

    /** A reference to the protoc generated object class. */
    public static final Class<com.hederahashgraph.api.proto.java.Account> PROTOC_MODEL_CLASS =
            com.hederahashgraph.api.proto.java.Account.class;
    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<Account> ARGUMENTS;

    static {
        final var accountIdList = AccountIDTest.ARGUMENTS;
        final var aliasList = BYTES_TESTS_LIST;
        final var keyList = KeyTest.ARGUMENTS;
        final var expirationSecondList = LONG_TESTS_LIST;
        final var tinybarBalanceList = LONG_TESTS_LIST;
        final var memoList = STRING_TESTS_LIST;
        final var deletedList = BOOLEAN_TESTS_LIST;
        final var stakedToMeList = LONG_TESTS_LIST;
        final var stakePeriodStartList = LONG_TESTS_LIST;
        final var stakedIdList = Stream.of(
                        List.of(new OneOf<>(Account.StakedIdOneOfType.UNSET, null)),
                        AccountIDTest.ARGUMENTS.stream()
                                .map(value -> new OneOf<>(Account.StakedIdOneOfType.STAKED_ACCOUNT_ID, value))
                                .toList(),
                        LONG_TESTS_LIST.stream()
                                .map(value -> new OneOf<>(Account.StakedIdOneOfType.STAKED_NODE_ID, value))
                                .toList())
                .flatMap(List::stream)
                .toList();
        final var declineRewardList = BOOLEAN_TESTS_LIST;
        final var receiverSigRequiredList = BOOLEAN_TESTS_LIST;
        final var headTokenIdList = TokenIDTest.ARGUMENTS;
        final var headNftIdList = NftIDTest.ARGUMENTS;
        final var headNftSerialNumberList = LONG_TESTS_LIST;
        final var numberOwnedNftsList = LONG_TESTS_LIST;
        final var maxAutoAssociationsList = INTEGER_TESTS_LIST;
        final var usedAutoAssociationsList = INTEGER_TESTS_LIST;
        final var numberAssociationsList = INTEGER_TESTS_LIST;
        final var smartContractList = BOOLEAN_TESTS_LIST;
        final var numberPositiveBalancesList = INTEGER_TESTS_LIST;
        final var ethereumNonceList = LONG_TESTS_LIST;
        final var stakeAtStartOfLastRewardedPeriodList = LONG_TESTS_LIST;
        final var autoRenewAccountIdList = AccountIDTest.ARGUMENTS;
        final var autoRenewSecondsList = LONG_TESTS_LIST;
        final var contractKvPairsNumberList = INTEGER_TESTS_LIST;
        final var cryptoAllowancesList = generateListArguments(AccountCryptoAllowanceTest.ARGUMENTS);
        final var approveForAllNftAllowancesList = generateListArguments(AccountApprovalForAllAllowanceTest.ARGUMENTS);
        final var tokenAllowancesList = generateListArguments(AccountFungibleTokenAllowanceTest.ARGUMENTS);
        final var numberTreasuryTitlesList = UNSIGNED_INTEGER_TESTS_LIST;
        final var expiredAndPendingRemovalList = BOOLEAN_TESTS_LIST;
        final var firstContractStorageKeyList = BYTES_TESTS_LIST;
        final var headPendingAirdropIdList = PendingAirdropIdTest.ARGUMENTS;
        final var numberPendingAirdropsList = UNSIGNED_LONG_TESTS_LIST;

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(
                        accountIdList.size(),
                        aliasList.size(),
                        keyList.size(),
                        expirationSecondList.size(),
                        tinybarBalanceList.size(),
                        memoList.size(),
                        deletedList.size(),
                        stakedToMeList.size(),
                        stakePeriodStartList.size(),
                        stakedIdList.size(),
                        declineRewardList.size(),
                        receiverSigRequiredList.size(),
                        headTokenIdList.size(),
                        headNftIdList.size(),
                        headNftSerialNumberList.size(),
                        numberOwnedNftsList.size(),
                        maxAutoAssociationsList.size(),
                        usedAutoAssociationsList.size(),
                        numberAssociationsList.size(),
                        smartContractList.size(),
                        numberPositiveBalancesList.size(),
                        ethereumNonceList.size(),
                        stakeAtStartOfLastRewardedPeriodList.size(),
                        autoRenewAccountIdList.size(),
                        autoRenewSecondsList.size(),
                        contractKvPairsNumberList.size(),
                        cryptoAllowancesList.size(),
                        approveForAllNftAllowancesList.size(),
                        tokenAllowancesList.size(),
                        numberTreasuryTitlesList.size(),
                        expiredAndPendingRemovalList.size(),
                        firstContractStorageKeyList.size(),
                        headPendingAirdropIdList.size(),
                        numberPendingAirdropsList.size())
                .max()
                .getAsInt();
        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new Account(
                        accountIdList.get(Math.min(i, accountIdList.size() - 1)),
                        aliasList.get(Math.min(i, aliasList.size() - 1)),
                        keyList.get(Math.min(i, keyList.size() - 1)),
                        expirationSecondList.get(Math.min(i, expirationSecondList.size() - 1)),
                        tinybarBalanceList.get(Math.min(i, tinybarBalanceList.size() - 1)),
                        memoList.get(Math.min(i, memoList.size() - 1)),
                        deletedList.get(Math.min(i, deletedList.size() - 1)),
                        stakedToMeList.get(Math.min(i, stakedToMeList.size() - 1)),
                        stakePeriodStartList.get(Math.min(i, stakePeriodStartList.size() - 1)),
                        stakedIdList.get(Math.min(i, stakedIdList.size() - 1)),
                        declineRewardList.get(Math.min(i, declineRewardList.size() - 1)),
                        receiverSigRequiredList.get(Math.min(i, receiverSigRequiredList.size() - 1)),
                        headTokenIdList.get(Math.min(i, headTokenIdList.size() - 1)),
                        headNftIdList.get(Math.min(i, headNftIdList.size() - 1)),
                        headNftSerialNumberList.get(Math.min(i, headNftSerialNumberList.size() - 1)),
                        numberOwnedNftsList.get(Math.min(i, numberOwnedNftsList.size() - 1)),
                        maxAutoAssociationsList.get(Math.min(i, maxAutoAssociationsList.size() - 1)),
                        usedAutoAssociationsList.get(Math.min(i, usedAutoAssociationsList.size() - 1)),
                        numberAssociationsList.get(Math.min(i, numberAssociationsList.size() - 1)),
                        smartContractList.get(Math.min(i, smartContractList.size() - 1)),
                        numberPositiveBalancesList.get(Math.min(i, numberPositiveBalancesList.size() - 1)),
                        ethereumNonceList.get(Math.min(i, ethereumNonceList.size() - 1)),
                        stakeAtStartOfLastRewardedPeriodList.get(
                                Math.min(i, stakeAtStartOfLastRewardedPeriodList.size() - 1)),
                        autoRenewAccountIdList.get(Math.min(i, autoRenewAccountIdList.size() - 1)),
                        autoRenewSecondsList.get(Math.min(i, autoRenewSecondsList.size() - 1)),
                        contractKvPairsNumberList.get(Math.min(i, contractKvPairsNumberList.size() - 1)),
                        cryptoAllowancesList.get(Math.min(i, cryptoAllowancesList.size() - 1)),
                        approveForAllNftAllowancesList.get(Math.min(i, approveForAllNftAllowancesList.size() - 1)),
                        tokenAllowancesList.get(Math.min(i, tokenAllowancesList.size() - 1)),
                        numberTreasuryTitlesList.get(Math.min(i, numberTreasuryTitlesList.size() - 1)),
                        expiredAndPendingRemovalList.get(Math.min(i, expiredAndPendingRemovalList.size() - 1)),
                        firstContractStorageKeyList.get(Math.min(i, firstContractStorageKeyList.size() - 1)),
                        headPendingAirdropIdList.get(Math.min(i, headPendingAirdropIdList.size() - 1)),
                        numberPendingAirdropsList.get(Math.min(i, numberPendingAirdropsList.size() - 1))))
                .toList();
    }

    /**
     * Create a stream of all test permutations of the Account class we are testing. This is reused by other tests
     * as well that have model objects with fields of this type.
     *
     * @return stream of model objects for all test cases
     */
    public static Stream<NoToStringWrapper<Account>> createModelTestArguments() {
        return ARGUMENTS.stream().map(NoToStringWrapper::new);
    }

    @SuppressWarnings("EqualsWithItself")
    @Test
    public void testTestEqualsAndHashCode() throws Exception {
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
}
