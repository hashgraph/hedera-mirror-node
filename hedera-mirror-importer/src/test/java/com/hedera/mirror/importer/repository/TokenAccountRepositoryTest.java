package com.hedera.mirror.importer.repository;

import java.util.Optional;
import javax.annotation.Resource;
import org.apache.commons.codec.DecoderException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TokenAccount;

public class TokenAccountRepositoryTest extends AbstractRepositoryTest {
    @Resource
    protected TokenAccountRepository tokenAccountRepository;

    private final EntityId tokenId = EntityId.of("0.0.101", EntityTypeEnum.TOKEN);
    private final EntityId accountId = EntityId.of("0.0.102", EntityTypeEnum.ACCOUNT);

    @Test
    void save() throws DecoderException {
        TokenAccount token = tokenAccountRepository.save(tokenAccount("0.0.101", "0.0.102"));
        Assertions.assertThat(tokenAccountRepository.findById(token.getId()).get())
                .isNotNull()
                .isEqualTo(token);
    }

    @Test
    void findByTokenIdAndAccountId() throws DecoderException {
        TokenAccount token1 = tokenAccountRepository.save(tokenAccount("0.0.101", "0.0.102"));
        TokenAccount token2 = tokenAccountRepository.save(tokenAccount("0.2.22", "0.2.44"));
        TokenAccount token3 = tokenAccountRepository.save(tokenAccount("1.0.7", "1.0.34"));
        Assertions.assertThat(tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of("0.2.22", EntityTypeEnum.TOKEN), EntityId
                        .of("0.2.44", EntityTypeEnum.ACCOUNT)).get())
                .isNotNull()
                .isEqualTo(token2);

        Assertions.assertThat(tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of("1.2.3", EntityTypeEnum.TOKEN), EntityId
                        .of("0.2.44", EntityTypeEnum.ACCOUNT))).isEqualTo(Optional.empty());
    }

    private TokenAccount tokenAccount(String tokenId, String accountId) throws DecoderException {
        TokenAccount tokenAccount = new TokenAccount();
        tokenAccount.setAssociated(true);
        tokenAccount.setKycStatus(0);
        tokenAccount.setFreezeStatus(0);
        tokenAccount.setAccountId(EntityId.of(accountId, EntityTypeEnum.ACCOUNT));
        tokenAccount.setCreatedTimestamp(1L);
        tokenAccount.setModifiedTimestamp(2L);
        tokenAccount.setTokenId(EntityId.of(tokenId, EntityTypeEnum.TOKEN));
        return tokenAccount;
    }
}
