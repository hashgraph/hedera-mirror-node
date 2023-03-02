package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.web3.Web3IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftAllowanceRepositoryTest extends Web3IntegrationTest {
    private final NftAllowanceRepository allowanceRepository;

    @Test
    void isSpenderAnOperator() {
        final var allowance = domainBuilder.nftAllowance()
                .customize(a -> a.approvedForAll(true)).persist();
        final var tokenId = allowance.getTokenId();
        final var ownerId = allowance.getOwner();
        final var spenderId = allowance.getSpender();

        assertThat(allowanceRepository.isSpenderAnOperator(tokenId, ownerId, spenderId)).isTrue();
    }
}
