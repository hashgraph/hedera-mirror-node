package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
public class TransactionRepositoryTest extends Web3IntegrationTest {
    private final TransactionRepository transactionRepository;

    @Test
    void findByConsensusTimestampSuccessful() {
        var timestamp = domainBuilder.timestamp();
        Transaction transaction = domainBuilder.transaction()
                .customize(c -> c.consensusTimestamp(timestamp))
                .persist();
        var result = transactionRepository.findById(timestamp);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(transaction);
    }

    @Test
    void findByPayerAccountIdAndValidStartNsSuccessful() {
        var entity = domainBuilder.entity().persist();
        var timestamp = domainBuilder.timestamp();
        var transaction = domainBuilder.transaction()
                .customize(e -> e.payerAccountId(entity.toEntityId()))
                .customize(e -> e.validStartNs(timestamp))
                .persist();
        var result = transactionRepository.findByPayerAccountIdAndValidStartNs(entity.toEntityId(), timestamp);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(transaction);
    }
}
