package com.hedera.mirror.web3.service.eth;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.web3.service.Web3Service;
import com.hedera.mirror.web3.service.Web3ServiceFactory;

@ExtendWith(MockitoExtension.class)
class Web3ServiceFactoryTest {

    private static final String METHOD = "foo";

    @Mock
    private Web3Service web3Service;

    private Web3ServiceFactory serviceFactory;

    @BeforeEach
    void setup() {
        when(web3Service.getMethod()).thenReturn(METHOD);
        serviceFactory = new Web3ServiceFactory(List.of(web3Service));
    }

    @Test
    void isValid() {
        assertThat(serviceFactory.isValid(METHOD)).isTrue();
        assertThat(serviceFactory.isValid(null)).isFalse();
        assertThat(serviceFactory.isValid("unknown")).isFalse();
    }

    @Test
    void lookup() {
        assertThat(serviceFactory.lookup(METHOD)).isEqualTo(web3Service);
    }

    @Test
    void lookupNotFound() {
        assertThat(serviceFactory.lookup(null)).isNull();
        assertThat(serviceFactory.lookup("unknown")).isNull();
    }
}
