package com.hedera.mirror.web3.evm;

import static com.hedera.services.transaction.exception.ValidationUtils.validateFalse;
import static com.hedera.services.transaction.exception.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIAL_NUMBER_LIMIT_REACHED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_MAX_SUPPLY_REACHED;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenRepository;

/**
 * Encapsulates the state and operations of a Hedera token.
 *
 * <p>Operations are validated, and throw a {@link
 * com.hedera.services.exceptions.InvalidTransactionException} with response code capturing the
 * failure when one occurs.
 *
 * <p><b>NOTE:</b> Some operations only apply to specific token types. For example, a {@link
 * SimulatedToken#mint(long, boolean)} call only makes sense for a token of type {@code
 * FUNGIBLE_COMMON}; the signature for a {@code NON_FUNGIBLE_UNIQUE} is {@link
 * SimulatedToken#mint(List)}.
 */
public class SimulatedToken {

    private static final long MAX_NUM_ALLOWED = 0xFFFFFFFFL;
    private long[] mintedSerialNumbers = new long[1];
    private byte[] supplyKey;
    private long totalSupply;
    private long maxSupply;
    private TokenTypeEnum type;
    private TokenSupplyTypeEnum supplyType;
    private EntityId treasury;
    private Address treasuryAddress = Address.ZERO;

    private NftRepository nftRepository;
    private TokenRepository tokenRepository;
    private EntityRepository entityRepository;
    private SimulatedEntityAccess entityAccess;

    public SimulatedToken(final Address tokenAddress, final NftRepository nftRepository,
            final TokenRepository tokenRepository, final EntityRepository entityRepository, final SimulatedEntityAccess entityAccess) {
        this.nftRepository = nftRepository;
        this.tokenRepository = tokenRepository;
        this.entityRepository = entityRepository;
        this.entityAccess = entityAccess;

        final var token = tokenRepository.findByAddress(tokenAddress.toArray()).orElse(null);
        validateTrue(token!=null, INVALID_TOKEN_ID);
        supplyType = token.getSupplyType();
        totalSupply = token.getTotalSupply();
        maxSupply = token.getMaxSupply();
        supplyKey = token.getSupplyKey();
        treasury = token.getTreasuryAccountId();
        type = token.getType();

        final var treasuryNum = treasury.getEntityNum();
        final var treasuryAddressBytes = entityRepository.findAddressByNum(treasuryNum).orElse(null);
        if(treasuryAddressBytes != null) {
            treasuryAddress = Address.wrap(Bytes.wrap(treasuryAddressBytes));
        }
    }

    public SimulatedToken(final long[] mintedSerialNumbers, final byte[] supplyKey, final long totalSupply,
                          final long maxSupply, final TokenTypeEnum type,
                          final TokenSupplyTypeEnum supplyType, final EntityId treasury,
                          final Address treasuryAddress, final SimulatedEntityAccess entityAccess) {
        this.mintedSerialNumbers = mintedSerialNumbers;
        this.supplyKey = supplyKey;
        this.totalSupply = totalSupply;
        this.maxSupply = maxSupply;
        this.type = type;
        this.supplyType = supplyType;
        this.treasury = treasury;
        this.treasuryAddress = treasuryAddress;
        this.entityAccess = entityAccess;
    }

    public void mint(final long amount, boolean ignoreSupplyKey) {
        validateTrue(
                amount > 0, INVALID_TOKEN_MINT_AMOUNT, errorMessage("mint", amount));
        validateTrue(
                type == TokenTypeEnum.FUNGIBLE_COMMON,
                FAIL_INVALID,
                "Fungible mint can be invoked only on fungible token type");

        changeSupply(amount, INVALID_TOKEN_MINT_AMOUNT, ignoreSupplyKey);
    }

    /**
     * Minting unique tokens creates new instances of the given base unique token. Increments the
     * serial number of the given base unique token, and assigns each of the numbers to each new
     * unique token instance.
     *
     * @param metadata - a list of user-defined metadata, related to the nft instances.
     */
    public void mint(
            final List<ByteString> metadata) {
        final var metadataCount = metadata.size();
        var positionCounter = 0;
        validateFalse(
                metadata.isEmpty(), INVALID_TOKEN_MINT_METADATA, "Cannot mint zero unique tokens");
        validateTrue(
                type == TokenTypeEnum.NON_FUNGIBLE_UNIQUE,
                FAIL_INVALID,
                "Non-fungible mint can be invoked only on non-fungible token type");
        long lastUsedSerialNumber = nftRepository.findLatestSerialNumber().orElse(Long.MAX_VALUE);
        validateTrue(
                (lastUsedSerialNumber + metadataCount) > 0 &&
                     (lastUsedSerialNumber + metadataCount) <= MAX_NUM_ALLOWED,
                SERIAL_NUMBER_LIMIT_REACHED);

        for (ByteString m : metadata) {
            lastUsedSerialNumber++;
            mintedSerialNumbers[positionCounter] = lastUsedSerialNumber;
            positionCounter++;
        }

        changeSupply(metadataCount, FAIL_INVALID, false);
    }

    private void changeSupply(
            final long amount,
            final ResponseCodeEnum negSupplyCode,
            final boolean ignoreSupplyKey) {
        validateTrue(treasury != null, FAIL_INVALID, "Cannot mint with a null treasury");

        if (!ignoreSupplyKey) {
            validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);
        }
        final long newTotalSupply = totalSupply + amount;
        validateTrue(newTotalSupply >= 0, negSupplyCode);
        if (supplyType == TokenSupplyTypeEnum.FINITE) {
            validateTrue(
                    maxSupply >= newTotalSupply,
                    TOKEN_MAX_SUPPLY_REACHED,
                    "Cannot mint new supply ("
                            + amount
                            + "). Max supply ("
                            + maxSupply
                            + ") reached");
        }
        final long newTreasuryBalance = entityAccess.getBalance(treasuryAddress) + amount;
        validateTrue(newTreasuryBalance >= 0, INSUFFICIENT_TOKEN_BALANCE);

        setTotalSupply(newTotalSupply);
    }

    public void setTotalSupply(long totalSupply) {
        this.totalSupply = totalSupply;
    }

    public long[] getSerialNumbers() {
        return mintedSerialNumbers;
    }

    public TokenTypeEnum getType() {
        return type;
    }

    public long getTotalSupply() {
        return totalSupply;
    }

    public SimulatedToken getSafeCopy() {
        return new SimulatedToken(mintedSerialNumbers, supplyKey, totalSupply, maxSupply, type, supplyType, treasury, treasuryAddress, entityAccess);
    }

    private String errorMessage(final String op, final long amount) {
        return "Cannot " + op + " " + amount + " units of " + this;
    }
}
