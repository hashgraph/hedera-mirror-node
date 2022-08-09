package com.hedera.services.transaction;

import static com.hedera.services.transaction.HTSPrecompiledContract.ABI_ID_ERC_NAME;
import static com.hedera.services.transaction.ParsingConstants.notSpecifiedType;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;

import com.hedera.services.transaction.ParsingConstants.FunctionType;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

@Singleton
public class EncodingFacade {
    public static final Bytes SUCCESS_RESULT = resultFrom(SUCCESS);
    private static final String STRING_RETURN_TYPE = "(string)";
    private static final TupleType nameType = TupleType.parse(STRING_RETURN_TYPE);
    private static final TupleType symbolType = TupleType.parse(STRING_RETURN_TYPE);


    @Inject
    public EncodingFacade() {

    }

    public static Bytes resultFrom(final ResponseCodeEnum status) {
        return UInt256.valueOf(status.getNumber());
    }

    public Bytes encodeName(final String name) {
        return functionResultBuilder().forFunction(FunctionType.ERC_NAME).withName(name).build();
    }

    public Bytes encodeSymbol(final String symbol) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_SYMBOL)
                .withSymbol(symbol)
                .build();
    }

    private FunctionResultBuilder functionResultBuilder() {
        return new FunctionResultBuilder();
    }

    private static class FunctionResultBuilder {
        private FunctionType functionType;
        private TupleType tupleType;
        private int status;
        private String name;
        private String symbol;

        private FunctionResultBuilder forFunction(final FunctionType functionType) {
            this.tupleType =
                    switch (functionType) {
                        case ERC_NAME -> nameType;
                        case ERC_SYMBOL -> symbolType;
                        default -> notSpecifiedType;
                    };

            this.functionType = functionType;
            return this;
        }


        private FunctionResultBuilder withName(final String name) {
            this.name = name;
            return this;
        }

        private FunctionResultBuilder withSymbol(final String symbol) {
            this.symbol = symbol;
            return this;
        }

        private Bytes build() {
            final var result =
                    switch (functionType) {
                        case ERC_NAME -> Tuple.of(name);
                        case ERC_SYMBOL -> Tuple.of(symbol);
                        default -> Tuple.of(status);
                    };

            return Bytes.wrap(tupleType.encode(result).array());
        }

    }


}
