package com.somust.yyteam.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.somust.yyteam.R;
import com.somust.yyteam.adapter.FriendRequestAdapter;
import com.somust.yyteam.adapter.FriendRequestAdapter.Callback;
import com.somust.yyteam.adapter.SearchUserAdapter;
import com.somust.yyteam.bean.AllUser;
import com.somust.yyteam.bean.FriendRequest;
import com.somust.yyteam.bean.FriendRequestUser;
import com.somust.yyteam.bean.TeamFriend;
import com.somust.yyteam.bean.TeamNews;
import com.somust.yyteam.bean.User;
import com.somust.yyteam.constant.Constant;
import com.somust.yyteam.constant.ConstantUrl;
import com.somust.yyteam.utils.log.L;
import com.somust.yyteam.utils.log.T;
import com.somust.yyteam.view.refreshview.RefreshLayout;
import com.yy.http.okhttp.OkHttpUtils;
import com.yy.http.okhttp.callback.BitmapCallback;

import com.yy.http.okhttp.callback.StringCallback;


import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.MediaType;


/**
 * 好友请求Activity
 */
public class FriendRequestActivity extends Activity implements SwipeRefreshLayout.OnRefreshListener, Callback {
    ImageView returnView;
    TextView titleName;


    private static final String TAG = "FriendRequestActivity:";
    private RefreshLayout refresh_view;
    private ListView friendRequestListview;


    private List<FriendRequestUser> friendRequests;   //添加好友的请求列表

    /**
     * 保存网络获取的图片集合
     */
    private Bitmap[] portraitBitmaps;

    /**
     * 全部用户的转化数据
     */
    private List<AllUser> allUsers;  //通过 friendRequests portraitBitmaps合并的数据
    private User user;

    private ProgressDialog dialog;
    /**
     * 搜索结果列表adapter
     */
    private FriendRequestAdapter friendRequestAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_request);

        T.isShow = false;  //关闭toast
        L.isDebug = false;  //关闭Log

        Intent intent = this.getIntent();
        user = (User) intent.getSerializableExtra("user");
        obtainFriendRequest(user.getUserPhone(), "insert"); //获取请求列表（加请求用户的个人信息）

        initView();
        initListener();
    }


    private void initView() {
        returnView = (ImageView) findViewById(R.id.id_title_back);
        titleName = (TextView) findViewById(R.id.actionbar_name);
        titleName.setText("好友添加请求");

        //刷新相关初始化
        friendRequestListview = (ListView) findViewById(R.id.request_friend_list);

        //初始化刷新
        refresh_view = (RefreshLayout) findViewById(R.id.refresh_view);
        refresh_view.setColorSchemeResources(R.color.color_bule2, R.color.color_bule, R.color.color_bule2, R.color.color_bule3);


    }

    private void initData() {

        friendRequestAdapter = new FriendRequestAdapter(this, allUsers, this);
        friendRequestListview.setAdapter(friendRequestAdapter);

    }

    private void initListener() {
        returnView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        refresh_view.setOnRefreshListener(this);
    }

    /**
     * 同意按钮点击事件
     * @param v
     */
    @Override
    public void agreeClick(View v) {
        //改变请求表状态
        UpdateRequestFriend(allUsers.get((Integer) v.getTag()).getUserPhone(), user.getUserPhone(), "agree");

        //互相添加为好友
        addFriend(user.getUserPhone(),allUsers.get((Integer) v.getTag()).getUserPhone());
        addFriend(allUsers.get((Integer) v.getTag()).getUserPhone(),user.getUserPhone());



    }

    /**
     * 拒绝按钮点击事件
     * @param v
     */
    @Override
    public void refuseClick(View v) {
        //改变请求表状态
        UpdateRequestFriend(allUsers.get((Integer) v.getTag()).getUserPhone(), user.getUserPhone(), "refuse");
    }


    /**
     * 改变添加好友请求状态的网络请求（同意或拒绝）
     */
    private void UpdateRequestFriend(final String requestPhone, final String receivePhone, final String state) {
        L.v(TAG, "同意添加");
        L.v(TAG, requestPhone);
        dialog = ProgressDialog.show(this, "提示", Constant.mProgressDialog_success, true, true);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //发起同意请求
                OkHttpUtils
                        .post()
                        .url(ConstantUrl.friendUrl + ConstantUrl.updateFriendRequest_interface)
                        .addParams("requestPhoneNumber", requestPhone)
                        .addParams("receivePhone", receivePhone)
                        .addParams("friendState", state)
                        .build()
                        .execute(new MyUpdateRequestFriendCallback());
            }
        }, 2000);//2秒后执行Runnable中的run方法

    }

    /**
     * 改变添加好友请求状态的网络回调
     */
    public class MyUpdateRequestFriendCallback extends StringCallback {
        @Override
        public void onError(Call call, Exception e, int id) {
            dialog.cancel();//关闭圆形进度条
            e.printStackTrace();
            L.v(TAG, "请求失败");
            T.testShowShort(FriendRequestActivity.this, Constant.mProgressDialog_error);
            L.v(e.getMessage());
        }

        @Override
        public void onResponse(String response, int id) {
            dialog.cancel();//关闭圆形进度条
            L.v(response);
            L.v(TAG, "请求成功");
            T.testShowShort(FriendRequestActivity.this, Constant.mMessage_success);

            //刷新
            if (allUsers != null){
                allUsers.clear();
                obtainFriendRequest(user.getUserPhone(), "insert");
                friendRequestAdapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * 互相加入好友的网络请求
     *
     */
    private void addFriend(String userPhone,String friendPhone) {
        OkHttpUtils
                .post()
                .url(ConstantUrl.friendUrl + ConstantUrl.addFriend_interface)
                .addParams("userId", userPhone)
                .addParams("friendPhone", friendPhone)
                .build()
                .execute(new MyAddFriendCallback());
    }

    /**
     * 加好友的回调
     */
    public class MyAddFriendCallback extends StringCallback{

        @Override
        public void onError(Call call, Exception e, int id) {
            L.v(TAG, "请求失败");
            e.printStackTrace();
        }

        @Override
        public void onResponse(String response, int id) {
            L.v(TAG, "请求成功");
            L.v(response);
        }
    }



    /**
     * 通过自己receivePhone获取请求列表
     */
    private void obtainFriendRequest(String receivePhone, String friendState) {
        OkHttpUtils
                .post()
                .url(ConstantUrl.friendUrl + ConstantUrl.obtainFriendRequest_interface)
                .addParams("receivePhone", receivePhone)
                .addParams("friendState", friendState)
                .build()
                .execute(new MyFriendRequestBack());
    }


    /**
     * 回调
     */
    public class MyFriendRequestBack extends StringCallback {
        @Override
        public void onError(Call call, Exception e, int id) {

            e.printStackTrace();
            L.e(TAG, "onError:" + e.getMessage());
            T.testShowShort(FriendRequestActivity.this, "获取失败,服务器正在维护中");
        }

        @Override
        public void onResponse(String response, int id) {

            if (response.equals("[]")) {
                T.testShowShort(FriendRequestActivity.this, "无好友请求");
            } else {

                T.testShowShort(FriendRequestActivity.this, "好友请求获取成功");
                L.v(TAG, "onResponse:" + response);
                Gson gson = new Gson();
                friendRequests = gson.fromJson(response, new TypeToken<List<FriendRequestUser>>() {
                }.getType());

                L.v(TAG, friendRequests.toString());
                portraitBitmaps = new Bitmap[friendRequests.size()];
                //获取请求用户的头像
                for (int i = 0; i < friendRequests.size(); i++) {
                    obtainImage(friendRequests.get(i).getRequestPhone().getUserImage(), i);
                }


            }
        }
    }


    /**
     * 获取网络图片请求，并将网络图片显示到imageview中去(如果是多次请求，需要一个bitmap数组)
     *
     * @param url 每次请求的Url
     * @param i   需要保存在bitmaps的对应位置
     */
    public void obtainImage(String url, final int i) {
        OkHttpUtils
                .get()
                .url(url)
                .tag(this)
                .build()
                .connTimeOut(20000)
                .readTimeOut(20000)
                .writeTimeOut(20000)
                .execute(new BitmapCallback() {
                    @Override
                    public void onError(Call call, Exception e, int id) {
                        L.e("onError:" + e.getMessage());
                    }

                    @Override
                    public void onResponse(Bitmap bitmap, int id) {
                        L.v("TAG", "请求图片");
                        portraitBitmaps[i] = bitmap;
                        //网络请求成功后
                        //初始化搜索结果数据
                        allUsers = new ArrayList<>();
                        Transformation(allUsers, portraitBitmaps);

                        initData();
                    }
                });
    }


    /**
     * 转换
     */
    public void Transformation(List<AllUser> mAllUser, Bitmap[] portraitBitmaps) {
        for (int i = 0; i < portraitBitmaps.length; i++) {
            AllUser allUser = new AllUser();
            allUser.setUserId(friendRequests.get(i).getRequestPhone().getUserId());
            allUser.setUserPhone(friendRequests.get(i).getRequestPhone().getUserPhone());
            allUser.setUserNickname(friendRequests.get(i).getRequestPhone().getUserNickname());
            allUser.setUserPassword(friendRequests.get(i).getRequestPhone().getUserPassword());
            allUser.setUserSex(friendRequests.get(i).getRequestPhone().getUserSex());
            allUser.setUserToken(friendRequests.get(i).getRequestPhone().getUserToken());
            allUser.setUserImage(portraitBitmaps[i]);
            mAllUser.add(allUser);
        }
        L.v(TAG, allUsers.toString());

    }


    /**
     * 下拉刷新的回调
     */
    @Override
    public void onRefresh() {
        refresh_view.postDelayed(new Runnable() {

            @Override
            public void run() {
                // 更新数据  更新完后调用该方法结束刷新
                obtainFriendRequest(user.getUserPhone(), "insert");
                if (allUsers != null){
                    allUsers.clear();
                    obtainFriendRequest(user.getUserPhone(), "insert");
                    refresh_view.setRefreshing(false);
                    friendRequestAdapter.notifyDataSetChanged();
                }else {
                    refresh_view.setRefreshing(false);
                }
            }
        }, 1500);
    }


}
