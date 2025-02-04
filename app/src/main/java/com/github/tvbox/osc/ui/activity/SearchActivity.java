package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchAdapter;
import com.github.tvbox.osc.ui.dialog.RemoteDialog;
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.util.js.JSEngine;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SearchActivity extends BaseActivity {
    private LinearLayout llLayout;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewWord;
    SourceViewModel sourceViewModel;
    private EditText etSearch;
    private TextView tvSearch;
    private TextView tvClear;
    private SearchKeyboard keyboard;
    private TextView tvAddress;
    private ImageView ivQRCode;
    private SearchAdapter searchAdapter;
    private PinyinAdapter wordAdapter;
    private String searchTitle = "";
    private String wdPic = "";
    private TextView tvSearchCheckboxBtn;

    private static HashMap<String, String> mCheckSources = null;
    private SearchCheckboxDialog mSearchCheckboxDialog = null;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }
    public static void start(Activity activity, String name, String pic,boolean clean) {
        Intent newIntent = new Intent(activity, SearchActivity.class);
        newIntent.putExtra("title", name);
        newIntent.putExtra("pic", pic);
        if(clean)newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(newIntent);
    }
    public static void start(Activity activity, String name, String pic) {
        start(activity,name,pic,true);
    }

    private static Boolean hasKeyBoard;
    private static Boolean isSearchBack;
    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
        hasKeyBoard = true;
        isSearchBack = false;
    }

    /*
     * 禁止软键盘
     * @param activity Activity
     */
    public static void disableKeyboard(Activity activity) {
        hasKeyBoard = false;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    /*
     * 启用软键盘
     * @param activity Activity
     */
    public static void enableKeyboard(Activity activity) {
        hasKeyBoard = true;
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    public void openSystemKeyBoard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(this.getCurrentFocus(), InputMethodManager.SHOW_FORCED);
    }

    private List<Runnable> pauseRunnable = null;

    @Override
    protected void onResume() {
        super.onResume();
        if (pauseRunnable != null && pauseRunnable.size() > 0) {
            searchExecutorService = Executors.newFixedThreadPool(5);
            allRunCount.set(pauseRunnable.size());
            for (Runnable runnable : pauseRunnable) {
                searchExecutorService.execute(runnable);
            }
            pauseRunnable.clear();
            pauseRunnable = null;
        }
        if (hasKeyBoard) {
            tvSearch.requestFocus();
            tvSearch.requestFocusFromTouch();
        }else {
            if(!isSearchBack){
                etSearch.requestFocus();
                etSearch.requestFocusFromTouch();
            }
        }
    }

    private void initView() {
        EventBus.getDefault().register(this);
        llLayout = findViewById(R.id.llLayout);
        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvSearchCheckboxBtn = findViewById(R.id.tvSearchCheckboxBtn);
        tvClear = findViewById(R.id.tvClear);
        tvAddress = findViewById(R.id.tvAddress);
        ivQRCode = findViewById(R.id.ivQRCode);
        mGridView = findViewById(R.id.mGridView);
        keyboard = findViewById(R.id.keyBoardRoot);
        mGridViewWord = findViewById(R.id.mGridViewWord);
        mGridViewWord.setHasFixedSize(true);
        mGridViewWord.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        wordAdapter = new PinyinAdapter();
        mGridViewWord.setAdapter(wordAdapter);
        wordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                    Bundle bundle = new Bundle();
                    bundle.putString("title", wordAdapter.getItem(position));
                    jumpActivity(FastSearchActivity.class, bundle);
                }else {
                    search(wordAdapter.getItem(position));
                }
            }
        });
        mGridView.setHasFixedSize(true);
        // lite
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0)
            mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
            // with preview
        else
            mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, 3));
        searchAdapter = new SearchAdapter();
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    try {
                        if (searchExecutorService != null) {
                            pauseRunnable = searchExecutorService.shutdownNow();
                            searchExecutorService = null;
                            JSEngine.getInstance().stopAll();
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                    hasKeyBoard = false;
                    isSearchBack = true;

                    String key = video.sourceKey;
                    DetailActivity.start(SearchActivity.this, key, video.id, searchTitle,wdPic);
                }
            }
        });

        tvSearch.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String isname = Hawk.get(HawkConfig.MY_NAME,"");
                if (isname.isEmpty()) {
                    Hawk.put(HawkConfig.MY_NAME, "yes");
                    //Toast.makeText(mContext, "开启搜索标题", Toast.LENGTH_SHORT).show();
                }else {
                    Hawk.put(HawkConfig.MY_NAME, "");
                    //Toast.makeText(mContext, "关闭搜索标题", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                hasKeyBoard = true;
                String wd = etSearch.getText().toString().trim();
                if (!TextUtils.isEmpty(wd)) {
                    wd.trim();
                    if (wd.startsWith("pwd")) {
                        String s = wd.replaceFirst("pwd", "");
                        Hawk.put(HawkConfig.MY_PWD, s);
                        Toast.makeText(mContext, "密码设置为："+s, Toast.LENGTH_SHORT).show();
                    }else if (wd.startsWith("apkv")) {
                        String s = wd.replaceFirst("apkv", "v");
                        Hawk.put(HawkConfig.MY_APKV, s);
                        Toast.makeText(mContext, "apkv值为："+s, Toast.LENGTH_SHORT).show();
                    }else if (wd.startsWith("site")) {
                        String s = wd.replaceFirst("site", "");
                        Hawk.put(HawkConfig.API_URL, s);
                        Hawk.put(HawkConfig.MY_SITE, s);
                        Toast.makeText(mContext, "site值为："+s, Toast.LENGTH_SHORT).show();
                    }else if (wd.startsWith("alert")) {
                        String s = wd.replaceFirst("alert", "");
                        String str = Hawk.get("my_"+s,"无");
                        etSearch.setText(str);
                        Toast.makeText(mContext, str, Toast.LENGTH_SHORT).show();
                    }else if (wd.startsWith("put")) {
                        String s = wd.replaceFirst("put", "");
                        String [] arr = s.split(" ");
                        String value = s.replace(arr[0] + " ", "");
                        Hawk.put("my_"+arr[0], value);
                        Toast.makeText(mContext, s, Toast.LENGTH_SHORT).show();
                    }else if (wd.startsWith("jar")) {
                        String s = wd.replaceFirst("jar", "");
                        Hawk.put(HawkConfig.MY_JAR, s);
                        Toast.makeText(mContext, "jar值为："+s, Toast.LENGTH_SHORT).show();
                    }else if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                        Bundle bundle = new Bundle();
                        bundle.putString("title", wd);
                        jumpActivity(FastSearchActivity.class, bundle);
                    }else {
                        search(wd);
                    }
                } else {
                    Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                }
            }
        });

        tvClear.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String vd = Hawk.get(HawkConfig.MY_VIDEO_DETAIL,"");
                if (vd.isEmpty()) {
                    Hawk.put(HawkConfig.MY_VIDEO_DETAIL, "yes");
                    Toast.makeText(mContext, "开启视频详情", Toast.LENGTH_SHORT).show();
                }else {
                    Hawk.put(HawkConfig.MY_VIDEO_DETAIL, "");
                    Toast.makeText(mContext, "关闭视频详情", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                etSearch.setText("");
            }
        });
//        etSearch.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                enableKeyboard(SearchActivity.this);
//                openSystemKeyBoard();//再次尝试拉起键盘
//                SearchActivity.this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
//            }
//        });

//        etSearch.setOnFocusChangeListener(tvSearchFocusChangeListener);
        keyboard.setOnSearchKeyListener(new SearchKeyboard.OnSearchKeyListener() {
            @Override
            public void onSearchKey(int pos, String key) {
                if (pos > 1) {
                    String text = etSearch.getText().toString().trim();
                    text += key;
                    etSearch.setText(text);
                    if (text.length() > 0) {
                        loadRec(text);
                    }
                } else if (pos == 1) {
                    String text = etSearch.getText().toString().trim();
                    if (text.length() > 0) {
                        text = text.substring(0, text.length() - 1);
                        etSearch.setText(text);
                    }
                    if (text.length() > 0) {
                        loadRec(text);
                    }
                } else if (pos == 0) {
                    RemoteDialog remoteDialog = new RemoteDialog(mContext);
                    remoteDialog.show();
                }
            }
        });
        setLoadSir(llLayout);
        tvSearchCheckboxBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                jumpActivity(SettingActivity.class);
                return true;
            }
        });
        tvSearchCheckboxBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSearchCheckboxDialog == null) {
                    List<SourceBean> allSourceBean = ApiConfig.get().getSourceBeanList();
                    List<SourceBean> searchAbleSource = new ArrayList<>();
                    for(SourceBean sourceBean : allSourceBean) {
                        if (sourceBean.isSearchable()) {
                            searchAbleSource.add(sourceBean);
                        }
                    }
                    mSearchCheckboxDialog = new SearchCheckboxDialog(SearchActivity.this, searchAbleSource, mCheckSources);
                }
                mSearchCheckboxDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                });
                mSearchCheckboxDialog.show();
            }
        });
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
    }

    /**
     * 拼音联想
     */
    private void loadRec(String key) {
//        OkGo.<String>get("https://s.video.qq.com/smartbox")
//                .params("plat", 2)
//                .params("ver", 0)
//                .params("num", 20)
//                .params("otype", "json")
//                .params("query", key)
//                .execute(new AbsCallback<String>() {
//                    @Override
//                    public void onSuccess(Response<String> response) {
//                        try {
//                            ArrayList<String> hots = new ArrayList<>();
//                            String result = response.body();
//                            JsonObject json = JsonParser.parseString(result.substring(result.indexOf("{"), result.lastIndexOf("}") + 1)).getAsJsonObject();
//                            JsonArray itemList = json.get("item").getAsJsonArray();
//                            for (JsonElement ele : itemList) {
//                                JsonObject obj = (JsonObject) ele;
//                                hots.add(obj.get("word").getAsString().trim().replaceAll("<|>|《|》|-", "").split(" ")[0]);
//                            }
//                            wordAdapter.setNewData(hots);
//                        } catch (Throwable th) {
//                            th.printStackTrace();
//                        }
//                    }
//
//                    @Override
//                    public String convertResponse(okhttp3.Response response) throws Throwable {
//                        return response.body().string();
//                    }
//                });
        OkGo.<String>get("https://suggest.video.iqiyi.com/")
                .params("if", "mobile")
                .params("key", key)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> hots = new ArrayList<>();
                            String result = response.body();
                            JsonObject json = JsonParser.parseString(result).getAsJsonObject();
                            JsonArray itemList = json.get("data").getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                JsonObject obj = (JsonObject) ele;
                                hots.add(obj.get("name").getAsString().trim().replaceAll("<|>|《|》|-", ""));
                            }
                            wordAdapter.setNewData(hots);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    private void initData() {
        refreshQRCode();
        initCheckedSourcesForSearch();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("title")) {
            Bundle bundle = intent.getExtras();
            String title = bundle.getString("title", "");
            wdPic = bundle.getString("pic", "");
            etSearch.setText(title);
            showLoading();
            if(title.equals("未找到")||title.equals("无名称")) title = "";
            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                bundle.putString("title", title);
                jumpActivity(FastSearchActivity.class, bundle);
            }else {
                search(title);
            }
        }
        // 加载热词
        OkGo.<String>get("https://node.video.qq.com/x/api/hot_search")
//        OkGo.<String>get("https://api.web.360kan.com/v1/rank")
//                .params("cat", "1")
                .params("channdlId", "0")
                .params("_", System.currentTimeMillis())
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> hots = new ArrayList<>();
                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonObject().get("mapResult").getAsJsonObject().get("0").getAsJsonObject().get("listInfo").getAsJsonArray();
//                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                JsonObject obj = (JsonObject) ele;
                                hots.add(obj.get("title").getAsString().trim().replaceAll("<|>|《|》|-", "").split(" ")[0]);
                            }
                            wordAdapter.setNewData(hots);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });

    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("远程搜索使用手机/电脑扫描下面二维码或者直接浏览器访问地址\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address + "search.html", 300, 300));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            showLoading();
            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false)){
                Bundle bundle = new Bundle();
                bundle.putString("title", title);
                jumpActivity(FastSearchActivity.class, bundle);
            }else{
                search(title);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    public static void setCheckedSourcesForSearch(HashMap<String,String> checkedSources) {
        mCheckSources = checkedSources;
    }

    private void search(String title) {
        cancel();
        showLoading();
        this.searchTitle = title;
        etSearch.setText(title);
        mGridView.setVisibility(View.INVISIBLE);
        searchAdapter.setNewData(new ArrayList<>());
        searchResult();
    }

    private ExecutorService searchExecutorService = null;
    private AtomicInteger allRunCount = new AtomicInteger(0);

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JSEngine.getInstance().stopAll();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            searchAdapter.setNewData(new ArrayList<>());
            allRunCount.set(0);
        }
        searchExecutorService = Executors.newFixedThreadPool(5);
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
            allRunCount.incrementAndGet();
        }
        if (siteKey.size() <= 0) {
            Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
            showEmpty();
            return;
        }
        for (String key : siteKey) {
            searchExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getSearch(key, searchTitle);
                }
            });
        }
    }

    private boolean matchSearchResult(String name, String searchTitle) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false;
        searchTitle = searchTitle.trim();
        String[] arr = searchTitle.split("\\s+");
        int matchNum = 0;
        for(String one : arr) {
            if (name.contains(one)) matchNum++;
        }
        return matchNum == arr.length ? true : false;
    }

    private void searchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                if (matchSearchResult(video.name, searchTitle)) data.add(video);
            }
            if (searchAdapter.getData().size() > 0) {
                searchAdapter.addData(data);
            } else {
                showSuccess();
                mGridView.setVisibility(View.VISIBLE);
                searchAdapter.setNewData(data);
            }
        }

        int count = allRunCount.decrementAndGet();
        if (count <= 0) {
            if (searchAdapter.getData().size() <= 0) {
                showEmpty();
            }
            cancel();
        }
    }


    private void cancel() {
        OkGo.getInstance().cancelTag("search");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel();
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JSEngine.getInstance().stopAll();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        EventBus.getDefault().unregister(this);
    }
}
