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

import static com.hedera.mirror.web3.service.eth.EthBlockNumberService.DEFAULT;
import static com.hedera.mirror.web3.service.eth.EthBlockNumberService.METHOD;
import static com.hedera.mirror.web3.service.eth.EthBlockNumberService.PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.web3.repository.RecordFileRepository;

@ExtendWith(MockitoExtension.class)
class EthBlockNumberServiceTest {

    @Mock
    private RecordFileRepository recordFileRepository;

    @InjectMocks
    private EthBlockNumberService ethBlockNumberService;

    @Test
    void get() {
        when(recordFileRepository.findLatestIndex()).thenReturn(Optional.of(1L));
        assertThat(ethBlockNumberService.get(null)).isEqualTo("0x1");
    }

    @Test
    void getMaxLong() {
        when(recordFileRepository.findLatestIndex()).thenReturn(Optional.of(Long.MAX_VALUE));
        assertThat(ethBlockNumberService.get(null)).isEqualTo("0x7fffffffffffffff");
    }

    @Test
    void getWhenEmptyDatabase() {
        when(recordFileRepository.findLatestIndex()).thenReturn(Optional.empty());
        assertThat(ethBlockNumberService.get(null)).isEqualTo(PREFIX + DEFAULT);
    }

    @Test
    void getMethod() {
        assertThat(ethBlockNumberService.getMethod()).isEqualTo(METHOD);
    }
}
