/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.TransactionFilterFields;
import com.hedera.mirror.importer.exception.InvalidConfigurationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

@CustomLog
@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.parser")
public class CommonParserProperties {

    @Min(8192)
    private int bufferSize = 32768; // tested max byte size of buffer used by PGCopyOutputStream

    @NotNull
    private Collection<TransactionFilter> exclude = new ArrayList<>();

    @NotNull
    private Collection<TransactionFilter> include = new ArrayList<>();

    @Getter(lazy = true)
    private final Predicate<TransactionFilterFields> filter = includeFilter().and(excludeFilter());

    public boolean hasFilter() {
        return (!exclude.isEmpty()) || (!include.isEmpty());
    }

    private Predicate<TransactionFilterFields> excludeFilter() {
        if (exclude.isEmpty()) {
            return t -> true;
        }
        return exclude.stream().map(f -> f.getFilter().negate()).reduce(a -> true, Predicate::and);
    }

    private Predicate<TransactionFilterFields> includeFilter() {
        if (include.isEmpty()) {
            return t -> true;
        }
        return include.stream().map(TransactionFilter::getFilter).reduce(a -> false, Predicate::or);
    }

    @Data
    @Validated
    static class TransactionFilter {

        private static final EvaluationContext evaluationContext =
                new SimpleEvaluationContext.Builder(new RecordItemPropertyAccessor()).build();
        private static final ExpressionParser expressionParser = new SpelExpressionParser();

        @NotNull
        private Collection<EntityId> entity = new LinkedHashSet<>();

        private String expression;

        @Getter(AccessLevel.NONE)
        @Setter(AccessLevel.NONE)
        private Expression parsedExpression;

        @NotNull
        private Collection<TransactionType> transaction = new LinkedHashSet<>();

        Predicate<TransactionFilterFields> getFilter() {
            return t -> matches(t) && matches(t.getEntities()) && matchesExpression(t.getRecordItem());
        }

        private boolean matches(TransactionFilterFields t) {
            if (transaction.isEmpty() || t.getTransactionType() == null) {
                return true;
            }

            return transaction.contains(t.getTransactionType());
        }

        private boolean matches(Collection<EntityId> entities) {
            if (entity.isEmpty()) {
                return true;
            }

            return entities != null && CollectionUtils.containsAny(entity, entities);
        }

        private boolean matchesExpression(RecordItem recordItem) {
            if (StringUtils.isEmpty(expression)) {
                return true;
            }

            if (parsedExpression == null) {
                try {
                    parsedExpression = expressionParser.parseExpression(expression);
                } catch (ParseException ex) {
                    throw new InvalidConfigurationException("Transaction filter expression failed to parse", ex);
                }
            }

            try {
                Boolean result = parsedExpression.getValue(evaluationContext, recordItem, Boolean.class);
                return result != null && result;
            } catch (EvaluationException ex) {
                throw new InvalidConfigurationException(
                        "Transaction filter expression failed to evaluate: %s".formatted(expression), ex);
            }
        }
    }

    /**
     * Limit scope of property access via SpEL for RecordItem instances.
     */
    static class RecordItemPropertyAccessor extends ReflectivePropertyAccessor {

        private static final Set<String> ACCESSIBLE_PROPERTIES = Set.of("transactionBody", "transactionRecord");

        RecordItemPropertyAccessor() {
            super(false);
        }

        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
            if (target instanceof RecordItem && !ACCESSIBLE_PROPERTIES.contains(name)) {
                return false;
            }
            return super.canRead(context, target, name);
        }
    }
}
