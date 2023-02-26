package com.v2ray.ang;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.v2ray.ang.service.V2RayServiceManager;
import com.v2ray.ang.util.MessageUtil;

/**
 * @author MrLiu
 * @date 2020/5/11
 * desc 广播接收者
 */
public class NetworkReceiver extends BroadcastReceiver {
    private boolean inStop;
    public static boolean isRunning;
    @Override
    public void onReceive(Context context, Intent intent) {
        // 监听网络连接，包括wifi和移动数据的打开和关闭,以及连接上可用的连接都会接到监听
        // 特殊注意：如果if条件生效，那么证明当前是有连接wifi或移动网络的，如果有业务逻辑最好把esle场景酌情考虑进去！
        if(inStop)
        {
            return;
        }
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            //获取联网状态的NetworkInfo对象
            NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (info != null) {
                if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                    //如果当前的网络连接成功并且网络连接可用
                    if (NetworkInfo.State.CONNECTED == info.getState() || NetworkInfo.State.DISCONNECTED == info.getState()) {
                        if(isRunning)
                        {
                            inStop = true;
                            MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {

                            }
                            V2RayServiceManager.startV2Ray(context);
                            inStop = false;
                        }
                    }
                }

            }
        }
    }
}
