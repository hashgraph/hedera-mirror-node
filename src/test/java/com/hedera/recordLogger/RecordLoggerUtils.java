package com.hedera.recordLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.TransactionResult;
import com.hedera.mirror.repository.CryptoTransferRepository;
import com.hedera.mirror.repository.EntityRepository;
import com.hedera.mirror.repository.EntityTypeRepository;
import com.hedera.mirror.repository.TransactionResultRepository;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;

public class RecordLoggerUtils {
	
	public static EntityTypeRepository entityTypeRepository;
	public static EntityRepository entityRepository;
	public static CryptoTransferRepository cryptoTransferRepository;
	public static TransactionResultRepository transactionResultRepository;
	
    public static void assertAccount(AccountID accountId, Optional<Entities> dbEntity) {
        assertThat(accountId)
            .isNotEqualTo(AccountID.getDefaultInstance())
            .extracting(AccountID::getShardNum, AccountID::getRealmNum, AccountID::getAccountNum)
            .containsExactly(dbEntity.get().getEntityShard(), dbEntity.get().getEntityRealm(), dbEntity.get().getEntityNum());
        assertThat(dbEntity.get().getEntityTypeId())
        	.isEqualTo(entityTypeRepository.findByName("account").get().getId());
    }    
    public static void assertFile(FileID fileId, Optional<Entities> dbEntity) {
        assertThat(fileId)
            .isNotEqualTo(FileID.getDefaultInstance())
            .extracting(FileID::getShardNum, FileID::getRealmNum, FileID::getFileNum)
            .containsExactly(dbEntity.get().getEntityShard(), dbEntity.get().getEntityRealm(), dbEntity.get().getEntityNum());
        assertThat(dbEntity.get().getEntityTypeId())
        	.isEqualTo(entityTypeRepository.findByName("file").get().getId());
    }    
    public static void assertTransfers(TransactionRecord record) {
    	final TransferList transferList = record.getTransferList();
    	for (AccountAmount accountAmount : transferList.getAccountAmountsList()) {
    		AccountID xferAccountId = accountAmount.getAccountID();
    		Optional<Entities> accountId = entityRepository.findByPrimaryKey(xferAccountId.getShardNum(), xferAccountId.getRealmNum(), xferAccountId.getAccountNum());
    		assertEquals(accountAmount.getAmount(), cryptoTransferRepository.findByConsensusTimestampAndAccountId(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountId.get().getId()).get().getAmount());
    	}
    }
    public static void assertRecord(TransactionRecord record, Optional<com.hedera.mirror.domain.Transaction> dbTransaction) {
    	final Optional<Entities> dbPayerEntity = entityRepository.findById(dbTransaction.get().getPayerAccountId());
    	final Optional<TransactionResult> dbResult = transactionResultRepository.findById(dbTransaction.get().getResultId());
        // record inputs
        assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.get().getConsensusNs());
        assertEquals(record.getTransactionFee(), dbTransaction.get().getChargedTxFee());
        // payer
        RecordLoggerUtils.assertAccount(record.getTransactionID().getAccountID(), dbPayerEntity);
        // transaction id
        assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction.get().getValidStartNs());
        // receipt
        assertEquals(record.getReceipt().getStatusValue(), dbResult.get().getProtobufId());
        assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.get().getResult());
    	
    }
}
