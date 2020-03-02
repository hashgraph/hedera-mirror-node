package com.hedera.mirror.grpc.jmeter.sampler;

import com.hedera.mirror.grpc.jmeter.props.MessageListener;
import com.hedera.mirror.grpc.jmeter.sampler.result.HCSSamplerResult;

public interface HCSTopicSampler {
    HCSSamplerResult subscribeTopic(MessageListener messageListener) throws InterruptedException;
}
