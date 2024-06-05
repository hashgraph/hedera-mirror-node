package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;


@RequiredArgsConstructor
public class ContractTransactionHashRepositoryTest extends Web3IntegrationTest {
    private final ContractTransactionHashRepository contractTransactionHashRepository;

     @Test
     void findByHashSuccessful() {
         var hash = domainBuilder.hash(32);
         ContractTransactionHash contractTransactionHash = domainBuilder.contractTransactionHash()
                 .customize(c -> c.hash(hash.getBytes()))
                 .persist();
         var result = contractTransactionHashRepository.findById(hash.getBytes());
         assertThat(result).isPresent();
         assertThat(result.get()).isEqualTo(contractTransactionHash);
    }

}
