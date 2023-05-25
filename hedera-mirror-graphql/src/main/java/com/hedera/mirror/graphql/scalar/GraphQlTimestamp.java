/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import java.time.Instant;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public class GraphQlTimestamp implements Coercing<Instant, String> {

    public static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
            .name("Timestamp")
            .description("An ISO 8601 compatible timestamp with nanoseconds granularity.")
            .coercing(new GraphQlTimestamp())
            .build();

    @Override
    public @NotNull Instant parseLiteral(
            @NotNull Value<?> input,
            @NotNull CoercedVariables variables,
            @NotNull GraphQLContext graphQLContext,
            @NotNull Locale locale)
            throws CoercingParseLiteralException {
        if (input instanceof StringValue str) {
            return Instant.parse(str.getValue());
        }
        throw new CoercingParseLiteralException("Expected a 'String' but was '" + typeName(input) + "'.");
    }

    @Override
    public @NotNull Instant parseValue(
            @NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale)
            throws CoercingParseValueException {
        if (input instanceof Instant instant) {
            return instant;
        } else if (input instanceof String string) {
            return Instant.parse(string);
        }
        throw new CoercingParseValueException("Expected a 'String' but was '" + typeName(input) + "'.");
    }

    @Override
    public String serialize(@NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale)
            throws CoercingSerializeException {
        if (input instanceof Instant instant) {
            return instant.toString();
        }
        throw new CoercingSerializeException("Unable to serialize timestamp to string: " + input);
    }
}
