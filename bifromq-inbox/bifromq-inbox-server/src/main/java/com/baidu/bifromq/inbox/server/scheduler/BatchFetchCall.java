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

package com.baidu.bifromq.inbox.server.scheduler;

import com.baidu.bifromq.basekv.client.IBaseKVStoreClient;
import com.baidu.bifromq.basekv.client.scheduler.BatchQueryCall;
import com.baidu.bifromq.basekv.client.scheduler.QueryCallBatcherKey;
import com.baidu.bifromq.basekv.proto.KVRangeId;
import com.baidu.bifromq.basekv.store.proto.ROCoProcInput;
import com.baidu.bifromq.basekv.store.proto.ROCoProcOutput;
import com.baidu.bifromq.basescheduler.CallTask;
import com.baidu.bifromq.inbox.storage.proto.BatchFetchRequest;
import com.baidu.bifromq.inbox.storage.proto.FetchParams;
import com.baidu.bifromq.inbox.storage.proto.Fetched;
import com.baidu.bifromq.inbox.storage.proto.InboxServiceROCoProcInput;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

public class BatchFetchCall extends BatchQueryCall<IInboxFetchScheduler.InboxFetch, Fetched> {
    protected BatchFetchCall(KVRangeId rangeId,
                             IBaseKVStoreClient storeClient,
                             Duration pipelineExpiryTime) {
        super(rangeId, storeClient, false, pipelineExpiryTime);
    }

    @Override
    protected ROCoProcInput makeBatch(Iterator<IInboxFetchScheduler.InboxFetch> inboxFetchIterator) {
        // key: scopedInboxIdUtf8
        Map<String, FetchParams> inboxFetches = new HashMap<>(128);

        inboxFetchIterator.forEachRemaining(request ->
            inboxFetches.compute(request.scopedInboxId.toStringUtf8(), (k, v) -> {
                if (v == null) {
                    return request.params;
                }
                FetchParams.Builder b = v.toBuilder();
                if (request.params.hasQos0StartAfter()) {
                    b.setQos0StartAfter(request.params.getQos1StartAfter());
                }
                if (request.params.hasQos1StartAfter()) {
                    b.setQos1StartAfter(request.params.getQos1StartAfter());
                }
                if (request.params.hasQos2StartAfter()) {
                    b.setQos2StartAfter(request.params.getQos2StartAfter());
                }
                b.setMaxFetch(request.params.getMaxFetch());
                return b.build();
            }));
        long reqId = System.nanoTime();
        return ROCoProcInput.newBuilder()
            .setInboxService(InboxServiceROCoProcInput.newBuilder()
                .setReqId(reqId)
                .setBatchFetch(BatchFetchRequest.newBuilder()
                    .putAllInboxFetch(inboxFetches)
                    .build())
                .build())
            .build();
    }

    @Override
    protected void handleOutput(
        Queue<CallTask<IInboxFetchScheduler.InboxFetch, Fetched, QueryCallBatcherKey>> batchedTasks,
        ROCoProcOutput output) {
        CallTask<IInboxFetchScheduler.InboxFetch, Fetched, QueryCallBatcherKey> task;
        while ((task = batchedTasks.poll()) != null) {
            task.callResult.complete(output.getInboxService()
                .getBatchFetch()
                .getResultMap()
                .get(task.call.scopedInboxId.toStringUtf8()));
        }
    }

    @Override
    protected void handleException(CallTask<IInboxFetchScheduler.InboxFetch, Fetched, QueryCallBatcherKey> callTask,
                                   Throwable e) {
        callTask.callResult.completeExceptionally(e);
    }
}
