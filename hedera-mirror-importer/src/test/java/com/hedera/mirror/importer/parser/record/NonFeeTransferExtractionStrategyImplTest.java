package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class NonFeeTransferExtractionStrategyImplTest {
    private static final long payerAccountNum = 999L;
    private static final AccountID payerAccountId = AccountID.newBuilder().setAccountNum(payerAccountNum).build();
    private static final AccountID testAccount1 = AccountID.newBuilder().setAccountNum(1234L).build();
    private static final AccountID testAccount2 = AccountID.newBuilder().setAccountNum(5555L).build();
    private static final long initialBalance = 1234L;
    private static final long newEntityNum = 987654L;
    private static final AccountID newAccountId = AccountID.newBuilder().setAccountNum(newEntityNum).build();

    private final NonFeeTransferExtractionStrategyImpl extractionStrategy = new NonFeeTransferExtractionStrategyImpl();

    @Test
    void extractNonFeeTransfersCryptoTransfer() {
        var transactionBody = getCryptoTransferTransactionBody();
        var result = extractionStrategy.extractNonFeeTransfers(transactionBody, getSimpleTransactionRecord());
        assertAll(
                () -> assertEquals(3, StreamSupport.stream(result.spliterator(), false).count()),
                () -> assertResult(transactionBody.getCryptoTransfer().getTransfers().getAccountAmountsList(), result)
        );
    }

    @Test
    void extractNonFeeTransfersCryptoCreate() {
        var transactionBody = getCryptoCreateTransactionBody();
        var result = extractionStrategy.extractNonFeeTransfers(transactionBody, getNewAccountTransactionRecord());
        assertAll(
                () -> assertEquals(2, StreamSupport.stream(result.spliterator(), false).count()),
                () -> assertResult(createAccountAmounts(payerAccountNum, 0 - initialBalance,
                        newEntityNum, initialBalance), result)
        );
    }

    @Test
    void extractNonFeeTransfersFailedCryptoCreate() {
        var transactionBody = getCryptoCreateTransactionBody();
        var result = extractionStrategy.extractNonFeeTransfers(transactionBody, getFailedTransactionRecord());
        assertAll(
                () -> assertEquals(1, StreamSupport.stream(result.spliterator(), false).count()),
                () -> assertResult(createAccountAmounts(payerAccountNum, 0 - initialBalance), result)
        );
    }

    @Test
    void extractNonFeeTransfersContractCreate() {
        var transactionBody = getContractCreateTransactionBody();
        var result = extractionStrategy.extractNonFeeTransfers(transactionBody, getNewContractTransactionRecord());
        assertAll(
                () -> assertEquals(2, StreamSupport.stream(result.spliterator(), false).count()),
                () -> assertResult(createAccountAmounts(payerAccountNum, 0 - initialBalance,
                        newEntityNum, initialBalance), result)
        );
    }

    @Test
    void extractNonFeeTransfersFailedContractCreate() {
        var transactionBody = getContractCreateTransactionBody();
        var result = extractionStrategy.extractNonFeeTransfers(transactionBody, getFailedTransactionRecord());
        assertAll(
                () -> assertEquals(1, StreamSupport.stream(result.spliterator(), false).count()),
                () -> assertResult(createAccountAmounts(payerAccountNum, 0 - initialBalance), result)
        );
    }

    @Test
    void extractNonFeeTransfersContractCall() {
        var amount = 123456L;
        var contractNum = 8888L;
        var transactionBody = getContractCallTransactionBody(contractNum, amount);
        var result = extractionStrategy.extractNonFeeTransfers(transactionBody, getSimpleTransactionRecord());
        assertAll(
                () -> assertEquals(2, StreamSupport.stream(result.spliterator(), false).count()),
                () -> assertResult(createAccountAmounts(contractNum, amount,
                        payerAccountNum, 0 - amount), result)
        );
    }

    @Test
    void extractNonFeeTransfersFileCreateNone() {
        var transactionBody = getFileCreateTransactionBody();
        var result = extractionStrategy.extractNonFeeTransfers(transactionBody, getNewFileTransactionRecord());
        assertEquals(0, StreamSupport.stream(result.spliterator(), false).count());
    }

    /**
     * Quick create account amounts list.
     *
     * @param accountNumThenAmount account num, amount, account num, amount, ...
     * @return
     */
    private List<AccountAmount> createAccountAmounts(long... accountNumThenAmount) {
        var result = new LinkedList<AccountAmount>();
        for (int i = 0; i < accountNumThenAmount.length; i += 2) {
            result.add(
                    AccountAmount.newBuilder()
                            .setAccountID(AccountID.newBuilder().setAccountNum(accountNumThenAmount[i]).build())
                            .setAmount(accountNumThenAmount[i + 1])
                            .build());
        }
        return result;
    }

    private void assertResult(List<AccountAmount> expected, Iterable<AccountAmount> actual) {
        assertArrayEquals(expected.toArray(), StreamSupport.stream(actual.spliterator(), false)
                .toArray());
    }

    private TransactionBody.Builder transactionBodyBuilder() {
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder().setAccountID(payerAccountId).build());
    }

    private TransactionBody getCryptoTransferTransactionBody() {
        var transferList = TransferList.newBuilder()
                .addAccountAmounts(newAccountAmount(payerAccountId, -3000L))
                .addAccountAmounts(newAccountAmount(testAccount1, 2000L))
                .addAccountAmounts(newAccountAmount(testAccount2, 1000L))
                .build();

        var innerBody = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(transferList).build();
        return transactionBodyBuilder().setCryptoTransfer(innerBody).build();
    }

    private TransactionBody getCryptoCreateTransactionBody() {
        var innerBody = CryptoCreateTransactionBody.newBuilder().setInitialBalance(initialBalance).build();
        return transactionBodyBuilder().setCryptoCreateAccount(innerBody).build();
    }

    private TransactionBody getContractCreateTransactionBody() {
        var innerBody = ContractCreateTransactionBody.newBuilder().setInitialBalance(initialBalance).build();
        return transactionBodyBuilder().setContractCreateInstance(innerBody).build();
    }

    private TransactionBody getContractCallTransactionBody(long contractNum, long amount) {
        var innerBody = ContractCallTransactionBody.newBuilder()
                .setContractID(ContractID.newBuilder().setContractNum(contractNum).build())
                .setAmount(amount).build();
        return transactionBodyBuilder().setContractCall(innerBody).build();
    }

    private TransactionBody getFileCreateTransactionBody() {
        var innerBody = FileCreateTransactionBody.newBuilder().build();
        return transactionBodyBuilder().setFileCreate(innerBody).build();
    }

    private TransactionRecord getSimpleTransactionRecord() {
        var receipt = TransactionReceipt.newBuilder().setStatus(ResponseCodeEnum.SUCCESS);
        return TransactionRecord.newBuilder().setReceipt(receipt).build();
    }

    private TransactionRecord getFailedTransactionRecord() {
        var receipt = TransactionReceipt.newBuilder().setStatus(ResponseCodeEnum.INVALID_SIGNATURE);
        return TransactionRecord.newBuilder().setReceipt(receipt).build();
    }

    private TransactionRecord getNewAccountTransactionRecord() {
        var receipt = TransactionReceipt.newBuilder().setAccountID(newAccountId).setStatus(ResponseCodeEnum.SUCCESS)
                .build();
        return TransactionRecord.newBuilder().setReceipt(receipt).build();
    }

    private TransactionRecord getNewContractTransactionRecord() {
        var receipt = TransactionReceipt.newBuilder().setContractID(
                ContractID.newBuilder().setContractNum(newEntityNum).build()
        ).setStatus(ResponseCodeEnum.SUCCESS).build();
        return TransactionRecord.newBuilder().setReceipt(receipt).build();
    }

    private TransactionRecord getNewFileTransactionRecord() {
        var receipt = TransactionReceipt.newBuilder().setFileID(
                FileID.newBuilder().setFileNum(newEntityNum).build()
        ).setStatus(ResponseCodeEnum.SUCCESS).build();
        return TransactionRecord.newBuilder().setReceipt(receipt).build();
    }

    private AccountAmount newAccountAmount(AccountID accountId, long amount) {
        return AccountAmount.newBuilder().setAccountID(accountId).setAmount(amount).build();
    }
}
