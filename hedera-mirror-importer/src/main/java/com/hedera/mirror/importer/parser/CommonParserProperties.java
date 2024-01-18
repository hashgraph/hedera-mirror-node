/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

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

    @Value
    static class TransactionFilter {

        private static final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
        private static final ExpressionParser expressionParser = new SpelExpressionParser();

        static {
            evaluationContext.setTypeLocator(new RestrictedTypeLocator());
            evaluationContext.setPropertyAccessors(List.of(new RecordItemPropertyAccessor()));
        }

        private final Collection<EntityId> entity;
        private final String expression;

        @Getter(AccessLevel.NONE)
        private final Expression parsedExpression;

        private final Collection<TransactionType> transaction;

        @Builder
        TransactionFilter(Collection<EntityId> entity, String expression, Collection<TransactionType> transaction) {
            this.entity = entity != null ? entity : new LinkedHashSet<>();
            this.expression = expression;
            this.transaction = transaction != null ? transaction : new LinkedHashSet<>();

            if (!StringUtils.isEmpty(expression)) {
                try {
                    this.parsedExpression = expressionParser.parseExpression(expression);
                } catch (ParseException ex) {
                    throw new InvalidConfigurationException("Transaction filter expression failed to parse", ex);
                }
            } else {
                this.parsedExpression = null;
            }
        }

        Predicate<TransactionFilterFields> getFilter() {
            return t -> matches(t) && matches(t.getEntities()) && matchesExpression(t.getRecordItem());
        }

        private boolean matches(TransactionFilterFields t) {
            if (transaction.isEmpty()) {
                return true;
            }

            return transaction.contains(TransactionType.of(t.getRecordItem().getTransactionType()));
        }

        private boolean matches(Collection<EntityId> entities) {
            if (entity.isEmpty()) {
                return true;
            }

            return entities != null && CollectionUtils.containsAny(entity, entities);
        }

        private boolean matchesExpression(RecordItem recordItem) {
            if (parsedExpression == null) {
                return true;
            }

            try {
                Boolean result = parsedExpression.getValue(evaluationContext, recordItem, Boolean.class);
                return Objects.requireNonNullElse(result, false);
            } catch (EvaluationException ex) {
                throw new InvalidConfigurationException(
                        "Transaction filter expression failed to evaluate: " + expression, ex);
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

    /**
     * Limit the types that may be accessed via SpEL to none. Everything starts with property access.
     */
    static class RestrictedTypeLocator implements TypeLocator {

        @Override
        public Class<?> findType(String typeName) throws EvaluationException {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        }
    }
}
