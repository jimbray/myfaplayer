
#ifdef HAVE_CONFIG_H
# include "config.h"
#endif

#include <vlc/vlc.h>
#include <vlc_common.h>

#ifdef ANDROID

void faplayer_message_logcat(const char *fmt, ...);

#include <jni.h>

#include "libvlcjni.h"

#define NAME1(CLZ, FUN) Java_##CLZ##_##FUN
#define NAME2(CLZ, FUN) NAME1(CLZ, FUN)

#define NAME(FUN) NAME2(CLASS, FUN)

#define CLASS org_stagex_danmaku_player_VlcMediaPlayer
#define PREFIX "org/stagex/danmaku/player/"

JavaVM *gJVM = NULL;

static void *s_surface = 0;
static pthread_mutex_t s_surface_mutex;

static jclass clz_VlcEvent = 0;
static jfieldID f_VlcEvent_eventType = 0;
static jfieldID f_VlcEvent_booleanValue = 0;
static jfieldID f_VlcEvent_intValue = 0;
static jfieldID f_VlcEvent_longValue = 0;
static jfieldID f_VlcEvent_floatValue = 0;
static jfieldID f_VlcEvent_stringValue = 0;
static jmethodID m_VlcMediaPlayer_onVlcEvent = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
    gJVM = vm;
    pthread_mutex_init(&s_surface_mutex, 0);

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved)
{
    pthread_mutex_destroy(&s_surface_mutex);
}

void LockSurface()
{
    pthread_mutex_lock(&s_surface_mutex);
}

void *GetSurface()
{
    return s_surface;
}

void UnlockSurface()
{
    pthread_mutex_unlock(&s_surface_mutex);
}

JNIEXPORT int Java_org_stagex_danmaku_helper_SystemUtility_setenv(JNIEnv *env, jclass klz, jstring key, jstring val, jboolean overwrite)
{
    const char *key_utf8 = (*env)->GetStringUTFChars(env, key, NULL);
    const char *val_utf8 = (*env)->GetStringUTFChars(env, val, NULL);
    int err = setenv(key_utf8, val_utf8, overwrite);
    (*env)->ReleaseStringUTFChars(env, key, key_utf8);
    (*env)->ReleaseStringUTFChars(env, val, val_utf8);
    return err;
}

static int getIntValue(JNIEnv *env, jobject thiz, const char *name)
{
    jclass clz = (*env)->GetObjectClass(env, thiz);
    jfieldID field = (*env)->GetFieldID(env, clz, name, "I");
    int value = (*env)->GetIntField(env, thiz, field);
    (*env)->DeleteLocalRef(env, clz);
    return value;

}

static void setIntValue(JNIEnv *env, jobject thiz, const char *name, int value)
{
    jclass clz = (*env)->GetObjectClass(env, thiz);
    jfieldID field = (*env)->GetFieldID(env, clz, name, "I");
    (*env)->SetIntField(env, thiz, field, value);
    (*env)->DeleteLocalRef(env, clz);
}

static void vlc_event_callback(const libvlc_event_t *ev, void *data)
{
    JNIEnv *env;
    jobject obj_VlcMediaPlayer = (jobject) data;
    jobject obj_VlcEvent;

    if ((*gJVM)->AttachCurrentThread(gJVM, &env, 0) < 0)
    {
        faplayer_message_logcat("AttachCurrentThread() failed");
        return;
    }
    obj_VlcEvent = (*env)->AllocObject(env, clz_VlcEvent);
    if (!obj_VlcEvent)
    {
        faplayer_message_logcat("AllocObject() failed");
        return;
    }
    (*env)->SetIntField(env, obj_VlcEvent, f_VlcEvent_eventType, ev->type);
    switch (ev->type) {
    case libvlc_MediaDurationChanged: {
        int64_t duration = ev->u.media_duration_changed.new_duration;
        (*env)->SetIntField(env, obj_VlcEvent, f_VlcEvent_longValue, (jlong) duration);
        break;
    }
    case libvlc_MediaParsedChanged: {
        int status = ev->u.media_parsed_changed.new_status;
        (*env)->SetBooleanField(env, obj_VlcEvent, f_VlcEvent_booleanValue, status > 0);
        break;
    }
    case libvlc_MediaStateChanged: {
        int state = ev->u.media_state_changed.new_state;
        (*env)->SetIntField(env, obj_VlcEvent, f_VlcEvent_intValue, state);
        break;
    }
    case libvlc_MediaPlayerBuffering: {
        float cache = ev->u.media_player_buffering.new_cache;
        (*env)->SetFloatField(env, obj_VlcEvent, f_VlcEvent_floatValue, cache);
        break;
    }
    case libvlc_MediaPlayerTimeChanged: {
        int64_t time = ev->u.media_player_time_changed.new_time;
        (*env)->SetLongField(env, obj_VlcEvent, f_VlcEvent_longValue, (jlong) time);
        break;
    }
    case libvlc_MediaPlayerPositionChanged: {
        float position = ev->u.media_player_position_changed.new_position;
        (*env)->SetFloatField(env, obj_VlcEvent, f_VlcEvent_floatValue, position);
        break;
    }
    case libvlc_MediaPlayerSeekableChanged: {
        int seekable = ev->u.media_player_seekable_changed.new_seekable;
        (*env)->SetBooleanField(env, obj_VlcEvent, f_VlcEvent_booleanValue, seekable > 0);
        break;
    }
    case libvlc_MediaPlayerPausableChanged: {
        int pausable = ev->u.media_player_pausable_changed.new_pausable;
        (*env)->SetBooleanField(env, obj_VlcEvent, f_VlcEvent_booleanValue, pausable > 0);
        break;
    }
    case libvlc_MediaPlayerTitleChanged: {
        int title = ev->u.media_player_title_changed.new_title;
        (*env)->SetIntField(env, obj_VlcEvent, f_VlcEvent_intValue, title);
        break;
    }
    case libvlc_MediaPlayerSnapshotTaken: {
        char *p = ev->u.media_player_snapshot_taken.psz_filename;
        jstring path = (*env)->NewStringUTF(env, p);
        (*env)->SetObjectField(env, obj_VlcEvent, f_VlcEvent_stringValue, path);
        break;
    }
    case libvlc_MediaPlayerLengthChanged: {
        int64_t length = ev->u.media_player_length_changed.new_length;
        (*env)->SetLongField(env, obj_VlcEvent, f_VlcEvent_longValue, (jlong) length);
        break;
    }
    default:
        break;
    }
    (*env)->CallVoidMethod(env, obj_VlcMediaPlayer, m_VlcMediaPlayer_onVlcEvent, obj_VlcEvent);
    (*env)->DeleteLocalRef(env, obj_VlcEvent);
    /* EXPLAIN: this is called in pthread wrapper routines */
    // (*gJVM)->DetachCurrentThread(gJVM);
}

static void initClasses(JNIEnv *env, jobject thiz)
{
    jclass clz;

    if (!m_VlcMediaPlayer_onVlcEvent)
    {
        clz = (*env)->GetObjectClass(env, thiz);
        m_VlcMediaPlayer_onVlcEvent = (*env)->GetMethodID(env, clz, "onVlcEvent", "(L" PREFIX "VlcMediaPlayer$VlcEvent;)V");
        (*env)->DeleteLocalRef(env, clz);
    }
    if (!clz_VlcEvent)
    {
        clz = (*env)->FindClass(env, PREFIX "VlcMediaPlayer$VlcEvent");
        clz_VlcEvent = (*env)->NewGlobalRef(env, clz);
        f_VlcEvent_eventType = (*env)->GetFieldID(env, clz, "eventType", "I");
        f_VlcEvent_booleanValue = (*env)->GetFieldID(env, clz, "booleanValue", "Z");
        f_VlcEvent_intValue = (*env)->GetFieldID(env, clz, "intValue", "I");
        f_VlcEvent_longValue = (*env)->GetFieldID(env, clz, "longValue", "J");
        f_VlcEvent_floatValue = (*env)->GetFieldID(env, clz, "floatValue", "F");
        f_VlcEvent_stringValue = (*env)->GetFieldID(env, clz, "stringValue", "Ljava/lang/String;");
    }
}

static void freeClasses(JNIEnv *env, jobject thiz)
{
    if (clz_VlcEvent)
    {
        (*env)->DeleteGlobalRef(env, clz_VlcEvent);
        clz_VlcEvent = 0;
    }
}

JNIEXPORT void JNICALL NAME(nativeAttachSurface)(JNIEnv *env, jobject thiz, jobject s)
{
    jint surface = 0;
    jclass clz;
    jfieldID f_Surface_mSurface;

    clz = (*env)->GetObjectClass(env, s);
    f_Surface_mSurface = (*env)->GetFieldID(env, clz, "mSurface", "I");
    if (f_Surface_mSurface == 0)
    {
        jthrowable e = (*env)->ExceptionOccurred(env);
        if (e)
        {
            (*env)->DeleteLocalRef(env, e);
            (*env)->ExceptionClear(env);
        }
        f_Surface_mSurface = (*env)->GetFieldID(env, clz, "mNativeSurface", "I");
    }
    surface = (*env)->GetIntField(env, s, f_Surface_mSurface);
    pthread_mutex_lock(&s_surface_mutex);
    s_surface = (void *) surface;
    pthread_mutex_unlock(&s_surface_mutex);
}

JNIEXPORT void JNICALL NAME(nativeDetachSurface)(JNIEnv *env, jobject thiz)
{
    pthread_mutex_lock(&s_surface_mutex);
    s_surface = 0;
    pthread_mutex_unlock(&s_surface_mutex);
}

static libvlc_event_type_t md_listening[] = {
    libvlc_MediaDurationChanged,
    libvlc_MediaParsedChanged,
    libvlc_MediaStateChanged,
};

static libvlc_event_type_t mp_listening[] = {
    libvlc_MediaPlayerOpening,
    libvlc_MediaPlayerBuffering,
    libvlc_MediaPlayerPlaying,
    libvlc_MediaPlayerPaused,
    libvlc_MediaPlayerStopped,
    libvlc_MediaPlayerEndReached,
    libvlc_MediaPlayerEncounteredError,
    libvlc_MediaPlayerTimeChanged,
    libvlc_MediaPlayerSeekableChanged,
    libvlc_MediaPlayerPausableChanged,
    libvlc_MediaPlayerLengthChanged,
};

JNIEXPORT void JNICALL NAME(nativeCreate)(JNIEnv *env, jobject thiz)
{
    initClasses(env, thiz);
    const char *argv[] = {"-I", "dummy", "-vvv", "--no-plugins-cache", "--no-drop-late-frames"};
    libvlc_instance_t *instance = libvlc_new_with_builtins(sizeof(argv) / sizeof(*argv), argv, vlc_builtins_modules);
    setIntValue(env, thiz, "mLibVlcInstance", (jint) instance);
    libvlc_media_player_t *mp = libvlc_media_player_new(instance);
    setIntValue(env, thiz, "mLibVlcMediaPlayer", (jint) mp);
    libvlc_event_manager_t *em = libvlc_media_player_event_manager(mp);
    for (int i = 0; i < sizeof(mp_listening) / sizeof(*mp_listening); i++)
    {
        libvlc_event_attach(em, mp_listening[i], vlc_event_callback, thiz);
    }
    jclass clz = (*env)->FindClass(env, PREFIX "VlcMediaPlayer$VlcEvent");
}

JNIEXPORT void JNICALL NAME(nativeRelease)(JNIEnv *env, jobject thiz)
{
    jint mLibVlcMediaPlayer = getIntValue(env, thiz, "mLibVlcMediaPlayer");
    if (mLibVlcMediaPlayer != 0)
    {
        libvlc_event_manager_t *em;
        libvlc_media_player_t *mp = (libvlc_media_player_t*) mLibVlcMediaPlayer;
        libvlc_media_t *md = libvlc_media_player_get_media(mp);
        em = libvlc_media_event_manager(md);
        for (int i = 0; i < sizeof(md_listening) / sizeof(*md_listening); i++)
        {
            libvlc_event_detach(em, md_listening[i], vlc_event_callback, thiz);
        }
        em = libvlc_media_player_event_manager(mp);
        for (int i = 0; i < sizeof(mp_listening) / sizeof(*mp_listening); i++)
        {
            libvlc_event_detach(em, mp_listening[i], vlc_event_callback, thiz);
        }
        libvlc_media_player_stop(mp);
        libvlc_media_player_release(mp);
        setIntValue(env, thiz, "mLibVlcMediaPlayer", 0);
    }
    jint mLibVlcInstance = getIntValue(env, thiz, "mLibVlcInstance");
    if (mLibVlcInstance != 0)
    {
        libvlc_instance_t *instance = (libvlc_instance_t*) mLibVlcInstance;
        libvlc_release(instance);
        setIntValue(env, thiz, "mLibVlcInstance", 0);
    }
    freeClasses(env, thiz);
}

JNIEXPORT jint JNICALL NAME(nativeGetCurrentPosition)(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = (libvlc_media_player_t *) getIntValue(env, thiz, "mLibVlcMediaPlayer");
    if (!mp)
    {
        return -1;
    }
    int64_t position = libvlc_media_player_get_time(mp);
    if (position < 0)
    {
        return -1;
    }
    return (jint) (position / 1000);
}

JNIEXPORT jint JNICALL NAME(nativeGetDuration)(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = (libvlc_media_player_t *) getIntValue(env, thiz, "mLibVlcMediaPlayer");
    if (!mp)
    {
        return -1;
    }
    int64_t duration = libvlc_media_player_get_length(mp);
    if (duration < 0)
    {
        return -1;
    }
    return (jint) (duration / 1000);
}

JNIEXPORT jint JNICALL NAME(nativeGetVideoHeight)(JNIEnv *env, jobject thiz)
{
    libvlc_media_t *media = (libvlc_media_t *) getIntValue(env, thiz, "mLibVlcMedia");
    if (!media || !libvlc_media_is_parsed(media))
        return 0;
    /* FIXME: it returns the first video's information only */
    int i, n;
    int width = 0, height = 0;
    libvlc_media_track_info_t *track = 0;
    n = libvlc_media_get_tracks_info(media, &track);
    if (n <= 0)
        return 0;
    for (i = 0; i < n; i++) {
        libvlc_media_track_info_t t = track[i];
        if (t.i_type == libvlc_track_video) {
            width = t.u.video.i_width;
            height = t.u.video.i_height;
            break;
        }
    }
    free(track);
    return height;
}

JNIEXPORT jint JNICALL NAME(nativeGetVideoWidth)(JNIEnv *env, jobject thiz)
{
    libvlc_media_t *media = (libvlc_media_t *) getIntValue(env, thiz, "mLibVlcMedia");
    if (!media || !libvlc_media_is_parsed(media))
        return 0;
    /* FIXME: it returns the first video's information only */
    int i, n;
    int width = 0, height = 0;
    libvlc_media_track_info_t *track = 0;
    n = libvlc_media_get_tracks_info(media, &track);
    if (n <= 0)
        return 0;
    for (i = 0; i < n; i++) {
        libvlc_media_track_info_t t = track[i];
        if (t.i_type == libvlc_track_video) {
            width = t.u.video.i_width;
            height = t.u.video.i_height;
            break;
        }
    }
    free(track);
    return width;
}

JNIEXPORT jboolean JNICALL NAME(nativeIsLooping)(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = (libvlc_media_player_t *) getIntValue(env, thiz, "mLibVlcMediaPlayer");
    if (!mp)
    {
        return 0;
    }
    return 0;
}

JNIEXPORT jboolean JNICALL NAME(nativeIsPlaying)(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = (libvlc_media_player_t *) getIntValue(env, thiz, "mLibVlcMediaPlayer");
    if (!mp)
    {
        return 0;
    }
    return (libvlc_media_player_is_playing(mp) != 0);
}

JNIEXPORT void JNICALL NAME(nativePause)(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = (libvlc_media_player_t *) getIntValue(env, thiz, "mLibVlcMediaPlayer");
    if (!mp)
    {
        return;
    }
    libvlc_media_player_set_pause(mp, 1);
}

JNIEXPORT void JNICALL NAME(nativePrepare)(JNIEnv *env, jobject thiz)
{
    libvlc_media_t *media = (libvlc_media_t *) getIntValue(env, thiz, "mLibVlcMedia");
    if (!media)
    {
        return;
    }
    libvlc_media_parse(media);
}

JNIEXPORT void JNICALL NAME(nativePrepareAsync)(JNIEnv *env, jobject thiz)
{
    libvlc_media_t *media = (libvlc_media_t *) getIntValue(env, thiz, "mLibVlcMedia");
    if (!media)
    {
        return;
    }
    libvlc_media_parse_async(media);
}

JNIEXPORT void JNICALL NAME(nativeSeekTo)(JNIEnv *env, jobject thiz, jint msec)
{
    libvlc_media_player_t *mp = (libvlc_media_player_t *) getIntValue(env, thiz, "mLibVlcMediaPlayer");
    if (!mp)
    {
        return;
    }
    libvlc_media_player_set_time(mp, msec);
}

JNIEXPORT void JNICALL NAME(nativeSetDataSource)(JNIEnv *env, jobject thiz, jstring path)
{
    libvlc_instance_t *instance = (libvlc_instance_t *) getIntValue(env, thiz, "mLibVlcInstance");
    if (!instance)
    {
        return;
    }
    libvlc_media_player_t *mp = (libvlc_media_player_t *) getIntValue(env, thiz, "mLibVlcMediaPlayer");
    if (!mp)
    {
        return;
    }
    const char *str = (*env)->GetStringUTFChars(env, path, 0);
    if (!str)
    {
        return;
    }
    libvlc_media_t *media = (*str == '/') ? libvlc_media_new_path(instance, str) : libvlc_media_new_location(instance, str);
    if (media)
    {
        libvlc_event_manager_t *em = libvlc_media_event_manager(media);
        for (int i = 0; i < sizeof(md_listening) / sizeof(*md_listening); i++)
        {
            libvlc_event_attach(em, md_listening[i], vlc_event_callback, thiz);
        }
        libvlc_media_player_set_media(mp, media);
    }
    (*env)->ReleaseStringUTFChars(env, path, str);
    setIntValue(env, thiz, "mLibVlcMedia", (jint) media);
}

JNIEXPORT void JNICALL NAME(nativeSetDisplay)(JNIEnv *env, jobject thiz, jobject holder)
{

}

JNIEXPORT void JNICALL NAME(nativeSetLooping)(JNIEnv *env, jobject thiz, jboolean looping)
{

}

JNIEXPORT void JNICALL NAME(nativeStart)(JNIEnv *env, jobject thiz)
{
    libvlc_media_player_t *mp = (libvlc_media_player_t *) getIntValue(env, thiz, "mLibVlcMediaPlayer");
    if (!mp)
    {
        return;
    }
    libvlc_media_player_play(mp);
}

JNIEXPORT void JNICALL NAME(nativeStop)(JNIEnv *env, jobject thiz) {
    libvlc_media_player_t *mp = (libvlc_media_player_t *) getIntValue(env, thiz, "mLibVlcMediaPlayer");
    if (!mp)
    {
        return;
    }
    libvlc_media_player_stop(mp);
}

#endif

