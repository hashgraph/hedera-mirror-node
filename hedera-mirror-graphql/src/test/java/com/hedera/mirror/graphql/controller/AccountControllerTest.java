package com.hedera.mirror.graphql.controller;

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

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import com.hedera.mirror.graphql.GraphqlIntegrationTest;
import com.hedera.mirror.graphql.mapper.AccountMapper;
import com.hedera.mirror.graphql.viewmodel.Account;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.binary.Base32;

@AutoConfigureHttpGraphQlTester
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class AccountControllerTest extends GraphqlIntegrationTest {

    private final AccountMapper accountMapper;
    private final HttpGraphQlTester tester;

    @CsvSource(delimiter = '|', textBlock = """
              query { account { id }}                                                                    | Missing field argument input
              query { account(input: {}) { id }}                                                         | Must provide exactly one input value
              query { account(input: {alias: ""}) { id }}                                                | alias must match
              query { account(input: {alias: "abcZ"}) { id }}                                            | alias must match
              query { account(input: {entityId: {num: -1}}) { id }}                                      | num must be greater than or equal to 0
              query { account(input: {entityId: {realm: -1, num: 1}}) { id }}                            | realm must be greater than or equal to 0
              query { account(input: {entityId: {shard: -1, num: 1}}) { id }}                            | shard must be greater than or equal to 0
              query { account(input: {entityId: {num: 1}, id: "a"}) { id }}                              | Must provide exactly one input value
              query { account(input: {evmAddress: ""}) { id }}                                           | evmAddress must match
              query { account(input: {evmAddress: "abc"}) { id }}                                        | evmAddress must match
              query { account(input: {evmAddress: "01234567890123456789012345678901234567890"}) { id }}  | evmAddress must match
              query { account(input: {id: ""}) { id }}                                                   | id must match
              query { account(input: {id: "*"}) { id }}                                                  | id must match
              query { account(input: {id: "azAZ0123456789+/="}) { id }}                                  | Not implemented
            """)
    @ParameterizedTest
    void invalidInput(String query, String error) {
        tester.document(query)
                .execute()
                .errors()
                .satisfy(r -> assertThat(r)
                        .hasSize(1)
                        .first()
                        .extracting(ResponseError::getMessage)
                        .asString()
                        .contains(error)
                );
    }

    @CsvSource(textBlock = """
            query { account(input: {entityId: {num: 999}}) { id }}
            query { account(input: {evmAddress: \"9999999999999999999999999999999999999999\"}) { id }}
            query { account(input: {alias: \"ABCDEFGHIJKLMNOPQABCDEFGHIJKLMNOPQ\"}) { id }}
            """)
    @ParameterizedTest
    void missing(String query) {
        tester.document(query)
                .execute()
                .errors()
                .verify()
                .path("account")
                .valueIsNull();
    }

    @Test
    void success() {
        var entity = domainBuilder.entity().persist();
        tester.document("""
                        query Account($id: Long!) {
                          account(input: { entityId: { num: $id } }) {
                            alias
                            autoRenewPeriod
                            balance
                            createdTimestamp
                            declineReward
                            deleted
                            entityId { shard, realm, num }
                            expirationTimestamp
                            id
                            key
                            maxAutomaticTokenAssociations
                            memo
                            nonce
                            pendingReward
                            receiverSigRequired
                            stakePeriodStart
                            timestamp {from, to}
                            type
                          }
                        }
                        """)
                .variable("id", entity.getNum())
                .execute()
                .errors()
                .verify()
                .path("account")
                .hasValue()
                .entity(Account.class)
                .satisfies(a -> assertThat(a).usingRecursiveComparison().isEqualTo(accountMapper.map(entity)));
    }

    @CsvSource(delimiter = '|', textBlock = """
            false | false
            false | true
            true  | false
            true  | true
            """)
    @ParameterizedTest
    void successByEvmAddress(boolean prefix, boolean uppercase) {
        var entity = domainBuilder.entity().persist();
        var evmAddress = Hex.encodeHexString(entity.getEvmAddress());
        if (uppercase) {
            evmAddress = evmAddress.toUpperCase();
        }
        if (prefix) {
            evmAddress = "0x" + evmAddress;
        }
        tester.document("""
                        query Account($evmAddress: String!) {
                          account(input: { evmAddress: $evmAddress }) {
                            alias
                            autoRenewPeriod
                            balance
                            createdTimestamp
                            declineReward
                            deleted
                            entityId { shard, realm, num }
                            expirationTimestamp
                            id
                            key
                            maxAutomaticTokenAssociations
                            memo
                            nonce
                            pendingReward
                            receiverSigRequired
                            stakePeriodStart
                            timestamp {from, to}
                            type
                          }
                        }
                        """)
                .variable("evmAddress", evmAddress)
                .execute()
                .errors()
                .verify()
                .path("account")
                .hasValue()
                .entity(Account.class)
                .satisfies(a -> assertThat(a).usingRecursiveComparison().isEqualTo(accountMapper.map(entity)));
    }

    @Test
    void successByAlias() {
        var entity = domainBuilder.entity().persist();
        var alias = new Base32().encodeAsString(entity.getAlias());
        tester.document("""
                        query Account($alias: String!) {
                          account(input: { alias: $alias }) {
                            alias
                            autoRenewPeriod
                            balance
                            createdTimestamp
                            declineReward
                            deleted
                            entityId { shard, realm, num }
                            expirationTimestamp
                            id
                            key
                            maxAutomaticTokenAssociations
                            memo
                            nonce
                            pendingReward
                            receiverSigRequired
                            stakePeriodStart
                            timestamp {from, to}
                            type
                          }
                        }
                        """)
                .variable("alias", alias)
                .execute()
                .errors()
                .verify()
                .path("account")
                .hasValue()
                .entity(Account.class)
                .satisfies(a -> assertThat(a).usingRecursiveComparison().isEqualTo(accountMapper.map(entity)));
    }

    @Test
    void balanceFormat() {
        var entity = domainBuilder.entity().persist();
        var query = "query Account($id: Long!) {account(input: { entityId: { num: $id } }) {balance(unit: HBAR) }}";
        tester.document(query)
                .variable("id", entity.getNum())
                .execute()
                .errors()
                .verify()
                .path("account.balance")
                .hasValue()
                .entity(Long.class)
                .isEqualTo(entity.getBalance() / 100_000_000L);
    }
}
