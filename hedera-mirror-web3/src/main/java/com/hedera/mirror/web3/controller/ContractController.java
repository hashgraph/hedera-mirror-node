package com.hedera.mirror.web3.controller;

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
