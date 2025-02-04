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

package com.baidu.bifromq.inbox.store.balance;

import com.baidu.bifromq.basekv.balance.StoreBalancer;
import com.baidu.bifromq.basekv.balance.command.BalanceCommand;
import com.baidu.bifromq.basekv.balance.command.SplitCommand;
import com.baidu.bifromq.basekv.proto.KVRangeDescriptor;
import com.baidu.bifromq.basekv.proto.KVRangeStoreDescriptor;
import com.baidu.bifromq.basekv.proto.LoadHint;
import com.baidu.bifromq.basekv.proto.SplitHint;
import com.baidu.bifromq.basekv.proto.State;
import com.baidu.bifromq.basekv.raft.proto.RaftNodeStatus;
import com.baidu.bifromq.basekv.utils.KVRangeIdUtil;
import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class RangeSplitBalancer extends StoreBalancer {
    private static final double DEFAULT_CPU_USAGE_LIMIT = 0.8;
    private static final int DEFAULT_MAX_IO_DENSITY_PER_RANGE = 30;
    private static final long DEFAULT_IO_NANOS_LIMIT_PER_RANGE = 30_000;
    private final double cpuUsageLimit;
    private final int maxIODensityPerRange;
    private final long ioNanosLimitPerRange;
    private volatile Set<KVRangeStoreDescriptor> latestStoreDescriptors = Collections.emptySet();

    public RangeSplitBalancer(String localStoreId) {
        this(localStoreId, DEFAULT_CPU_USAGE_LIMIT, DEFAULT_MAX_IO_DENSITY_PER_RANGE, DEFAULT_IO_NANOS_LIMIT_PER_RANGE);
    }

    public RangeSplitBalancer(String localStoreId,
                              double cpuUsageLimit,
                              int maxIoDensityPerRange,
                              long ioNanoLimitPerRange) {
        super(localStoreId);
        Preconditions.checkArgument(0 < cpuUsageLimit && cpuUsageLimit < 1.0, "Invalid cpu usage limit");
        this.cpuUsageLimit = cpuUsageLimit;
        this.maxIODensityPerRange = maxIoDensityPerRange;
        this.ioNanosLimitPerRange = ioNanoLimitPerRange;
    }

    @Override
    public void update(Set<KVRangeStoreDescriptor> storeDescriptors) {
        latestStoreDescriptors = storeDescriptors;
    }

    @Override
    public Optional<BalanceCommand> balance() {
        KVRangeStoreDescriptor localStoreDesc = null;
        for (KVRangeStoreDescriptor d : latestStoreDescriptors) {
            if (d.getId().equals(localStoreId)) {
                localStoreDesc = d;
                break;
            }
        }
        if (localStoreDesc == null) {
            log.warn("There is no storeDescriptor for local store[{}]", localStoreId);
            return Optional.empty();
        }
        double cpuUsage = localStoreDesc.getStatisticsMap().get("cpu.usage");
        if (cpuUsage > cpuUsageLimit) {
            log.warn("High CPU usage[{}], temporarily disable RangeSplitBalancer for local store[{}]",
                cpuUsage, localStoreId);
            return Optional.empty();
        }
        List<KVRangeDescriptor> localLeaderRangeDescriptors = localStoreDesc.getRangesList()
            .stream()
            .filter(d -> d.getRole() == RaftNodeStatus.Leader)
            .filter(d -> d.getState() == State.StateType.Normal)
            // split range with highest io density
            .sorted((o1, o2) -> Long.compare(o2.getLoadHint().getMutation().getIoDensity(),
                o1.getLoadHint().getMutation().getIoDensity()))
            .toList();
        // No leader range in localStore
        if (localLeaderRangeDescriptors.isEmpty()) {
            return Optional.empty();
        }
        for (KVRangeDescriptor leaderRangeDescriptor : localLeaderRangeDescriptors) {
            LoadHint hint = leaderRangeDescriptor.getLoadHint();
            SplitHint splitHint = hint.getMutation();
            if (splitHint.getIoLatencyNanos() < ioNanosLimitPerRange &&
                splitHint.getIoDensity() > maxIODensityPerRange && splitHint.hasSplitKey()) {
                log.debug("Split range[{}] in store[{}]: key={}",
                    KVRangeIdUtil.toString(leaderRangeDescriptor.getId()),
                    localStoreId, splitHint.getSplitKey());
                return Optional.of(SplitCommand.builder()
                    .toStore(localStoreId)
                    .expectedVer(leaderRangeDescriptor.getVer())
                    .kvRangeId(leaderRangeDescriptor.getId())
                    .splitKey(splitHint.getSplitKey())
                    .build());
            }
        }
        return Optional.empty();
    }
}
