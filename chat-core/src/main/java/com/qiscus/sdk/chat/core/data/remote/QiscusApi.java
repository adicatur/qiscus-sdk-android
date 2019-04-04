/*
 * Copyright (c) 2016 Qiscus.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qiscus.sdk.chat.core.data.remote;

import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qiscus.sdk.chat.core.BuildConfig;
import com.qiscus.sdk.chat.core.QiscusCore;
import com.qiscus.sdk.chat.core.R;
import com.qiscus.sdk.chat.core.data.model.QiscusAccount;
import com.qiscus.sdk.chat.core.data.model.QiscusChatRoom;
import com.qiscus.sdk.chat.core.data.model.QiscusComment;
import com.qiscus.sdk.chat.core.data.model.QiscusNonce;
import com.qiscus.sdk.chat.core.data.model.QiscusRoomMember;
import com.qiscus.sdk.chat.core.event.QiscusClearCommentsEvent;
import com.qiscus.sdk.chat.core.event.QiscusCommentSentEvent;
import com.qiscus.sdk.chat.core.util.BuildVersionUtil;
import com.qiscus.sdk.chat.core.util.QiscusDateUtil;
import com.qiscus.sdk.chat.core.util.QiscusErrorLogger;
import com.qiscus.sdk.chat.core.util.QiscusFileUtil;
import com.qiscus.sdk.chat.core.util.QiscusLogger;
import com.qiscus.sdk.chat.core.util.QiscusTextUtil;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.Util;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;
import rx.Emitter;
import rx.Observable;
import rx.exceptions.OnErrorThrowable;

/**
 * Created on : August 18, 2016
 * Author     : zetbaitsu
 * Name       : Zetra
 * GitHub     : https://github.com/zetbaitsu
 */
public enum QiscusApi {
    INSTANCE;
    private final OkHttpClient httpClient;
    private final Api api;
    private String baseUrl;

    QiscusApi() {
        baseUrl = QiscusCore.getAppServer();

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(this::headersInterceptor)
                .addInterceptor(makeLoggingInterceptor(QiscusCore.getChatConfig().isEnableLog()))
                .build();

        api = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build()
                .create(Api.class);
    }

    public static QiscusApi getInstance() {
        return INSTANCE;
    }

    private Response headersInterceptor(Interceptor.Chain chain) throws IOException {
        Request req = chain.request().newBuilder()
                .addHeader("QISCUS_SDK_APP_ID", QiscusCore.getAppId())
                .addHeader("QISCUS_SDK_TOKEN", QiscusCore.hasSetupUser() ? QiscusCore.getToken() : "")
                .addHeader("QISCUS_SDK_USER_EMAIL", QiscusCore.hasSetupUser() ? QiscusCore.getQiscusAccount().getEmail() : "")
                .addHeader("QISCUS_SDK_VERSION", "ANDROID_" + BuildConfig.VERSION_NAME)
                .addHeader("QISCUS_SDK_PLATFORM", "ANDROID")
                .addHeader("QISCUS_SDK_DEVICE_BRAND", Build.MANUFACTURER)
                .addHeader("QISCUS_SDK_DEVICE_MODEL", Build.MODEL)
                .addHeader("QISCUS_SDK_DEVICE_OS_VERSION", BuildVersionUtil.OS_VERSION_NAME)
                .build();
        return chain.proceed(req);
    }

    private HttpLoggingInterceptor makeLoggingInterceptor(boolean isDebug) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(isDebug ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);
        return logging;
    }

    public Observable<QiscusNonce> requestNonce() {
        return api.requestNonce().map(QiscusApiParser::parseNonce);
    }

    public Observable<QiscusAccount> login(String token) {
        return api.login(token).map(QiscusApiParser::parseQiscusAccount);
    }

    public Observable<QiscusAccount> loginOrRegister(String email, String password, String username, String avatarUrl) {
        return loginOrRegister(email, password, username, avatarUrl, null);
    }

    public Observable<QiscusAccount> loginOrRegister(String email, String password, String username, String avatarUrl, JSONObject extras) {
        return api.loginOrRegister(email, password, username, avatarUrl, extras == null ? null : extras.toString())
                .map(QiscusApiParser::parseQiscusAccount);
    }

    public Observable<QiscusAccount> updateProfile(String username, String avatarUrl) {
        return updateProfile(username, avatarUrl, null);
    }

    public Observable<QiscusAccount> updateProfile(String username, String avatarUrl, JSONObject extras) {
        return api.updateProfile(QiscusCore.getToken(), username, avatarUrl, extras == null ? null : extras.toString())
                .map(QiscusApiParser::parseQiscusAccount);
    }

    public Observable<QiscusChatRoom> getChatRoom(String withEmail, String distinctId, JSONObject options) {
        return api.createOrGetChatRoom(QiscusCore.getToken(), Collections.singletonList(withEmail), distinctId,
                options == null ? null : options.toString())
                .map(QiscusApiParser::parseQiscusChatRoom);
    }

    public Observable<QiscusChatRoom> createGroupChatRoom(String name, List<String> emails, String avatarUrl, JSONObject options) {
        return api.createGroupChatRoom(QiscusCore.getToken(), name, emails, avatarUrl, options == null ? null : options.toString())
                .map(QiscusApiParser::parseQiscusChatRoom);
    }

    public Observable<QiscusChatRoom> getGroupChatRoom(String uniqueId, String name, String avatarUrl, JSONObject options) {
        return api.createOrGetGroupChatRoom(QiscusCore.getToken(), uniqueId, name, avatarUrl, options == null ? null : options.toString())
                .map(QiscusApiParser::parseQiscusChatRoom);
    }

    public Observable<QiscusChatRoom> getChatRoom(long roomId) {
        return api.getChatRooms(QiscusCore.getToken(), Collections.singletonList(roomId), new ArrayList<>(), true)
                .map(QiscusApiParser::parseQiscusChatRoomInfo)
                .flatMap(Observable::from)
                .take(1);
    }

    public Observable<Pair<QiscusChatRoom, List<QiscusComment>>> getChatRoomComments(long roomId) {
        return api.getChatRoom(QiscusCore.getToken(), roomId)
                .map(QiscusApiParser::parseQiscusChatRoomWithComments);
    }

    public Observable<List<QiscusChatRoom>> getChatRooms(int page, int limit, boolean showMembers) {
        return api.getChatRooms(QiscusCore.getToken(), page, limit, showMembers)
                .map(QiscusApiParser::parseQiscusChatRoomInfo);
    }

    public Observable<List<QiscusChatRoom>> getChatRooms(List<Long> roomIds, List<String> uniqueIds, boolean showMembers) {
        return api.getChatRooms(QiscusCore.getToken(), roomIds, uniqueIds, showMembers)
                .map(QiscusApiParser::parseQiscusChatRoomInfo);
    }

    public Observable<QiscusComment> getComments(long roomId, long lastCommentId) {
        return api.getComments(QiscusCore.getToken(), roomId, lastCommentId, false)
                .flatMap(jsonElement -> Observable.from(jsonElement.getAsJsonObject().get("results")
                        .getAsJsonObject().get("comments").getAsJsonArray()))
                .map(jsonElement -> QiscusApiParser.parseQiscusComment(jsonElement, roomId));
    }

    public Observable<QiscusComment> getCommentsAfter(long roomId, long lastCommentId) {
        return api.getComments(QiscusCore.getToken(), roomId, lastCommentId, true)
                .flatMap(jsonElement -> Observable.from(jsonElement.getAsJsonObject().get("results")
                        .getAsJsonObject().get("comments").getAsJsonArray()))
                .map(jsonElement -> QiscusApiParser.parseQiscusComment(jsonElement, roomId));
    }

    public Observable<QiscusComment> postComment(QiscusComment qiscusComment) {
        QiscusCore.getChatConfig().getCommentSendingInterceptor().sendComment(qiscusComment);
        return api.postComment(QiscusCore.getToken(), qiscusComment.getMessage(),
                qiscusComment.getRoomId(), qiscusComment.getUniqueId(), qiscusComment.getRawType(),
                qiscusComment.getExtraPayload(), qiscusComment.getExtras() == null ? null :
                        qiscusComment.getExtras().toString())
                .map(jsonElement -> {
                    JsonObject jsonComment = jsonElement.getAsJsonObject()
                            .get("results").getAsJsonObject().get("comment").getAsJsonObject();
                    qiscusComment.setId(jsonComment.get("id").getAsLong());
                    qiscusComment.setCommentBeforeId(jsonComment.get("comment_before_id").getAsInt());

                    //timestamp is in nano seconds format, convert it to milliseconds by divide it
                    long timestamp = jsonComment.get("unix_nano_timestamp").getAsLong() / 1000000L;
                    qiscusComment.setTime(new Date(timestamp));
                    QiscusLogger.print("Sent Comment...");
                    return qiscusComment;
                })
                .doOnNext(comment -> EventBus.getDefault().post(new QiscusCommentSentEvent(comment)));
    }

    public Observable<QiscusComment> sync(long lastCommentId) {
        return api.sync(QiscusCore.getToken(), lastCommentId)
                .onErrorReturn(throwable -> {
                    QiscusErrorLogger.print("Sync", throwable);
                    return null;
                })
                .filter(jsonElement -> jsonElement != null)
                .flatMap(jsonElement -> Observable.from(jsonElement.getAsJsonObject().get("results")
                        .getAsJsonObject().get("comments").getAsJsonArray()))
                .map(jsonElement -> {
                    JsonObject jsonComment = jsonElement.getAsJsonObject();
                    return QiscusApiParser.parseQiscusComment(jsonElement, jsonComment.get("room_id").getAsLong());
                });
    }

    public Observable<QiscusComment> sync() {
        QiscusComment latestComment = QiscusCore.getDataStore().getLatestComment();
        if (latestComment == null || !QiscusTextUtil.getString(R.string.qiscus_today)
                .equals(QiscusDateUtil.toTodayOrDate(latestComment.getTime()))) {
            return Observable.empty();
        }
        return sync(latestComment.getId());
    }

    public Observable<Uri> uploadFile(File file, ProgressListener progressListener) {
        return Observable.create(subscriber -> {
            long fileLength = file.length();

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("token", QiscusCore.getToken())
                    .addFormDataPart("file", file.getName(),
                            new CountingFileRequestBody(file, totalBytes -> {
                                int progress = (int) (totalBytes * 100 / fileLength);
                                progressListener.onProgress(progress);
                            }))
                    .build();

            Request request = new Request.Builder()
                    .url(baseUrl + "api/v2/mobile/upload")
                    .post(requestBody).build();

            try {
                Response response = httpClient.newCall(request).execute();
                JSONObject responseJ = new JSONObject(response.body().string());
                String result = responseJ.getJSONObject("results").getJSONObject("file").getString("url");

                subscriber.onNext(Uri.parse(result));
                subscriber.onCompleted();
            } catch (IOException | JSONException e) {
                QiscusErrorLogger.print("UploadFile", e);
                subscriber.onError(e);
            }
        }, Emitter.BackpressureMode.BUFFER);
    }

    public Observable<File> downloadFile(String url, String fileName, ProgressListener progressListener) {
        return Observable.create(subscriber -> {
            InputStream inputStream = null;
            FileOutputStream fos = null;
            try {
                Request request = new Request.Builder().url(url).build();

                Response response = httpClient.newCall(request).execute();

                File output = new File(QiscusFileUtil.generateFilePath(fileName));
                fos = new FileOutputStream(output.getPath());
                if (!response.isSuccessful()) {
                    throw new IOException();
                } else {
                    ResponseBody responseBody = response.body();
                    long fileLength = responseBody.contentLength();

                    inputStream = responseBody.byteStream();
                    byte[] buffer = new byte[4096];
                    long total = 0;
                    int count;
                    while ((count = inputStream.read(buffer)) != -1) {
                        total += count;
                        long totalCurrent = total;
                        if (fileLength > 0) {
                            progressListener.onProgress((totalCurrent * 100 / fileLength));
                        }
                        fos.write(buffer, 0, count);
                    }
                    fos.flush();

                    subscriber.onNext(output);
                    subscriber.onCompleted();
                }
            } catch (Exception e) {
                throw OnErrorThrowable.from(OnErrorThrowable.addValueAsLastCause(e, url));
            } finally {
                try {
                    if (fos != null) {
                        fos.close();
                    }
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException ignored) {
                    //Do nothing
                }
            }
        }, Emitter.BackpressureMode.BUFFER);
    }

    public Observable<QiscusChatRoom> updateChatRoom(long roomId, String name, String avatarUrl, JSONObject options) {
        return api.updateChatRoom(QiscusCore.getToken(), roomId, name, avatarUrl, options == null ? null : options.toString())
                .map(QiscusApiParser::parseQiscusChatRoom)
                .doOnNext(qiscusChatRoom -> QiscusCore.getDataStore().addOrUpdate(qiscusChatRoom));
    }

    public Observable<Void> updateCommentStatus(long roomId, long lastReadId, long lastReceivedId) {
        return api.updateCommentStatus(QiscusCore.getToken(), roomId, lastReadId, lastReceivedId)
                .map(jsonElement -> null);
    }

    public Observable<Void> registerFcmToken(String fcmToken) {
        return api.registerFcmToken(QiscusCore.getToken(), "android", fcmToken)
                .map(jsonElement -> null);
    }

    @Deprecated
    public Observable<List<QiscusComment>> searchComments(String query, long lastCommentId) {
        return searchComments(query, 0, lastCommentId);
    }

    @Deprecated
    public Observable<List<QiscusComment>> searchComments(String query, long roomId, long lastCommentId) {
        return Observable.error(new RuntimeException("Please use local data search!, we are currently working on search"));

    }

    public Observable<Void> clearCommentsByRoomIds(List<Long> roomIds) {
        return api.getChatRooms(QiscusCore.getToken(), roomIds, null, false)
                .map(JsonElement::getAsJsonObject)
                .map(jsonObject -> jsonObject.get("results").getAsJsonObject())
                .map(jsonObject -> jsonObject.get("rooms_info").getAsJsonArray())
                .flatMap(Observable::from)
                .map(JsonElement::getAsJsonObject)
                .map(jsonObject -> jsonObject.get("unique_id").getAsString())
                .toList()
                .flatMap(this::clearCommentsByRoomUniqueIds);
    }

    public Observable<Void> clearCommentsByRoomUniqueIds(List<String> roomUniqueIds) {
        return api.clearChatRoomMessages(QiscusCore.getToken(), roomUniqueIds)
                .map(JsonElement::getAsJsonObject)
                .map(jsonResponse -> jsonResponse.get("results").getAsJsonObject())
                .map(jsonResults -> jsonResults.get("rooms").getAsJsonArray())
                .flatMap(Observable::from)
                .map(JsonElement::getAsJsonObject)
                .doOnNext(json -> {
                    long roomId = json.get("id").getAsLong();
                    if (QiscusCore.getDataStore().deleteCommentsByRoomId(roomId)) {
                        EventBus.getDefault().post(new QiscusClearCommentsEvent(roomId));
                    }
                })
                .toList()
                .map(qiscusChatRooms -> null);
    }

    public Observable<List<QiscusComment>> deleteComments(List<String> commentUniqueIds,
                                                          boolean isHardDelete) {
        // isDeleteForEveryone => akan selalu true, karena deleteForMe deprecated
        return api.deleteComments(QiscusCore.getToken(), commentUniqueIds, true, isHardDelete)
                .flatMap(jsonElement -> Observable.from(jsonElement.getAsJsonObject().get("results")
                        .getAsJsonObject().get("comments").getAsJsonArray()))
                .map(jsonElement -> {
                    JsonObject jsonComment = jsonElement.getAsJsonObject();
                    return QiscusApiParser.parseQiscusComment(jsonElement, jsonComment.get("room_id").getAsLong());
                })
                .toList()
                .doOnNext(comments -> {
                    QiscusAccount account = QiscusCore.getQiscusAccount();
                    QiscusRoomMember actor = new QiscusRoomMember();
                    actor.setEmail(account.getEmail());
                    actor.setUsername(account.getUsername());
                    actor.setAvatar(account.getAvatar());

                    List<QiscusDeleteCommentHandler.DeletedCommentsData.DeletedComment> deletedComments = new ArrayList<>();
                    for (QiscusComment comment : comments) {
                        deletedComments.add(new QiscusDeleteCommentHandler.DeletedCommentsData.DeletedComment(comment.getRoomId(),
                                comment.getUniqueId()));
                    }

                    QiscusDeleteCommentHandler.DeletedCommentsData deletedCommentsData
                            = new QiscusDeleteCommentHandler.DeletedCommentsData();
                    deletedCommentsData.setActor(actor);
                    deletedCommentsData.setHardDelete(isHardDelete);
                    deletedCommentsData.setDeletedComments(deletedComments);

                    QiscusDeleteCommentHandler.handle(deletedCommentsData);
                });
    }

    public Observable<List<JSONObject>> getEvents(long startEventId) {
        return api.getEvents(QiscusCore.getToken(), startEventId)
                .flatMap(jsonElement -> Observable.from(jsonElement.getAsJsonObject().get("events").getAsJsonArray()))
                .map(jsonEvent -> {
                    try {
                        return new JSONObject(jsonEvent.toString());
                    } catch (JSONException e) {
                        return null;
                    }
                })
                .filter(jsonObject -> jsonObject != null)
                .doOnNext(QiscusPusherApi::handleNotification)
                .toList();
    }

    public Observable<Long> getTotalUnreadCount() {
        return api.getTotalUnreadCount(QiscusCore.getToken())
                .map(JsonElement::getAsJsonObject)
                .map(jsonResponse -> jsonResponse.get("results").getAsJsonObject())
                .map(jsonResults -> jsonResults.get("total_unread_count").getAsLong());
    }

    public Observable<QiscusChatRoom> addRoomMember(long roomId, List<String> emails) {
        return api.addRoomMember(QiscusCore.getToken(), roomId, emails)
                .flatMap(jsonElement -> getChatRoom(roomId));
    }

    public Observable<QiscusChatRoom> removeRoomMember(long roomId, List<String> emails) {
        return api.removeRoomMember(QiscusCore.getToken(), roomId, emails)
                .flatMap(jsonElement -> getChatRoom(roomId));
    }

    public Observable<QiscusAccount> blockUser(String userEmail) {
        return api.blockUser(QiscusCore.getToken(), userEmail)
                .map(JsonElement::getAsJsonObject)
                .map(jsonResponse -> jsonResponse.getAsJsonObject("results"))
                .map(jsonResults -> jsonResults.getAsJsonObject("user"))
                .map(jsonAccount -> QiscusApiParser.parseQiscusAccount(jsonAccount, false));
    }

    public Observable<QiscusAccount> unblockUser(String userEmail) {
        return api.unblockUser(QiscusCore.getToken(), userEmail)
                .map(JsonElement::getAsJsonObject)
                .map(jsonResponse -> jsonResponse.getAsJsonObject("results"))
                .map(jsonResults -> jsonResults.getAsJsonObject("user"))
                .map(jsonAccount -> QiscusApiParser.parseQiscusAccount(jsonAccount, false));
    }

    public Observable<List<QiscusAccount>> getBlockedUsers() {
        return getBlockedUsers(0, 100);
    }

    public Observable<List<QiscusAccount>> getBlockedUsers(long page, long limit) {
        return api.getBlockedUsers(QiscusCore.getToken(), page, limit)
                .map(JsonElement::getAsJsonObject)
                .map(jsonResponse -> jsonResponse.getAsJsonObject("results"))
                .map(jsonResults -> jsonResults.getAsJsonArray("blocked_users"))
                .flatMap(Observable::from)
                .map(JsonElement::getAsJsonObject)
                .map(jsonAccount -> QiscusApiParser.parseQiscusAccount(jsonAccount, false))
                .toList();
    }

    public Observable<List<QiscusRoomMember>> getRoomMembers(String roomUniqueId) {
        return getRoomMembers(roomUniqueId, 0, null, null, null);
    }

    public Observable<List<QiscusRoomMember>> getRoomMembers(String roomUniqueId, int offset, String orderKey,
                                                             String sorting, MetaRoomMembersListener metaRoomMembersListener) {
        return getRoomMembers(roomUniqueId, offset, orderKey, sorting, null, metaRoomMembersListener);
    }

    public Observable<List<QiscusRoomMember>> getRoomMembers(String roomUniqueId, int offset, String orderKey, String sorting,
                                                             String userName, MetaRoomMembersListener metaRoomMembersListener) {
        return api.getRoomParticipants(QiscusCore.getToken(), roomUniqueId, offset, orderKey, sorting, userName)
                .map(JsonElement::getAsJsonObject)
                .map(jsonResponse -> jsonResponse.getAsJsonObject("results"))
                .doOnNext(jsonResults -> {
                    JsonObject meta = jsonResults.getAsJsonObject("meta");
                    if (metaRoomMembersListener != null) {
                        metaRoomMembersListener.onMetaReceived(
                                meta.get("current_offset").getAsInt(),
                                meta.get("per_page").getAsInt(),
                                meta.get("total").getAsInt()
                        );
                    }
                })
                .map(jsonResults -> jsonResults.getAsJsonArray("participants"))
                .flatMap(Observable::from)
                .map(JsonElement::getAsJsonObject)
                .map(QiscusApiParser::parseQiscusRoomMember)
                .toList();
    }

    public Observable<String> getMqttBaseUrl() {
        return Observable.create(subscriber -> {
            Request request = new Request.Builder()
                    .url(BuildConfig.BASE_URL_MQTT_LB)
                    .build();

            try {
                Response response = httpClient.newCall(request).execute();
                JSONObject jsonResponse = new JSONObject(response.body().string());
                String node = jsonResponse.getString("node");

                subscriber.onNext(node);
                subscriber.onCompleted();

            } catch (JSONException | IOException e) {
                QiscusErrorLogger.print("getMqttBaseUrl", e);
                subscriber.onError(e);
            }
        }, Emitter.BackpressureMode.BUFFER);
    }

    public Observable<HashMap<String, List<QiscusRoomMember>>> getCommentInfo(long commentId) {
        return api.getCommentReceipt(QiscusCore.getToken(), commentId)
                .map(JsonElement::getAsJsonObject)
                .map(jsonResponse -> jsonResponse.getAsJsonObject("results"))
                .map(QiscusApiParser::parseQiscusCommentInfo);
    }

    public Observable<List<QiscusAccount>> getUsers(String query) {
        return getUsers(0, 100, query);
    }

    public Observable<List<QiscusAccount>> getUsers(long page, long limit,
                                                       String query) {
        return api.getUserList(QiscusCore.getToken(), page, limit, "username asc", query)
                .map(JsonElement::getAsJsonObject)
                .map(jsonResponse -> jsonResponse.getAsJsonObject("results"))
                .map(jsonResults -> jsonResults.getAsJsonArray("users"))
                .flatMap(Observable::from)
                .map(JsonElement::getAsJsonObject)
                .map(jsonAccount -> QiscusApiParser.parseQiscusAccount(jsonAccount, false))
                .toList();
    }

    private interface Api {

        @POST("api/v2/auth/nonce")
        Observable<JsonElement> requestNonce();

        @FormUrlEncoded
        @POST("api/v2/auth/verify_identity_token")
        Observable<JsonElement> login(
                @Field("identity_token") String token
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/login_or_register")
        Observable<JsonElement> loginOrRegister(
                @Field("email") String email,
                @Field("password") String password,
                @Field("username") String username,
                @Field("avatar_url") String avatarUrl,
                @Field("extras") String extras
        );

        @FormUrlEncoded
        @PATCH("api/v2/mobile/my_profile")
        Observable<JsonElement> updateProfile(
                @Field("token") String token,
                @Field("name") String name,
                @Field("avatar_url") String avatarUrl,
                @Field("extras") String extras
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/get_or_create_room_with_target")
        Observable<JsonElement> createOrGetChatRoom(
                @Field("token") String token,
                @Field("emails[]") List<String> emails,
                @Field("distinct_id") String distinctId,
                @Field("options") String options
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/create_room")
        Observable<JsonElement> createGroupChatRoom(
                @Field("token") String token,
                @Field("name") String name,
                @Field("participants[]") List<String> emails,
                @Field("avatar_url") String avatarUrl,
                @Field("options") String options
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/get_or_create_room_with_unique_id")
        Observable<JsonElement> createOrGetGroupChatRoom(
                @Field("token") String token,
                @Field("unique_id") String uniqueId,
                @Field("name") String name,
                @Field("avatar_url") String avatarUrl,
                @Field("options") String options
        );

        @GET("api/v2/mobile/get_room_by_id")
        Observable<JsonElement> getChatRoom(
                @Query("token") String token,
                @Query("id") long roomId
        );

        @GET("api/v2/mobile/load_comments")
        Observable<JsonElement> getComments(
                @Query("token") String token,
                @Query("topic_id") long roomId,
                @Query("last_comment_id") long lastCommentId,
                @Query("after") boolean after
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/post_comment")
        Observable<JsonElement> postComment(
                @Field("token") String token,
                @Field("comment") String message,
                @Field("topic_id") long roomId,
                @Field("unique_temp_id") String uniqueId,
                @Field("type") String type,
                @Field("payload") String payload,
                @Field("extras") String extras
        );

        @GET("api/v2/mobile/sync")
        Observable<JsonElement> sync(
                @Query("token") String token,
                @Query("last_received_comment_id") long lastCommentId
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/update_room")
        Observable<JsonElement> updateChatRoom(
                @Field("token") String token,
                @Field("id") long id,
                @Field("room_name") String name,
                @Field("avatar_url") String avatarUrl,
                @Field("options") String options
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/update_comment_status")
        Observable<JsonElement> updateCommentStatus(
                @Field("token") String token,
                @Field("room_id") long roomId,
                @Field("last_comment_read_id") long lastReadId,
                @Field("last_comment_received_id") long lastReceivedId
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/set_user_device_token")
        Observable<JsonElement> registerFcmToken(
                @Field("token") String token,
                @Field("device_platform") String devicePlatform,
                @Field("device_token") String fcmToken
        );

        @Deprecated
        @POST("api/v2/mobile/search_messages")
        Observable<JsonElement> searchComments(
                @Query("token") String token,
                @Query("query") String query,
                @Query("room_id") long roomId,
                @Query("last_comment_id") long lastCommentId
        );

        @GET("api/v2/mobile/user_rooms")
        Observable<JsonElement> getChatRooms(
                @Query("token") String token,
                @Query("page") int page,
                @Query("limit") int limit,
                @Query("show_participants") boolean showParticipants
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/rooms_info")
        Observable<JsonElement> getChatRooms(
                @Field("token") String token,
                @Field("room_id[]") List<Long> roomIds,
                @Field("room_unique_id[]") List<String> roomUniqueIds,
                @Field("show_participants") boolean showParticipants
        );

        @DELETE("api/v2/mobile/clear_room_messages")
        Observable<JsonElement> clearChatRoomMessages(
                @Query("token") String token,
                @Query("room_channel_ids[]") List<String> roomUniqueIds
        );

        @DELETE("api/v2/mobile/delete_messages")
        Observable<JsonElement> deleteComments(
                @Query("token") String token,
                @Query("unique_ids[]") List<String> commentUniqueIds,
                @Query("is_delete_for_everyone") boolean isDeleteForEveryone,
                @Query("is_hard_delete") boolean isHardDelete
        );

        @GET("api/v2/mobile/sync_event")
        Observable<JsonElement> getEvents(
                @Query("token") String token,
                @Query("start_event_id") long startEventId
        );

        @GET("api/v2/sdk/total_unread_count")
        Observable<JsonElement> getTotalUnreadCount(
                @Query("token") String token
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/add_room_participants")
        Observable<JsonElement> addRoomMember(
                @Field("token") String token,
                @Field("room_id") long roomId,
                @Field("emails[]") List<String> emails
        );

        @FormUrlEncoded
        @POST("api/v2/mobile/remove_room_participants")
        Observable<JsonElement> removeRoomMember(
                @Field("token") String token,
                @Field("room_id") long roomId,
                @Field("emails[]") List<String> emails
        );

        @FormUrlEncoded
        @POST("/api/v2/mobile/block_user")
        Observable<JsonElement> blockUser(
                @Field("token") String token,
                @Field("user_email") String userEmail
        );

        @FormUrlEncoded
        @POST("/api/v2/mobile/unblock_user")
        Observable<JsonElement> unblockUser(
                @Field("token") String token,
                @Field("user_email") String userEmail
        );


        @GET("/api/v2/mobile/get_blocked_users")
        Observable<JsonElement> getBlockedUsers(
                @Query("token") String token,
                @Query("page") long page,
                @Query("limit") long limit
        );

        @GET("/api/v2/sdk/room_participants")
        Observable<JsonElement> getRoomParticipants(
                @Query("token") String token,
                @Query("room_unique_id") String roomId,
                @Query("offset") int offset,
                @Query("order_by_key_name") String orderKey,
                @Query("sorting") String sorting,
                @Query("user_name") String userName
        );

        @GET("/api/v2/sdk/comment_receipt")
        Observable<JsonElement> getCommentReceipt(
                @Query("token") String token,
                @Query("comment_id") long commentId
        );

        @GET("/api/v2/mobile/get_user_list")
        Observable<JsonElement> getUserList(
                @Query("token") String token,
                @Query("page") long page,
                @Query("limit") long limit,
                @Query("order_query") String orderQuery,
                @Query("query") String query
        );
    }

    public interface MetaRoomMembersListener {
        void onMetaReceived(int currentOffset, int perPage, int total);
    }

    public interface ProgressListener {
        void onProgress(long total);
    }

    private static class CountingFileRequestBody extends RequestBody {
        private static final int SEGMENT_SIZE = 2048;
        private static final int IGNORE_FIRST_NUMBER_OF_WRITE_TO_CALL = 0;
        private final File file;
        private final ProgressListener progressListener;
        private int numWriteToCall = -1;

        private CountingFileRequestBody(File file, ProgressListener progressListener) {
            this.file = file;
            this.progressListener = progressListener;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse("application/octet-stream");
        }

        @Override
        public long contentLength() throws IOException {
            return file.length();
        }

        @Override
        public void writeTo(@NonNull BufferedSink sink) throws IOException {
            numWriteToCall++;

            Source source = null;
            try {
                source = Okio.source(file);
                long total = 0;
                long read;

                while ((read = source.read(sink.buffer(), SEGMENT_SIZE)) != -1) {
                    total += read;
                    sink.flush();

                    /**
                     * When we use HttpLoggingInterceptor,
                     * we have issue with progress update not valid.
                     * So we must check, first call is to HttpLoggingInterceptor
                     * second call is to request
                     */
                    if (numWriteToCall > IGNORE_FIRST_NUMBER_OF_WRITE_TO_CALL) {
                        progressListener.onProgress(total);
                    }

                }
            } finally {
                Util.closeQuietly(source);
            }
        }
    }
}
