package com.hedera.mirror.importer.parser.record.transactionhandler;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.domain.AbstractEntity;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.NftTransferId;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.util.Utility;

@ExtendWith(MockitoExtension.class)
class TokenUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private NftRepository nftRepository;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenUpdateTransactionHandler(entityListener, nftRepository);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
                        .setToken(TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM).build())
                        .setAdminKey(DEFAULT_KEY)
                        .setExpiry(Timestamp.newBuilder().setSeconds(360).build())
                        .setKycKey(DEFAULT_KEY)
                        .setFreezeKey(DEFAULT_KEY)
                        .setSymbol("SYMBOL")
                        .setTreasury(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1).build())
                        .setAutoRenewAccount(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2)
                                .build())
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(100))
                        .setName("token_name")
                        .setWipeKey(DEFAULT_KEY)
                        .build());
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return EntityTypeEnum.TOKEN;
    }

    @Test
    void updateTreasury() {
        AbstractEntity entity = getExpectedUpdatedEntity();
        AccountID previousAccountId = AccountID.newBuilder().setAccountNum(1L).build();
        AccountID newAccountId = AccountID.newBuilder().setAccountNum(2L).build();
        TokenID tokenID = TokenID.newBuilder().setTokenNum(3L).build();
        long consensusTimestamp = Utility.timestampInNanosMax(MODIFIED_TIMESTAMP);
        TokenTransferList tokenTransferList = TokenTransferList.newBuilder()
                .setToken(tokenID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(newAccountId)
                        .setSenderAccountID(previousAccountId)
                        .setSerialNumber(NftTransferId.WILDCARD_SERIAL_NUMBER)
                        .build())
                .build();
        TransactionRecord record = getDefaultTransactionRecord().addTokenTransferLists(tokenTransferList).build();
        RecordItem recordItem = getRecordItem(getDefaultTransactionBody().build(), record);

        com.hedera.mirror.importer.domain.Transaction transaction =
                new com.hedera.mirror.importer.domain.Transaction();
        transaction.setEntityId(entity.toEntityId());
        transactionHandler.updateTransaction(transaction, recordItem);

        Mockito.verify(nftRepository).updateTreasury(tokenID.getTokenNum(), previousAccountId.getAccountNum(),
                newAccountId.getAccountNum(), consensusTimestamp);
    }

    @Test
    void noTreasuryUpdate() {
        AbstractEntity entity = getExpectedUpdatedEntity();
        TokenTransferList tokenTransferList = TokenTransferList.newBuilder()
                .setToken(TokenID.newBuilder().setTokenNum(3L).build())
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(AccountID.newBuilder().setAccountNum(2L).build())
                        .setSenderAccountID(AccountID.newBuilder().setAccountNum(1L).build())
                        .setSerialNumber(1L) // Not wildcard
                        .build())
                .build();
        TransactionRecord record = getDefaultTransactionRecord().addTokenTransferLists(tokenTransferList).build();
        RecordItem recordItem = getRecordItem(getDefaultTransactionBody().build(), record);

        com.hedera.mirror.importer.domain.Transaction transaction =
                new com.hedera.mirror.importer.domain.Transaction();
        transaction.setEntityId(entity.toEntityId());
        transactionHandler.updateTransaction(transaction, recordItem);

        Mockito.verifyNoInteractions(nftRepository);
    }
}
