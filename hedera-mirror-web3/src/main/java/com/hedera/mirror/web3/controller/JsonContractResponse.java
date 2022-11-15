package com.hedera.mirror.web3.controller;

import lombok.Data;

@Data
public class JsonContractResponse {
    private String result;
    private String gas;
    //Adding a simple error field for the moment since the endpoints is not actively functioning
    private String error;
}
