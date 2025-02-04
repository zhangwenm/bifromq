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

package com.baidu.bifromq.retain.store;

import static com.baidu.bifromq.metrics.TenantMetric.RetainUsedSpaceGauge;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.baidu.bifromq.plugin.settingprovider.Setting;
import com.baidu.bifromq.retain.rpc.proto.RetainResult;
import com.baidu.bifromq.type.TopicMessage;
import io.micrometer.core.instrument.Meter;
import lombok.SneakyThrows;
import org.testng.annotations.Test;

public class StatsTest extends RetainStoreTest {
    @SneakyThrows
    @Test(groups = "integration")
    public void reportRangeMetrics() {
        String tenantId = "tenantId";
        String topic = "/a/b/c";
        TopicMessage message = message(topic, "hello");
        when(settingProvider.provide(Setting.RetainedTopicLimit, tenantId)).thenReturn(10);

        RetainResult reply = requestRetain(tenantId, message);
        assertEquals(reply, RetainResult.RETAINED);

        await().until(() -> {
            for (Meter meter : meterRegistry.getMeters()) {
                if (meter.getId().getType() == Meter.Type.GAUGE &&
                    meter.getId().getName().equals(RetainUsedSpaceGauge.metricName)) {
                    return true;
                }
            }
            return false;
        });
    }
}
