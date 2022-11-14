package com.hedera.mirror.test.e2e.acceptance.response;

import lombok.Data;

@Data
public class JsonRpcSuccessResponse<T> {

    private T result;

    private T gas;

}
