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
              query { account(input: {alias: "CIQ"}) { id }}                                             | Not implemented
              query { account(input: {entityId: {num: -1}}) { id }}                                      | num must be greater than or equal to 0
              query { account(input: {entityId: {realm: -1, num: 1}}) { id }}                            | realm must be greater than or equal to 0
              query { account(input: {entityId: {shard: -1, num: 1}}) { id }}                            | shard must be greater than or equal to 0
              query { account(input: {entityId: {num: 1}, id: "a"}) { id }}                              | Must provide exactly one input value
              query { account(input: {evmAddress: ""}) { id }}                                           | evmAddress must match
              query { account(input: {evmAddress: "abc"}) { id }}                                        | evmAddress must match
              query { account(input: {evmAddress: "01234567890123456789012345678901234567890"}) { id }}  | evmAddress must match
              query { account(input: {evmAddress: "0x0123456789012345678901234567890123456789"}) { id }} | Not implemented
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

    @Test
    void missing() {
        tester.document("query { account(input: {entityId: {num: 999}}) { id }}")
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
