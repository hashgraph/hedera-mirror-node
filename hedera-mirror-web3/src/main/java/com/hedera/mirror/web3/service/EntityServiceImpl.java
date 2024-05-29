package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.repository.EntityRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntityServiceImpl implements EntityService {
    private final EntityRepository entityRepository;

    @Override
    public Optional<Entity> findById(long entityId) {
        return entityRepository.findById(entityId);
    }
}
