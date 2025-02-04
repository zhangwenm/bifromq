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

package com.baidu.bifromq.basekv.store.option;

import com.baidu.bifromq.basekv.localengine.KVEngineConfigurator;
import com.baidu.bifromq.basekv.localengine.rocksdb.RocksDBKVEngineConfigurator;
import com.baidu.bifromq.basekv.store.util.ProcessUtil;
import java.nio.file.Paths;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
public class KVRangeStoreOptions {
    private String overrideIdentity;
    private KVRangeOptions kvRangeOptions = new KVRangeOptions();
    private int statsCollectIntervalSec = 5;

    private KVEngineConfigurator<?> dataEngineConfigurator = new RocksDBKVEngineConfigurator()
        .setDisableWAL(true) // data engine no need extra wal
        .setDbRootDir(Paths.get(System.getProperty("java.io.tmpdir"), "basekv",
            ProcessUtil.processId(), "data").toString())
        .setDbCheckpointRootDir(Paths.get(System.getProperty("java.io.tmpdir"), "basekvcp",
            ProcessUtil.processId(), "data").toString());

    private KVEngineConfigurator<?> walEngineConfigurator = new RocksDBKVEngineConfigurator()
        .setDbRootDir(Paths.get(System.getProperty("java.io.tmpdir"), "basekv",
            ProcessUtil.processId(), "wal").toString())
        .setDbCheckpointRootDir(Paths.get(System.getProperty("java.io.tmpdir"), "basekvcp",
            ProcessUtil.processId(), "wal").toString());
}
