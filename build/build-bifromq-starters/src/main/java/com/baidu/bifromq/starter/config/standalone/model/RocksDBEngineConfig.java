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

package com.baidu.bifromq.starter.config.standalone.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class RocksDBEngineConfig extends StorageEngineConfig {
    private String dataPathRoot = "";
    private boolean manualCompaction = false;
    private int compactMinTombstoneKeys = 200000;
    private int compactMinTombstoneRanges = 100000;
    private double compactTombstoneRatio = 0.3; // 30%
    private boolean asyncWALFlush = false; // only work for wal engine
    private boolean fsyncWAL = false; // only work for wal engine
}
