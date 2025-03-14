/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.widget.Switch;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.DataSaverController;
import com.android.systemui.statusbar.policy.NetworkController;

import javax.inject.Inject;

public class DataSaverTile extends QSTileImpl<BooleanState> implements
        DataSaverController.Listener{

    private final DataSaverController mDataSaverController;

    private final KeyguardMonitor mKeyguard;
    private final KeyguardCallback mKeyguardCallback = new KeyguardCallback();

    @Inject
    public DataSaverTile(QSHost host, NetworkController networkController) {
        super(host);
        mDataSaverController = networkController.getDataSaverController();
        mDataSaverController.observe(getLifecycle(), this);
        mKeyguard = Dependency.get(KeyguardMonitor.class);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening) {
            mKeyguard.addCallback(mKeyguardCallback);
        } else {
            mKeyguard.removeCallback(mKeyguardCallback);
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_DATA_SAVER_SETTINGS);
    }

    private boolean isUnlockingRequired() {
        return (Settings.Secure.getIntForUser(
                mContext.getContentResolver(), Settings.Secure.QSTILE_REQUIRES_UNLOCKING, 1,
                UserHandle.USER_CURRENT) == 1);
    }

    @Override
    protected void handleClick() {
        if (mKeyguard.isSecure() && mKeyguard.isShowing() && isUnlockingRequired()) {
            Dependency.get(ActivityStarter.class).postQSRunnableDismissingKeyguard(() -> {
                mHost.openPanels();
                handleClickInner();
            });
            return;
        }
        handleClickInner();
    }

    private void handleClickInner() {
        if (mState.value
                || Prefs.getBoolean(mContext, Prefs.Key.QS_DATA_SAVER_DIALOG_SHOWN, false)) {
            // Do it right away.
            toggleDataSaver();
            return;
        }
        // Shows dialog first
        SystemUIDialog dialog = new SystemUIDialog(mContext);
        dialog.setTitle(com.android.internal.R.string.data_saver_enable_title);
        dialog.setMessage(com.android.internal.R.string.data_saver_description);
        dialog.setPositiveButton(com.android.internal.R.string.data_saver_enable_button,
                (OnClickListener) (dialogInterface, which) -> {
                    toggleDataSaver();
                    Prefs.putBoolean(mContext, Prefs.Key.QS_DATA_SAVER_DIALOG_SHOWN, true);
                });
        dialog.setNegativeButton(com.android.internal.R.string.cancel, null);
        dialog.setShowForAllUsers(true);
        dialog.show();
    }

    private void toggleDataSaver() {
        mState.value = !mDataSaverController.isDataSaverEnabled();
        mDataSaverController.setDataSaverEnabled(mState.value);
        refreshState(mState.value);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.data_saver);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = arg instanceof Boolean ? (Boolean) arg
                : mDataSaverController.isDataSaverEnabled();
        state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.label = mContext.getString(R.string.data_saver);
        state.contentDescription = state.label;
        state.icon = ResourceIcon.get(state.value ? R.drawable.ic_data_saver
                : R.drawable.ic_data_saver_off);
        state.expandedAccessibilityClassName = Switch.class.getName();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_DATA_SAVER;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_data_saver_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_data_saver_changed_off);
        }
    }

    @Override
    public void onDataSaverChanged(boolean isDataSaving) {
        refreshState(isDataSaving);
    }

    private final class KeyguardCallback implements KeyguardMonitor.Callback {
        @Override
        public void onKeyguardShowingChanged() {
            refreshState();
        }
    };
}
