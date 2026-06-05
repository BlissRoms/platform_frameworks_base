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
 * Per-UID partial wakelock statistics accumulated since the last charge.
 *
 * <p>Partial wakelocks are the most battery-impactful kind: they prevent the CPU from
 * sleeping even when the screen is off. Each instance represents a single named wakelock
 * held by a specific UID.
 *
 * @hide
 */
public final class UidWakelockStats implements Parcelable {
    /** UID that holds this wakelock. */
    public final int uid;
    /** Tag passed to {@code PowerManager.newWakeLock(…, tag)}. */
    @NonNull
    public final String wakelockName;
    /** Total cumulative hold time for this partial wakelock, in milliseconds. */
    public final long partialTimeMs;
    /** Number of times this partial wakelock was acquired. */
    public final int count;

    /** @hide */
    public UidWakelockStats(int uid, @NonNull String wakelockName,
            long partialTimeMs, int count) {
        this.uid = uid;
        this.wakelockName = wakelockName;
        this.partialTimeMs = partialTimeMs;
        this.count = count;
    }

    private UidWakelockStats(@NonNull Parcel in) {
        uid = in.readInt();
        wakelockName = in.readString();
        partialTimeMs = in.readLong();
        count = in.readInt();
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(uid);
        out.writeString(wakelockName);
        out.writeLong(partialTimeMs);
        out.writeInt(count);
    }

    @NonNull
    public static final Creator<UidWakelockStats> CREATOR =
            new Creator<UidWakelockStats>() {
                @Override
                public UidWakelockStats createFromParcel(Parcel in) {
                    return new UidWakelockStats(in);
                }

                @Override
                public UidWakelockStats[] newArray(int size) {
                    return new UidWakelockStats[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }
}
