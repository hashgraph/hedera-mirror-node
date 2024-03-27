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

package com.hedera.mirror.restjava.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.restjava.mapper.NftAllowanceMapper;
import com.hedera.mirror.restjava.service.NftAllowanceService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = AllowancesController.class)
public class ExceptionControllerAdviceTest {

    private static final String CALL_URI = "http://localhost:8094/api/v1/accounts/{id}/allowances/nfts";

    @Resource
    private MockMvc mockMvc;

    @MockBean
    private NftAllowanceService service;

    @MockBean
    private NftAllowanceMapper mapper;

    @Resource
    private ObjectMapper objectMapper;

    // @Test
    void bindExceptionTest() throws Exception {

        // given(service.getNftAllowances(any())).willThrow(new EntityNotFoundException(exceptionMessage));

        mockMvc.perform(get(CALL_URI).accept(MediaType.ALL)).andExpect(status().isBadRequest());
    }
}
