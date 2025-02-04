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

package com.baidu.bifromq.basekv.localengine;

/**
 * A writer for update range state, only when done method is called the changes are persisted and visible
 */
public interface IKVSpaceWriter extends IKVSpaceReader, IKVSpaceWritable<IKVSpaceWriter> {
    /**
     * Persist the changes, after done the writer should not be used again
     */
    void done();

    /**
     * Abort the batch
     */
    void abort();

    /**
     * How many changes made
     *
     * @return the change count
     */
    int count();
}
