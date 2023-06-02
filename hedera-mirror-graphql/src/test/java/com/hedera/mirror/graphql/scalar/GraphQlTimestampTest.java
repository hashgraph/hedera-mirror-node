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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import graphql.language.BooleanValue;
import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GraphQlTimestampTest {
    @Test
    void parseLiteral() {
        var graphQlTimestamp = new GraphQlTimestamp();
        var instant = Instant.EPOCH;
        var value = StringValue.newStringValue(instant.toString()).build();
        assertThat(graphQlTimestamp.parseLiteral(value, null, null, null)).isEqualTo(instant);
        var invalidValue = new BooleanValue(true);
        assertThatThrownBy(() -> graphQlTimestamp.parseLiteral(invalidValue, null, null, null))
                .isInstanceOf(CoercingParseLiteralException.class);
    }

    @Test
    void parseValue() {
        var graphQlTimestamp = new GraphQlTimestamp();
        var instant = Instant.EPOCH;
        assertThat(graphQlTimestamp.parseValue(instant, null, null)).isEqualTo(instant);
        assertThat(graphQlTimestamp.parseValue("1970-01-01T00:00:00Z", null, null))
                .isEqualTo(instant);
        assertThatThrownBy(() -> graphQlTimestamp.parseValue(5L, null, null))
                .isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void serialize() {
        var graphQlTimestamp = new GraphQlTimestamp();
        var instant = Instant.now();
        assertThat(graphQlTimestamp.serialize(instant, null, null)).isEqualTo(instant.toString());
        assertThatThrownBy(() -> graphQlTimestamp.serialize("5s", null, null))
                .isInstanceOf(CoercingSerializeException.class);
    }
}
