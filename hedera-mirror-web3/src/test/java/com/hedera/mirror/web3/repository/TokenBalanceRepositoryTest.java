package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.web3.Web3IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenBalanceRepositoryTest extends Web3IntegrationTest {

    private final TokenBalanceRepository balanceRepository;

    @Test
    void findBalance() {
        final var tokenBalance = domainBuilder.tokenBalance().persist();
        final var tokenId = tokenBalance.getId().getTokenId().getId();
        final var accountId = tokenBalance.getId().getAccountId().getId();
        final var expectedBalance = tokenBalance.getBalance();

        assertThat(balanceRepository.findBalance(tokenId, accountId).orElse(0L))
                .isEqualTo(expectedBalance);
    }
}
