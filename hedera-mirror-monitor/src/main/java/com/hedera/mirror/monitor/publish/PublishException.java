package com.hedera.mirror.monitor.publish;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.google.common.base.Throwables;
import io.grpc.StatusRuntimeException;
import lombok.Getter;

import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;

@Getter
public class PublishException extends RuntimeException {

    private static final long serialVersionUID = 5825147561227266065L;
    private final transient PublishRequest publishRequest;

    public PublishException(PublishRequest publishRequest, Throwable throwable) {
        super(throwable);
        this.publishRequest = publishRequest;
    }

    public String getStatus() {
        Throwable throwable = Throwables.getRootCause(this);

        if (throwable instanceof PrecheckStatusException) {
            PrecheckStatusException pse = (PrecheckStatusException) throwable;
            return pse.status.toString();
        } else if (throwable instanceof ReceiptStatusException) {
            ReceiptStatusException rse = (ReceiptStatusException) throwable;
            return rse.receipt.status.toString();
        } else if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException sre = (StatusRuntimeException) throwable;
            return sre.getStatus().getCode().toString();
        } else {
            return throwable.getClass().getSimpleName();
        }
    }
}
