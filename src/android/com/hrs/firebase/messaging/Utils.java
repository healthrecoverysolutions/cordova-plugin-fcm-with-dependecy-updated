package com.hrs.firebase.messaging;

import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;

import timber.log.Timber;

public class Utils {

    public static Bundle jsonToBundle(JSONObject jsonObject) throws JSONException {
        Bundle bundle = new Bundle();

        for (Iterator<String> it = jsonObject.keys(); it.hasNext();) {
            String key = it.next();
            Object value = jsonObject.get(key);

            if (value instanceof String) {
                bundle.putString(key, (String) value);
            } else if (value instanceof Integer) {
                bundle.putInt(key, (Integer) value);
            } else if (value instanceof Boolean) {
                bundle.putBoolean(key, (Boolean) value);
            } else if (value instanceof Double) {
                bundle.putDouble(key, (Double) value);
            } else if (value instanceof Long) {
                bundle.putLong(key, (Long) value);
            } else if(value instanceof JSONObject) {
                bundle.putBundle(key, jsonToBundle((JSONObject) value));
            }
        }

        return bundle;
    }

    public static HashMap<String, Object> bundleToHashMap(Bundle bundle) {
        HashMap<String, Object> map = new HashMap<>();
        for (String key : bundle.keySet()) {
            if (bundle.get(key) instanceof Bundle) {
                HashMap<String, Object> innerMap = new HashMap<>();
                Bundle innerBundle = (Bundle) bundle.get(key);
                if (innerBundle != null) {
                    innerMap = bundleToHashMap(innerBundle);
                }
                map.put(key, innerMap);
                continue;
            }
            map.put(key, bundle.get(key));
        }

        return map;
    }

    public static JSONObject hashMapToJSONObject(HashMap<String, Object> hashMap) throws JSONException {
        JSONObject jsonPayload = new JSONObject();
        for (String key : hashMap.keySet()) {
            if (hashMap.get(key) instanceof HashMap) {
                JSONObject innerJSONObject = hashMapToJSONObject((HashMap<String, Object>) hashMap.get(key));
                jsonPayload.put(key, innerJSONObject);
            } else {
                jsonPayload.put(key, hashMap.get(key));
            }
            Timber.d("\tpayload: " + key + " => " + hashMap.get(key));
        }

        return jsonPayload;
    }
}
