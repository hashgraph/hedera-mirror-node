package com.hedera.services.transaction;

import com.hedera.mirror.web3.evm.InfrastructureFactory;
import com.hedera.mirror.web3.evm.PrecompilePricingUtils;
import com.hedera.mirror.web3.evm.SimulatedAliasManager;
import com.hedera.mirror.web3.evm.SimulatedBackingTokens;
import com.hedera.mirror.web3.evm.SimulatedStackedWorldStateUpdater;

import com.hedera.mirror.web3.evm.SimulatedTxnAwareEvmSigsVerifier;

import java.util.Optional;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.jetbrains.annotations.NotNull;

import com.hedera.mirror.web3.repository.TokenRepository;

public class HTSPrecompiledContract implements PrecompiledContract {
    private static final Logger log = LogManager.getLogger(HTSPrecompiledContract.class);

    private long gasRequirement = 0;
    private Precompile precompile;
    private static final Bytes STATIC_CALL_REVERT_REASON =
            Bytes.of("HTS precompiles are not static".getBytes());
    private SimulatedStackedWorldStateUpdater updater;

    public static final int ABI_ID_REDIRECT_FOR_TOKEN = 0x618dc65e;
    public static final int ABI_ID_ERC_NAME = 0x06fdde03;
    public static final int ABI_ID_ERC_SYMBOL = 0x95d89b41;

    public static final int ABI_ID_MINT_TOKEN = 0x278e0b88;

    private final TokenRepository tokenRepository;
    private final EncodingFacade encoder;
    private final InfrastructureFactory infrastructureFactory;
    private final PrecompilePricingUtils pricingUtils;
    private final SimulatedAliasManager aliasManager;
    private final SimulatedTxnAwareEvmSigsVerifier sigsVerifier;
    private SimulatedBackingTokens tokens;

    public HTSPrecompiledContract(final TokenRepository tokenRepository,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final SimulatedAliasManager aliasManager,
            final SimulatedTxnAwareEvmSigsVerifier sigsVerifier) {
        this.tokenRepository = tokenRepository;
        this.encoder = new EncodingFacade();
        this.infrastructureFactory = infrastructureFactory;
        this.pricingUtils = pricingUtils;
        this.aliasManager = aliasManager;
        this.sigsVerifier = sigsVerifier;
    }

    public static boolean isTokenProxyRedirect(final Bytes input) {
        return ABI_ID_REDIRECT_FOR_TOKEN == input.getInt(0);
    }

    public Pair<Long, Bytes> computeCosted(final Bytes input, final MessageFrame frame) {
        if (frame.isStatic()) {
            if (!isTokenProxyRedirect(input)) {
                frame.setRevertReason(STATIC_CALL_REVERT_REASON);
                return Pair.of(defaultGas(), null);
            }
        }

        final var result = computePrecompile(input, frame);
        return Pair.of(gasRequirement, result.getOutput());
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public long gasRequirement(Bytes input) {
        return 0;
    }

    public PrecompileContractResult computePrecompile(final Bytes input, @NotNull final MessageFrame frame) {
        prepareFields(frame);
        prepareComputation(input, updater::unaliased);

        Bytes result = computeInternal(frame);

        return result == null
                ? PrecompiledContract.PrecompileContractResult.halt(
                null, Optional.of(ExceptionalHaltReason.NONE))
                : PrecompiledContract.PrecompileContractResult.success(result);
    }

    void prepareFields(final MessageFrame frame) {
        this.updater = (SimulatedStackedWorldStateUpdater) frame.getWorldUpdater();
        this.tokens = updater.wrappedBackingTokens();
    }


    void prepareComputation(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        int functionId = input.getInt(0);

        this.precompile = switch (functionId) {
            case ABI_ID_MINT_TOKEN -> new MintPrecompile(tokenRepository, tokens, encoder, infrastructureFactory,
                    pricingUtils, aliasManager, sigsVerifier);
            case ABI_ID_REDIRECT_FOR_TOKEN -> {
                final var target = getRedirectTarget(input);
                final var address = target.address();
                final var nestedFunctionSelector = target.descriptor();
                yield switch (nestedFunctionSelector) {
                    case ABI_ID_ERC_NAME -> new NamePrecompile(address, tokenRepository, encoder);
                    case ABI_ID_ERC_SYMBOL -> new SymbolPrecompile(address, tokenRepository, encoder);
                    default -> null;
                };
            }

            default -> null;
        };
        if (precompile != null) {
            decodeInput(input, aliasResolver);
        }
    }

    void decodeInput(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        try {
            this.precompile.body(input, aliasResolver);
        } catch (Exception e) {
            log.warn("Internal precompile failure", e);
        }
    }

    protected Bytes computeInternal(final MessageFrame frame) {
        precompile.run(frame);
        Bytes result;

        result = precompile.getSuccessResultFor();

        return result;
    }

    public static RedirectTarget getRedirectTarget(final Bytes input) {
        final var tokenAddress = input.slice(4, 20);
        final var address = tokenAddress.toArray();
        final var nestedInput = input.slice(24);
        return new RedirectTarget(nestedInput.getInt(0), address);
    }

    private long defaultGas() {
        return 0L;
    }
}
