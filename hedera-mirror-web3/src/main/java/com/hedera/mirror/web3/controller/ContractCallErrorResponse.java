package com.hedera.mirror.web3.controller;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("_status")
@Data
public class ContractCallErrorResponse extends ContractCallResponse {
    private List<Message> messages = new ArrayList<>();

    public ContractCallErrorResponse(String message) {
        messages.add(new Message(message));
    }

    public ContractCallErrorResponse(List<String> errorMessages) {
        errorMessages.forEach(s -> this.messages.add(new Message(s)));
    }

    @Data
    public static class Message {
        private final String message;
    }
}
