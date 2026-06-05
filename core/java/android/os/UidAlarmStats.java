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
 * Per-UID wakeup-alarm statistics accumulated since the last charge.
 *
 * <p>Alarm wakeups are delivered via {@code AlarmManager} with the intent flag
 * {@code FLAG_WAKE_FROM_IDLE}. This object records how many times alarms tagged
 * with a particular name woke the device for a given UID and package.
 *
 * @hide
 */
public final class UidAlarmStats implements Parcelable {
    /** UID that scheduled these alarms. */
    public final int uid;
    /** Package name that owns the alarms. */
    @NonNull
    public final String packageName;
    /**
     * Tag for the alarm as passed to {@code AlarmManager.set*(…)} or the
     * action/class string of the intent if no explicit tag was given.
     */
    @NonNull
    public final String tag;
    /** Number of times this alarm tag woke the device while on battery. */
    public final int count;

    /** @hide */
    public UidAlarmStats(int uid, @NonNull String packageName,
            @NonNull String tag, int count) {
        this.uid = uid;
        this.packageName = packageName;
        this.tag = tag;
        this.count = count;
    }

    private UidAlarmStats(@NonNull Parcel in) {
        uid = in.readInt();
        packageName = in.readString();
        tag = in.readString();
        count = in.readInt();
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(uid);
        out.writeString(packageName);
        out.writeString(tag);
        out.writeInt(count);
    }

    @NonNull
    public static final Creator<UidAlarmStats> CREATOR =
            new Creator<UidAlarmStats>() {
                @Override
                public UidAlarmStats createFromParcel(Parcel in) {
                    return new UidAlarmStats(in);
                }

                @Override
                public UidAlarmStats[] newArray(int size) {
                    return new UidAlarmStats[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }
}
