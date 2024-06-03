package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
public class EthereumTransactionRepositoryTest extends Web3IntegrationTest {
    private final EthereumTransactionRepository ethereumTransactionRepository;

    @Test
    void findByConsensusTimestampSuccessful() {
        var timestamp = domainBuilder.timestamp();
        var ethereumTransaction = domainBuilder.ethereumTransaction(false)
                .customize(e -> e.consensusTimestamp(timestamp))
                .persist();
        var result = ethereumTransactionRepository.findById(timestamp);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(ethereumTransaction);
    }

    @Test
    void findByConsensusTimestampAndPayerAccountIdSuccessful() {
        var entity = domainBuilder.entity().persist();
        var timestamp = domainBuilder.timestamp();
        var ethereumTransaction = domainBuilder.ethereumTransaction(false)
                .customize(e -> e.payerAccountId(entity.toEntityId()))
                .customize(e -> e.consensusTimestamp(timestamp))
                .persist();
        var result = ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                timestamp, entity.toEntityId());
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(ethereumTransaction);
    }


}
