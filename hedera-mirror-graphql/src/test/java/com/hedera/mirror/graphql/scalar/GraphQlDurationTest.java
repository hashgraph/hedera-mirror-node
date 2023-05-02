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

import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GraphQlDurationTest {
    @Test
    void parseLiteral() {
        var graphQlDuration = new GraphQlDuration();
        var duration = Duration.ofSeconds(1L);
        assertThat(graphQlDuration.parseLiteral(
                        StringValue.newStringValue("PT1s").build()))
                .isEqualTo(duration);
        assertThatThrownBy(() -> graphQlDuration.parseLiteral(duration))
                .isInstanceOf(CoercingParseLiteralException.class);
        assertThatThrownBy(() -> graphQlDuration.parseLiteral("")).isInstanceOf(CoercingParseLiteralException.class);
    }

    @Test
    void parseValue() {
        var graphQlDuration = new GraphQlDuration();
        var duration = Duration.ofSeconds(1L);
        assertThat(graphQlDuration.parseValue(duration)).isEqualTo(duration);
        assertThat(graphQlDuration.parseValue("PT1s")).isEqualTo(duration);
        assertThatThrownBy(() -> graphQlDuration.parseValue(5L)).isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void serialize() {
        var graphQlDuration = new GraphQlDuration();
        var duration = Duration.ofSeconds(1L);
        assertThat(graphQlDuration.serialize(duration)).isEqualTo(duration.toString());
        assertThatThrownBy(() -> graphQlDuration.serialize("5s")).isInstanceOf(CoercingSerializeException.class);
    }
}
