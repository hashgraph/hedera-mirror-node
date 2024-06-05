package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;


@RequiredArgsConstructor
public class ContractResultRepositoryTest extends Web3IntegrationTest {

    private final ContractResultRepository contractResultRepository;

    @Test
    void findByConsensusTimestampSuccessful() {
        var timestamp = domainBuilder.timestamp();
        ContractResult contractResult = domainBuilder.contractResult()
                .customize(c -> c.consensusTimestamp(timestamp))
                .persist();
        var result = contractResultRepository.findById(timestamp);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(contractResult);
    }
}
