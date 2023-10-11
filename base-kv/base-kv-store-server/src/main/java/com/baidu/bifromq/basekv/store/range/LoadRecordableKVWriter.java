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

package com.baidu.bifromq.basekv.store.range;

import com.baidu.bifromq.basekv.proto.Boundary;
import com.baidu.bifromq.basekv.store.api.IKVWriter;
import com.google.protobuf.ByteString;

class LoadRecordableKVWriter implements IKVWriter {
    private final IKVWriter delegate;
    private final ILoadTracker.ILoadRecorder recorder;

    public LoadRecordableKVWriter(IKVWriter delegate, ILoadTracker.ILoadRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    @Override
    public void delete(ByteString key) {
        long start = System.nanoTime();
        delegate.delete(key);
        recorder.record(key, System.nanoTime() - start);
    }

    @Override
    public void clear(Boundary boundary) {
        delegate.clear(boundary);
    }

    @Override
    public void insert(ByteString key, ByteString value) {
        long start = System.nanoTime();
        delegate.insert(key, value);
        recorder.record(key, System.nanoTime() - start);
    }

    @Override
    public void put(ByteString key, ByteString value) {
        long start = System.nanoTime();
        delegate.put(key, value);
        recorder.record(key, System.nanoTime() - start);
    }
}
