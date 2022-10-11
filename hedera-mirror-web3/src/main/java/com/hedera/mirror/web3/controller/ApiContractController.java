package com.hedera.mirror.web3.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.core.codec.DecodingException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.mirror.web3.service.ApiContractService;
import com.hedera.mirror.web3.service.ApiContractServiceFactory;

@CustomLog
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@RestController
public class ApiContractController {

    static final String INVALID_VERSION = "jsonrpc field must be " + JsonRpcResponse.VERSION;
    public static final String METRIC = "hedera.mirror.api.contract.requests";

    public final Map<Tags, Timer> timers = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;
    private final ApiContractServiceFactory apiContractServiceFactory;

    @PostMapping(value = "/contracts")
    public Mono<JsonRpcResponse> api(@RequestBody @Valid JsonRpcRequest<?> request,
                                     @RequestParam(required = false, name = "estimate") boolean estimate) {
        try {
            if (!request.getJsonrpc().equals(JsonRpcResponse.VERSION)) {
                return response(request, new JsonRpcErrorResponse(
                        JsonRpcErrorCode.INVALID_REQUEST, INVALID_VERSION));
            }

            ApiContractService<Object, Object> apiContractService =
                    apiContractServiceFactory.lookup(request.getMethod());

            if (apiContractService == null) {
                return response(request, new JsonRpcErrorResponse(
                        JsonRpcErrorCode.METHOD_NOT_FOUND));
            }

            final var params = (List<Object>) request.getParams();
            final var ethParams = (Map<String, String>) params.get(0);
//            final var ethParamsConverted = new EthParams(ethParams.get("from"),
//                    ethParams.get("to"), ethParams.get("gas"), ethParams.get("gasPrice"), ethParams.get("value"),
//                    ethParams.get("data"));
//            final var txnResult = new EthRpcCallBody(ethParamsConverted, (String) params.get(1));
//            Object result = apiContractService.get(txnResult);

            JsonRpcSuccessResponse jsonRpcSuccessResponse = new JsonRpcSuccessResponse();
            jsonRpcSuccessResponse.setId(request.getId());
            if (estimate) {
                jsonRpcSuccessResponse.setGas("0x1");
            } else {
                jsonRpcSuccessResponse.setResult("0x1");
//                jsonRpcSuccessResponse.setResult(result);
            }

            return response(request, jsonRpcSuccessResponse);
        } catch (InvalidParametersException e) {
            return response(request, new JsonRpcErrorResponse(
                    JsonRpcErrorCode.INVALID_PARAMS, e.getMessage()));
        } catch (Exception e) {
            return response(request, new JsonRpcErrorResponse(
                    JsonRpcErrorCode.INTERNAL_ERROR));
        }
    }

    @RequestMapping(value = "*", method = RequestMethod.POST)
    @ResponseBody
    public Mono<JsonRpcResponse> getFallback() {
        return null;
    }

    private Mono<JsonRpcResponse> response(
            JsonRpcRequest<?> request, JsonRpcResponse response) {
        if (request.getId() != null && request.getId() >= 0) {
            response.setId(request.getId());
        }

        // Ensure bad user input doesn't cause a cardinality explosion
        String method = request.getMethod();
        if (response instanceof JsonRpcErrorResponse && !apiContractServiceFactory.isValid(method)) {
            method = Tags.UNKNOWN_METHOD;
        }

        Tags tags = new Tags(method, response.getStatus());
        long time = System.nanoTime() - request.getStartTime();
        Timer timer = timers.computeIfAbsent(tags, this::newTimer);
        timer.record(time, TimeUnit.NANOSECONDS);

        return Mono.just(response);
    }

    private Mono<JsonRpcResponse> response(
            JsonRpcResponse response) {
        Tags tags = new Tags(ApiContractController.Tags.UNKNOWN_METHOD, response.getStatus());
        Timer timer = timers.computeIfAbsent(tags, this::newTimer);
        timer.record(0, TimeUnit.NANOSECONDS); // We can't calculate an accurate start time
        return Mono.just(response);
    }

    private Timer newTimer(Tags tags) {
        return Timer.builder(METRIC)
                .description("The time it takes to process an api contract request")
                .tag("method", tags.getMethod())
                .tag("status", tags.getStatus())
                .register(meterRegistry);
    }

    @ExceptionHandler
    Mono<JsonRpcResponse> parseError(DecodingException e) {
        log.warn("Parse error: {}", e.getMessage());
        return response(new JsonRpcErrorResponse(
                JsonRpcErrorCode.PARSE_ERROR));
    }

    @ExceptionHandler
    Mono<JsonRpcResponse> validationError(WebExchangeBindException e) {
        log.warn("Validation error: {}", e.getMessage());
        String message = e.getAllErrors()
                .stream()
                .map(this::formatError)
                .collect(Collectors.joining(", "));
        var errorResponse = new JsonRpcErrorResponse(
                JsonRpcErrorCode.INVALID_REQUEST, message);
        var target = e.getTarget();

        if (target instanceof JsonRpcRequest) {
            return response((JsonRpcRequest<?>) target, errorResponse);
        } else {
            return response(errorResponse);
        }
    }

    private String formatError(ObjectError error) {
        if (error instanceof FieldError) {
            FieldError fieldError = (FieldError) error;
            return fieldError.getField() + " field " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }

    @Value
    private class Tags {
        private static final String UNKNOWN_METHOD = "unknown";

        private final String method;
        private final String status;
    }
}
