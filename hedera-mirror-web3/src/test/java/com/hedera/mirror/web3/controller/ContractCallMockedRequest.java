package com.hedera.mirror.web3.controller;

import lombok.Data;

@Data
public class ContractCallMockedRequest {
    private String block = "latest";
    private String data;
    private boolean estimate;
    private String from;
    private long gas;
    private long gasPrice;
    private String to;
    private long value;
}
