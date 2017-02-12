package com.lantern.lantern.dump;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by yky on 2017. 2. 12..
 */

public class DumpFileManager {
    private final String TAG = "DumpFileManager";
    private final String DUMP_FILE_NAME = "ryla_dump";

    private Context mContext;

    private JSONObject dumpJson;

    private static DumpFileManager dumpFileManager;

    private DumpFileManager(Context context) {
        mContext = context;
    }

    public static DumpFileManager getInstance(Context context) {
        if(dumpFileManager == null) {
            dumpFileManager = new DumpFileManager(context.getApplicationContext());
        }

        return dumpFileManager;
    }

    //덤프 파일 생성하기 및 덤프 헤더 파일에 저장하기
    public void initDumpFile() {
        //덤프 헤더 파일 작성
        dumpJson = getDumpHeader();

        //덤프 헤더를 파일에 저장
        saveDumpFile(dumpJson.toString());
    }

    //덤프 헤더 JSON 생성하기
    private JSONObject getDumpHeader() {
        JSONObject dumpData = new JSONObject();
        JSONObject deviceInfo = new JSONObject();
        JSONArray dumpContents = new JSONArray();
        try {
            dumpData.put("launch_time", System.currentTimeMillis());
            dumpData.put("dump_interval", mContext.getSharedPreferences("pref", MODE_PRIVATE).getInt("dump_term", 1000));
            dumpData.put("package_name", mContext.getPackageName());

            deviceInfo.put("os", Build.VERSION.RELEASE);
            deviceInfo.put("app", getAppVersion());
            deviceInfo.put("device", Build.DEVICE);
            deviceInfo.put("brand", Build.BRAND);

            dumpData.put("device_info", deviceInfo);
            dumpData.put("data", dumpContents);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return dumpData;
    }

    private String getAppVersion() {
        String version = "";
        try {
            PackageInfo i = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            version = i.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return version;
    }

    //덤프 파일에 저장하기
    //JSON 형식의 String을 파일에 저장
    private void saveDumpFile(String dumpFileString) {
        FileOutputStream outputStream;

        try {
            outputStream = mContext.openFileOutput(DUMP_FILE_NAME, Context.MODE_PRIVATE);
            outputStream.write(dumpFileString.getBytes());
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //덤프 파일 읽어오기
    private String readDumpFile() {
        FileInputStream inputStream;
        StringBuilder builder = new StringBuilder();

        try {
            String inputStr;
            inputStream = mContext.openFileInput(DUMP_FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            inputStr = reader.readLine();

            while(inputStr != null) {
                //buffer.append(inputStr + "\n");
                builder.append(inputStr + "\n");
                inputStr = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return builder.toString();
    }

    //type이 res인 dump data 파일에 저장하기
    public void saveResDumpData(ShallowDumpData shallowDumpData) {
        JSONObject resDumpJson = getResDumpData(shallowDumpData);
        JSONObject preSavedDumpData;

        try {
            preSavedDumpData = new JSONObject(readDumpFile());
            preSavedDumpData.getJSONArray("data").put(resDumpJson);
            saveDumpFile(preSavedDumpData.toString());

            Log.d(TAG, new JSONObject(readDumpFile()).toString(2));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //type이 res인 dump JSON object 생성하기
    private JSONObject getResDumpData(ShallowDumpData shallowDumpData) {
        JSONObject resData = new JSONObject();
        JSONObject durationData = new JSONObject();
        JSONObject cpuData = new JSONObject();
        JSONObject memoryData = new JSONObject();
        JSONArray activiyData = new JSONArray();
        JSONObject networkData = new JSONObject();

        try {
            //type
            resData.put("type", "res");

            //duration_time
            durationData.put("start", shallowDumpData.getStartTime());
            durationData.put("end", shallowDumpData.getEndTime());
            resData.put("duration_time", durationData);

            //cpu
            String[] labelCpu = {"user", "nice", "system", "idle", "iowait", "irq", "softirq", "steal", "guest", "guest_nice"};
            for(int i=0;i<labelCpu.length;i++) {
                try {
                    cpuData.put(labelCpu[i], shallowDumpData.getCpuInfo().get(i));
                } catch(IndexOutOfBoundsException e) {
                    cpuData.put(labelCpu[i], -1);
                }
            }
            resData.put("cpu", cpuData);

            //memory
            String[] labelMemory = {"max", "total", "alloc", "free"};
            for(int i=0;i<labelMemory.length;i++) {
                memoryData.put(labelMemory[i], shallowDumpData.getMemoryInfo().get(i));
            }
            resData.put("memory", memoryData);

            //battery
            resData.put("battery", "battery stat");

            //activity_stack
            for(int i=0;i<shallowDumpData.getActivityStackInfo().size();i++) {
                activiyData.put(shallowDumpData.getActivityStackInfo().get(i));
            }
            resData.put("activity_stack", activiyData);

            //network_usage
            String[] labelNetwork = {"type", "rx", "tx"};
            for(int i=0;i<labelNetwork.length;i++) {
                if(i==0) {
                    networkData.put(labelNetwork[i], shallowDumpData.getNetworkUsageInfo().get(i));
                } else {
                    networkData.put(labelNetwork[i], Long.parseLong(shallowDumpData.getNetworkUsageInfo().get(i)));
                }
            }
            resData.put("network_usage", networkData);

            //thread_trace
            resData.put("thread_trace", "traced list");

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return resData;
    }
}