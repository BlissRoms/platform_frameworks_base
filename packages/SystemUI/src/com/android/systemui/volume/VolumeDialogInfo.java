/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.volume;

import android.graphics.Bitmap;

import java.util.function.Supplier;

/**
 * Metadata about an available clock face.
 */
final class VolumeDialogInfo {

    private final String mName;
    private final Supplier<String> mTitle;
    private final String mId;
    private final Supplier<Bitmap> mThumbnail;

    private VolumeDialogInfo(String name, Supplier<String> title, String id,
            Supplier<Bitmap> thumbnail) {
        mName = name;
        mTitle = title;
        mId = id;
        mThumbnail = thumbnail;
    }

    /**
     * Gets the non-internationalized name for the clock face.
     */
    String getName() {
        return mName;
    }

    /**
     * Gets the name (title) of the clock face to be shown in the picker app.
     */
    String getTitle() {
        return mTitle.get();
    }

    /**
     * Gets the ID of the clock face, used by the picker to set the current selection.
     */
    String getId() {
        return mId;
    }

    /**
     * Gets a thumbnail image of the clock.
     */
    Bitmap getThumbnail() {
        return mThumbnail.get();
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private String mName;
        private Supplier<String> mTitle;
        private String mId;
        private Supplier<Bitmap> mThumbnail;

        public VolumeDialogInfo build() {
            return new VolumeDialogInfo(mName, mTitle, mId, mThumbnail);
        }

        public Builder setName(String name) {
            mName = name;
            return this;
        }

        public Builder setTitle(Supplier<String> title) {
            mTitle = title;
            return this;
        }

        public Builder setId(String id) {
            mId = id;
            return this;
        }

        public Builder setThumbnail(Supplier<Bitmap> thumbnail) {
            mThumbnail = thumbnail;
            return this;
        }
    }
}
