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

package com.hedera.services.fees.calculation.crypto.queries;

import static com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage.MISSING_RECORD_STANDIN;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.hapi.utils.fees.CryptoFeeBuilder;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTxnRecordResourceUsageTest {
    private Store store;
    private CryptoFeeBuilder usageEstimator;
    private TransactionRecord desiredRecord;
    private GetTxnRecordResourceUsage subject;

    private static final TransactionID targetTxnId = TransactionID.newBuilder()
            .setAccountID(asAccount("0.0.2"))
            .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
            .build();

    public static Query queryOf(final TransactionGetRecordQuery op) {
        return Query.newBuilder().setTransactionGetRecord(op).build();
    }

    private static final TransactionGetRecordQuery satisfiableCostAnswer = txnRecordQuery(targetTxnId, COST_ANSWER);
    private static final TransactionGetRecordQuery satisfiableAnswerOnly = txnRecordQuery(targetTxnId, ANSWER_ONLY);
    private static final Query satisfiableCostAnswerQuery = queryOf(satisfiableCostAnswer);
    private static final Query satisfiableAnswerOnlyQuery = queryOf(satisfiableAnswerOnly);

    public static TransactionGetRecordQuery txnRecordQuery(final TransactionID txnId, final ResponseType type) {
        return txnRecordQuery(txnId, type, false);
    }

    public static TransactionGetRecordQuery txnRecordQuery(
            final TransactionID txnId, final ResponseType type, final boolean duplicates) {
        return txnRecordQuery(txnId, type, Transaction.getDefaultInstance(), duplicates);
    }

    public static TransactionGetRecordQuery txnRecordQuery(
            final TransactionID txnId,
            final ResponseType type,
            final Transaction paymentTxn,
            final boolean duplicates) {
        return txnRecordQuery(txnId, type, paymentTxn, duplicates, false);
    }

    public static TransactionGetRecordQuery txnRecordQuery(
            final TransactionID txnId,
            final ResponseType type,
            final Transaction paymentTxn,
            final boolean duplicates,
            final boolean children) {
        return TransactionGetRecordQuery.newBuilder()
                .setTransactionID(txnId)
                .setHeader(queryHeaderOf(type, paymentTxn))
                .setIncludeDuplicates(duplicates)
                .setIncludeChildRecords(children)
                .build();
    }

    public static QueryHeader.Builder queryHeaderOf(final ResponseType type, final Transaction paymentTxn) {
        return queryHeaderOf(type).setPayment(paymentTxn);
    }

    public static QueryHeader.Builder queryHeaderOf(final ResponseType type) {
        return QueryHeader.newBuilder().setResponseType(type);
    }

    @BeforeEach
    void setup() {
        desiredRecord = mock(TransactionRecord.class);
        store = mock(Store.class);

        usageEstimator = mock(CryptoFeeBuilder.class);
        subject = new GetTxnRecordResourceUsage(usageEstimator);
    }

    @Test
    void onlySetsPriorityRecordInQueryCxtIfFound() {
        final var answerOnlyUsage = mock(FeeData.class);
        final var queryCtx = new HashMap<String, Object>();
        given(usageEstimator.getTransactionRecordQueryFeeMatrices(MISSING_RECORD_STANDIN, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);
        final var actual = subject.usageGiven(satisfiableAnswerOnlyQuery, store, queryCtx);
        assertSame(answerOnlyUsage, actual);
    }

    @Test
    void recognizesApplicableQueries() {
        assertTrue(subject.applicableTo(satisfiableAnswerOnlyQuery));
        assertFalse(subject.applicableTo(Query.getDefaultInstance()));
    }
}
