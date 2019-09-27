package com.hedera.recordLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import javax.annotation.Resource;

import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.IntegrationTest;
import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.TransactionResult;
import com.hedera.mirror.repository.ContractResultRepository;
import com.hedera.mirror.repository.CryptoTransferRepository;
import com.hedera.mirror.repository.EntityRepository;
import com.hedera.mirror.repository.EntityTypeRepository;
import com.hedera.mirror.repository.FileDataRepository;
import com.hedera.mirror.repository.LiveHashRepository;
import com.hedera.mirror.repository.RecordFileRepository;
import com.hedera.mirror.repository.TransactionRepository;
import com.hedera.mirror.repository.TransactionResultRepository;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;

public class AbstractRecordFileLoggerTest extends IntegrationTest {
	
    @Resource
    protected TransactionRepository transactionRepository;
    @Resource
    protected EntityRepository entityRepository;
    @Resource
    protected ContractResultRepository contractResultRepository;
    @Resource
    protected RecordFileRepository recordFileRepository;
    @Resource
    protected CryptoTransferRepository cryptoTransferRepository;
    @Resource
    protected LiveHashRepository liveHashRepository;
    @Resource
    protected FileDataRepository fileDataRepository;
    @Resource
    protected TransactionResultRepository transactionResultRepository;
    @Resource
    protected EntityTypeRepository entityTypeRepository;	
	
    protected final void assertAccount(AccountID accountId, Optional<Entities> dbEntity) {
        assertThat(accountId)
            .isNotEqualTo(AccountID.getDefaultInstance())
            .extracting(AccountID::getShardNum, AccountID::getRealmNum, AccountID::getAccountNum)
            .containsExactly(dbEntity.get().getEntityShard(), dbEntity.get().getEntityRealm(), dbEntity.get().getEntityNum());
        assertThat(dbEntity.get().getEntityTypeId())
        	.isEqualTo(entityTypeRepository.findByName("account").get().getId());
    }    
    protected final void assertFile(FileID fileId, Optional<Entities> dbEntity) {
        assertThat(fileId)
            .isNotEqualTo(FileID.getDefaultInstance())
            .extracting(FileID::getShardNum, FileID::getRealmNum, FileID::getFileNum)
            .containsExactly(dbEntity.get().getEntityShard(), dbEntity.get().getEntityRealm(), dbEntity.get().getEntityNum());
        assertThat(dbEntity.get().getEntityTypeId())
        	.isEqualTo(entityTypeRepository.findByName("file").get().getId());
    }    
    protected final void assertTransfers(TransactionRecord record) {
    	final TransferList transferList = record.getTransferList();
    	for (AccountAmount accountAmount : transferList.getAccountAmountsList()) {
    		AccountID xferAccountId = accountAmount.getAccountID();
    		Optional<Entities> accountId = entityRepository.findByPrimaryKey(xferAccountId.getShardNum(), xferAccountId.getRealmNum(), xferAccountId.getAccountNum());
    		assertEquals(accountAmount.getAmount(), cryptoTransferRepository.findByConsensusTimestampAndAccountId(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountId.get().getId()).get().getAmount());
    	}
    }
    protected final void assertRecord(TransactionRecord record, Optional<com.hedera.mirror.domain.Transaction> dbTransaction) {
    	final Optional<Entities> dbPayerEntity = entityRepository.findById(dbTransaction.get().getPayerAccountId());
    	final Optional<TransactionResult> dbResult = transactionResultRepository.findById(dbTransaction.get().getResultId());
        // record inputs
        assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.get().getConsensusNs());
        assertEquals(record.getTransactionFee(), dbTransaction.get().getChargedTxFee());
        // payer
        assertAccount(record.getTransactionID().getAccountID(), dbPayerEntity);
        // transaction id
        assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction.get().getValidStartNs());
        // receipt
        assertEquals(record.getReceipt().getStatusValue(), dbResult.get().getProtobufId());
        assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.get().getResult());
    	
    }
}
