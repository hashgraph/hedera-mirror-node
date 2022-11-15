package com.hedera.mirror.web3.controller;

import lombok.Data;

@Data
public class JsonContractRequest {
    private String data;
    private String from;
    private String gas;
    private String gasPrice;
    private String value;
}

