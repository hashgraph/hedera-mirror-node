package com.hedera.mirror.web3.controller;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
enum JsonRpcErrorCode {

    INTERNAL_ERROR(-32603, "Unknown error invoking RPC"),
    INVALID_PARAMS(-32602, "Invalid params"),
    INVALID_REQUEST(-32600, "Invalid request"),
    METHOD_NOT_FOUND(-32601, "Unsupported JSON-RPC method"),
    PARSE_ERROR(-32700, "Unable to parse JSON");

    @JsonValue
    private final int code;
    private final String message;
}
