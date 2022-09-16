package com.hedera.services.transaction;

import static com.hedera.services.transaction.ParsingConstants.FunctionType.HAPI_MINT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.transaction.ParsingConstants.FunctionType;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;

@Singleton
public class EncodingFacade {
    private static final String STRING_RETURN_TYPE = "(string)";
    private static final String MINT_RETURN_TYPE = "(int32,uint64,int64[])";
    private static final TupleType nameType = TupleType.parse(STRING_RETURN_TYPE);
    private static final TupleType symbolType = TupleType.parse(STRING_RETURN_TYPE);
    public static final TupleType mintReturnType = TupleType.parse(MINT_RETURN_TYPE);
    private static final long[] NO_MINTED_SERIAL_NUMBERS = new long[0];


    @Inject
    public EncodingFacade() {
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

    public Bytes encodeMintSuccess(final long totalSupply, final long[] serialNumbers) {
        return functionResultBuilder()
                .forFunction(HAPI_MINT)
                .withStatus(SUCCESS.getNumber())
                .withTotalSupply(totalSupply)
                .withSerialNumbers(serialNumbers != null ? serialNumbers : NO_MINTED_SERIAL_NUMBERS)
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
        private long totalSupply;
        private long[] serialNumbers;

        private FunctionResultBuilder forFunction(final FunctionType functionType) {
            this.tupleType =
                    switch (functionType) {
                        case ERC_NAME -> nameType;
                        case ERC_SYMBOL -> symbolType;
                        case HAPI_MINT -> mintReturnType;
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

        private FunctionResultBuilder withStatus(final int status) {
            this.status = status;
            return this;
        }

        private FunctionResultBuilder withTotalSupply(final long totalSupply) {
            this.totalSupply = totalSupply;
            return this;
        }

        private FunctionResultBuilder withSerialNumbers(final long... serialNumbers) {
            this.serialNumbers = serialNumbers;
            return this;
        }

        private Bytes build() {
            final var result =
                    switch (functionType) {
                        case ERC_NAME -> Tuple.of(name);
                        case ERC_SYMBOL -> Tuple.of(symbol);
                        case HAPI_MINT -> Tuple.of(
                                status, BigInteger.valueOf(totalSupply), serialNumbers);
                    };

            return Bytes.wrap(tupleType.encode(result).array());
        }
    }
}
