/*
 * Copyright (c) 2008-2021, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.config;

import com.hazelcast.memory.MemorySize;

import java.io.File;
import java.util.Objects;

public class TSDiskTierConfig {

    /**
     * Default base directory for the tiered-store.
     */
    public static final String DEFAULT_TSTORE_BASE_DIR = "tstore";

    /**
     * Default block/sector size in bytes.
     */
    public static final int DEFAULT_BLOCK_SIZE_IN_BYTES = 4096;


    private boolean enabled;
    private File baseDir = new File(DEFAULT_TSTORE_BASE_DIR);
    private int blockSize = DEFAULT_BLOCK_SIZE_IN_BYTES;
    private MemorySize capacity;

    public TSDiskTierConfig() {

    }

    public TSDiskTierConfig(TSDiskTierConfig tsDiskTierConfig) {
        enabled = tsDiskTierConfig.isEnabled();
        baseDir = tsDiskTierConfig.getBaseDir();
        blockSize = tsDiskTierConfig.getBlockSize();
        capacity = tsDiskTierConfig.getCapacity();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public TSDiskTierConfig setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public TSDiskTierConfig setBaseDir(File baseDir) {
        this.baseDir = baseDir;
        return this;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public TSDiskTierConfig setBlockSize(int blockSize) {
        this.blockSize = blockSize;
        return this;
    }

    public MemorySize getCapacity() {
        return capacity;
    }

    public TSDiskTierConfig setCapacity(MemorySize capacity) {
        this.capacity = capacity;
        return this;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TSDiskTierConfig)) {
            return false;
        }

        TSDiskTierConfig that = (TSDiskTierConfig) o;

        if (enabled != that.enabled) {
            return false;
        }
        if (blockSize != that.blockSize) {
            return false;
        }
        if (!Objects.equals(baseDir, that.baseDir)) {
            return false;
        }
        return Objects.equals(capacity, that.capacity);
    }

    @Override
    public final int hashCode() {
        int result = (enabled ? 1 : 0);
        result = 31 * result + (baseDir != null ? baseDir.hashCode() : 0);
        result = 31 * result + blockSize;
        result = 31 * result + (capacity != null ? capacity.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TSDiskTierConfig{"
                + "enabled=" + enabled
                + ", baseDir=" + baseDir
                + ", blockSize=" + blockSize
                + ", capacity=" + capacity
                + '}';
    }
}
