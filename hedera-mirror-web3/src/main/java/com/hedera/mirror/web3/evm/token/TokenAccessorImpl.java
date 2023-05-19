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
import static com.hedera.mirror.common.util.DomainUtils.*;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.*;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static java.util.Objects.requireNonNullElse;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.*;
import com.hedera.mirror.common.domain.token.*;
import com.hedera.mirror.web3.evm.exception.ParsingException;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.*;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.util.CollectionUtils;

@Named
@RequiredArgsConstructor
public class TokenAccessorImpl implements TokenAccessor {

    private StackedStateFrames<Object> state;
    private final MirrorNodeEvmProperties properties;

    public void setState(StackedStateFrames<Object> state) {
        this.state = state;
    }

    @Override
    public Optional<EvmTokenInfo> evmInfoForToken(Address address) {
        return getTokenInfo(address);
    }

    @Override
    public Optional<EvmNftInfo> evmNftInfo(final Address nft, long serialNo) {
        final var topFrame = state.top();
        final var nftAccessor = topFrame.getAccessor(Nft.class);
        final var nftOptional = nftAccessor.get(new NftId(serialNo, fromEvmAddress(nft.toArrayUnsafe())));
        if (nftOptional.isEmpty()) {
            return Optional.empty();
        }

        final var ledgerId = properties.getNetwork().getLedgerId();
        final var nftEntity = nftOptional.get();
        final var entityAddress = evmAddressFromId(nftEntity.getAccountId());
        final var creationTime = nftEntity.getCreatedTimestamp();
        final var metadata = nftEntity.getMetadata();
        final var spender = nftEntity.getSpender() != null ? evmAddressFromId(nftEntity.getSpender()) : Address.ZERO;
        final var nftInfo = new EvmNftInfo(serialNo, entityAddress, creationTime, metadata, spender, ledgerId);
        return Optional.of(nftInfo);
    }

    @Override
    public boolean isTokenAddress(final Address address) {
        final var topFrame = state.top();
        final var entityAccessor = topFrame.getAccessor(Entity.class);
        final var entityOptional = entityAccessor.get(address);
        return entityOptional.filter(e -> e.getType() == TOKEN).isPresent();
    }

    @Override
    public boolean isFrozen(final Address account, final Address token) {
        final var tokenAccountId = new AbstractTokenAccount.Id();
        final var topFrame = state.top();
        final var tokenAccountAccessor = topFrame.getAccessor(TokenAccount.class);
        tokenAccountId.setTokenId(entityIdNumFromEvmAddress(token));
        tokenAccountId.setAccountId(entityIdFromAccountAddress(account));
        return tokenAccountAccessor
                .get(tokenAccountId)
                .map(acc -> acc.getFreezeStatus().equals(FROZEN))
                .orElse(false);
    }

    @Override
    public boolean defaultFreezeStatus(final Address token) {
        final var tokenId = new TokenId(entityIdFromEvmAddress(token));
        final var topFrame = state.top();
        final var tokenAccessor = topFrame.getAccessor(Token.class);
        return tokenAccessor.get(tokenId).map(Token::getFreezeDefault).orElse(false);
    }

    @Override
    public boolean defaultKycStatus(final Address token) {
        final var tokenId = new TokenId(entityIdFromEvmAddress(token));
        final var topFrame = state.top();
        final var tokenAccessor = topFrame.getAccessor(Token.class);
        return tokenAccessor.get(tokenId).map(Token::getKycKey).isPresent();
    }

    @Override
    public boolean isKyc(final Address account, final Address token) {
        final var tokenAccountId = new AbstractTokenAccount.Id();
        tokenAccountId.setTokenId(entityIdNumFromEvmAddress(token));
        tokenAccountId.setAccountId(entityIdFromAccountAddress(account));
        final var topFrame = state.top();
        final var tokenAccountAccessor = topFrame.getAccessor(TokenAccount.class);
        return tokenAccountAccessor
                .get(tokenAccountId)
                .map(acc -> acc.getKycStatus().equals(GRANTED))
                .orElse(false);
    }

    @Override
    public Optional<List<CustomFee>> infoForTokenCustomFees(final Address token) {
        return Optional.of(getCustomFees(token));
    }

    @Override
    public TokenType typeOf(final Address token) {
        final var tokenId = new TokenId(entityIdFromEvmAddress(token));
        final var topFrame = state.top();
        final var tokenAccessor = topFrame.getAccessor(Token.class);
        return tokenAccessor
                .get(tokenId)
                .map(t -> TokenType.valueOf(t.getType().name()))
                .orElse(null);
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
    public String nameOf(final Address token) {
        final var tokenId = new TokenId(entityIdFromEvmAddress(token));
        final var topFrame = state.top();
        final var tokenAccessor = topFrame.getAccessor(Token.class);
        return tokenAccessor.get(tokenId).map(Token::getName).orElse("");
    }

    @Override
    public String symbolOf(final Address token) {
        final var tokenId = new TokenId(entityIdFromEvmAddress(token));
        final var topFrame = state.top();
        final var tokenAccessor = topFrame.getAccessor(Token.class);
        return tokenAccessor.get(tokenId).map(Token::getSymbol).orElse("");
    }

    @Override
    public long totalSupplyOf(final Address token) {
        final var tokenId = new TokenId(entityIdFromEvmAddress(token));
        final var topFrame = state.top();
        final var tokenAccessor = topFrame.getAccessor(Token.class);
        return tokenAccessor.get(tokenId).map(Token::getTotalSupply).orElse(0L);
    }

    @Override
    public int decimalsOf(final Address token) {
        final var tokenId = new TokenId(entityIdFromEvmAddress(token));
        final var topFrame = state.top();
        final var tokenAccessor = topFrame.getAccessor(Token.class);
        return tokenAccessor.get(tokenId).map(Token::getDecimals).orElse(0);
    }

    @Override
    public long balanceOf(Address account, Address token) {
        final var tokenAccountId = new AbstractTokenAccount.Id();
        final var topFrame = state.top();
        final var tokenAccountAccessor = topFrame.getAccessor(TokenAccount.class);
        tokenAccountId.setAccountId(entityIdFromAccountAddress(account));
        tokenAccountId.setTokenId(entityIdNumFromEvmAddress(token));
        return tokenAccountAccessor
                .get(tokenAccountId)
                .map(AbstractTokenAccount::getBalance)
                .orElse(0L);
    }

    @Override
    public long staticAllowanceOf(final Address owner, final Address spender, final Address token) {
        if (!properties.isAllowanceEnabled()) {
            throw new UnsupportedOperationException("allowance(address owner, address spender) is not supported.");
        }

        final var tokenAllowanceId = new AbstractTokenAllowance.Id();
        tokenAllowanceId.setOwner(entityIdFromAccountAddress(owner));
        tokenAllowanceId.setSpender(entityIdFromAccountAddress(spender));
        tokenAllowanceId.setTokenId(entityIdNumFromEvmAddress(token));

        final var topFrame = state.top();
        final var tokenAllowanceAccessor = topFrame.getAccessor(TokenAllowance.class);
        return tokenAllowanceAccessor
                .get(tokenAllowanceId)
                .map(TokenAllowance::getAmount)
                .orElse(0L);
    }

    @Override
    public Address staticApprovedSpenderOf(final Address nft, long serialNo) {
        final var nftId = new NftId(serialNo, entityIdFromEvmAddress(nft));
        final var topFrame = state.top();
        final var nftAccessor = topFrame.getAccessor(Nft.class);
        final var spenderEntity = nftAccessor.get(nftId).map(Nft::getSpender);

        if (spenderEntity.isEmpty()) {
            return Address.ZERO;
        }

        return evmAddressFromId(spenderEntity.get());
    }

    @Override
    public boolean staticIsOperator(final Address owner, final Address operator, final Address token) {
        if (!properties.isApprovedForAllEnabled()) {
            throw new UnsupportedOperationException(
                    "isApprovedForAll(address owner, address operator) is not supported.");
        }

        final var nftAllowanceId = new AbstractNftAllowance.Id();
        nftAllowanceId.setOwner(entityIdFromAccountAddress(owner));
        nftAllowanceId.setSpender(entityIdFromAccountAddress(operator));
        nftAllowanceId.setTokenId(entityIdNumFromEvmAddress(token));

        final var topFrame = state.top();
        final var nftAllowanceAccessor = topFrame.getAccessor(NftAllowance.class);

        return nftAllowanceAccessor
                .get(nftAllowanceId)
                .map(NftAllowance::isApprovedForAll)
                .orElse(false);
    }

    @Override
    public Address ownerOf(final Address nft, long serialNo) {
        final var nftId = new NftId(serialNo, entityIdFromEvmAddress(nft));
        final var topFrame = state.top();
        final var nftAccessor = topFrame.getAccessor(Nft.class);
        final var ownerEntity = nftAccessor.get(nftId).map(Nft::getAccountId);

        if (ownerEntity.isEmpty()) {
            return Address.ZERO;
        }

        return evmAddressFromId(ownerEntity.get());
    }

    @Override
    public Address canonicalAddress(final Address addressOrAlias) {
        return addressOrAlias;
    }

    @Override
    public String metadataOf(final Address nft, long serialNo) {
        final var nftId = new NftId(serialNo, entityIdFromEvmAddress(nft));

        final var topFrame = state.top();
        final var nftAccessor = topFrame.getAccessor(Nft.class);
        return nftAccessor
                .get(nftId)
                .map(n -> new String(n.getMetadata(), StandardCharsets.UTF_8))
                .orElse("");
    }

    @Override
    public byte[] ledgerId() {
        return properties.getNetwork().getLedgerId();
    }

    private Optional<EvmTokenInfo> getTokenInfo(final Address token) {
        final var topFrame = state.top();
        final var tokenAccessor = topFrame.getAccessor(Token.class);
        final var entityAccessor = topFrame.getAccessor(Entity.class);
        final var tokenEntityOptional = tokenAccessor.get(new TokenId(fromEvmAddress(token.toArray())));
        final var entityOptional = entityAccessor.get(token);

        if (tokenEntityOptional.isEmpty() || entityOptional.isEmpty()) {
            return Optional.empty();
        }

        final var tokenEntity = tokenEntityOptional.get();
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
                evmAddressFromId(tokenEntity.getTreasuryAccountId()),
                tokenEntity.getTotalSupply(),
                tokenEntity.getMaxSupply(),
                tokenEntity.getDecimals(),
                expirationTimeInSec);
        evmTokenInfo.setAutoRenewPeriod(entity.getAutoRenewPeriod() != null ? entity.getAutoRenewPeriod() : 0);
        evmTokenInfo.setDefaultFreezeStatus(tokenEntity.getFreezeDefault());
        evmTokenInfo.setCustomFees(getCustomFees(token));
        setEvmKeys(entity, tokenEntity, evmTokenInfo);
        final var isPaused = tokenEntity.getPauseStatus().equals(TokenPauseStatusEnum.PAUSED);
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
    private List<CustomFee> getCustomFees(final Address token) {
        final List<CustomFee> customFees = new ArrayList<>();
        final var topFrame = state.top();
        final var customFeeAccessor = topFrame.getAccessor(List.class);
        final List<com.hedera.mirror.common.domain.transaction.CustomFee> customFeesCollection =
                customFeeAccessor.get(entityIdNumFromEvmAddress(token)).get();
        if (CollectionUtils.isEmpty(customFeesCollection)) {
            return customFees;
        }

        for (final var customFee : customFeesCollection) {
            final var collectorId = customFee.getCollectorAccountId();
            if (collectorId == null) {
                continue;
            }

            final var collector = evmAddressFromId(collectorId);
            final long amount = requireNonNullElse(customFee.getAmount(), 0L);
            final var denominatingTokenId = customFee.getDenominatingTokenId();
            final var denominatingTokenAddress =
                    denominatingTokenId == null ? EMPTY_EVM_ADDRESS : toAddress(denominatingTokenId);
            final long amountDenominator = requireNonNullElse(customFee.getAmountDenominator(), 0L);
            final var maximumAmount = requireNonNullElse(customFee.getMaximumAmount(), 0L);
            final var minimumAmount = customFee.getMinimumAmount();
            final var netOfTransfers = requireNonNullElse(customFee.getNetOfTransfers(), false);
            final long royaltyDenominator = requireNonNullElse(customFee.getRoyaltyDenominator(), 0L);
            final long royaltyNumerator = requireNonNullElse(customFee.getRoyaltyNumerator(), 0L);

            CustomFee customFeeConstructed = new CustomFee();

            if (royaltyNumerator > 0 && royaltyDenominator > 0) {
                final var royaltyFee = new RoyaltyFee(
                        royaltyNumerator,
                        royaltyDenominator,
                        amount,
                        denominatingTokenAddress,
                        denominatingTokenId == null,
                        collector);
                customFeeConstructed.setRoyaltyFee(royaltyFee);

            } else if (amountDenominator > 0) {
                final var fractionFee = new FractionalFee(
                        amount, amountDenominator, minimumAmount, maximumAmount, netOfTransfers, collector);
                customFeeConstructed.setFractionalFee(fractionFee);

            } else {
                final var fixedFee =
                        new FixedFee(amount, denominatingTokenAddress, denominatingTokenId == null, false, collector);

                customFeeConstructed.setFixedFee(fixedFee);
            }
            customFees.add(customFeeConstructed);
        }
        return customFees;
    }

    private void setEvmKeys(final Entity entity, final Token tokenEntity, final EvmTokenInfo evmTokenInfo) {
        try {
            final var adminKey = evmKey(entity.getKey());
            final var kycKey = evmKey(tokenEntity.getKycKey());
            final var supplyKey = evmKey(tokenEntity.getSupplyKey());
            final var freezeKey = evmKey(tokenEntity.getFreezeKey());
            final var wipeKey = evmKey(tokenEntity.getWipeKey());
            final var pauseKey = evmKey(tokenEntity.getPauseKey());
            final var feeScheduleKey = evmKey(tokenEntity.getFeeScheduleKey());

            evmTokenInfo.setAdminKey(adminKey);
            evmTokenInfo.setKycKey(kycKey);
            evmTokenInfo.setSupplyKey(supplyKey);
            evmTokenInfo.setFreezeKey(freezeKey);
            evmTokenInfo.setWipeKey(wipeKey);
            evmTokenInfo.setPauseKey(pauseKey);
            evmTokenInfo.setFeeScheduleKey(feeScheduleKey);
        } catch (final InvalidProtocolBufferException e) {
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

        final var topFrame = state.top();
        final var entityAccessor = topFrame.getAccessor(Entity.class);
        return entityAccessor.get(address).map(AbstractEntity::getId).orElse(0L);
    }

    private Address evmAddressFromId(EntityId entityId) {
        final var topFrame = state.top();
        final var entityAccessor = topFrame.getAccessor(Entity.class);
        Entity entity = entityAccessor.get(entityId).orElse(null);

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
