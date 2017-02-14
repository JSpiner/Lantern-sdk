package com.lantern.lantern;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Debug;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.lantern.lantern.dump.DumpFileManager;
import com.lantern.lantern.dump.ShallowDumpData;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static com.lantern.lantern.RYLA.isAppForeground;

/**
 * Created by YS on 2017-02-05.
 */

public class RylaInstrumentation extends Instrumentation {

    // window manager
    private WindowManager mWindowManager;

    // linear layout will use to detect touch event
    private LinearLayout touchLayout;

    static List<Long> usages1 = new ArrayList<>();

    private static int dumpTerm;

    public RylaInstrumentation() {
        SharedPreferences pref = RYLA.getInstance().getContext().getSharedPreferences("pref", MODE_PRIVATE);
        dumpTerm = pref.getInt("dump_term", 10000);
    }

    // Instrumentation 초기화 실행
    private static RylaInstrumentation rylaInstrumentation = new RylaInstrumentation();

    // Instrumentation Alive Check
    private boolean isResThreadAlive = false;

    public static RylaInstrumentation getInstance() {
        if (rylaInstrumentation == null) {
            rylaInstrumentation = new RylaInstrumentation();
        }
        return rylaInstrumentation;
    }

    public void excute() {
        isResThreadAlive = true;

        DumpFileManager.getInstance(RYLA.getInstance().getContext()).initDumpFile();

        if (rylaInstrumentation != null) {
            rylaInstrumentation.start();
            startTouchTracing(RYLA.getInstance().getContext());
        }
    }

    public void stop() {
        isResThreadAlive = false;

        stopTouchTracing();

        if (rylaInstrumentation != null) {
            rylaInstrumentation.onDestroy();
        }
    }

    public boolean isResThreadAlive() {
        return isResThreadAlive;
    }

    public void setResThreadAlive(boolean isAlive) {
        isResThreadAlive = isAlive;

        // 터치
        if (isAlive) {
            startTouchTracing(RYLA.getInstance().getContext());
        } else {
            stopTouchTracing();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d("eunchan", "MyInstrumentation::onDestory() : I'm being destroeyd!!! O.M.G.");
    }

    @Override
    public void onStart() {
        while (true) {
            if (isResThreadAlive) {
                if (!isAppForeground()) {
                    isResThreadAlive = false;
                    stopTouchTracing();
                    continue;
                }

                //Dataset for dump file
                Long dumpStartTime, dumpEndTime;
                List<Long> cpuInfoList;
                List<Long> memoryInfoList;
                List<String> activityStackList = new ArrayList<>();
                List<String> networkUsageList;
                List<String> stackTraceInfo;

                // 시작시간
                dumpStartTime = System.currentTimeMillis();
                Log.d("DUMP TIME", "====== "+ dumpStartTime +" =======");

                // dumpTerm 마다 쓰레드 트레이싱으로 문제가 되는 부분을 한번에 확인 가능
                stackTraceInfo = RYLA.getInstance().getThreadTracing();

                for (Activity activity : RYLA.getInstance().getActivityList()) {
                    Log.d("ACTIVITIES", activity.getClass().getSimpleName());
                    activityStackList.add(activity.getClass().getSimpleName());
                }

                // NETWORK USAGE INFO
                networkUsageList = getNetworkRxTxTracing();

                // MEMORY INFO
                Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
                Debug.getMemoryInfo(memoryInfo);
                ResDumpData resDumpData = new ResDumpData(
                        Debug.getNativeHeapSize(),
                        Debug.getNativeHeapFreeSize(),
                        Debug.getPss(),
                        Debug.getLoadedClassCount(),
                        memoryInfo);

                resDumpData.printMemoryInfo();
                memoryInfoList = resDumpData.getMemoryInfoForDump();

                // top 방식 아닌 직접 가져오는 방식 사용
                cpuInfoList = readUsage3();

                // 종료시간
                dumpEndTime = System.currentTimeMillis();
                Log.d("DUMP TIME", "====== "+ dumpEndTime +" =======");

                //save res dump file
                DumpFileManager.getInstance(RYLA.getInstance().getContext()).saveDumpData(
                        new ShallowDumpData(
                                dumpStartTime,
                                dumpEndTime,
                                cpuInfoList,
                                memoryInfoList,
                                activityStackList,
                                networkUsageList,
                                stackTraceInfo
                        )
                );
            }
            try {
                Thread.sleep(dumpTerm);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private float readUsage() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            String[] toks = load.split(" +");  // Split on one or more spaces

            long idle1 = Long.parseLong(toks[4]);
            long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            try {
                Thread.sleep(360);
            } catch (Exception e) {
            }

            reader.seek(0);
            load = reader.readLine();
            reader.close();

            toks = load.split(" +");

            long idle2 = Long.parseLong(toks[4]);
            long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            return (float) (cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return 0;
    }

    private String readUsage2() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            List<String> toks = new LinkedList<String>(Arrays.asList(load.split(" +")));  // Split on one or more spaces
            toks.remove(0);

            List<Long> usages2 = new ArrayList<>();
            for (String token : toks) {
                usages2.add(Long.parseLong(token));
            }

            if (usages1.isEmpty()) {
                usages1 = usages2;
                return "init";
            }

            // user nice system idle iowait  irq  softirq steal guest guest_nice
            String rtn = "\nuser\tnice\tsystem\tidle\tiowait\tirq\tsoftirq\tsteal\tguest\tguest_nice\n";
            for (int i = 0; i < usages1.size(); i++) {
                rtn += (usages2.get(i) - usages1.get(i)) + "\t\t";
            }

            usages1 = usages2;
            return rtn;

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return "none";
    }

    private List<Long> readUsage3() {
        List<Long> cpuInfoList = new ArrayList<>();
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            List<String> toks = new LinkedList<String>(Arrays.asList(load.split(" +")));  // Split on one or more spaces
            toks.remove(0);

            List<Long> usages2 = new ArrayList<>();
            for (String token : toks) {
                usages2.add(Long.parseLong(token));
            }

            if (usages1.isEmpty()) {
                usages1 = usages2;
                cpuInfoList.add(-1L);
                return cpuInfoList;
            }

            // user nice system idle iowait  irq  softirq steal guest guest_nice
            String rtn = "\nuser\tnice\tsystem\tidle\tiowait\tirq\tsoftirq\tsteal\tguest\tguest_nice\n";
            for (int i = 0; i < usages1.size(); i++) {
                rtn += (usages2.get(i) - usages1.get(i)) + "\t\t";
                cpuInfoList.add(usages2.get(i) - usages1.get(i));
            }

            Log.d("CPU INFO", rtn);
            usages1 = usages2;

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return cpuInfoList;
    }

    public List<String> getNetworkRxTxTracing() {
        List<String> rxtxInfo = new ArrayList<>();

        ConnectivityManager connManager;
        connManager = (ConnectivityManager) RYLA.getInstance().getContext().getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo mNetwork = connManager.getActiveNetworkInfo();

        Log.d("NETWORK NAME", mNetwork.getTypeName());
        rxtxInfo.add(mNetwork.getTypeName());

        long mRX = TrafficStats.getMobileRxBytes();
        long mTX = TrafficStats.getMobileTxBytes();

        if (mRX == TrafficStats.UNSUPPORTED || mTX == TrafficStats.UNSUPPORTED) {
            Log.d("NETWORK USAGE", "지원안함");
            rxtxInfo.add("-1");
            rxtxInfo.add("-1");
        } else {
            Log.d("NETWORK USAGE", "Rx: " + mRX + ", Tx: " + mTX);
            rxtxInfo.add(Long.toString(mRX));
            rxtxInfo.add(Long.toString(mRX));
        }

        return rxtxInfo;
    }

    public void startTouchTracing(Context mApplication) {
        // 이방법으로 하면 ACTION 의 이름을 가져올수 없음

//        mWindowManager = (WindowManager) mApplication.getSystemService(WINDOW_SERVICE);
//        touchLayout = new LinearLayout(mApplication);
//
//        WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, WindowManager.LayoutParams.MATCH_PARENT);
//        touchLayout.setLayoutParams(params);
//        touchLayout.setOnTouchListener(touchListener);
//
//
//        WindowManager.LayoutParams params2 = new WindowManager.LayoutParams(
//                1,  // width
//                1,  // height
//                WindowManager.LayoutParams.TYPE_PHONE,
//                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
//                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
//                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
//                PixelFormat.TRANSPARENT
//        );
//        params.gravity = Gravity.LEFT | Gravity.TOP;
//        mWindowManager.addView(touchLayout, params2);
    }

    public void stopTouchTracing() {
//        if(mWindowManager != null) {
//            if(touchLayout != null) mWindowManager.removeView(touchLayout);
//        }
    }

    private View.OnTouchListener touchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.d("TOUCH INFO", event.toString());
            Log.d("TOUCH INFO", event.getX()+"("+event.getRawX()+")" +"/"+ event.getY()+"("+event.getRawY()+")");
            return false;
        }
    };
}
