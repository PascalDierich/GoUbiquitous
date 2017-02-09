package com.example.android.sunshine.sync;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;
import com.example.android.sunshine.utility.SunshineWeatherUtils;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;

public class DataListenerService extends WearableListenerService {
    private static final String LOG_TAG = DataListenerService.class.getSimpleName();

    GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for(DataEvent event:dataEvents){
            if(event.getType()== DataEvent.TYPE_CHANGED){
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo("/request_data") == 0) {
                    Log.d(LOG_TAG, "onDataChanged: request received");
                    ContentResolver sunshineContentResolver = getApplicationContext().getContentResolver();
                    Cursor cursor = sunshineContentResolver.query(WeatherContract.WeatherEntry.CONTENT_URI,
                            null, null, null, null);
                    try {
                        if (cursor == null && !cursor.moveToFirst()) {
                            return;
                        }
                    } catch (NullPointerException e) {
                        Log.e(LOG_TAG, "onDataChanged: NPE" + "\n" +
                                " --> " + e.fillInStackTrace());
                    }

                    String highTempString = cursor.getInt(4) + "\u00B0";
                    String lowTempString = cursor.getInt(3) + "\u00B0";
                    int weatherId = cursor.getInt(2);
                    cursor.close();
                    int weatherImageId = SunshineWeatherUtils
                            .getLargeArtResourceIdForWeatherCondition(weatherId);

                    Bitmap bitmap = SunshineSyncTask.getBitMapFormVectorDrawable(weatherImageId,getApplicationContext());
                    final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                    bitmap = Bitmap.createScaledBitmap(bitmap, 40, 40, true);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
                    Asset asset = Asset.createFromBytes(byteStream.toByteArray());

                    PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/data_changed");
                    putDataMapReq.getDataMap().putString("high", highTempString);
                    putDataMapReq.getDataMap().putString("low", lowTempString);
                    putDataMapReq.getDataMap().putAsset("image", asset);

                    PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
                    Wearable.DataApi.deleteDataItems(mGoogleApiClient, putDataReq.getUri());
                    putDataReq.setUrgent();
                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq)
                            .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                                @Override
                                public void onResult(DataApi.DataItemResult dataItemResult) {
                                    if (!dataItemResult.getStatus().isSuccess()) {
                                        Log.d(LOG_TAG, "onResult: failed");
                                    } else {
                                        Log.d(LOG_TAG, "onResult: success");
                                    }
                                }
                            });
                }
            }
        }
    }
}