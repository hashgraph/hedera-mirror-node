package com.hedera.mirror.web3.controller;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RequestMapping("/api/v1")
@RestController
public class ContractController {

    @PostMapping(value = "/contracts/call")
    public Mono<JsonContractResponse> api(@RequestBody @Valid JsonContractRequest request,
                                          @RequestParam(required = false, name = "estimate") boolean estimate) {
        return unsupportedOpResponse();
    }

    //This is temporary method till eth_call and gas_estimate business logic got impl.
    private Mono<JsonContractResponse> unsupportedOpResponse() {
        final var errorMessage = "Operations eth_call and gas_estimate are not supported yet!";
        final var jsonErrorResponse = new JsonContractResponse();
        jsonErrorResponse.setError(errorMessage);
        return Mono.just(jsonErrorResponse);
    }
}
