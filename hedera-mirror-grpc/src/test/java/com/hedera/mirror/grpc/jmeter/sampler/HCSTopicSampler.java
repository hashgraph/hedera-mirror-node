package com.hedera.mirror.grpc.jmeter.sampler;

import com.hedera.mirror.grpc.jmeter.props.MessageListener;

public interface HCSTopicSampler {
    HCSSamplerResult subscribeTopic(MessageListener messageListener) throws InterruptedException;
}
