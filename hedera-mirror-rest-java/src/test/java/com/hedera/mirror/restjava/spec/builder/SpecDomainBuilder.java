/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.spec.builder;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.DomainWrapper;
import com.hedera.mirror.common.domain.DomainWrapperImpl;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenAccount;
import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Used to build domain objects for use in spec testing. This is different from general unit tests utilizing
 * {@link DomainBuilder}, in that default values and overrides are based on the original REST module
 * integrationDomainOps.js and attribute names provided in the spec test JSON setup.
 */
@Component
@CustomLog
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SpecDomainBuilder {

    /*
     * Map builder parameter value provided in spec JSON to the type expected by the builder method.
     */
    private static final Map<Class<?>, Function<Object, Object>> PARAMETER_CONVERTERS = Map.of(
            java.lang.Boolean.class, source -> Boolean.parseBoolean(String.valueOf(source)),
            java.lang.Integer.class, source -> Integer.parseInt(String.valueOf(source)),
            java.lang.Long.class, source -> Long.parseLong(String.valueOf(source)),
            byte[].class, source -> source
    );

    private static final Map<Class<?>, Map<String, Method>> methodCache = new HashMap<>();

    private final EntityManager entityManager;
    private final TransactionOperations transactionOperations;

    public void addAccounts(List<Map<String, Object>> accounts) {
        accounts.forEach(this::addAccount);
    }

    public void addTokenAccounts(List<Map<String, Object>> tokenAccounts) {
        tokenAccounts.forEach(this::addTokenAccount);
    }

    private void addAccount(Map<String, Object> account) {
        var builder = Entity.builder();
        // set defaults
        builder
                .alias(null)
                .autoRenewAccountId(null)
                .autoRenewAccountId(null)
                .balance(null)
                .balanceTimestamp(null)
                .createdTimestamp(null)
                .declineReward(Boolean.FALSE)
                .deleted(Boolean.FALSE)
                .ethereumNonce(null)
                .evmAddress(null)
                .expirationTimestamp(null)
                .id(null)
                .key(null)
                .maxAutomaticTokenAssociations(0)
                .num(0L)
                .memo("entity memo")
                .num(0L)
                .obtainerId(null)
                .permanentRemoval(null)
                .proxyAccountId(null)
                .publicKey("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f")
                .realm(0L)
                .receiverSigRequired(Boolean.FALSE)
                .shard(0L)
                .stakedAccountId(null)
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .submitKey(null)
                .timestampRange(Range.atLeast(0L))
                .type(EntityType.ACCOUNT);

        var wrapper = new DomainWrapperImpl<Entity, Entity.EntityBuilder<?, ?>>(builder, builder::build, entityManager, transactionOperations);
        customizeWithSpec(wrapper, account);

        var accountEntity = wrapper.get();
        builder.id(EntityId.of(accountEntity.getShard(), accountEntity.getRealm(), accountEntity.getNum()).getId());
        wrapper.persist();
    }

    private void addTokenAccount(Map<String, Object> tokenAccount) {
        var builder = TokenAccount.builder();
        // set defaults
        builder
                .associated(true)
                .automaticAssociation(false)
                .balance(0L);

        var wrapper = new DomainWrapperImpl<TokenAccount, TokenAccount.TokenAccountBuilder<?, ?>>(builder, builder::build, entityManager, transactionOperations);
        customizeWithSpec(wrapper, tokenAccount);
    }

    private void customizeWithSpec(DomainWrapper<?, ?> wrapper, Map<String, Object> customizations) {
        wrapper.customize(builder -> {
            var builderClass = builder.getClass();
            var builderMethods = methodCache.computeIfAbsent(builderClass, clazz -> Arrays.stream(
                    clazz.getMethods()).collect(Collectors.toMap(Method::getName, Function.identity(), (m1, m2) -> m2)));

            for (var customization : customizations.entrySet()) {
                var methodName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, customization.getKey());
                var method = builderMethods.get(methodName);
                if (method != null) {
                    try {
                        var expectedParameterType = method.getParameterTypes()[0];
                        var mappedBuilderParameter = mapBuilderParameter(expectedParameterType, customization.getValue());
                        method.invoke(builder, mappedBuilderParameter);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        log.warn("Failed to invoke method '{}' for attribute override '{}' for {}",
                                methodName, customization.getKey(), builderClass.getName(), e);
                    }
                }
                else {
                    log.warn("Unknown attribute override '{}' for {}", customization.getKey(), builderClass.getName());
                }
            }
        });
    }

    private Object mapBuilderParameter(Class<?> expectedType, Object specParameterValue) {
        var typeMapper = PARAMETER_CONVERTERS.getOrDefault(expectedType, Function.identity());
        return  typeMapper.apply(specParameterValue);
    }
}
