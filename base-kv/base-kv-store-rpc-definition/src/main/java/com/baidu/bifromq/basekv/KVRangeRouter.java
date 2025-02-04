/*
 * Copyright (c) 2023. Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.baidu.bifromq.basekv;

import static com.baidu.bifromq.basekv.utils.BoundaryUtil.EMPTY_BOUNDARY;
import static com.baidu.bifromq.basekv.utils.BoundaryUtil.MIN_KEY;
import static com.baidu.bifromq.basekv.utils.BoundaryUtil.inRange;
import static com.baidu.bifromq.basekv.utils.BoundaryUtil.isOverlap;
import static com.google.protobuf.ByteString.unsignedLexicographicalComparator;
import static java.util.Collections.emptySet;

import com.baidu.bifromq.basekv.proto.Boundary;
import com.baidu.bifromq.basekv.proto.KVRangeDescriptor;
import com.baidu.bifromq.basekv.proto.KVRangeId;
import com.baidu.bifromq.basekv.proto.KVRangeStoreDescriptor;
import com.baidu.bifromq.basekv.raft.proto.RaftNodeStatus;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.StampedLock;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
public final class KVRangeRouter implements IKVRangeRouter {
    private final String clusterId;
    private final StampedLock stampedLock = new StampedLock();
    private final Comparator<ByteString> comparator = unsignedLexicographicalComparator();
    @ToString.Include
    private final NavigableMap<ByteString, KVRangeSetting> rangeTable = new TreeMap<>(comparator);
    private final Map<String, Set<KVRangeSetting>> rangesToStoreMap = new HashMap<>();
    private final Map<KVRangeId, KVRangeSetting> rangeMap = new HashMap<>();

    public KVRangeRouter(String clusterId) {
        this.clusterId = clusterId;
    }

    public void reset(KVRangeStoreDescriptor storeDescriptor) {
        final long stamp = stampedLock.writeLock();
        try {
            rangeTable.clear();
            rangesToStoreMap.clear();
            rangeMap.clear();
            storeDescriptor.getRangesList()
                .forEach(rangeDesc -> this.upsertWithoutLock(storeDescriptor.getId(), rangeDesc));
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    public boolean upsert(KVRangeStoreDescriptor storeDescriptor) {
        final long stamp = stampedLock.writeLock();
        try {
            boolean changed = false;
            for (KVRangeDescriptor rangeDesc : storeDescriptor.getRangesList()) {
                changed |= this.upsertWithoutLock(storeDescriptor.getId(), rangeDesc);
            }
            return changed;
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    public boolean isFullRangeCovered() {
        final long stamp = stampedLock.readLock();
        try {
            if (rangeTable.isEmpty()) {
                return false;
            }
            ByteString firstKey = rangeTable.firstKey();
            if (!firstKey.equals(MIN_KEY) || rangeTable.firstEntry().getValue().boundary.hasStartKey()) {
                // the lower bound of the first range is explicitly set to empty byte string.
                return false;
            }
            ByteString endKey = MIN_KEY;
            for (Map.Entry<ByteString, KVRangeSetting> entry : rangeTable.entrySet()) {
                Boundary range = entry.getValue().boundary;
                if (!endKey.equals(range.getStartKey())) {
                    return false;
                } else {
                    endKey = range.getEndKey();
                }
            }
            // the upper bound of the last range is open
            return !rangeTable.lastEntry().getValue().boundary.hasEndKey();
        } finally {
            stampedLock.unlockRead(stamp);
        }
    }

    public Set<KVRangeSetting> findByStore(String storeId) {
        final long stamp = stampedLock.readLock();
        try {
            return Collections.unmodifiableSet(rangesToStoreMap.getOrDefault(storeId, emptySet()));
        } finally {
            stampedLock.unlockRead(stamp);
        }
    }

    @Override
    public Optional<KVRangeSetting> findByKey(ByteString key) {
        final long stamp = stampedLock.readLock();
        try {
            Map.Entry<ByteString, KVRangeSetting> entry = rangeTable.floorEntry(key);
            if (entry != null) {
                KVRangeSetting setting = entry.getValue();
                if (inRange(key, setting.boundary)) {
                    return Optional.of(setting);
                } else {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        } finally {
            stampedLock.unlockRead(stamp);
        }
    }

    @Override
    public List<KVRangeSetting> findByBoundary(Boundary boundary) {
        final long stamp = stampedLock.readLock();
        try {
            return findByRangeWithoutLock(boundary);
        } finally {
            stampedLock.unlockRead(stamp);
        }
    }

    @Override
    public Optional<KVRangeSetting> findById(KVRangeId id) {
        return Optional.ofNullable(rangeMap.get(id));
    }

    private List<KVRangeSetting> findByRangeWithoutLock(Boundary range) {
        List<KVRangeSetting> ranges = new ArrayList<>();
        // range before range.start
        Map.Entry<ByteString, KVRangeSetting> before = rangeTable.lowerEntry(range.getStartKey());
        if (before != null && inRange(range.getStartKey(), before.getValue().boundary)) {
            ranges.add(before.getValue());
        }
        // ranges after range.start
        NavigableMap<ByteString, KVRangeSetting> after = rangeTable.tailMap(range.getStartKey(), true);
        for (Map.Entry<ByteString, KVRangeSetting> entry : after.entrySet()) {
            if (isOverlap(entry.getValue().boundary, range)) {
                ranges.add(entry.getValue());
            } else {
                break;
            }
        }
        return ranges;
    }

    private boolean upsertWithoutLock(String storeId, KVRangeDescriptor descriptor) {
        if (descriptor.getRole() != RaftNodeStatus.Leader) {
            return false;
        }
        if (descriptor.getBoundary().equals(EMPTY_BOUNDARY)) {
            return false;
        }
        switch (descriptor.getState()) {
            case Removed, Purged, Merged, MergedQuiting -> {
                return false;
            }
        }
        KVRangeSetting setting = new KVRangeSetting(clusterId, storeId, descriptor);

        List<KVRangeSetting> overlapped = findByRangeWithoutLock(setting.boundary);
        if (overlapped.isEmpty()) {
            rangeTable.put(setting.boundary.getStartKey(), setting);
            rangesToStoreMap.computeIfAbsent(storeId, k -> new HashSet<>()).add(setting);
            rangeMap.put(setting.id, setting);
            return true;
        } else {
            if (overlapped.stream().allMatch(o -> o.ver <= setting.ver)) {
                overlapped.forEach(o -> {
                    rangeTable.remove(o.boundary.getStartKey());
                    rangesToStoreMap.getOrDefault(o.leader, emptySet()).remove(o);
                    rangeMap.remove(o.id);
                });
                rangeTable.put(setting.boundary.getStartKey(), setting);
                rangesToStoreMap.computeIfAbsent(setting.leader, k -> new HashSet<>()).add(setting);
                rangeMap.put(setting.id, setting);
                return true;
            }
            return false;
        }
    }
}
