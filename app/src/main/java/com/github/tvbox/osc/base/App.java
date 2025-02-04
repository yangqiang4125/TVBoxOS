package com.github.tvbox.osc.base;

import android.app.Activity;
import androidx.multidex.MultiDexApplication;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.js.JSEngine;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;
import com.p2p.P2PClass;
import com.github.tvbox.osc.util.ScreenUtils;
import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public class App extends MultiDexApplication {
    private static App instance;

    private static P2PClass p;
    public static String burl;
    private static String dashData;
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initParams();
        // OKGo
        OkGoHelper.init(); //台标获取
        EpgUtil.init();
        // 初始化Web服务器
        ControlManager.init(this);
        //初始化数据库
        AppDataManager.init();
        LoadSir.beginBuilder()
                .addCallback(new EmptyCallback())
                .addCallback(new LoadingCallback())
                .commit();
        AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                .setSupportDP(false)
                .setSupportSP(false)
                .setSupportSubunits(Subunits.MM);
        PlayerHelper.init();
        JSEngine.getInstance().create();
        FileUtils.cleanPlayerCache();
    }

    private void initParams() {
        // Hawk
        Hawk.init(this).build();
        Hawk.put(HawkConfig.DEBUG_OPEN, false);
        if (!Hawk.contains(HawkConfig.MY_DEVICEID)) {//deviceId
            String deviceId = ApiConfig.getUuid();
            if(ScreenUtils.isTv(instance)){
                deviceId = "TV"+deviceId;
                Hawk.put(HawkConfig.HOME_REC_STYLE, true);
            }
            Hawk.put(HawkConfig.MY_DEVICEID, deviceId);
        }
        if (!Hawk.contains(HawkConfig.PLAY_TYPE)) {//播放器
            Hawk.put(HawkConfig.PLAY_TYPE, 1);
        }
        if (!Hawk.contains(HawkConfig.API_URL)) {
            Hawk.put(HawkConfig.API_URL, ApiConfig._api);
        }
        if (!Hawk.contains(HawkConfig.IJK_CODEC)) {
            Hawk.put(HawkConfig.IJK_CODEC,"硬解码");
        }
        if (!Hawk.contains(HawkConfig.MY_VIDEO_DETAIL)) {
            Hawk.put(HawkConfig.MY_VIDEO_DETAIL,"yes");
        }
        if (!Hawk.contains(HawkConfig.TIME_FLAG)) {
            Hawk.put(HawkConfig.TIME_FLAG,true);
        }
        if (!Hawk.contains(HawkConfig.PLAY_RENDER)) {
            Hawk.put(HawkConfig.PLAY_RENDER,1);
        }
    }

    public static App getInstance() {
        return instance;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        JSEngine.getInstance().destroy();
    }


    private VodInfo vodInfo;
    public void setVodInfo(VodInfo vodinfo){
        this.vodInfo = vodinfo;
    }
    public VodInfo getVodInfo(){
        return this.vodInfo;
    }

    public static P2PClass getp2p() {
        try {
            if (p == null) {
                p = new P2PClass(instance.getExternalCacheDir().getAbsolutePath());
            }
            return p;
        } catch (Exception e) {
            LOG.e(e.toString());
            return null;
        }
    }

    public Activity getCurrentActivity() {
        return AppManager.getInstance().currentActivity();
    }

    public void setDashData(String data) {
        dashData = data;
    }
    public String getDashData() {
        return dashData;
    }
}