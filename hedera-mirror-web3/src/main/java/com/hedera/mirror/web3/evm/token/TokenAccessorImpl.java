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

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.evmKey;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.web3.evm.exception.ParsingException;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
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
        final var nftOptional = nftRepository.findById(new NftId(serialNo, fromEvmAddress(nft.toArray())));
        if (nftOptional.isEmpty()) {
            return Optional.empty();
        }
        final var ledgerId = properties.getNetwork().getLedgerId();
        final var nftEntity = nftOptional.get();
        final var entityAddress = toAddress(nftEntity.getAccountId());
        final var creationTime = nftEntity.getCreatedTimestamp();
        final var metadata = nftEntity.getMetadata();
        final var spender = toAddress(nftEntity.getSpender());
        final var nftInfo = new EvmNftInfo(serialNo, entityAddress, creationTime, metadata, spender, ledgerId);

        return Optional.of(nftInfo);
    }

    @Override
    public boolean isTokenAddress(final Address address) {
        final var entityId = entityIdFromEvmAddress(address);
        final var entity = entityRepository.findByIdAndDeletedIsFalse(entityId);

        return entity.filter(e -> e.getType() == TOKEN).isPresent();
    }

    @Override
    public boolean isFrozen(final Address account, final Address token) {
        final var accountId = entityIdFromEvmAddress(account);
        final var tokenId = entityIdFromEvmAddress(token);
        final var status = tokenAccountRepository.findFrozenStatus(accountId, tokenId);
        return status.filter(e -> e == 1).isPresent();
    }

    @Override
    public boolean defaultFreezeStatus(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        return tokenRepository.findFreezeDefault(tokenId);
    }

    @Override
    public boolean defaultKycStatus(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        return tokenRepository.findKycDefault(tokenId).isPresent();
    }

    @Override
    public boolean isKyc(final Address account, final Address token) {
        final var accountId = entityIdFromEvmAddress(account);
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
    public EvmKey keyOf(Address address, TokenKeyType tokenKeyType) {
        final var tokenInfoOptional = getTokenInfo(address);

        if (tokenInfoOptional.isPresent()) {
            final var tokenInfo = tokenInfoOptional.get();
            return
                    switch (tokenKeyType) {
                        case ADMIN_KEY -> tokenInfo.getAdminKey();
                        case KYC_KEY -> tokenInfo.getKycKey();
                        case FREEZE_KEY -> tokenInfo.getFreezeKey();
                        case WIPE_KEY -> tokenInfo.getWipeKey();
                        case SUPPLY_KEY -> tokenInfo.getSupplyKey();
                        case FEE_SCHEDULE_KEY -> tokenInfo.getFeeScheduleKey();
                        case PAUSE_KEY -> tokenInfo.getPauseKey();
                    };
        }
        return new EvmKey();
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
        final var accountId = entityIdFromEvmAddress(account);
        return tokenBalanceRepository.findBalance(tokenId, accountId).orElse(0L);
    }

    @Override
    public long staticAllowanceOf(final Address owner, final Address spender, final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        final var ownerId = entityIdFromEvmAddress(owner);
        final var spenderId = entityIdFromEvmAddress(spender);
        return tokenAllowanceRepository.findAllowance(tokenId, ownerId, spenderId).orElse(0L);
    }

    @Override
    public Address staticApprovedSpenderOf(final Address nft, long serialNo) {
        final var tokenId = entityIdFromEvmAddress(nft);
        final var spender = nftRepository.findSpender(tokenId, serialNo);
        return spender.map(s -> Address.wrap(Bytes.wrap(toEvmAddress(s)))).orElse(Address.ZERO);
    }

    @Override
    public boolean staticIsOperator(final Address owner, final Address operator,
                                    final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        final var ownerId = entityIdFromEvmAddress(owner);
        final var spenderId = entityIdFromEvmAddress(operator);
        return nftAllowanceRepository.isSpenderAnOperator(tokenId, spenderId, ownerId);
    }

    @Override
    public Address ownerOf(final Address nft, long serialNo) {
        final var tokenId = entityIdFromEvmAddress(nft);
        final var owner = nftRepository.findOwner(tokenId, serialNo);
        return owner.map(s -> Address.wrap(Bytes.wrap(toEvmAddress(s)))).orElse(Address.ZERO);
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
        final var entityOptional = entityRepository.findById(entityIdFromEvmAddress(token));

        if (tokenEntityOptional.isPresent() && entityOptional.isPresent()) {
            final var tokenEntity = tokenEntityOptional.get();
            final var entity = entityOptional.get();
            final var ledgerId = properties.getNetwork().getLedgerId();

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
                    asSeconds(entity.getExpirationTimestamp()));
            evmTokenInfo.setAutoRenewPeriod(entity.getAutoRenewPeriod() != null ? entity.getAutoRenewPeriod() : 0);
            evmTokenInfo.setAutoRenewAccount(toAddress(entity.getProxyAccountId()));

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
            final var isPaused = tokenEntity.getPauseStatus().ordinal() == 1;
            evmTokenInfo.setDefaultFreezeStatus(tokenEntity.getFreezeDefault());
            evmTokenInfo.setIsPaused(isPaused);
            final var customFeesOptional = getCustomFees(token);

            evmTokenInfo.setCustomFees(customFeesOptional);
            return Optional.of(evmTokenInfo);
        }
        return Optional.empty();
    }

    private List<CustomFee> getCustomFees(final Address token) {
        final List<CustomFee> customFees = new ArrayList<>();

        final var customFeesOptional = customFeeRepository.findCustomFees(entityIdFromEvmAddress(token));
        if (customFeesOptional.isPresent()) {
            for (final var customFee : customFeesOptional.get()) {

                final var amount = customFee.getAmount();
                final var collector = toAddress(customFee.getCollectorAccountId());

                final var denominatingTokenId = customFee.getDenominatingTokenId();
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
                            toAddress(denominatingTokenId),
                            denominatingTokenId.getEntityNum() == 0,
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
                            toAddress(denominatingTokenId),
                            denominatingTokenId.getEntityNum() == 0,
                            collector);
                    customFeeConstructed.setRoyaltyFee(royaltyFee);
                }

                customFees.add(customFeeConstructed);
            }
        }
        return customFees;
    }

    private long asSeconds(long nanoSeconds) {
        return nanoSeconds / 1000000000;
    }
}
