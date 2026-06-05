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
 * Snapshot of system suspend counters from the suspend control service.
 *
 * <p>Check {@link #available} before reading counter fields — when the suspend-control
 * service is not present all counter fields are zero and {@code available} is {@code false}.
 *
 * @hide
 */
public final class SuspendStats implements Parcelable {
    /**
     * {@code true} if the suspend-control service was reachable and returned valid data;
     * {@code false} if the service was unavailable (all other fields will be zero).
     */
    public final boolean available;
    public final long suspendAttemptCount;
    public final long failedSuspendCount;
    public final long shortSuspendCount;
    public final long suspendTimeMillis;
    public final long shortSuspendTimeMillis;
    public final long suspendOverheadTimeMillis;
    public final long failedSuspendOverheadTimeMillis;
    public final long newBackoffCount;
    public final long backoffContinueCount;
    public final long sleepTimeMillis;

    /** @hide */
    public SuspendStats(long suspendAttemptCount, long failedSuspendCount,
            long shortSuspendCount, long suspendTimeMillis, long shortSuspendTimeMillis,
            long suspendOverheadTimeMillis, long failedSuspendOverheadTimeMillis,
            long newBackoffCount, long backoffContinueCount, long sleepTimeMillis) {
        this.available = true;
        this.suspendAttemptCount = suspendAttemptCount;
        this.failedSuspendCount = failedSuspendCount;
        this.shortSuspendCount = shortSuspendCount;
        this.suspendTimeMillis = suspendTimeMillis;
        this.shortSuspendTimeMillis = shortSuspendTimeMillis;
        this.suspendOverheadTimeMillis = suspendOverheadTimeMillis;
        this.failedSuspendOverheadTimeMillis = failedSuspendOverheadTimeMillis;
        this.newBackoffCount = newBackoffCount;
        this.backoffContinueCount = backoffContinueCount;
        this.sleepTimeMillis = sleepTimeMillis;
    }

    /** Returns a sentinel instance indicating the suspend-control service was unreachable. */
    @NonNull
    public static SuspendStats unavailable() {
        return new SuspendStats(/* available= */ false);
    }

    /** Private constructor for the unavailable sentinel and Parcel reconstruction. */
    private SuspendStats(boolean available) {
        this.available = available;
        this.suspendAttemptCount = 0;
        this.failedSuspendCount = 0;
        this.shortSuspendCount = 0;
        this.suspendTimeMillis = 0;
        this.shortSuspendTimeMillis = 0;
        this.suspendOverheadTimeMillis = 0;
        this.failedSuspendOverheadTimeMillis = 0;
        this.newBackoffCount = 0;
        this.backoffContinueCount = 0;
        this.sleepTimeMillis = 0;
    }

    private SuspendStats(@NonNull Parcel in) {
        available = in.readBoolean();
        suspendAttemptCount = in.readLong();
        failedSuspendCount = in.readLong();
        shortSuspendCount = in.readLong();
        suspendTimeMillis = in.readLong();
        shortSuspendTimeMillis = in.readLong();
        suspendOverheadTimeMillis = in.readLong();
        failedSuspendOverheadTimeMillis = in.readLong();
        newBackoffCount = in.readLong();
        backoffContinueCount = in.readLong();
        sleepTimeMillis = in.readLong();
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeBoolean(available);
        out.writeLong(suspendAttemptCount);
        out.writeLong(failedSuspendCount);
        out.writeLong(shortSuspendCount);
        out.writeLong(suspendTimeMillis);
        out.writeLong(shortSuspendTimeMillis);
        out.writeLong(suspendOverheadTimeMillis);
        out.writeLong(failedSuspendOverheadTimeMillis);
        out.writeLong(newBackoffCount);
        out.writeLong(backoffContinueCount);
        out.writeLong(sleepTimeMillis);
    }

    @NonNull
    public static final Creator<SuspendStats> CREATOR =
            new Creator<SuspendStats>() {
                @Override
                public SuspendStats createFromParcel(Parcel in) {
                    return new SuspendStats(in);
                }

                @Override
                public SuspendStats[] newArray(int size) {
                    return new SuspendStats[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }
}
