package com.hedera.services.store.contracts.precompile.impl;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.BurnResult;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txn.token.BurnLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.BURN_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.BURN_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class BurnPrecompile extends AbstractWritePrecompile{

    private static final List<Long> NO_SERIAL_NOS = Collections.emptyList();
    private final EncodingFacade encoder;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private BurnWrapper burnOp;
    private final OptionValidator optionValidator;

    public BurnPrecompile(
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils,
            final OptionValidator optionValidator) {
        super(pricingUtils);
        this.encoder = encoder;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.optionValidator = optionValidator;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input,
            final UnaryOperator<byte[]> aliasResolver,
            final BodyParams bodyParams) {
        final var functionId = ((FunctionParam) bodyParams).functionId();
        final var burnAbi =
                switch (functionId) {
                    case AbiConstants.ABI_ID_BURN_TOKEN -> SystemContractAbis.BURN_TOKEN_V1;
                    case AbiConstants.ABI_ID_BURN_TOKEN_V2 -> SystemContractAbis.BURN_TOKEN_V2;
                    default -> throw new IllegalArgumentException("invalid selector to burn precompile");
                };
        burnOp = getBurnWrapper(input, burnAbi);
        return syntheticTxnFactory.createBurn(burnOp);
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        Objects.requireNonNull(burnOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(
                (burnOp.type() == NON_FUNGIBLE_UNIQUE) ? BURN_NFT : BURN_FUNGIBLE, consensusTime);
    }

    @Override
    public RunResult run(final MessageFrame frame, final Store store, final TransactionBody transactionBody) {
        Objects.requireNonNull(burnOp, "`body` method should be called before `run`");

        /* --- Check required signatures --- */ // TODO: delete comment?
        final var burnBody = transactionBody.getTokenBurn();
        final var tokenId = burnBody.getToken();

        // TODO: do I need that?
        /*final var hasRequiredSigs = KeyActivationUtils.validateKey(
                frame, tokenId.asEvmAddress(), sigsVerifier::hasActiveSupplyKey, ledgers, aliases, TokenBurn);
        validateTrue(hasRequiredSigs, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE, BURN);*/

        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var burnLogic = new BurnLogic(optionValidator);

        final var validity = burnLogic.validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        final TokenModificationResult tokenModificationResult;
        if (burnOp.type() == NON_FUNGIBLE_UNIQUE) {
            final var targetSerialNos = burnOp.serialNos();
            tokenModificationResult = burnLogic.burn(Id.fromGrpcToken(tokenId), 0, targetSerialNos, store);
        } else {
            tokenModificationResult = burnLogic.burn(Id.fromGrpcToken(tokenId), burnOp.amount(), NO_SERIAL_NOS, store);
        }

        final var modifiedToken = tokenModificationResult.token();
        return new BurnResult(
                modifiedToken.getTotalSupply(),
                modifiedToken.removedUniqueTokens().stream().map(UniqueToken::getSerialNumber).toList());
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_BURN_TOKEN, AbiConstants.ABI_ID_BURN_TOKEN_V2);
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var burnResult = (BurnResult) runResult;
        return encoder.encodeBurnSuccess(burnResult.totalSupply());
    }

    @Override
    public Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return encoder.encodeBurnFailure(status);
    }

    // TODO: private?
    public static BurnWrapper getBurnWrapper(final Bytes input, @NonNull final SystemContractAbis abi) {
        final Tuple decodedArguments = decodeFunctionCall(input, abi.selector, abi.decoder);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var fungibleAmount = SystemContractAbis.toLongSafely(decodedArguments.get(1));
        final var serialNumbers = (long[]) decodedArguments.get(2);

        if (fungibleAmount < 0 || (fungibleAmount == 0 && serialNumbers.length == 0)) {
            throw new IllegalArgumentException("Illegal amount of tokens to burn");
        }

        if (fungibleAmount > 0) {
            return BurnWrapper.forFungible(tokenID, fungibleAmount);
        } else {
            return BurnWrapper.forNonFungible(
                    tokenID, Arrays.stream(serialNumbers).boxed().toList());
        }
    }
}
