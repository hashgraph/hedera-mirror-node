package com.hedera.mirror.web3.evm.token;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.evmKey;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static org.apache.tuweni.bytes.Bytes.EMPTY;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.web3.evm.exception.ParsingException;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.utils.EvmTokenUtils;
import com.hedera.mirror.web3.repository.CustomFeeRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.mirror.web3.repository.TokenBalanceRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FixedFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.RoyaltyFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenKeyType;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;

@Named
@RequiredArgsConstructor
public class TokenAccessorImpl implements TokenAccessor {

    private final EntityRepository entityRepository;
    private final TokenRepository tokenRepository;
    private final NftRepository nftRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final CustomFeeRepository customFeeRepository;
    private final MirrorNodeEvmProperties properties;

    @Override
    public Optional<EvmTokenInfo> evmInfoForToken(Address address) {
        return getTokenInfo(address);
    }

    @Override
    public Optional<EvmNftInfo> evmNftInfo(final Address nft, long serialNo) {
        final var nftOptional = nftRepository.findById(new NftId(serialNo, fromEvmAddress(nft.toArrayUnsafe())));
        if (nftOptional.isEmpty()) {
            return Optional.empty();
        }
        final var ledgerId = properties.getNetwork().getLedgerId();
        final var nftEntity = nftOptional.get();
        final var entityAddress = toAddress(nftEntity.getAccountId());
        final var creationTime = nftEntity.getCreatedTimestamp();
        final var metadata = nftEntity.getMetadata();
        final var spender = nftEntity.getSpender() != null ? toAddress(nftEntity.getSpender()) : Address.ZERO;
        final var nftInfo = new EvmNftInfo(serialNo, entityAddress, creationTime, metadata, spender, ledgerId);

        return Optional.of(nftInfo);
    }

    @Override
    public boolean isTokenAddress(final Address address) {
        final var entityId = entityIdFromAccountAddress(address);
        final var entity = entityRepository.findByIdAndDeletedIsFalse(entityId);

        return entity.filter(e -> e.getType() == TOKEN).isPresent();
    }

    @Override
    public boolean isFrozen(final Address account, final Address token) {
        final var accountId = entityIdFromAccountAddress(account);
        final var tokenId = entityIdFromEvmAddress(token);
        final var status = tokenAccountRepository.findFrozenStatus(accountId, tokenId);
        return status.filter(e -> e == 1).isPresent();
    }

    @Override
    public boolean defaultFreezeStatus(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        final var defaultFreezeStatus = tokenRepository.findFreezeDefault(tokenId);
        return defaultFreezeStatus.orElse(false);
    }

    @Override
    public boolean defaultKycStatus(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        return tokenRepository.findKycKey(tokenId).isPresent();
    }

    @Override
    public boolean isKyc(final Address account, final Address token) {
        final var accountId = entityIdFromAccountAddress(account);
        final var tokenId = entityIdFromEvmAddress(token);
        final var status = tokenAccountRepository.findKycStatus(accountId, tokenId);
        return status.filter(e -> e == 1).isPresent();
    }

    @Override
    public Optional<List<CustomFee>> infoForTokenCustomFees(final Address token) {
        return Optional.of(getCustomFees(token));
    }

    @Override
    public TokenType typeOf(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        final var type = tokenRepository.findType(tokenId);
        return type.map(tokenTypeEnum -> TokenType.valueOf(tokenTypeEnum.name())).orElse(null);
    }

    @Override
    public EvmKey keyOf(final Address address, final TokenKeyType tokenKeyType) {
        final var tokenInfoOptional = getTokenInfo(address);

        return tokenInfoOptional.map(tokenInfo -> switch (tokenKeyType) {
            case ADMIN_KEY -> tokenInfo.getAdminKey();
            case KYC_KEY -> tokenInfo.getKycKey();
            case FREEZE_KEY -> tokenInfo.getFreezeKey();
            case WIPE_KEY -> tokenInfo.getWipeKey();
            case SUPPLY_KEY -> tokenInfo.getSupplyKey();
            case FEE_SCHEDULE_KEY -> tokenInfo.getFeeScheduleKey();
            case PAUSE_KEY -> tokenInfo.getPauseKey();
        }).orElse(new EvmKey());
    }

    @Override
    public String nameOf(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        return tokenRepository.findName(tokenId).orElse("");
    }

    @Override
    public String symbolOf(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        return tokenRepository.findSymbol(tokenId).orElse("");
    }

    @Override
    public long totalSupplyOf(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        return tokenRepository.findTotalSupply(tokenId).orElse(0L);
    }

    @Override
    public int decimalsOf(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        return tokenRepository.findDecimals(tokenId).orElse(0);
    }

    @Override
    public long balanceOf(Address account, Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        final var accountId = entityIdFromAccountAddress(account);
        return tokenBalanceRepository.findBalance(tokenId, accountId).orElse(0L);
    }

    @Override
    public long staticAllowanceOf(final Address owner, final Address spender, final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        final var ownerId = entityIdFromAccountAddress(owner);
        final var spenderId = entityIdFromAccountAddress(spender);
        return tokenAllowanceRepository.findAllowance(tokenId, ownerId, spenderId).orElse(0L);
    }

    @Override
    public Address staticApprovedSpenderOf(final Address nft, long serialNo) {
        final var tokenId = entityIdFromEvmAddress(nft);
        final var spenderNum = nftRepository.findSpender(tokenId, serialNo);
        if (spenderNum.isEmpty()) {
            return Address.ZERO;
        }
        final var spenderEntity = EntityId.of(spenderNum.get(), ACCOUNT);

        return EvmTokenUtils.toAddress(spenderEntity);
    }

    @Override
    public boolean staticIsOperator(final Address owner, final Address operator, final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        final var ownerId = entityIdFromAccountAddress(owner);
        final var spenderId = entityIdFromAccountAddress(operator);
        final var isSpenderAnOperator = nftAllowanceRepository.spenderHasApproveForAll(tokenId, ownerId, spenderId);

        return isSpenderAnOperator.orElse(false);
    }

    @Override
    public Address ownerOf(final Address nft, long serialNo) {
        final var tokenId = entityIdFromEvmAddress(nft);
        final var ownerNum = nftRepository.findOwner(tokenId, serialNo);
        if (ownerNum.isEmpty()) {
            return Address.ZERO;
        }
        final var ownerEntity =  EntityId.of(ownerNum.get(), ACCOUNT);
        return EvmTokenUtils.toAddress(ownerEntity);
    }

    @Override
    public Address canonicalAddress(final Address addressOrAlias) {
        return addressOrAlias;
    }

    @Override
    public String metadataOf(final Address nft, long serialNo) {
        final var tokenId = entityIdFromEvmAddress(nft);
        final var metadata = nftRepository.findMetadata(tokenId, serialNo);
        return metadata.map(String::new).orElse("");
    }

    @Override
    public byte[] ledgerId() {
        return properties.getNetwork().getLedgerId();
    }

    private Optional<EvmTokenInfo> getTokenInfo(final Address token) {
        final var tokenEntityOptional = tokenRepository.findById(new TokenId(fromEvmAddress(token.toArray())));
        final var entityOptional = entityRepository.findByIdAndDeletedIsFalse(entityIdFromEvmAddress(token));

        if (tokenEntityOptional.isEmpty() || entityOptional.isEmpty()) {
            return Optional.empty();
        }

        final var tokenEntity = tokenEntityOptional.get();
        final var entity = entityOptional.get();
        final var ledgerId = ledgerId();
        final var expirationTimeInSec = entity.getExpirationTimestamp() == null ? 0L :
                entity.getExpirationTimestamp() / 1000000000;

        final EvmTokenInfo evmTokenInfo = new EvmTokenInfo(
                ledgerId,
                tokenEntity.getSupplyType().ordinal(),
                entity.getDeleted(),
                tokenEntity.getSymbol(),
                tokenEntity.getName(),
                entity.getMemo(),
                toAddress(tokenEntity.getTreasuryAccountId()),
                tokenEntity.getTotalSupply(),
                tokenEntity.getMaxSupply(),
                tokenEntity.getDecimals(),
                expirationTimeInSec);
        evmTokenInfo.setAutoRenewPeriod(entity.getAutoRenewPeriod() != null ? entity.getAutoRenewPeriod() : 0);
        evmTokenInfo.setDefaultFreezeStatus(tokenEntity.getFreezeDefault());
        evmTokenInfo.setCustomFees(getCustomFees(token));
        setEvmKeys(entity, tokenEntity, evmTokenInfo);
        final var isPaused = tokenEntity.getPauseStatus().ordinal() == 1;
        evmTokenInfo.setIsPaused(isPaused);

        entityRepository.findById(entity.getAutoRenewAccountId())
                .ifPresent(a -> evmTokenInfo.setAutoRenewAccount(toAddress(
                        new EntityId(a.getShard(), a.getRealm(), a.getNum(), EntityType.ACCOUNT))));

        return Optional.of(evmTokenInfo);
    }

    private List<CustomFee> getCustomFees(final Address token) {
        final List<CustomFee> customFees = new ArrayList<>();
        final var customFeesCollection = customFeeRepository.findByTokenId(entityIdFromEvmAddress(token));

        if (CollectionUtils.isEmpty(customFeesCollection)) {
            return customFees;
        }

        for (final var customFee : customFeesCollection) {
            final var collectorId = customFee.getCollectorAccountId();
            if (collectorId == null) {
                continue;
            }

            final var amount = customFee.getAmount();
            final var collector = toAddress(collectorId);
            final var denominatingTokenId = customFee.getDenominatingTokenId();
            final var denominatingTokenAddress = denominatingTokenId == null ? Address.wrap(EMPTY)
                    : toAddress(denominatingTokenId);
            final var amountNumerator = customFee.getRoyaltyNumerator();
            final var amountDenominator = customFee.getAmountDenominator();
            final var maximumAmount = customFee.getMaximumAmount();
            final var minimumAmount = customFee.getMinimumAmount();

            final var netOfTransfers = customFee.getNetOfTransfers();
            final var royaltyDenominator = customFee.getRoyaltyDenominator();
            final var royaltyNumerator = customFee.getRoyaltyNumerator();

            CustomFee customFeeConstructed = new CustomFee();

            if (amountNumerator == 0 && royaltyDenominator == 0) {
                final var fixedFee = new FixedFee(
                        amount,
                        denominatingTokenAddress,
                        denominatingTokenId == null,
                        false,
                        collector);

                customFeeConstructed.setFixedFee(fixedFee);
            } else if (royaltyDenominator == 0) {
                final var fractionFee = new FractionalFee(
                        amountNumerator,
                        amountDenominator,
                        minimumAmount,
                        maximumAmount,
                        netOfTransfers,
                        collector);
                customFeeConstructed.setFractionalFee(fractionFee);
            } else {
                final var royaltyFee = new RoyaltyFee(
                        royaltyNumerator,
                        royaltyDenominator,
                        amount,
                        denominatingTokenAddress,
                        denominatingTokenId == null,
                        collector);
                customFeeConstructed.setRoyaltyFee(royaltyFee);
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
            return fromEvmAddress(addressBytes).getId();
        }

        return entityRepository.findByEvmAddressAndDeletedIsFalse(addressBytes)
                .map(AbstractEntity::getId).orElse(0L);
    }
}
