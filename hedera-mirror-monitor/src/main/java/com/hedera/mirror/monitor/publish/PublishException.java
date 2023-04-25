/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.monitor.publish;

import com.google.common.base.Throwables;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import io.grpc.StatusRuntimeException;
import lombok.Getter;

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

        if (throwable instanceof PrecheckStatusException pse) {
            return pse.status.toString();
        } else if (throwable instanceof ReceiptStatusException rse) {
            return rse.receipt.status.toString();
        } else if (throwable instanceof StatusRuntimeException sre) {
            return sre.getStatus().getCode().toString();
        } else {
            return throwable.getClass().getSimpleName();
        }
    }
}
