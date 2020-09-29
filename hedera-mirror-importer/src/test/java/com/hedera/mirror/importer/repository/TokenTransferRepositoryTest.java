package com.hedera.mirror.importer.repository;

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.TokenTransfer;

public class TokenTransferRepositoryTest extends AbstractRepositoryTest {
    @Resource
    protected TokenTransferRepository tokenTransferRepository;

    @Test
    void findById() {
        EntityId tokenId = EntityId.of(0L, 1L, 20L, TOKEN);
        EntityId accountId = EntityId.of(0L, 1L, 7L, ACCOUNT);
        long amount = 40L;
        TokenTransfer tokenTransfer = new TokenTransfer(1L, amount, tokenId, accountId);

        tokenTransferRepository.save(tokenTransfer);

        assertThat(tokenTransferRepository.findById(tokenTransfer.getId()))
                .get()
                .isEqualTo(tokenTransfer);
    }
}
