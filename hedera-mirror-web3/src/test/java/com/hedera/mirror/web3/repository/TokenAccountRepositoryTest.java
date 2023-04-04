package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum.FROZEN;
import static com.hedera.mirror.common.domain.token.TokenKycStatusEnum.GRANTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.web3.Web3IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenAccountRepositoryTest extends Web3IntegrationTest {
    private final TokenAccountRepository repository;

    @Test
    void findById() {
        final var tokenAccount = domainBuilder.tokenAccount()
                .customize(a -> a.freezeStatus(FROZEN).kycStatus(GRANTED)).persist();

        assertThat(repository.findById(tokenAccount.getId()).get())
                .returns(tokenAccount.getFreezeStatus(), TokenAccount::getFreezeStatus)
                .returns(tokenAccount.getKycStatus(), TokenAccount::getKycStatus)
                .returns(tokenAccount.getBalance(), TokenAccount::getBalance);
    }
}
