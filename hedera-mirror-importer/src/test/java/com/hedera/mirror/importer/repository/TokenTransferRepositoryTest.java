package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NftTransferId;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenPauseStatusEnum;
import com.hedera.mirror.importer.domain.TokenSupplyTypeEnum;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TokenTypeEnum;

class TokenTransferRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private NftRepository nftRepository;

    @Resource
    private NftTransferRepository nftTransferRepository;

    @Resource
    private TokenRepository tokenRepository;

    @Resource
    private TokenTransferRepository tokenTransferRepository;

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

    @Test
    void insertNftTransferForTokenDissociate() {
        // given
        // it's nft and the account has 2 nft instances at the time of token dissociate
        Token token = deletedNftClass(10L, 18L);
        EntityId tokenId = token.getTokenId().getTokenId();
        EntityId accountId = EntityId.of("0.0.210", EntityTypeEnum.ACCOUNT);
        Nft nft1 = nft(tokenId, accountId, 1, 15L);
        Nft nft2 = nft(tokenId, accountId, 2, 16L);

        tokenRepository.save(token);
        nftRepository.saveAll(List.of(nft1, nft2));

        // when
        // there is a tokenTransferList in the transaction record of a token dissociate transaction
        tokenTransferRepository.insertTransferForTokenDissociate(accountId.getId(), -2, 20L, tokenId.getId());

        // then
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(
                nft(tokenId, accountId, 1, 15L, 20L, true),
                nft(tokenId, accountId, 2, 16L, 20L, true)
        );
        assertThat(nftTransferRepository.findAll()).containsExactlyInAnyOrder(
                nftTransfer(tokenId, accountId, 1L, 20L),
                nftTransfer(tokenId, accountId, 2L, 20L)
        );
        assertThat(tokenTransferRepository.findAll()).isEmpty();
    }

    @Test
    void insertTokenTransferForTokenDissociate() {
        // given
        // it's a fungible token transfer from a token dissociate transaction
        EntityId accountId = EntityId.of("0.0.200", EntityTypeEnum.ACCOUNT);
        long amount = -2;
        long consensusTimestamp = 20;
        EntityId tokenId = EntityId.of("0.0.100", EntityTypeEnum.TOKEN);
        TokenTransfer expected = new TokenTransfer(consensusTimestamp, amount, tokenId, accountId);

        // when
        tokenTransferRepository.insertTransferForTokenDissociate(accountId.getId(), amount, consensusTimestamp,
                tokenId.getId());

        // then
        assertThat(nftRepository.findAll()).isEmpty();
        assertThat(nftTransferRepository.findAll()).isEmpty();
        assertThat(tokenTransferRepository.findAll()).containsOnly(expected);
    }

    private Token deletedNftClass(long createdTimestamp, long deletedTimestamp) {
        Token token = Token.of(EntityId.of("0.0.100", EntityTypeEnum.TOKEN));
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(0);
        token.setFreezeDefault(false);
        token.setInitialSupply(0L);
        token.setModifiedTimestamp(deletedTimestamp);
        token.setName("foo");
        token.setPauseStatus(TokenPauseStatusEnum.NOT_APPLICABLE);
        token.setSupplyType(TokenSupplyTypeEnum.FINITE);
        token.setSymbol("bar");
        token.setTotalSupply(200L);
        token.setTreasuryAccountId(EntityId.of("0.0.200", EntityTypeEnum.ACCOUNT));
        token.setType(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        return token;
    }

    private Nft nft(EntityId tokenId, EntityId accountId, long serialNumber, long consensusTimestamp) {
        return nft(tokenId, accountId, serialNumber, consensusTimestamp, consensusTimestamp, false);
    }

    private Nft nft(EntityId tokenId, EntityId accountId, long serialNumber, long consensusTimestamp,
                    long modifiedTimestamp, boolean deleted) {
        Nft nft = new Nft(serialNumber, tokenId);
        nft.setAccountId(accountId);
        nft.setCreatedTimestamp(consensusTimestamp);
        nft.setMetadata(new byte[] {1});
        nft.setDeleted(deleted);
        nft.setModifiedTimestamp(modifiedTimestamp);
        return nft;
    }

    private NftTransfer nftTransfer(EntityId tokenId, EntityId senderAccountId, long serialNumber,
                                    long consensusTimestamp) {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(consensusTimestamp, serialNumber, tokenId));
        nftTransfer.setSenderAccountId(senderAccountId);
        return nftTransfer;
    }
}
