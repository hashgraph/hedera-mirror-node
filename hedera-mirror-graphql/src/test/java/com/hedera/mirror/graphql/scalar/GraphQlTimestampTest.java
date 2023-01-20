package com.hedera.mirror.graphql.scalar;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(graphQlTimestamp.parseLiteral(StringValue.newStringValue(instant.toString())
                .build())).isEqualTo(instant);
        assertThatThrownBy(() -> graphQlTimestamp.parseLiteral(instant)).isInstanceOf(CoercingParseLiteralException.class);
        assertThatThrownBy(() -> graphQlTimestamp.parseLiteral("")).isInstanceOf(CoercingParseLiteralException.class);
    }

    @Test
    void parseValue() {
        var graphQlTimestamp = new GraphQlTimestamp();
        var instant = Instant.EPOCH;
        assertThat(graphQlTimestamp.parseValue(instant)).isEqualTo(instant);
        assertThat(graphQlTimestamp.parseValue("1970-01-01T00:00:00Z")).isEqualTo(instant);
        assertThatThrownBy(() -> graphQlTimestamp.parseValue(5L)).isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void serialize() {
        var graphQlTimestamp = new GraphQlTimestamp();
        var instant = Instant.now();
        assertThat(graphQlTimestamp.serialize(instant)).isEqualTo(instant.toString());
        assertThatThrownBy(() -> graphQlTimestamp.serialize("5s")).isInstanceOf(CoercingSerializeException.class);
    }
}
