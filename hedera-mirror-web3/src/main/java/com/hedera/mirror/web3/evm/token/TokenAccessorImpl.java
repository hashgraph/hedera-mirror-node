/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.token;

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum.FROZEN;
import static com.hedera.mirror.common.domain.token.TokenKycStatusEnum.GRANTED;
import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.evmKey;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;

import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.AbstractNftAllowance;
import com.hedera.mirror.common.domain.entity.AbstractTokenAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.web3.evm.exception.ParsingException;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenKeyType;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
public class TokenAccessorImpl implements TokenAccessor {

    private final MirrorNodeEvmProperties properties;
    private final Store store;

    @Override
    public Optional<EvmTokenInfo> evmInfoForToken(Address address) {
        return getTokenInfo(address);
    }

    @Override
    public Optional<EvmNftInfo> evmNftInfo(final Address address, long serialNo) {
        final var entityId = entityIdFromEvmAddress(address);
        final var nft = store.getUniqueToken(
                new NftId(entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum(), serialNo),
                OnMissing.DONT_THROW);
        if (nft.isEmptyUniqueToken()) {
            return Optional.empty();
        }

        final var ledgerId = properties.getNetwork().getLedgerId();
        final var entityAddress = nft.getOwner() != null
                ? evmAddressFromId(EntityId.of(nft.getOwner().asGrpcAccount()))
                : Address.ZERO;
        final var creationTime = nft.getCreationTime();
        final var metadata = nft.getMetadata();
        final var spender = nft.getSpender() != null
                ? evmAddressFromId(EntityId.of(nft.getSpender().asGrpcAccount()))
                : Address.ZERO;
        final var nftInfo =
                new EvmNftInfo(serialNo, entityAddress, creationTime.getSeconds(), metadata, spender, ledgerId);
        return Optional.of(nftInfo);
    }

    @Override
    public boolean isTokenAddress(final Address address) {
        final var entityOptional = store.getEntity(address, OnMissing.DONT_THROW);
        return entityOptional.filter(e -> e.getType() == TOKEN).isPresent();
    }

    @Override
    public boolean isFrozen(final Address address, final Address token) {
        final var tokenAccountId = new AbstractTokenAccount.Id();
        tokenAccountId.setTokenId(entityIdNumFromEvmAddress(token));
        tokenAccountId.setAccountId(entityIdFromAccountAddress(address));
        return store.getTokenAccount(tokenAccountId, OnMissing.DONT_THROW)
                .map(acc -> acc.getFreezeStatus().equals(FROZEN))
                .orElse(false);
    }

    @Override
    public boolean defaultFreezeStatus(final Address address) {
        return store.getFungibleToken(address, OnMissing.DONT_THROW).hasFreezeKey();
    }

    @Override
    public boolean defaultKycStatus(final Address address) {
        return store.getFungibleToken(address, OnMissing.DONT_THROW).hasKycKey();
    }

    @Override
    public boolean isKyc(final Address address, final Address token) {
        final var tokenAccountId = new AbstractTokenAccount.Id();
        tokenAccountId.setTokenId(entityIdNumFromEvmAddress(token));
        tokenAccountId.setAccountId(entityIdFromAccountAddress(address));
        return store.getTokenAccount(tokenAccountId, OnMissing.DONT_THROW)
                .map(acc -> acc.getKycStatus().equals(GRANTED))
                .orElse(false);
    }

    @Override
    public Optional<List<CustomFee>> infoForTokenCustomFees(final Address token) {
        return Optional.of(getCustomFees(token));
    }

    @Override
    public TokenType typeOf(final Address address) {
        return store.getFungibleToken(address, OnMissing.DONT_THROW).getType();
    }

    @Override
    public EvmKey keyOf(final Address address, final TokenKeyType tokenKeyType) {
        final var tokenInfoOptional = getTokenInfo(address);

        return tokenInfoOptional
                .map(tokenInfo -> switch (tokenKeyType) {
                    case ADMIN_KEY -> tokenInfo.getAdminKey();
                    case KYC_KEY -> tokenInfo.getKycKey();
                    case FREEZE_KEY -> tokenInfo.getFreezeKey();
                    case WIPE_KEY -> tokenInfo.getWipeKey();
                    case SUPPLY_KEY -> tokenInfo.getSupplyKey();
                    case FEE_SCHEDULE_KEY -> tokenInfo.getFeeScheduleKey();
                    case PAUSE_KEY -> tokenInfo.getPauseKey();
                })
                .orElse(new EvmKey());
    }

    @Override
    public String nameOf(final Address address) {
        return store.getFungibleToken(address, OnMissing.DONT_THROW).getName();
    }

    @Override
    public String symbolOf(final Address address) {
        return store.getFungibleToken(address, OnMissing.DONT_THROW).getSymbol();
    }

    @Override
    public long totalSupplyOf(final Address address) {
        return store.getFungibleToken(address, OnMissing.DONT_THROW).getTotalSupply();
    }

    @Override
    public int decimalsOf(final Address address) {
        return store.getFungibleToken(address, OnMissing.DONT_THROW).getDecimals();
    }

    @Override
    public long balanceOf(Address address, Address token) {
        final var tokenAccountId = new AbstractTokenAccount.Id();
        tokenAccountId.setTokenId(entityIdNumFromEvmAddress(token));
        tokenAccountId.setAccountId(entityIdFromAccountAddress(address));
        return store.getTokenAccount(tokenAccountId, OnMissing.DONT_THROW)
                .map(AbstractTokenAccount::getBalance)
                .orElse(0L);
    }

    @Override
    public long staticAllowanceOf(final Address owner, final Address spender, final Address token) {
        final var tokenAllowanceId = new AbstractTokenAllowance.Id();
        tokenAllowanceId.setOwner(entityIdFromAccountAddress(owner));
        tokenAllowanceId.setSpender(entityIdFromAccountAddress(spender));
        tokenAllowanceId.setTokenId(entityIdNumFromEvmAddress(token));

        return store.getTokenAllowance(tokenAllowanceId, OnMissing.DONT_THROW)
                .map(TokenAllowance::getAmount)
                .orElse(0L);
    }

    @Override
    public Address staticApprovedSpenderOf(final Address address, long serialNo) {
        final var entityId = entityIdFromEvmAddress(address);
        final var nft = store.getUniqueToken(
                new NftId(entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum(), serialNo),
                OnMissing.DONT_THROW);
        if (nft.isEmptyUniqueToken()) {
            return Address.ZERO;
        }
        final var spender = nft.getSpender();
        if (spender == null) {
            return Address.ZERO;
        }
        return evmAddressFromId(EntityId.of(spender.asGrpcAccount()));
    }

    @Override
    public boolean staticIsOperator(final Address owner, final Address operator, final Address token) {
        final var nftAllowanceId = new AbstractNftAllowance.Id();
        nftAllowanceId.setOwner(entityIdFromAccountAddress(owner));
        nftAllowanceId.setSpender(entityIdFromAccountAddress(operator));
        nftAllowanceId.setTokenId(entityIdNumFromEvmAddress(token));
        return store.getNftAllowance(nftAllowanceId, OnMissing.DONT_THROW)
                .map(NftAllowance::isApprovedForAll)
                .orElse(false);
    }

    @Override
    public Address ownerOf(final Address address, long serialNo) {
        final var entityId = entityIdFromEvmAddress(address);
        final var nft = store.getUniqueToken(
                new NftId(entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum(), serialNo),
                OnMissing.DONT_THROW);
        if (nft.isEmptyUniqueToken()) {
            return Address.ZERO;
        }
        final var owner = nft.getOwner();

        if (owner == null) {
            return Address.ZERO;
        }

        return evmAddressFromId(EntityId.of(owner.asGrpcAccount()));
    }

    @Override
    public Address canonicalAddress(final Address addressOrAlias) {
        return addressOrAlias;
    }

    @Override
    public String metadataOf(final Address address, long serialNo) {
        final var entityId = entityIdFromEvmAddress(address);
        final var nft = store.getUniqueToken(
                new NftId(entityId.getShardNum(), entityId.getRealmNum(), entityId.getEntityNum(), serialNo),
                OnMissing.DONT_THROW);
        if (nft.isEmptyUniqueToken()) {
            return "";
        }
        return new String(nft.getMetadata(), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] ledgerId() {
        return properties.getNetwork().getLedgerId();
    }

    private Optional<EvmTokenInfo> getTokenInfo(final Address address) {
        final var tokenEntity = store.getFungibleToken(address, OnMissing.DONT_THROW);
        final var entityOptional = store.getEntity(address, OnMissing.DONT_THROW);

        if (tokenEntity.isEmptyToken() || entityOptional.isEmpty()) {
            return Optional.empty();
        }

        final var entity = entityOptional.get();
        final var ledgerId = ledgerId();
        final var expirationTimeInSec = expirationTimeSeconds(entity);
        final EvmTokenInfo evmTokenInfo = new EvmTokenInfo(
                ledgerId,
                tokenEntity.getSupplyType().ordinal(),
                entity.getDeleted(),
                tokenEntity.getSymbol(),
                tokenEntity.getName(),
                entity.getMemo(),
                tokenEntity.getTreasury().getAccountAddress(),
                tokenEntity.getTotalSupply(),
                tokenEntity.getMaxSupply(),
                tokenEntity.getDecimals(),
                expirationTimeInSec);
        evmTokenInfo.setAutoRenewPeriod(entity.getAutoRenewPeriod() != null ? entity.getAutoRenewPeriod() : 0);
        evmTokenInfo.setDefaultFreezeStatus(tokenEntity.hasFreezeKey());
        evmTokenInfo.setCustomFees(getCustomFees(address));
        setEvmKeys(entity, tokenEntity, evmTokenInfo);
        final var isPaused = tokenEntity.isPaused();
        evmTokenInfo.setIsPaused(isPaused);

        if (entity.getAutoRenewAccountId() != null) {
            var autoRenewAddress = evmAddressFromId(EntityId.of(entity.getAutoRenewAccountId(), EntityType.ACCOUNT));
            evmTokenInfo.setAutoRenewAccount(autoRenewAddress);
        }

        return Optional.of(evmTokenInfo);
    }

    private Long expirationTimeSeconds(Entity entity) {
        Long createdTimestamp = entity.getCreatedTimestamp();
        Long autoRenewPeriod = entity.getAutoRenewPeriod();
        Long expirationTimestamp = entity.getExpirationTimestamp();

        if (expirationTimestamp != null) {
            return expirationTimestamp / NANOS_PER_SECOND;
        } else if (createdTimestamp != null && autoRenewPeriod != null) {
            return createdTimestamp / NANOS_PER_SECOND + autoRenewPeriod;
        } else {
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    private List<CustomFee> getCustomFees(final Address address) {
        return store.getCustomFee(entityIdNumFromEvmAddress(address), OnMissing.DONT_THROW)
                .orElse(Collections.emptyList());
    }

    private void setEvmKeys(final Entity entity, final Token tokenEntity, final EvmTokenInfo evmTokenInfo) {
        try {
            asKeyUnchecked(tokenEntity.getAdminKey()).toByteArray();
            final var adminKey = evmKey(entity.getKey());
            final var kycKey = evmKey(asKeyUnchecked(tokenEntity.getAdminKey()).toByteArray());
            final var supplyKey =
                    evmKey(asKeyUnchecked(tokenEntity.getSupplyKey()).toByteArray());
            final var freezeKey =
                    evmKey(asKeyUnchecked(tokenEntity.getFreezeKey()).toByteArray());
            final var wipeKey = evmKey(asKeyUnchecked(tokenEntity.getWipeKey()).toByteArray());
            final var pauseKey =
                    evmKey(asKeyUnchecked(tokenEntity.getPauseKey()).toByteArray());
            final var feeScheduleKey =
                    evmKey(asKeyUnchecked(tokenEntity.getFeeScheduleKey()).toByteArray());

            evmTokenInfo.setAdminKey(adminKey);
            evmTokenInfo.setKycKey(kycKey);
            evmTokenInfo.setSupplyKey(supplyKey);
            evmTokenInfo.setFreezeKey(freezeKey);
            evmTokenInfo.setWipeKey(wipeKey);
            evmTokenInfo.setPauseKey(pauseKey);
            evmTokenInfo.setFeeScheduleKey(feeScheduleKey);
        } catch (final IOException e) {
            throw new ParsingException("Error parsing token keys.");
        }
    }

    /**
     * This method is temporary and prevent wrong conversion for eth_call account and contract addresses. This will be
     * later addressed directly in the hedera-evm-lib and this method will be removed.
     */
    private Long entityIdFromAccountAddress(final Address address) {
        final var addressBytes = address.toArrayUnsafe();
        if (isMirror(addressBytes)) {
            final var id = fromEvmAddress(addressBytes);
            return id != null ? id.getId() : 0L;
        }
        return store.getEntity(address, OnMissing.DONT_THROW)
                .map(AbstractEntity::getId)
                .orElse(0L);
    }

    private Address evmAddressFromId(EntityId entityId) {
        Entity entity = store.getEntity(entityId, OnMissing.DONT_THROW).orElse(null);
        if (entity == null) {
            return Address.ZERO;
        }

        if (entity.getEvmAddress() != null) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }

        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }

        return toAddress(entityId);
    }
}
