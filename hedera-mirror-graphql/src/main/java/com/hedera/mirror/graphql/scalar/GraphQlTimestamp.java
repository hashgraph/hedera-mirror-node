/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.graphql.scalar;

import static graphql.scalars.util.Kit.typeName;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import jakarta.annotation.Nonnull;
import java.time.Instant;
import java.util.Locale;

public class GraphQlTimestamp implements Coercing<Instant, String> {

    public static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
            .name("Timestamp")
            .description("An ISO 8601 compatible timestamp with nanoseconds granularity.")
            .coercing(new GraphQlTimestamp())
            .build();

    @Override
    public @Nonnull Instant parseLiteral(
            @Nonnull Value<?> input,
            @Nonnull CoercedVariables variables,
            @Nonnull GraphQLContext graphQLContext,
            @Nonnull Locale locale)
            throws CoercingParseLiteralException {
        if (input instanceof StringValue str) {
            return Instant.parse(str.getValue());
        }
        throw new CoercingParseLiteralException("Expected a 'String' but was '" + typeName(input) + "'.");
    }

    @Override
    public @Nonnull Instant parseValue(
            @Nonnull Object input, @Nonnull GraphQLContext graphQLContext, @Nonnull Locale locale)
            throws CoercingParseValueException {
        if (input instanceof Instant instant) {
            return instant;
        } else if (input instanceof String string) {
            return Instant.parse(string);
        }
        throw new CoercingParseValueException("Expected a 'String' but was '" + typeName(input) + "'.");
    }

    @Override
    public String serialize(@Nonnull Object input, @Nonnull GraphQLContext graphQLContext, @Nonnull Locale locale)
            throws CoercingSerializeException {
        if (input instanceof Instant instant) {
            return instant.toString();
        }
        throw new CoercingSerializeException("Unable to serialize timestamp to string: " + input);
    }
}
