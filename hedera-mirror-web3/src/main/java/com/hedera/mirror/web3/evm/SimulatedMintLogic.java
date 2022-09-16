package com.hedera.mirror.web3.evm;

import static com.hedera.services.transaction.exception.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import com.google.protobuf.ByteString;
import com.hedera.services.transaction.txns.token.OptionValidator;
import java.util.List;
import lombok.AllArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.evm.properties.EvmProperties;

@AllArgsConstructor
public class SimulatedMintLogic {
    private final SimulatedBackingTokens tokens;
    private final EvmProperties evmProperties;
    private final OptionValidator validator;

    public void mint(
            final Address targetAddress,
            final int metaDataCount,
            final long amount,
            final List<ByteString> metaDataList) {

        /* --- Load the model objects --- */
        final var token = tokens.getRef(targetAddress);

        validateTrue(token!=null, INVALID_TOKEN_ID);

        /* --- Do the business logic --- */
        if (token.getType() == TokenTypeEnum.FUNGIBLE_COMMON) {
            token.mint(amount, false);
        } else {
            token.mint(metaDataList);
        }
    }
}
