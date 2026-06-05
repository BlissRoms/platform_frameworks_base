/*
 * Copyright (C) 2026 VoltageOS
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

package android.os;

import android.annotation.NonNull;

/**
 * Snapshot of a kernel/native wakeup source tracked by batterystats.
 *
 * @hide
 */
public final class WakeupSourceStats implements Parcelable {
    @NonNull public final String name;
    public final int count;
    public final long totalTimeMs;

    /** @hide */
    public WakeupSourceStats(@NonNull String name, int count, long totalTimeMs) {
        this.name = name;
        this.count = count;
        this.totalTimeMs = totalTimeMs;
    }

    private WakeupSourceStats(@NonNull Parcel in) {
        name = in.readString();
        count = in.readInt();
        totalTimeMs = in.readLong();
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeString(name);
        out.writeInt(count);
        out.writeLong(totalTimeMs);
    }

    @NonNull
    public static final Creator<WakeupSourceStats> CREATOR =
            new Creator<WakeupSourceStats>() {
                @Override
                public WakeupSourceStats createFromParcel(Parcel in) {
                    return new WakeupSourceStats(in);
                }

                @Override
                public WakeupSourceStats[] newArray(int size) {
                    return new WakeupSourceStats[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }
}
