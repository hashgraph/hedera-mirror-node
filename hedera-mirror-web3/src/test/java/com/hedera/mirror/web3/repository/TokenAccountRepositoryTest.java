package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum.FROZEN;
import static com.hedera.mirror.common.domain.token.TokenKycStatusEnum.GRANTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.web3.Web3IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenAccountRepositoryTest extends Web3IntegrationTest {
    private final TokenAccountRepository repository;
    private long tokenId;
    private long accountId;

    @BeforeEach
    void persistTokenAccount() {
        final var tokenAccount = domainBuilder.tokenAccount()
                .customize(a -> a.freezeStatus(FROZEN).kycStatus(GRANTED)).persist();
        tokenId = tokenAccount.getTokenId();
        accountId = tokenAccount.getAccountId();
    }

    @Test
    void findFrozenStatus() {
        assertThat(repository.findFrozenStatus(accountId, tokenId).orElse(0)).isEqualTo(FROZEN.ordinal());
    }

    @Test
    void findKycStatus() {
        assertThat(repository.findKycStatus(accountId, tokenId).orElse(0))
                .isEqualTo(GRANTED.ordinal());
    }
}
