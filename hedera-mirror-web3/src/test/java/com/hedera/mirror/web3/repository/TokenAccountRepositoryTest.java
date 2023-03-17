package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum.FROZEN;
import static com.hedera.mirror.common.domain.token.TokenKycStatusEnum.GRANTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.web3.Web3IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenAccountRepositoryTest extends Web3IntegrationTest {
    private final TokenAccountRepository repository;
    private AbstractTokenAccount.Id tokenId;
    private static final long BALANCE = 122321455L;

    @BeforeEach
    void persistTokenAccount() {
        final var tokenAccount = domainBuilder.tokenAccount()
                .customize(a -> a.freezeStatus(FROZEN).kycStatus(GRANTED).balance(BALANCE)).persist();
        tokenId = tokenAccount.getId();
    }

    @Test
    void findFrozenStatus() {
        assertThat(repository.findById(tokenId).map(acc -> acc.getFreezeStatus().equals(FROZEN))
                .orElse(false)).isTrue();
    }

    @Test
    void findKycStatus() {
        assertThat(repository.findById(tokenId).map(acc -> acc.getKycStatus().equals(GRANTED)).orElse(false))
                .isTrue();
    }

    @Test
    void findBalance() {
        assertThat(repository.findById(tokenId).map(AbstractTokenAccount::getBalance).orElse(0L)).isEqualTo(BALANCE);
    }
}
