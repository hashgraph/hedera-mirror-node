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

package com.hedera.services.hapi.utils.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.Test;

class FeeBuilderTest {
    private final ByteString contractCallResult = ByteString.copyFromUtf8("contractCallResult");
    private final ByteString contractCreateResult = ByteString.copyFromUtf8("contractCreateResult");
    private final ByteString bloom = ByteString.copyFromUtf8("Bloom");
    private final ByteString error = ByteString.copyFromUtf8("Error");
    private final AccountID accountId = AccountID.newBuilder()
            .setShardNum(0)
            .setRealmNum(0)
            .setAccountNum(1002)
            .build();
    private final AccountID designatedNodeAccount = AccountID.newBuilder()
            .setShardNum(0)
            .setRealmNum(0)
            .setAccountNum(3)
            .build();
    private final ContractFunctionResult contractFunctionResult = ContractFunctionResult.newBuilder()
            .setContractCallResult(contractCallResult)
            .setBloom(bloom)
            .setErrorMessageBytes(error)
            .build();
    private final AccountAmount accountAmount =
            AccountAmount.newBuilder().setAccountID(accountId).setAmount(1500L).build();
    private final TransferList transferList =
            TransferList.newBuilder().addAccountAmounts(accountAmount).build();
    private final TransactionRecord.Builder transactionRecordBuilder =
            TransactionRecord.newBuilder().setMemo("memo").setTransferList(transferList);
    private final FeeComponents feeComponents = FeeComponents.newBuilder()
            .setBpr(10L)
            .setBpt(10L)
            .setGas(1234L)
            .setConstant(10L)
            .setMax(10L)
            .setMin(5L)
            .setRbh(10L)
            .setSbh(10L)
            .setSbpr(10L)
            .setTv(10L)
            .setVpt(10L)
            .build();
    private final FeeData feeData = FeeData.newBuilder()
            .setNetworkdata(feeComponents)
            .setNodedata(feeComponents)
            .setServicedata(feeComponents)
            .build();
    private final FeeData feeMatrices = FeeData.newBuilder()
            .setNetworkdata(feeComponents)
            .setNodedata(feeComponents)
            .setServicedata(feeComponents)
            .build();
    private final Duration transactionDuration =
            Duration.newBuilder().setSeconds(30L).build();
    private final TransactionID transactionId =
            TransactionID.newBuilder().setAccountID(accountId).build();
    private final ByteString bodyBytes = TransactionBody.newBuilder()
            .setMemo("memo signed tx")
            .setTransactionValidDuration(transactionDuration)
            .setNodeAccountID(designatedNodeAccount)
            .setTransactionID(transactionId)
            .build()
            .toByteString();
    private final ByteString CANONICAL_SIG =
            ByteString.copyFromUtf8("0123456789012345678901234567890123456789012345678901234567890123");
    private final SignaturePair signPair =
            SignaturePair.newBuilder().setEd25519(CANONICAL_SIG).build();
    private final SignatureMap signatureMap =
            SignatureMap.newBuilder().addSigPair(signPair).build();
    private final Transaction signedTxn = Transaction.newBuilder()
            .setSignedTransactionBytes(SignedTransaction.newBuilder()
                    .setSigMap(signatureMap)
                    .setBodyBytes(bodyBytes)
                    .build()
                    .toByteString())
            .build();
    private final ExchangeRate exchangeRate =
            ExchangeRate.newBuilder().setHbarEquiv(1000).setCentEquiv(100).build();

    @Test
    void assertGetTinybarsFromTinyCents() {
        var exchangeRate =
                ExchangeRate.newBuilder().setCentEquiv(10).setHbarEquiv(100).build();
        assertEquals(100, FeeBuilder.getTinybarsFromTinyCents(exchangeRate, 10));
    }

    @Test
    void assertCalculateKeysMetadata() {
        int[] countKeyMetatData = {0, 0};
        Key validKey = Key.newBuilder()
                .setEd25519(
                        ByteString.copyFromUtf8(
                                "a479462fba67674b5a41acfb16cb6828626b61d3f389fa611005a45754130e5c749073c0b1b791596430f4a54649cc8a3f6d28147dd4099070a5c3c4811d1771"))
                .build();

        Key validKey1 = Key.newBuilder()
                .setEd25519(
                        ByteString.copyFromUtf8(
                                "a479462fba67674b5a41acfb16cb6828626b61d3f389fa611005a45754130e5c749073c0b1b791596430f4a54649cc8a3f6d28147dd4099070a5c3c4811d1771"))
                .build();
        Key validED25519Keys = Key.newBuilder()
                .setKeyList(KeyList.newBuilder()
                        .addKeys(validKey)
                        .addKeys(validKey1)
                        .build())
                .build();
        assertEquals(
                countKeyMetatData.length, FeeBuilder.calculateKeysMetadata(validED25519Keys, countKeyMetatData).length);
        assertEquals(4, FeeBuilder.calculateKeysMetadata(validED25519Keys, countKeyMetatData)[0]);
    }

    @Test
    void assertCalculateKeysMetadataThresholdKey() {
        int[] countKeyMetatData = {0, 0};
        KeyList thresholdKeyList = KeyList.newBuilder()
                .addKeys(Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("aaaaaaaa"))
                        .build())
                .addKeys(Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("bbbbbbbbbbbbbbbbbbbbb"))
                        .build())
                .build();
        ThresholdKey thresholdKey = ThresholdKey.newBuilder()
                .setKeys(thresholdKeyList)
                .setThreshold(2)
                .build();
        Key validED25519Keys = Key.newBuilder().setThresholdKey(thresholdKey).build();
        assertEquals(
                countKeyMetatData.length, FeeBuilder.calculateKeysMetadata(validED25519Keys, countKeyMetatData).length);
        assertEquals(2, FeeBuilder.calculateKeysMetadata(validED25519Keys, countKeyMetatData)[1]);
    }
}
