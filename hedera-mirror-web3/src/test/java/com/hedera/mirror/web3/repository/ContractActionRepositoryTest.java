package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
public class ContractActionRepositoryTest extends Web3IntegrationTest {
    private final ContractActionRepository contractActionRepository;

    @Test
    void findAllByConsensusTimestampSuccessful() {
        var timestamp = domainBuilder.timestamp();
        ContractAction contractAction = domainBuilder.contractAction()
                .customize(c -> c.consensusTimestamp(timestamp))
                .persist();
        assertThat(contractActionRepository.findAllByConsensusTimestamp(timestamp))
                .containsExactly(contractAction);
    }

}
