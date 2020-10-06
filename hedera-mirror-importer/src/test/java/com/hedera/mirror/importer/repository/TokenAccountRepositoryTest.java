package com.hedera.mirror.importer.repository;

import java.util.Optional;
import javax.annotation.Resource;
import org.apache.commons.codec.DecoderException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;

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
        tokenAccountRepository.save(tokenAccount("0.0.101", "0.0.102"));
        String tokenId = "0.2.22";
        String accountId = "0.2.44";
        TokenAccount token2 = tokenAccountRepository.save(tokenAccount(tokenId, accountId));
        tokenAccountRepository.save(tokenAccount("1.0.7", "1.0.34"));
        Assertions.assertThat(tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of(tokenId, EntityTypeEnum.TOKEN).getId(), EntityId
                        .of(accountId, EntityTypeEnum.ACCOUNT).getId()).get())
                .isNotNull()
                .isEqualTo(token2);

        Assertions.assertThat(tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of("1.2.3", EntityTypeEnum.TOKEN).getId(), EntityId
                        .of("0.2.44", EntityTypeEnum.ACCOUNT).getId())).isEqualTo(Optional.empty());
    }

    private TokenAccount tokenAccount(String tokenId, String accountId) {
        TokenAccount tokenAccount = new TokenAccount(EntityId
                .of(tokenId, EntityTypeEnum.TOKEN), EntityId.of(accountId, EntityTypeEnum.ACCOUNT));
        tokenAccount.setAssociated(true);
        tokenAccount.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        tokenAccount.setCreatedTimestamp(1L);
        tokenAccount.setModifiedTimestamp(2L);
        return tokenAccount;
    }
}
