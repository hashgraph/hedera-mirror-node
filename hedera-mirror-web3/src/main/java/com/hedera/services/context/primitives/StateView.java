package com.hedera.services.context.primitives;

import com.hederahashgraph.api.proto.java.FileID;

public interface StateView {
    void attrOf(FileID fid);
}
