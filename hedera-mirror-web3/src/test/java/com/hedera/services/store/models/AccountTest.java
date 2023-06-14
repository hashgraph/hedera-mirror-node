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

package com.hedera.services.store.models;

import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountTest {
    private static final byte[] mockCreate2Addr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
    private final long miscAccountNum = 12345;
    private final Id subjectId = new Id(0, 0, miscAccountNum);
    private final int numAssociations = 3;
    private final int numPositiveBalances = 2;
    private final int numTreasuryTitles = 0;
    private final int alreadyUsedAutoAssociations = 123;
    private final long defaultLongValue = 0;
    private final long ownedNfts = 5;
    private Account subject;

    @BeforeEach
    void setUp() {
        subject = new Account(
                subjectId,
                defaultLongValue,
                defaultLongValue,
                false,
                ownedNfts,
                defaultLongValue,
                Id.DEFAULT,
                alreadyUsedAutoAssociations,
                null,
                null,
                null,
                numAssociations,
                numPositiveBalances,
                numTreasuryTitles,
                0L);
    }

    @Test
    void objectContractWorks() {
        final var TEST_LONG_VALUE = 0;

        assertEquals(subjectId, subject.getId());
        assertEquals(TEST_LONG_VALUE, subject.getExpiry());
        assertFalse(subject.isDeleted());
        assertEquals(TEST_LONG_VALUE, subject.getBalance());
        assertEquals(TEST_LONG_VALUE, subject.getAutoRenewSecs());
        assertEquals(ownedNfts, subject.getOwnedNfts());
        assertEquals(Id.DEFAULT, subject.getProxy());
        assertEquals(subjectId.asEvmAddress(), subject.getAccountAddress());
        assertNull(subject.getCryptoAllowances());
        assertNull(subject.getFungibleTokenAllowances());
        assertNull(subject.getApproveForAllNfts());
        assertEquals(numTreasuryTitles, subject.getNumTreasuryTitles());
    }

    @Test
    void canonicalAddressIs20ByteAliasIfPresent() {
        subject.setAlias(ByteString.copyFrom(mockCreate2Addr));
        assertEquals(Address.wrap(Bytes.wrap(mockCreate2Addr)), subject.canonicalAddress());
    }

    @Test
    void canonicalAddressIsEVMAddressIfCorrectAlias() {
        // default truffle address #0
        subject.setAlias(ByteString.copyFrom(
                Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(
                Address.wrap(Bytes.fromHexString("627306090abaB3A6e1400e9345bC60c78a8BEf57")),
                subject.canonicalAddress());
    }

    @Test
    void invalidCanonicalAddresses() {
        Address untranslatedAddress = Address.wrap(Bytes.fromHexString("0000000000000000000000000000000000003039"));

        // bogus alias
        subject.setAlias(ByteString.copyFromUtf8("This alias is invalid"));
        assertEquals(untranslatedAddress, subject.canonicalAddress());

        // incorrect starting bytes for ECDSA
        subject.setAlias(ByteString.copyFrom(
                Hex.decode("ffff03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(untranslatedAddress, subject.canonicalAddress());

        // incorrect ECDSA key
        subject.setAlias(ByteString.copyFrom(
                Hex.decode("3a21ffaf80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(untranslatedAddress, subject.canonicalAddress());
    }

    @Test
    void toStringAsExpected() {
        final var desired = "Account{id=0.0.12345, expiry=0, balance=0, deleted=false, ownedNfts=5,"
                + " alreadyUsedAutoAssociations=0, maxAutoAssociations=123, alias=,"
                + " cryptoAllowances=null, fungibleTokenAllowances=null,"
                + " approveForAllNfts=null, numAssociations=3, numPositiveBalances=2}";

        // expect:
        assertEquals(desired, subject.toString());
    }
}
