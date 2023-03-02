package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.Web3IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftRepositoryTest extends Web3IntegrationTest {
    private static final EntityId spender = new EntityId(0L, 0L, 56L, EntityType.TOKEN);
    private final NftRepository nftRepository;
    private long nftId;
    private long serialNum;
    private long owner;
    private byte[] metadata;

    @BeforeEach
    private void persistNft() {
        final var nft = domainBuilder.nft().customize(n -> n.spender(spender)).persist();
        nftId = nft.getId().getTokenId().getId();
        serialNum = nft.getId().getSerialNumber();
        owner = nft.getAccountId().getId();
        metadata = nft.getMetadata();
    }

    @Test
    void findSpender() {

        assertThat(nftRepository.findSpender(nftId, serialNum).orElse(0L))
                .isEqualTo(spender.getEntityNum());
    }

    @Test
    void findOwner() {
        assertThat(nftRepository.findOwner(nftId, serialNum).orElse(0L))
                .isEqualTo(owner);
    }

    @Test
    void findMetadata() {
        assertThat(nftRepository.findMetadata(nftId, serialNum).orElse(new byte[0]))
                .isEqualTo(metadata);
    }
}
