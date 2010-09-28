/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.vcard;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.R;

/**
 * The class responsible for importing vCard from one ore multiple Uris.
 */
public class VCardService extends Service {
    private final static String LOG_TAG = VCardService.class.getSimpleName();

    /* package */ static final int MSG_IMPORT_REQUEST = 1;
    /* package */ static final int MSG_EXPORT_REQUEST = 2;
    /* package */ static final int MSG_CANCEL_IMPORT_REQUEST = 3;
    /* package */ static final int MSG_NOTIFY_IMPORT_FINISHED = 5;

    /* package */ static final int IMPORT_NOTIFICATION_ID = 1000;
    /* package */ static final int EXPORT_NOTIFICATION_ID = 1001;

    /**
     * Small vCard file is imported soon, so any meassage saying "vCard import started" is
     * not needed. We show the message when the size of vCard is larger than this constant. 
     */
    private static final int IMPORT_NOTIFICATION_THRESHOLD = 10;

    public class ImportRequestHandler extends Handler {
        private ImportProcessor mImportProcessor;
        private ExportProcessor mExportProcessor = new ExportProcessor(VCardService.this);
        private boolean mDoDelayedCancel = false;

        public ImportRequestHandler() {
            super();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_IMPORT_REQUEST: {
                    if (mDoDelayedCancel) {
                        Log.i(LOG_TAG, "A cancel request came before import request. " +
                                "Refrain current import once.");
                        mDoDelayedCancel = false;
                    } else {
                        final ImportRequest parameter = (ImportRequest)msg.obj;

                        if (mImportProcessor == null) {
                            mImportProcessor = new ImportProcessor(VCardService.this);
                        } else if (mImportProcessor.isCanceled()) {
                            Log.i(LOG_TAG,
                                    "Existing ImporterProcessor is canceled. create another.");
                            mImportProcessor = new ImportProcessor(VCardService.this);
                        }

                        mImportProcessor.pushRequest(parameter);
                        if (parameter.entryCount > IMPORT_NOTIFICATION_THRESHOLD) {
                            Toast.makeText(VCardService.this,
                                    getString(R.string.vcard_importer_start_message),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                    break;
                }
                case MSG_EXPORT_REQUEST: {
                    final ExportRequest parameter = (ExportRequest)msg.obj;
                    mExportProcessor.pushRequest(parameter);
                    Toast.makeText(VCardService.this,
                            getString(R.string.vcard_exporter_start_message),
                            Toast.LENGTH_LONG).show();
                    break;
                }
                case MSG_CANCEL_IMPORT_REQUEST: {
                    if (mImportProcessor != null) {
                        mImportProcessor.cancel();
                    } else {
                        Log.w(LOG_TAG, "ImportProcessor isn't ready. Delay the cancel request.");
                        mDoDelayedCancel = true;
                    }
                    break;
                }
                case MSG_NOTIFY_IMPORT_FINISHED: {
                    Log.d(LOG_TAG, "MSG_NOTIFY_IMPORT_FINISHED");
                    break;
                }
                default: {
                    Log.e(LOG_TAG, "Unknown request type: " + msg.what);
                    super.hasMessages(msg.what);
                }
            }
        }
    }

    private ImportRequestHandler mHandler = new ImportRequestHandler();
    private Messenger mMessenger = new Messenger(mHandler);

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }
}
