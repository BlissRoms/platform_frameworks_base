/**
 * Copyright (c) 2025, The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

/** On-going action progress chip view group stores all elements of chip */
public class OnGoingActionProgressGroup {
    public final View rootView;
    public final ImageView iconView;
    public final ProgressBar progressBarView;

    public OnGoingActionProgressGroup(View rootView, ImageView iconView,
                                      ProgressBar progressBarView){
        this.rootView = rootView;
        this.iconView = iconView;
        this.progressBarView = progressBarView;
    }
}