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

package com.baidu.bifromq.inbox.store;

import static com.baidu.bifromq.metrics.TenantMetric.InboxUsedSpaceGauge;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.Meter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
public class StatsTest extends InboxStoreTest {
    @SneakyThrows
    @Test(groups = "integration")
    public void reportRangeMetrics() {
        String tenantId = "reportRangeMetrics_tenantId";
        String inboxId = "reportRangeMetrics_inboxId";
        requestCreate(tenantId, inboxId, 10, 100, false);

        await().until(() -> {
            for (Meter meter : meterRegistry.getMeters()) {
                if (meter.getId().getType() == Meter.Type.GAUGE &&
                    meter.getId().getName().equals(InboxUsedSpaceGauge.metricName)) {
                    return true;
                }
            }
            return false;
        });
    }
}
