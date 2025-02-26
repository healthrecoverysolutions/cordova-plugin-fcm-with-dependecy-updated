package com.hrs.firebase.messaging;

import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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
            } else if (value instanceof JSONObject) {
                bundle.putBundle(key, jsonToBundle((JSONObject) value));
            } else if (value instanceof JSONArray) {
                bundle.putSerializable(key, jsonArrayToArrayList((JSONArray) value));
            }
        }

        return bundle;
    }

    private static ArrayList<Object> jsonArrayToArrayList(JSONArray jsonArray) throws JSONException {
        ArrayList<Object> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONObject) {
                list.add(jsonToBundle((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(jsonArrayToArrayList((JSONArray) value));
            } else {
                list.add(value);
            }
        }
        return list;
    }

    public static HashMap<String, Object> bundleToHashMap(Bundle bundle) {
        HashMap<String, Object> map = new HashMap<>();

        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);

            if (value instanceof Bundle) {
                map.put(key, bundleToHashMap((Bundle) value));
            } else if (value instanceof ArrayList) {
                map.put(key, arrayListToJsonArray((ArrayList<?>) value));
            } else {
                map.put(key, value);
            }
        }

        return map;
    }

    private static JSONArray arrayListToJsonArray(ArrayList<?> list) {
        JSONArray jsonArray = new JSONArray();
        for (Object value : list) {
            if (value instanceof Bundle) {
                jsonArray.put(new JSONObject(bundleToHashMap((Bundle) value)));
            } else if (value instanceof ArrayList) {
                jsonArray.put(arrayListToJsonArray((ArrayList<?>) value));
            } else {
                jsonArray.put(value);
            }
        }
        return jsonArray;
    }

    public static JSONObject hashMapToJSONObject(HashMap<String, Object> hashMap) throws JSONException {
        JSONObject jsonPayload = new JSONObject();

        for (String key : hashMap.keySet()) {
            Object value = hashMap.get(key);

            if (value instanceof HashMap) {
                jsonPayload.put(key, hashMapToJSONObject((HashMap<String, Object>) value));
            } else if (value instanceof ArrayList) {
                jsonPayload.put(key, arrayListToJsonArray((ArrayList<?>) value));
            } else {
                jsonPayload.put(key, value);
            }
        }

        return jsonPayload;
    }

    public static JSONArray convertToJsonArray(JSONObject jsonObject) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        Iterator<String> keys = jsonObject.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            jsonArray.put(jsonObject.get(key));
        }

        return jsonArray;
    }

    /**
     * Converts HRS Unique Notification ID included in Notifications to int
     * Can be used to consistently reference the same unique id passed to the OS from the id included in the notification from our backend
     * @return int
     */
    public static int createNotificationId(String id) {
        return Math.abs(id.hashCode());
    }
}
