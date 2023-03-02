package com.hedera.mirror.web3.repository;

import com.hedera.mirror.web3.Web3IntegrationTest;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenAllowanceRepositoryTest extends Web3IntegrationTest {
    private final TokenAllowanceRepository repository;

    @Test
    void findAllowance() {
        final var tokenAllowance = domainBuilder.tokenAllowance().persist();
        final var tokenId = tokenAllowance.getTokenId();
        final var owner = tokenAllowance.getOwner();
        final var spender = tokenAllowance.getSpender();
        final var expectedAllowance = tokenAllowance.getAmount();


        assertThat(repository.findAllowance(tokenId, owner, spender).orElse(0L))
                .isEqualTo(expectedAllowance);
    }
}
