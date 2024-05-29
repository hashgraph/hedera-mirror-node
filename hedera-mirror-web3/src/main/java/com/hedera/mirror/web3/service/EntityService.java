package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.entity.Entity;
import java.util.Optional;

public interface EntityService {

    /**
     * @param entityId the entity id
     * @return {@link Entity}
     */
    Optional<Entity> findById(long entityId);
}
