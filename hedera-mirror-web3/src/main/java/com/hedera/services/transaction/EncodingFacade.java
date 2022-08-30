package com.hedera.services.transaction;

import static com.hedera.services.transaction.ParsingConstants.FunctionType.HAPI_MINT;
import static com.hedera.services.transaction.ParsingConstants.notSpecifiedType;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;

import com.hedera.services.transaction.ParsingConstants.FunctionType;

@Singleton
public class EncodingFacade {
    public static final Bytes SUCCESS_RESULT = resultFrom(SUCCESS);
    private static final String STRING_RETURN_TYPE = "(string)";
    private static final TupleType nameType = TupleType.parse(STRING_RETURN_TYPE);
    private static final TupleType symbolType = TupleType.parse(STRING_RETURN_TYPE);
    private static final List<Long> NO_MINTED_SERIAL_NUMBERS = new ArrayList<>();

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

    public Bytes encodeMintSuccess(final long totalSupply, final List<Long> serialNumbers) {
        return functionResultBuilder()
                .forFunction(HAPI_MINT)
                .withStatus(SUCCESS.getNumber())
                .withTotalSupply(totalSupply)
                .withSerialNumbers(serialNumbers != null ? serialNumbers : NO_MINTED_SERIAL_NUMBERS)
                .build();
    }

    public Bytes encodeMintFailure(@NotNull final ResponseCodeEnum status) {
        return functionResultBuilder()
                .forFunction(HAPI_MINT)
                .withStatus(status.getNumber())
                .withTotalSupply(0L)
                .withSerialNumbers(NO_MINTED_SERIAL_NUMBERS)
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
        private List<Long> serialNumbers;

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

        private FunctionResultBuilder withStatus(final int status) {
            this.status = status;
            return this;
        }

        private FunctionResultBuilder withTotalSupply(final long totalSupply) {
            this.totalSupply = totalSupply;
            return this;
        }

        private FunctionResultBuilder withSerialNumbers(final List<Long> serialNumbers) {
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
                        default -> Tuple.of(status);
                    };

            return Bytes.wrap(tupleType.encode(result).array());
        }

    }


}
