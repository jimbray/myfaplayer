
#include <jni.h>
#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>
#include <netinet/in.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <string.h>
#ifdef ANDROID
#include <android/log.h>
#endif

static int init = 0;
static pthread_mutex_t mutex;
static int sock = -1;
static struct sockaddr_in addr = {
    .sin_family = AF_INET,
    .sin_port = 0x4e21,
    .sin_addr.s_addr = 0x7f000001
};

// TODO: fix leaks

void faplayer_message_udp(const char* fmt, ...) {
    va_list vlist;
    va_start(vlist, fmt);
    char *msg = NULL;
    vasprintf(&msg, fmt, vlist);
    if (sock < 0) {
        sock = socket(AF_INET, SOCK_DGRAM, 0);
    }
    if (msg) {
        if (sock >= 0)
            sendto(sock, msg, strlen(msg), 0, (const struct sockaddr *) &addr, sizeof(addr));
        free(msg);
    }
}

void faplayer_message_logcat(const char* fmt, ...) {
    va_list vlist;
    va_start(vlist, fmt);
    if (!init) {
        init = -1;
        pthread_mutex_init(&mutex, 0);
    }
    pthread_mutex_lock(&mutex);
#ifdef ANDROID
    __android_log_vprint(ANDROID_LOG_DEBUG, "faplayer", fmt, vlist);
#else
    vfprintf(stdout, fmt, vlist);
    fflush(stdout);
#endif
    pthread_mutex_unlock(&mutex);
}

