package com.hedera.mirror.web3.evm.token;

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.nanosMaxToTimestamp;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.web3.evm.exception.ParsingException;
import com.hedera.mirror.web3.repository.CustomFeeRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.mirror.web3.repository.TokenBalanceRepository;
import com.hedera.mirror.web3.repository.TokenRepository;

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

    @Override
    public Optional<TokenNftInfo> evmNftInfo(final Address nft, long serialNo,
            final ByteString ledgerId) {
        final var nftOptional = nftRepository.findById(new NftId(serialNo, fromEvmAddress(nft.toArray())));
        if(nftOptional.isEmpty()) {
            return Optional.empty();
        }

        final var nftEntity = nftOptional.get();
        final var nftInfo = TokenNftInfo.newBuilder()
                .setNftID(NftID.newBuilder().setTokenID(tokenIdFromEvmAddress(nft.toArray()))
                        .setSerialNumber(serialNo).build())
                .setAccountID(convertEntityIdToAccountId(nftEntity.getAccountId()))
                .setCreationTime(nanosMaxToTimestamp(nftEntity.getCreatedTimestamp()))
                .setMetadata(ByteString.copyFrom(nftEntity.getMetadata()))
                .setSpenderId(convertEntityIdToAccountId(nftEntity.getSpender()))
                .setLedgerId(ledgerId)
                .build();

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
        return tokenAccountRepository.findFrozenStatus(accountId, tokenId);
    }

    @Override
    public boolean defaultFreezeStatus(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        return tokenRepository.findFreezeDefault(tokenId);
    }

    @Override
    public boolean defaultKycStatus(final Address token) {
        return false;
    }

    @Override
    public boolean isKyc(final Address account, final Address token) {
        final var accountId = entityIdFromEvmAddress(account);
        final var tokenId = entityIdFromEvmAddress(account);
        return tokenAccountRepository.findKycStatus(accountId, tokenId);
    }

    @Override
    public Optional<List<CustomFee>> infoForTokenCustomFees(final Address token) {
        return getCustomFees(token);
    }

    @Override
    public TokenType typeOf(final Address token) {
        final var tokenId = entityIdFromEvmAddress(token);
        final var type = tokenRepository.findType(tokenId);
        return type.map(tokenTypeEnum -> TokenType.forNumber(tokenTypeEnum.ordinal())).orElse(null);
    }

    @Override
    public Optional<TokenInfo> infoForToken(final Address token, final ByteString ledgerId) {
        final var tokenInfoBuilder = getTokenInfoBuilder(token, false, ledgerId);
        return Optional.of(tokenInfoBuilder.build());
    }

    @Override
    public Key keyOf(Address token, Integer keyType) {
        return null;
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
        return null;
    }

    @Override
    public String metadataOf(final Address nft, long serialNo) {
        final var tokenId = entityIdFromEvmAddress(nft);
        final var metadata = nftRepository.findMetadata(tokenId, serialNo);
        return metadata.map(String::new).orElse("");
    }

    private TokenInfo.Builder getTokenInfoBuilder(final Address token, boolean setDecimals, final ByteString ledgerId) {
        final var tokenEntityOptional = tokenRepository.findById(new TokenId(fromEvmAddress(token.toArray())));
        final var entityOptional = entityRepository.findById(entityIdFromEvmAddress(token));

        final var tokenInfoBuilder = TokenInfo.newBuilder();
        if(tokenEntityOptional.isPresent()) {
            final var tokenEntity = tokenEntityOptional.get();

            final var kycKey = tokenEntity.getKycKey();
            final var supplyKey = tokenEntity.getSupplyKey();
            final var freezeKey = tokenEntity.getFreezeKey();
            final var wipeKey = tokenEntity.getWipeKey();
            final var pauseKey = tokenEntity.getPauseKey();
            final var feeScheduleKey = tokenEntity.getFeeScheduleKey();
            try {
                tokenInfoBuilder.setKycKey(Key.parseFrom(kycKey));
                tokenInfoBuilder.setSupplyKey(Key.parseFrom(supplyKey));
                tokenInfoBuilder.setFreezeKey(Key.parseFrom(freezeKey));
                tokenInfoBuilder.setWipeKey(Key.parseFrom(wipeKey));
                tokenInfoBuilder.setPauseKey(Key.parseFrom(pauseKey));
                tokenInfoBuilder.setFeeScheduleKey(Key.parseFrom(feeScheduleKey));
            } catch (final InvalidProtocolBufferException e) {
                throw new ParsingException("Error parsing token keys.");
            }

            tokenInfoBuilder.setName(tokenEntity.getName());
            tokenInfoBuilder.setSymbol(tokenEntity.getSymbol());
            tokenInfoBuilder.setTokenType(TokenType.forNumber(tokenEntity.getType().ordinal()));
            tokenInfoBuilder.setTotalSupply(tokenEntity.getTotalSupply());
            tokenInfoBuilder.setMaxSupply(tokenEntity.getMaxSupply());
            tokenInfoBuilder.setSupplyType(TokenSupplyType.forNumber(tokenEntity.getSupplyType().ordinal()));

            final var tokenId = tokenEntity.getTokenId().getTokenId();
            tokenInfoBuilder.setTokenId(TokenID.newBuilder().setShardNum(tokenId.getShardNum()).setRealmNum(tokenId.getRealmNum()).setTokenNum(tokenId.getEntityNum()).build());

            final var treasury = tokenEntity.getTreasuryAccountId();
            tokenInfoBuilder.setTreasury(AccountID.newBuilder().setShardNum(treasury.getShardNum()).setRealmNum(treasury.getRealmNum()).setAccountNum(treasury.getEntityNum()).build());

            if(setDecimals) {
                tokenInfoBuilder.setDecimals(tokenEntity.getDecimals());
            }
        }

        if(entityOptional.isPresent()) {
            final var entity = entityOptional.get();
            final var proxyAutoRenewAccount = entity.getProxyAccountId();

            tokenInfoBuilder.setAutoRenewAccount(AccountID.newBuilder().setShardNum(proxyAutoRenewAccount.getShardNum()).setRealmNum(proxyAutoRenewAccount.getRealmNum()).setAccountNum(proxyAutoRenewAccount.getEntityNum()).build());
            tokenInfoBuilder.setAutoRenewPeriod(Duration.newBuilder().setSeconds(entity.getAutoRenewPeriod()).build());

            tokenInfoBuilder.setDeleted(entity.getDeleted());
            tokenInfoBuilder.setMemo(entity.getMemo());
            tokenInfoBuilder.setExpiry(nanosMaxToTimestamp(entity.getExpirationTimestamp()));
        }

        final var customFeesOptional = getCustomFees(token);
        if(customFeesOptional.isPresent()) {
            final var customFees = customFeesOptional.get();
            for(int i = 0; i < customFees.size(); i++) {
                tokenInfoBuilder.setCustomFees(i, customFees.get(i));
            }
        }

        tokenInfoBuilder.setLedgerId(ledgerId);
        return tokenInfoBuilder;
    }

    private Optional<List<CustomFee>> getCustomFees(final Address token) {
        final List<CustomFee> customFees = new ArrayList<>();

        final var customFeesOptional = customFeeRepository.findCustomFees(entityIdFromEvmAddress(token));
        if(customFeesOptional.isPresent()) {
            for(final var customFee: customFeesOptional.get()) {
                var feeIndex = 0;

                final var amount = customFee.getAmount();
                final var collector = customFee.getCollectorAccountId();

                final var denominatingTokenId = customFee.getDenominatingTokenId();
                final var amountNumerator = customFee.getRoyaltyNumerator();
                final var amountDenominator = customFee.getAmountDenominator();
                final var maximumAmount = customFee.getMaximumAmount();
                final var minimumAmount = customFee.getMinimumAmount();

                final var netOfTransfers = customFee.getNetOfTransfers();
                final var royaltyDenominator = customFee.getRoyaltyDenominator();
                final var royaltyNumerator = customFee.getRoyaltyNumerator();

                CustomFee customFeeConstructed;

                if(amountNumerator == 0 && royaltyDenominator == 0) {
                    final var fixedFeeBuilder = FixedFee.newBuilder().setAmount(amount);
                    if(denominatingTokenId != null) {
                        fixedFeeBuilder.setDenominatingTokenId(convertEntityIdToTokenId(denominatingTokenId));
                    }

                    customFeeConstructed = CustomFee.newBuilder().setFixedFee(fixedFeeBuilder.build())
                            .setFeeCollectorAccountId(convertEntityIdToAccountId(collector)).build();
                } else if (royaltyDenominator == 0) {
                    final var fractionFee = FractionalFee.newBuilder().setMaximumAmount(maximumAmount)
                            .setMinimumAmount(minimumAmount)
                            .setNetOfTransfers(netOfTransfers)
                            .setFractionalAmount(Fraction.newBuilder().setNumerator(amountNumerator)
                                    .setDenominator(amountDenominator).build()).build();

                    customFeeConstructed = CustomFee.newBuilder().setFractionalFee(fractionFee)
                            .setFeeCollectorAccountId(convertEntityIdToAccountId(collector)).build();
                } else {
                    final var royaltyFeeBuilder = RoyaltyFee.newBuilder().setExchangeValueFraction(Fraction.newBuilder().setNumerator(royaltyNumerator)
                            .setDenominator(royaltyDenominator));
                    if(amount != 0) {
                        final var fallbackFeeBuilder = FixedFee.newBuilder().setAmount(amount);

                        if(denominatingTokenId != null) {
                            fallbackFeeBuilder.setDenominatingTokenId(convertEntityIdToTokenId(denominatingTokenId));
                            royaltyFeeBuilder.setFallbackFee(fallbackFeeBuilder.build());
                        }
                    }

                    customFeeConstructed = CustomFee.newBuilder().setRoyaltyFee(royaltyFeeBuilder.build())
                            .setFeeCollectorAccountId(convertEntityIdToAccountId(collector)).build();
                }

                customFees.add(customFeeConstructed);
            }
        }

        return !customFees.isEmpty() ? Optional.of(customFees) : Optional.empty();
    }

    private TokenID convertEntityIdToTokenId(final EntityId entity) {
        return TokenID.newBuilder().setShardNum(entity.getShardNum())
                .setRealmNum(entity.getRealmNum())
                .setTokenNum(entity.getEntityNum()).build();
    }

    private AccountID convertEntityIdToAccountId(final EntityId entity) {
        return AccountID.newBuilder().setShardNum(entity.getShardNum())
                .setRealmNum(entity.getRealmNum())
                .setAccountNum(entity.getEntityNum()).build();
    }

    private Long entityIdFromEvmAddress(final Address address) {
        final var id = fromEvmAddress(address.toArrayUnsafe());
        return id.getId();
    }

    private static TokenID tokenIdFromEvmAddress(final byte[] bytes) {
        return TokenID.newBuilder()
                .setShardNum(Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4)))
                .setRealmNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 4, 12)))
                .setTokenNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20)))
                .build();
    }
}
