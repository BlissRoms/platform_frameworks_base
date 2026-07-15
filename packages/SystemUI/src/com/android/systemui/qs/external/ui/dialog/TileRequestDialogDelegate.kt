/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.external.ui.dialog

import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.DialogInterface.OnMultiChoiceClickListener
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.dialog.AlertDialogContent
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.external.TileData
import com.android.systemui.qs.external.ui.viewmodel.TileRequestDialogViewModel
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LargeStaticTile
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallStaticTile
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class TileRequestDialogDelegate
@AssistedInject
constructor(
    private val sysuiDialogFactory: SystemUIDialogFactory,
    private val tileRequestDialogViewModelFactory: TileRequestDialogViewModel.Factory,
    @Assisted private val tileData: TileData,
    @Assisted private val dialogListener: OnMultiChoiceClickListener,
) : SystemUIDialog.Delegate {

    override fun createDialog(): SystemUIDialog {
        return sysuiDialogFactory
            .create { TileRequestDialogContent(it) }
            .apply {
                window?.attributes?.accessibilityTitle =
                    context.getString(R.string.qs_tile_request_dialog_title)
            }
    }

    @Composable
    private fun TileRequestDialogContent(dialog: SystemUIDialog) {
        PlatformTheme {
            val selectedLargeFormat = remember { mutableStateOf(false) }
            AlertDialogContent(
                title = {
                    if (Flags.qsSizesInTileRequestDialog()) {
                        Text(text = stringResource(R.string.qs_tile_request_dialog_title))
                    }
                },
                content = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = spacedBy(16.dp),
                    ) {
                        val viewModel =
                            rememberViewModel(traceName = "TileRequestDialog", key = tileData) {
                                tileRequestDialogViewModelFactory.create(dialog.context, tileData)
                            }

                        val bodyResourceId =
                            if (Flags.qsSizesInTileRequestDialog()) {
                                R.string.qs_tile_request_dialog_text_with_size
                            } else {
                                R.string.qs_tile_request_dialog_text
                            }
                        Text(
                            text = stringResource(bodyResourceId, tileData.appName),
                            textAlign = TextAlign.Start,
                        )

                        if (Flags.qsSizesInTileRequestDialog()) {
                            TileFormatsRow(
                                viewModel = viewModel,
                                selectedLargeFormat = selectedLargeFormat,
                            )
                        } else {
                            LargeStaticTile(
                                uiState = viewModel.uiState,
                                iconProvider = viewModel.iconProvider,
                                modifier =
                                    Modifier.width(
                                        dimensionResource(
                                            id = R.dimen.qs_tile_service_request_tile_width
                                        )
                                    ),
                            )
                        }
                    }
                },
                positiveButton = {
                    PlatformButton(
                        onClick = {
                            dialogListener.onClick(
                                dialog,
                                BUTTON_POSITIVE,
                                selectedLargeFormat.value,
                            )
                            dialog.dismiss()
                        }
                    ) {
                        Text(stringResource(R.string.qs_tile_request_dialog_add))
                    }
                },
                negativeButton = {
                    PlatformOutlinedButton(
                        onClick = {
                            dialogListener.onClick(
                                dialog,
                                BUTTON_NEGATIVE,
                                selectedLargeFormat.value,
                            )
                            dialog.dismiss()
                        }
                    ) {
                        Text(stringResource(R.string.qs_tile_request_dialog_not_add))
                    }
                },
            )
        }
    }

    @Composable
    private fun TileFormatsRow(
        viewModel: TileRequestDialogViewModel,
        selectedLargeFormat: MutableState<Boolean>,
        modifier: Modifier = Modifier,
    ) {
        Row(horizontalArrangement = spacedBy(24.dp), modifier = modifier) {
            TileFormatRadioButton(
                selected = !selectedLargeFormat.value,
                viewModel = viewModel,
                formatContentDescription =
                    stringResource(
                        R.string.qs_tile_request_dialog_small_format_content_description
                    ),
                onClick = { selectedLargeFormat.value = false },
            ) {
                SmallStaticTile(
                    uiState = viewModel.uiState,
                    iconProvider = viewModel.iconProvider,
                )
            }

            TileFormatRadioButton(
                selected = selectedLargeFormat.value,
                viewModel = viewModel,
                formatContentDescription =
                    stringResource(
                        R.string.qs_tile_request_dialog_large_format_content_description
                    ),
                onClick = { selectedLargeFormat.value = true },
            ) {
                LargeStaticTile(
                    uiState = viewModel.uiState,
                    iconProvider = viewModel.iconProvider,
                    modifier =
                        Modifier.width(
                            dimensionResource(id = R.dimen.qs_tile_service_request_tile_width)
                        ),
                )
            }
        }
    }

    @Composable
    private fun TileFormatRadioButton(
        selected: Boolean,
        viewModel: TileRequestDialogViewModel,
        formatContentDescription: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
        tile: @Composable () -> Unit,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                modifier.clearAndSetSemantics {
                    // Set the semantics on the parent column since the tile and radio button are
                    // essentially one button and shouldn't be able to be focused on separately
                    contentDescription = formatContentDescription + ", " + viewModel.uiState.label
                    this.selected = selected
                    this.onClick {
                        onClick()
                        true
                    }
                    role = Role.RadioButton
                },
        ) {
            tile()

            RadioButton(selected = selected, onClick = onClick)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            tiledata: TileData,
            dialogListener: OnMultiChoiceClickListener,
        ): TileRequestDialogDelegate
    }
}
