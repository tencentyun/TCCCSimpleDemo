package com.tencent.debug;

import android.os.Handler;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
/**
 * 注意：该方案仅适用于调试Demo，正式上线前请将 UserSig 计算代码和密钥迁移到您的后台服务器上，以避免加密密钥泄露导致的流量盗用。
 */
public class GenerateTestUserSig {

    /***
     * 视频客服应用配置入口 APP ID
     */
    public static final String VIDEO_CHANNELID = "PLACEHOLDER";

    /***
     * 音频客服应用配置入口 APP ID
     */
    public static final String AUDIO_CHANNELID = "PLACEHOLDER";


    /**
     * 腾讯云 SDKAppId，需要替换为您自己账号下的 SDKAppId。
     * 进入腾讯云联络中心[控制台](https://console.cloud.tencent.com/ccc ) 创建应用，即可看到 SDKAppId，
     * 它是腾讯云用于区分客户的唯一标识。
     */
    public static final int SDKAPPID = 0;
    /**
     * 计算签名用的加密密钥ID，[查看秘钥](https://console.cloud.tencent.com/cam/capi)
     * 注意：该方案仅适用于调试Demo，正式上线前请将 UserSig 计算代码和密钥迁移到您的后台服务器上，以避免加密密钥泄露导致的流量盗用。
     * 文档：https://cloud.tencent.com/document/product/679/58260
     */
    public static final String SECRETID = "PLACEHOLDER";

    /**
     * 计算签名用的加密密钥Key，[查看秘钥](https://console.cloud.tencent.com/cam/capi)
     * 注意：该方案仅适用于调试Demo，正式上线前请将 UserSig 计算代码和密钥迁移到您的后台服务器上，以避免加密密钥泄露导致的流量盗用。
     * 文档：https://cloud.tencent.com/document/product/679/58260
     */
    public static final String SECRETKEY = "PLACEHOLDER";

    /**
     * 签名过期时间，不能超过一个小时
     * <p>
     * 时间单位：秒
     * 默认时间：30 x 60 = 1800 = 30 分钟
     */
    private static final int EXPIRETIME = 1800;

    /**
     * 计算 UserSig 签名
     *
     * @note: 请不要将如下代码发布到您的线上正式版本的 App 中，原因如下：
     *
     * 本文件中的代码虽然能够正确计算出 UserSig，但仅适合快速调通 SDK 的基本功能，不适合线上产品，
     * 这是因为客户端代码中的 SECRETKEY 很容易被反编译逆向破解，尤其是 Web 端的代码被破解的难度几乎为零。
     * 一旦您的密钥泄露，攻击者就可以计算出正确的 UserSig 来盗用您的腾讯云流量。
     *
     * 正确的做法是将 UserSig 的计算代码和加密密钥放在您的业务服务器上，然后由 App 按需向您的服务器获取实时算出的 UserSig。
     * 由于破解服务器的成本要高于破解客户端 App，所以服务器计算的方案能够更好地保护您的加密密钥。
     *
     * 文档：https://cloud.tencent.com/document/product/679/58260
     */
    public static String genTestUserSig(String userId,UserSigCallBack callBack) {
        return GenTLSSignature(SDKAPPID, userId, EXPIRETIME, SECRETID, SECRETKEY,callBack);
    }

    /**
     * 生成票据
     * @param sdkappid    应用的 appid
     * @param userId      用户 id
     * @param expire      有效期，单位是秒
     * @return 如果出错，会返回为空，或者有异常打印，成功返回有效的票据
     */
    /**
     * 生成票据
     * @param sdkappid 应用的 appid
     * @param userId   用户 id
     * @param expire   有效期，单位是秒
     * @param secretId 秘钥ID
     * @param secretKey 秘钥Key
     * @return 如果出错，会返回为空，或者有异常打印，成功返回有效的票据
     */
    private static String GenTLSSignature(long sdkappid, String userId, long expire, String secretId, String secretKey,UserSigCallBack callBack)  {
        final MediaType         MEDIA_JSON        = MediaType.parse("application/json; charset=utf-8");
        JSONObject sigDoc = new JSONObject();
        try {
            sigDoc.put("secretId", secretId);
            sigDoc.put("secretKey", secretKey);
            sigDoc.put("SdkAppId", sdkappid);
            sigDoc.put("ExpiredTime", expire);
            sigDoc.put("Uid", userId);
            sigDoc.put("ClientData","");
            if (sdkappid == 1400264214){
                sigDoc.put("isTest",true);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Request request = new Request.Builder()
                .url("https://tccc-gavin-5g19jovqc598f12b-1258344699.ap-guangzhou.app.tcloudbase.com/tccc")
                .post(RequestBody.create(MEDIA_JSON, sigDoc.toString()))
                .build();
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
        okHttpClient.newCall(request).enqueue(new Callback() {

            Handler mainHandler = new Handler();
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(()->{
                    callBack.onError(-1,e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                try {
                    JSONObject jsonObject = new JSONObject(body);
                    int errorCode = jsonObject.getInt("errorCode");
                    String errorMessage = jsonObject.getString("errorMessage");
                    if(errorCode!=0){
                        mainHandler.post(()-> {
                            callBack.onError(errorCode, errorMessage);
                        });
                        return;
                    }
                    String userSig = jsonObject.getString("UserSig");
                    mainHandler.post(()->{
                        callBack.onSuccess(userSig);
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                    mainHandler.post(()->{
                        callBack.onError(-1,e.getMessage());
                    });
                }
            }
        });
        return "";
    }

    public  interface  UserSigCallBack {
        /**
         * 成功时回调
         * @param value 回调值
         */
        void onSuccess(String value);

        /**
         * 出错时回调
         * @param code 错误码，详细描述请参见错误码表
         * @param desc 错误描述
         */
        void onError(int code,String desc);
    }
}
