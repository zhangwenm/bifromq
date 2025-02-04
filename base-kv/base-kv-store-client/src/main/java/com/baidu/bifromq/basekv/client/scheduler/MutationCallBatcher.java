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

package com.baidu.bifromq.basekv.client.scheduler;

import com.baidu.bifromq.basekv.client.IBaseKVStoreClient;
import com.baidu.bifromq.basescheduler.Batcher;

public abstract class MutationCallBatcher<Req, Resp> extends Batcher<Req, Resp, MutationCallBatcherKey> {
    protected final IBaseKVStoreClient storeClient;

    protected MutationCallBatcher(String name,
                                  long tolerableLatencyNanos,
                                  long burstLatencyNanos,
                                  MutationCallBatcherKey batcherKey,
                                  IBaseKVStoreClient storeClient) {
        super(batcherKey, name, tolerableLatencyNanos, burstLatencyNanos);
        this.storeClient = storeClient;
    }

    @Override
    public void close() {
        super.close();
    }
}
