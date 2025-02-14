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

package com.hedera.mirror.graphql.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.hedera.mirror.graphql.scalar.GraphQlDuration;
import com.hedera.mirror.graphql.scalar.GraphQlTimestamp;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.parser.ParserOptions;
import graphql.parser.ParserOptions.Builder;
import graphql.scalars.ExtendedScalars;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.validation.rules.OnValidationErrorStrategy;
import graphql.validation.rules.ValidationRules;
import graphql.validation.schemawiring.ValidationSchemaWiring;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
class GraphQlConfiguration {

    static {
        // Configure GraphQL parsing limits to reject malicious input
        Consumer<Builder> consumer =
                b -> b.maxCharacters(10000).maxRuleDepth(100).maxTokens(1000).maxWhitespaceTokens(1000);
        ParserOptions.setDefaultParserOptions(
                ParserOptions.getDefaultParserOptions().transform(consumer));
        ParserOptions.setDefaultOperationParserOptions(
                ParserOptions.getDefaultOperationParserOptions().transform(consumer));
    }

    @Bean
    GraphQlSourceBuilderCustomizer graphQlCustomizer(PreparsedDocumentProvider provider) {
        var maxQueryComplexity = new MaxQueryComplexityInstrumentation(200);
        var maxQueryDepth = new MaxQueryDepthInstrumentation(10);
        var instrumentation = new ChainedInstrumentation(maxQueryComplexity, maxQueryDepth);

        return b -> b.configureGraphQl(
                graphQL -> graphQL.instrumentation(instrumentation).preparsedDocumentProvider(provider));
    }

    @Bean
    RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .directiveWiring(validationDirectives())
                .scalar(ExtendedScalars.GraphQLLong)
                .scalar(ExtendedScalars.Object)
                .scalar(GraphQlDuration.INSTANCE)
                .scalar(GraphQlTimestamp.INSTANCE);
    }

    @Bean
    SchemaDirectiveWiring validationDirectives() {
        var validationRules = ValidationRules.newValidationRules()
                .onValidationErrorStrategy(OnValidationErrorStrategy.RETURN_NULL)
                .build();
        return new ValidationSchemaWiring(validationRules);
    }

    // Configure JSON parsing limits to reject malicious input
    @Bean
    Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            var streamReadConstraints = StreamReadConstraints.builder()
                    .maxDocumentLength(11000)
                    .maxNameLength(100)
                    .maxNestingDepth(10)
                    .maxNumberLength(19)
                    .maxStringLength(11000)
                    .maxTokenCount(100)
                    .build();
            var streamWriteConstraints =
                    StreamWriteConstraints.builder().maxNestingDepth(100).build();
            var factory = new MappingJsonFactory();
            factory.setStreamReadConstraints(streamReadConstraints);
            factory.setStreamWriteConstraints(streamWriteConstraints);
            builder.factory(factory);
        };
    }
}
