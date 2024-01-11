/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.balance.AccountBalance.Id;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.repository.TokenRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@Tag("migration")
class FixFungibleTokenTotalSupplyMigrationTest extends ImporterIntegrationTest {

    private final FixFungibleTokenTotalSupplyMigration migration;
    private final TokenRepository tokenRepository;

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isEqualTo(2);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        long lastTimestamp = domainBuilder.timestamp();
        var treasuryAccount =
                domainBuilder.entity().customize(a -> a.num(2L)).persist().toEntityId();
        var account = domainBuilder.entity().persist().toEntityId();
        long token1DissociateAmount = 500;
        var treasury = domainBuilder.entity().persist().toEntityId();
        var token1 = domainBuilder
                .token()
                // with incorrect total supply
                .customize(t -> t.initialSupply(100_000L)
                        .totalSupply(100_000L - token1DissociateAmount)
                        .treasuryAccountId(treasury))
                .persist();
        var token1EntityId = EntityId.of(token1.getTokenId());
        var token2 = domainBuilder
                .token()
                .customize(t -> t.initialSupply(1_000_000_000L)
                        .totalSupply(1_000_000_000L)
                        .treasuryAccountId(treasury))
                .persist();
        var token2EntityId = EntityId.of(token2.getTokenId());
        var token3 = domainBuilder
                .token()
                .customize(t -> t.initialSupply(0L)
                        .totalSupply(1_000_000_000L)
                        .treasuryAccountId(treasury)
                        .type(NON_FUNGIBLE_UNIQUE))
                .persist(); // nft
        var token3EntityId = EntityId.of(token3.getTokenId());
        // token4 created after the last account balance file
        var token4 = domainBuilder
                .token()
                .customize(t -> t.createdTimestamp(plus(lastTimestamp, Duration.ofSeconds(-1)))
                        .initialSupply(1_000_000_000L)
                        .totalSupply(1_000_000_000L)
                        .treasuryAccountId(treasury))
                .persist();
        var token4EntityId = EntityId.of(token4.getTokenId());

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(plus(lastTimestamp, Duration.ofSeconds(-2)))
                        .consensusEnd(lastTimestamp))
                .persist();
        var accountBalanceTimestamp = plus(lastTimestamp, Duration.ofMinutes(-5));
        // treasury account balance
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(accountBalanceTimestamp, treasuryAccount)))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(accountBalanceTimestamp, account)))
                .persist();
        // token1 balance distribution
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(token1.getInitialSupply() - token1DissociateAmount)
                        .id(new TokenBalance.Id(accountBalanceTimestamp, treasury, token1EntityId)))
                .persist();
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(token1DissociateAmount)
                        .id(new TokenBalance.Id(accountBalanceTimestamp, account, token1EntityId)))
                .persist();
        // token2 balance distribution
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(token2.getTotalSupply() - 3000L)
                        .id(new TokenBalance.Id(accountBalanceTimestamp, treasury, token2EntityId)))
                .persist();
        domainBuilder
                .tokenBalance()
                .customize(tb ->
                        tb.balance(3000L).id(new TokenBalance.Id(accountBalanceTimestamp, account, token2EntityId)))
                .persist();
        // token3 balance distribution
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(token3.getTotalSupply() - 5000L)
                        .id(new TokenBalance.Id(accountBalanceTimestamp, treasury, token3EntityId)))
                .persist();
        domainBuilder
                .tokenBalance()
                .customize(tb ->
                        tb.balance(5000L).id(new TokenBalance.Id(accountBalanceTimestamp, account, token3EntityId)))
                .persist();
        // no token4 balance distribution in account balance file

        // account dissociate from token1 with returning to treasury token transfers
        long dissociateTimestamp = plus(accountBalanceTimestamp, Duration.ofNanos(10));
        domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.amount(token1DissociateAmount)
                        .id(new TokenTransfer.Id(dissociateTimestamp, token1EntityId, treasury)))
                .persist();
        domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.amount(-token1DissociateAmount)
                        .id(new TokenTransfer.Id(dissociateTimestamp, token1EntityId, account)))
                .persist();
        // token2 has mint burn, and transfer. mint and burn sum to 0
        long token2MintTimestamp = plus(dissociateTimestamp, Duration.ofNanos(10));
        domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.amount(200).id(new TokenTransfer.Id(token2MintTimestamp, token2EntityId, treasury)))
                .persist();
        long token2BurnTimestamp = plus(token2MintTimestamp, Duration.ofNanos(10));
        domainBuilder
                .tokenTransfer()
                .customize(
                        tt -> tt.amount(-200).id(new TokenTransfer.Id(token2BurnTimestamp, token2EntityId, treasury)))
                .persist();
        long token2TransferTimestamp = plus(token2BurnTimestamp, Duration.ofNanos(10));
        domainBuilder
                .tokenTransfer()
                .customize(tt ->
                        tt.amount(-100).id(new TokenTransfer.Id(token2TransferTimestamp, token2EntityId, treasury)))
                .persist();
        domainBuilder
                .tokenTransfer()
                .customize(
                        tt -> tt.amount(100).id(new TokenTransfer.Id(token2TransferTimestamp, token2EntityId, account)))
                .persist();
        // no transactions for token3
        // initial transfer to treasury for token4
        domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.amount(token4.getInitialSupply())
                        .id(new TokenTransfer.Id(token4.getCreatedTimestamp(), token4EntityId, treasury)))
                .persist();

        // when
        runMigration();

        // then
        token1.setTotalSupply(token1.getInitialSupply());
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(token1, token2, token3, token4);
    }

    private long plus(long baseNanos, Duration d) {
        return baseNanos + d.toNanos();
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }
}
