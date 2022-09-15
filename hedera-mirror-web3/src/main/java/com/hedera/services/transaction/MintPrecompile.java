package com.hedera.services.transaction;

import static com.hedera.mirror.web3.evm.PrecompilePricingUtils.GasCostType.MINT_FUNGIBLE;
import static com.hedera.mirror.web3.evm.PrecompilePricingUtils.GasCostType.MINT_NFT;
import static com.hedera.services.transaction.exception.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

import com.hedera.mirror.web3.evm.InfrastructureFactory;
import com.hedera.mirror.web3.evm.PrecompilePricingUtils;
import com.hedera.mirror.web3.evm.SimulatedAliasManager;
import com.hedera.mirror.web3.evm.SimulatedBackingTokens;
import com.hedera.mirror.web3.evm.SimulatedTxnAwareEvmSigsVerifier;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.services.transaction.contracts.sources.EvmSigsVerifier;
import com.hedera.services.transaction.store.contracts.precompile.utils.KeyActivationUtils;

public class MintPrecompile extends AbstractReadOnlyPrecompile {
    private static final List<ByteString> NO_METADATA = Collections.emptyList();
    private static final String FAILURE_MESSAGE = "Invalid full prefix for %s precompile!";
    private static final String MINT = String.format(FAILURE_MESSAGE, "mint");
    private static final int ADDRESS_BYTES_LENGTH = 20;
    private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
    private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;
    private static final String INT = "(int)";
    private static final Function MINT_TOKEN_FUNCTION =
            new Function("mintToken(address,uint64,bytes[])", INT);
    private static final Bytes MINT_TOKEN_SELECTOR = Bytes.wrap(MINT_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> MINT_TOKEN_DECODER =
            TypeFactory.create("(bytes32,int64,bytes[])");

    private final TokenRepository tokenRepository;
    private final PrecompilePricingUtils pricingUtils;
    private final InfrastructureFactory infrastructureFactory;
    private final EncodingFacade encoder;
    private final SimulatedBackingTokens tokens;
    private final SimulatedAliasManager aliasManager;
    private final EvmSigsVerifier sigsVerifier;
    private MintWrapper mintOp;

    public MintPrecompile(
            final TokenRepository tokenRepository,
            final SimulatedBackingTokens tokens,
            final EncodingFacade encoder,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final SimulatedAliasManager aliasManager,
            final SimulatedTxnAwareEvmSigsVerifier sigsVerifier
    ) {
        this.tokenRepository = tokenRepository;
        this.encoder = encoder;
        this.infrastructureFactory = infrastructureFactory;
        this.pricingUtils = pricingUtils;
        this.tokens = tokens;
        this.aliasManager = aliasManager;
        this.sigsVerifier = sigsVerifier;
    }

    @Override
    public Builder body(
            Bytes input, UnaryOperator<byte[]> aliasResolver) {
        mintOp = decodeMint(input);
        return null;
    }

    public void run(final MessageFrame frame) {
        Objects.requireNonNull(mintOp, "`body` method should be called before `run`");

        // --- Check required signatures ---
        final var tokenAddress = Objects.requireNonNull(mintOp).tokenAddress();
        final var hasRequiredSigs =
                KeyActivationUtils.validateKey(
                        frame,
                        tokenAddress,
                        sigsVerifier::hasActiveSupplyKey,
                        tokenRepository,
                        aliasManager);
        validateTrue(hasRequiredSigs, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE, MINT);

        final var mintLogic = infrastructureFactory.newMintLogic(tokens, null);

        /* --- Execute the transaction and capture its results --- */
        if (mintOp.type() == NON_FUNGIBLE_UNIQUE) {
            final var newMeta = mintOp.metadata();
            mintLogic.mint(tokenAddress, newMeta.size(), 0, newMeta);
        } else {
            mintLogic.mint(tokenAddress, 0, mintOp.amount(), NO_METADATA);
        }
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        final var isNftMint = Objects.requireNonNull(mintOp).type() == NON_FUNGIBLE_UNIQUE;
        return pricingUtils.getMinimumPriceInTinybars(
                isNftMint ? MINT_NFT : MINT_FUNGIBLE, consensusTime);
    }

    public Bytes getSuccessResultFor() {
        final var simulatedToken = tokens.getImmutableRef(mintOp.tokenAddress());
        return encoder.encodeMintSuccess(
                simulatedToken.getTotalSupply(),
                simulatedToken.getSerialNumbers());
    }

    public Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return encoder.encodeMintFailure(status);
    }

    private MintWrapper decodeMint(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, MINT_TOKEN_SELECTOR, MINT_TOKEN_DECODER);

        final var tokenAddress = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var fungibleAmount = (long) decodedArguments.get(1);
        final var metadataList = (byte[][]) decodedArguments.get(2);
        final List<ByteString> wrappedMetadata = new ArrayList<>();
        for (final var meta : metadataList) {
            wrappedMetadata.add(UnsafeByteOperations.unsafeWrap(meta));
        }
        if (fungibleAmount > 0) {
            return MintWrapper.forFungible(tokenAddress, fungibleAmount);
        } else {
            return MintWrapper.forNonFungible(tokenAddress, wrappedMetadata);
        }
    }

    private static Address convertAddressBytesToTokenID(final byte[] addressBytes) {
        return
                Address.wrap(
                        Bytes.wrap(addressBytes)
                                .slice(ADDRESS_SKIP_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
    }

    private Tuple decodeFunctionCall(
            final Bytes input, final Bytes selector, final ABIType<Tuple> decoder) {
        if (!selector.equals(input.slice(0, FUNCTION_SELECTOR_BYTES_LENGTH))) {
            throw new IllegalArgumentException(
                    "Selector does not match, expected "
                            + selector
                            + " actual "
                            + input.slice(0, FUNCTION_SELECTOR_BYTES_LENGTH));
        }
        return decoder.decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH).toArray());
    }
}
