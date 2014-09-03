package com.tv.ui.metro.loader;

import android.content.Context;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import com.android.volley.*;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;


import java.io.*;
import java.util.Map;

/**
 * Created by tv metro on 9/1/14.
 */
public abstract  class BaseGsonLoader<T> extends Loader<T> {
    private final  String TAG = "BaseGsonLoader";

    private            T       mResult;
    protected volatile boolean mIsLoading;
    private ProgressNotifiable mProgressNotifiable;
    private boolean            mHasDeliveredResult;

    protected String cacheFileName = "";
    public abstract void setCacheFileName();
    
    protected String calledURL = "";
    public abstract void setLoaderURL();

    public BaseGsonLoader(Context context) {
        super(context);
        init();
    }

    private void init(){
        mIsLoading = false;
        mHasDeliveredResult = false;
        
        setCacheFileName();
        setLoaderURL();
    }

    public void setProgressNotifiable(ProgressNotifiable progressNotifiable) {
        this.mProgressNotifiable = progressNotifiable;
        if (progressNotifiable != null) {
            progressNotifiable.init(dataExists(), mIsLoading);
        }
    }

    protected boolean dataExists() {
        // data exist and delivered to UI
        return mResult != null && mHasDeliveredResult;
    }

    @Override
    protected void onStartLoading() {
        if(mResult != null){
            deliverResult(mResult);
        }

        if (!mIsLoading && (mResult == null || takeContentChanged())) {
            forceLoad();
        }
    }

    @Override
    protected void onForceLoad() {

        //load from server
        mIsLoading = true;
        if (mProgressNotifiable != null) {
            mProgressNotifiable.startLoading(dataExists());
        }
        loadDataByGson();
    }


    Response.Listener<T> listener = new Response.Listener<T>() {
        @Override
        public void onResponse(T response) {
            mResult = response;
            deliverResult(response);
            mHasDeliveredResult = true;
            mIsLoading = false;

            if (mProgressNotifiable != null) {
                mProgressNotifiable.stopLoading(dataExists(), false);
            }
        }
    };

    Response.ErrorListener errorListener = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.d(TAG, "onErrorResponse error:" + error.toString());

            mIsLoading = false;
            if (mProgressNotifiable != null) {
                mProgressNotifiable.stopLoading(dataExists(), false);
            }
        }
    };

    protected abstract void loadDataByGson();


    public class GsonRequest<T> extends Request<T> {
        private final Gson gson = new Gson();
        private final Class<T> clazz;
        private final Map<String, String> headers;
        private final Response.Listener<T> listener;
        private String cacheFile;

        public void setCacheNeed(String _cacheFile){
            cacheFile = _cacheFile;
        }

        public GsonRequest(String url, Class<T> clazz, Map<String, String> headers,
                           Response.Listener<T> listener, Response.ErrorListener errorListener) {
            super(Method.GET, url, errorListener);
            this.clazz = clazz;
            this.headers = headers;
            this.listener = listener;
        }

        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            return headers != null ? headers : super.getHeaders();
        }

        @Override
        protected void deliverResponse(T response) {
            listener.onResponse(response);
        }

        @Override
        protected Response<T> parseNetworkResponse(NetworkResponse response) {
            try {
                String json = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
                Log.d(TAG, "response json:" + json);
                long timeStart = System.currentTimeMillis();
                T fromJson = gson.fromJson(json, clazz);
                long timeEnd = System.currentTimeMillis();
                Log.d(TAG, "fromJson take time in ms: " + (timeEnd - timeStart));
                Response<T> res =  Response.success(fromJson, HttpHeaderParser.parseCacheHeaders(response));

                if(cacheFile != null && cacheFile.length() > 0){
                    //save to files
                    updateToFile(cacheFile, json);
                }
                return  res;
            } catch (UnsupportedEncodingException e) {
                return Response.error(new ParseError(e));
            } catch (JsonSyntaxException e) {
                return Response.error(new ParseError(e));
            }
        }
    }

    public static void updateToFile(String fileName, String response){
        if(null == response || TextUtils.isEmpty(fileName)){
            return;
        }
        File f = new File(fileName);
        if(f.exists()){
            f.delete();
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            fos.write(response.getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            if(fos != null){
                try {
                    fos.close();
                    fos = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static StringBuilder readCacheFromFile(String filePath){
        StringBuilder sb = new StringBuilder();
        File f = new File(filePath);
        if(f.exists() == false){
            return  sb;
        }

        FileInputStream fin = null;
        try {
            fin = new FileInputStream(new File(filePath));
            byte []buffer = new byte[4096*2];
            int len = -1;
            while((len = fin.read(buffer, 0, 4096*2)) > 0){
                sb.append(buffer);
            }
            buffer = null;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            if(fin != null){
                try {
                    fin.close();
                    fin = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return sb;
    }
}