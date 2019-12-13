package com.hedera.faker.common;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.inject.Named;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Named
public class EntityManager {

    @Getter
    private final long nodeAccountId;
    @Getter
    private final EntitySet accounts;
    @Getter
    private final EntitySet files;
    /**
     * Keeps track of balances for the entities. It is ArrayList for efficient iteration when writing out balances
     * data.
     */
    @Getter
    private final ArrayList<Long> balances;
    private long nextNewEntityNum;
    @Getter
    private final long portalEntity;  // Used to create crypto accounts
    private final Random random;

    public EntityManager() {
        random = new Random();
        nextNewEntityNum = 0;
        accounts = new EntitySet();
        files = new EntitySet();
        balances = new ArrayList<>(100000);  // Reasonable initial capacity.

        // Create one node account with id = 0.
        nodeAccountId = accounts.newEntity();
        balances.add(0L);  // balance of nodeAccountId

        // Create portal account with id = 1 which can fund other accounts on creation.
        portalEntity = accounts.newEntity();
        // Source of all hbars for couple 100 million transactions.
        balances.add(1000_000_000_000_000_000L);  // balance of portalEntity
    }

    public void addBalance(long accountId, long value) {
        balances.set((int) accountId, balances.get((int) accountId) + value);
    }

    // Source: https://stackoverflow.com/a/5669034
    // This is to get random values from a set. Stream on HashSet doesn't give random members, it returns almost same
    // values every time.
    public static class RandomSet<E> extends AbstractSet<E> {

        List<E> dta = new ArrayList<>();
        Map<E, Integer> idx = new HashMap<>();

        @Override
        public boolean add(E item) {
            if (idx.containsKey(item)) {
                return false;
            }
            idx.put(item, dta.size());
            dta.add(item);
            return true;
        }

        /**
         * Override element at position <code>id</code> with last element.
         *
         * @param id
         */
        public E removeAt(int id) {
            if (id >= dta.size()) {
                return null;
            }
            E res = dta.get(id);
            idx.remove(res);
            E last = dta.remove(dta.size() - 1);
            // skip filling the hole if last is removed
            if (id < dta.size()) {
                idx.put(last, id);
                dta.set(id, last);
            }
            return res;
        }

        @Override
        public boolean remove(Object item) {
            Integer id = idx.get(item);
            if (id == null) {
                return false;
            }
            removeAt(id);
            return true;
        }

        public E pollRandom(Random rnd) {
            if (dta.isEmpty()) {
                return null;
            }
            int id = rnd.nextInt(dta.size());
            return dta.get(id);
        }

        @Override
        public int size() {
            return dta.size();
        }

        @Override
        public Iterator<E> iterator() {
            return dta.iterator();
        }
    }

    public class EntitySet {
        @Getter
        final RandomSet<Long> active = new RandomSet<>();

        @Getter
        final RandomSet<Long> deleted = new RandomSet<>();

        public List<Long> getActive(int n) {
            List<Long> result = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                result.add(active.pollRandom(random));
            }
            return result;
        }

        public List<Long> getDeleted(int n) {
            List<Long> result = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                result.add(deleted.pollRandom(random));
            }
            return result;
        }

        public Long newEntity() {
            long newEntity = nextNewEntityNum;
            nextNewEntityNum++;
            active.add(newEntity);
            balances.add(0L);  // initial balance
            log.trace("New entity {}", newEntity);
            return newEntity;
        }

        public void delete(Long entityId) {
            if (active.contains(entityId)) {
                active.remove(entityId);
                deleted.add(entityId);
            } else {
                log.error("Cannot delete entity {}, not in active set", entityId);
            }
        }

        public void undelete(Long entityId) {
            if (deleted.contains(entityId)) {
                deleted.remove(entityId);
                active.add(entityId);
            } else {
                log.error("Cannot undelete entity {}, not in deleted set", entityId);
            }
        }
    }
}
