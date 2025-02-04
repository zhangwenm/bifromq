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

import static com.baidu.bifromq.basekv.utils.BoundaryUtil.inRange;

import com.baidu.bifromq.basekv.localengine.IKVSpaceIterator;
import com.baidu.bifromq.basekv.localengine.IKVSpaceReader;
import com.baidu.bifromq.basekv.proto.Boundary;
import com.baidu.bifromq.basekv.store.api.IKVIterator;
import com.baidu.bifromq.basekv.store.api.IKVRangeReader;
import com.baidu.bifromq.basekv.store.api.IKVReader;
import com.google.protobuf.ByteString;
import java.util.Optional;

public class KVReader implements IKVReader {
    private final IKVSpaceReader kvSpace;
    private final IKVSpaceIterator kvSpaceIterator;
    private final IKVRangeReader kvRangeReader;

    KVReader(IKVSpaceReader kvSpace, IKVRangeReader reader) {
        this.kvSpace = kvSpace;
        this.kvSpaceIterator = kvSpace.newIterator();
        this.kvRangeReader = reader;
    }

    @Override
    public Boundary boundary() {
        return kvRangeReader.boundary();
    }

    @Override
    public long size(Boundary boundary) {
        assert inRange(boundary, boundary());
        return kvRangeReader.size(boundary);
    }

    @Override
    public boolean exist(ByteString key) {
        assert inRange(key, boundary());
        return kvSpace.exist(key);
    }

    @Override
    public Optional<ByteString> get(ByteString key) {
        assert inRange(key, boundary());
        return kvSpace.get(key);
    }

    @Override
    public IKVIterator iterator() {
        return new KVIterator(kvSpaceIterator);
    }

    @Override
    public void refresh() {
        kvSpaceIterator.refresh();
    }
}
