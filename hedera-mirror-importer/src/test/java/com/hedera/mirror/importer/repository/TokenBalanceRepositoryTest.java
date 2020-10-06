package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TokenBalance;

class TokenBalanceRepositoryTest extends AbstractRepositoryTest {

    @Resource
    TokenBalanceRepository tokenBalanceRepository;

    @Test
    void save() {
        TokenBalance tokenBalance1 = create(1L, 1, 1, 100);
        TokenBalance tokenBalance2 = create(2L, 2, 2, 200);
        TokenBalance tokenBalance3 = create(3L, 3, 2, 300);
        assertThat(tokenBalanceRepository.findById(tokenBalance1.getId())).get()
                .isEqualTo(tokenBalance1);
        assertThat(tokenBalanceRepository.findAll())
                .containsExactlyInAnyOrder(tokenBalance1, tokenBalance2, tokenBalance3);
    }

    private TokenBalance create(long consensusTimestamp, int accountNum, int tokenNum, long balance) {
        TokenBalance tokenBalance = new TokenBalance();
        TokenBalance.Id id = new TokenBalance.Id();
        id.setAccountId(EntityId.of(0, 0, accountNum, EntityTypeEnum.ACCOUNT));
        id.setConsensusTimestamp(consensusTimestamp);
        id.setTokenId(EntityId.of(0, 0, tokenNum, EntityTypeEnum.TOKEN));
        tokenBalance.setBalance(balance);
        tokenBalance.setId(id);
        return tokenBalanceRepository.save(tokenBalance);
    }
}
