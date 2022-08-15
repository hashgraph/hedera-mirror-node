package com.hedera.services.transaction;

import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.jetbrains.annotations.NotNull;

import com.hedera.mirror.web3.repository.TokenRepository;

public class HTSPrecompiledContract implements PrecompiledContract {
    private long gasRequirement = 0;
    private Precompile precompile;
    private static final Bytes STATIC_CALL_REVERT_REASON =
            Bytes.of("HTS precompiles are not static".getBytes());

    public static final int ABI_ID_REDIRECT_FOR_TOKEN = 0x618dc65e;
    public static final int ABI_ID_ERC_NAME = 0x06fdde03;
    public static final int ABI_ID_ERC_SYMBOL = 0x95d89b41;

    final TokenRepository tokenRepository;
    final EncodingFacade encoder;

    public HTSPrecompiledContract(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
        this.encoder = new EncodingFacade();
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
        prepareComputation(input);

        Bytes result = computeInternal(frame);

        return result == null
                ? PrecompiledContract.PrecompileContractResult.halt(
                null, Optional.of(ExceptionalHaltReason.NONE))
                : PrecompiledContract.PrecompileContractResult.success(result);
    }

    void prepareComputation(final Bytes input) {
        int functionId = input.getInt(0);

        this.precompile = switch (functionId) {
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


    }

    protected Bytes computeInternal(final MessageFrame frame) {
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
