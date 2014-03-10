
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <vlc_common.h>
#include <vlc_plugin.h>

#include <errno.h>
#include <signal.h>
#include <assert.h>
#include <unistd.h>
#include <sys/types.h>
#include <poll.h>

#include <vlc_interface.h>
#include <vlc_aout.h>
#include <vlc_vout.h>
#include <vlc_playlist.h>
#include <vlc_network.h>

static int  Activate(vlc_object_t *);
static void Deactivate(vlc_object_t *);
static void Run(intf_thread_t *);

static void Notify(intf_thread_t *, const char*, ...);

static void ProcessCommandLine(intf_thread_t *, const char *);
static int ParseCommandLine(char *p_string, char*** p_argv);

static int GetCurrentChoice(vlc_object_t *p_object, const char *psz_name);
static int SetCurrentChoice(vlc_object_t *p_object, const char *psz_name, int choice);

static int InputEvent(vlc_object_t *p_this, char const *psz_cmd, vlc_value_t oldval, vlc_value_t newval, void *p_data);
static int VoutEvent(vlc_object_t *p_this, char const *psz_cmd, vlc_value_t oldval, vlc_value_t newval, void *p_data);

struct intf_sys_t {
    int i_socket;
    int *pi_socket;
    int i_wakeup[2];
    char p_read_buffer[512];
    int i_read_offset;
    vlc_mutex_t o_write_lock;
    char p_write_buffer[4096];
    int i_write_offset;
    int i_write_length;
    playlist_t *p_playlist;
    vout_thread_t *p_vout;
};

vlc_module_begin ()
    set_shortname(N_("reporter"))
    set_category(CAT_INTERFACE)
    set_subcategory(SUBCAT_INTERFACE_MAIN)
    set_description(N_("interactive TCP remote control interface"))
#if HAVE_ANDROID
    set_capability("interface", 25)
#else
    set_capability("interface", 0)
#endif
    set_callbacks(Activate, Deactivate)
vlc_module_end ()

static int Activate(vlc_object_t *p_this)
{
    int i_port = 21178;
    const char* psz_host = "127.0.0.1";
    intf_thread_t *p_intf = (intf_thread_t*) p_this;
    intf_sys_t *p_sys;

    p_sys = calloc(1, sizeof(intf_sys_t));
    if (!p_sys)
        return VLC_ENOMEM;
    if (pipe(p_sys->i_wakeup) < 0)
    {
        free(p_sys);
        return VLC_EGENERIC;
    }
    p_sys->pi_socket = net_ListenTCP(p_this, psz_host, i_port);
    if (p_sys->pi_socket == NULL)
    {
        free(p_sys);
        msg_Err(p_intf, "Can't listen to %s port %i", psz_host, i_port);
        return VLC_EGENERIC;
    }
    p_sys->i_socket = -1;    
    vlc_mutex_init(&p_sys->o_write_lock);
    p_sys->i_read_offset = 0;
    p_sys->i_write_offset = 0;
    p_sys->i_write_length = 0;

    p_intf->p_sys = p_sys;
    p_intf->pf_run = Run;

    return VLC_SUCCESS;
}

static void Deactivate(vlc_object_t *p_this)
{
    intf_thread_t *p_intf = (intf_thread_t*)p_this;
    intf_sys_t *p_sys = p_intf->p_sys;

    net_ListenClose(p_sys->pi_socket);
    if(p_sys->i_socket != -1)
        net_Close(p_sys->i_socket);
    vlc_mutex_destroy(&p_sys->o_write_lock);
    close(p_sys->i_wakeup[0]);
    close(p_sys->i_wakeup[1]);
    free(p_sys);
}

static void Run(intf_thread_t *p_intf)
{
    intf_sys_t *p_sys = p_intf->p_sys;
    input_thread_t *p_input = NULL;
    playlist_t *p_playlist = pl_Get(p_intf);
    int *pi_fd, i_listen = 0, i_err;

    p_intf->p_sys->p_playlist = p_playlist;

    for (pi_fd = p_sys->pi_socket; *pi_fd != -1; pi_fd++)
        i_listen++;
    for (; vlc_object_alive(p_intf);)
    {
        vlc_value_t val;
        struct pollfd fd[i_listen + 2];

        vlc_testcancel();

        if (p_input == NULL)
        {
            p_input = playlist_CurrentInput(p_playlist);
            if (p_input) {
                var_AddCallback(p_input, "intf-event", InputEvent, p_intf);
            }
        }
        else
        {
            if (p_input->b_dead || !vlc_object_alive(p_input))
            {
                var_DelCallback(p_input, "intf-event", InputEvent, p_intf);
                vlc_object_release(p_input);
                p_input = NULL;
            }
        }
        memset(&fd, 0, sizeof(fd[0]) * (i_listen + 1));
        if (p_sys->i_socket == -1)
        {
            for (int i = 0; i < i_listen; i++)
            {
                fd[i].fd = p_sys->pi_socket[i];
                fd[i].events = POLLIN;
                fd[i].revents = 0;
            }
        }
        else
        {
            fd[0].fd = p_sys->i_wakeup[0];
            fd[0].events = POLLIN;
            fd[0].revents = 0;
            fd[1].fd = p_sys->i_socket;
            fd[1].events = POLLIN | (p_sys->i_write_length > 0 ? POLLOUT : 0);
            fd[1].revents = 0;
        }
        i_err = poll(fd, (p_sys->i_socket != -1) ? 2 : i_listen, -1);
        if (i_err < 0)
        {
            msg_Err(p_intf, "poll() failed");
            vlc_object_kill(p_intf);
            break;
        }
        if (i_err == 0)
            continue;
        if (p_sys->i_socket == -1)
        {
            for (int i = 0; i < i_listen; i++)
            {
                if (fd[i].revents & POLLIN)
                {
                    int client = net_AcceptSingle(VLC_OBJECT(p_intf), fd[i].fd);
                    if (client == -1)
                        continue;
                    p_sys->i_socket = client;
                    break;
                }
            }
        }
        else
        {
            if (fd[0].revents & POLLIN)
            {
                char ch;

                read(fd[0].fd, &ch, 1);
            }
            if (fd[1].revents & (POLLERR|POLLHUP|POLLNVAL))
            {
                net_Close(fd[0].fd);
                p_sys->i_socket = -1;
                msg_Err(VLC_OBJECT(p_intf), "connection error");
                continue;
            }
            ssize_t i_len, i_test;
            if (fd[1].revents & POLLIN)
            {
                bool b_line = false;
                while ((i_len = recv(fd[1].fd, p_sys->p_read_buffer + p_sys->i_read_offset, 1, 0)) > 0)
                {
                    char ch;

                    ch = p_sys->p_read_buffer[p_sys->i_read_offset];
                    switch (ch)
                    {
                    case '\r':
                    case '\n':
                        p_sys->p_read_buffer[p_sys->i_read_offset] = '\0';
                        b_line = true;
                        break;
                    case '\0':
                        b_line = true;
                        break;
                    default:
                        break;
                    }
                    p_sys->i_read_offset++;
                    if (p_sys->i_read_offset == sizeof(p_sys->p_read_buffer) && !b_line)
                    {
                        net_Close(p_sys->i_socket);
                        p_sys->i_socket = -1;
                        msg_Err(VLC_OBJECT(p_intf), "input is too long, close connection");
                        break;
                    }
                }
                if (b_line && strlen(p_sys->p_read_buffer))
                {
                    p_sys->i_read_offset = 0;
                    msg_Info(p_intf, "%s", p_sys->p_read_buffer);
                    ProcessCommandLine(p_intf, p_sys->p_read_buffer);
                }
                if(i_len == 0)
                {
                    net_Close(p_sys->i_socket);
                    p_sys->i_socket = -1;
                    msg_Dbg(VLC_OBJECT(p_intf), "connection is closed by client");
                }
            }
            if (fd[1].revents & POLLOUT)
            {
                vlc_mutex_lock(&p_sys->o_write_lock);
                if (p_sys->i_write_length)
                {
                    int i_first, i_second;

                    i_first = sizeof(p_sys->p_write_buffer) - p_sys->i_write_offset;
                    i_second = (p_sys->i_write_offset + p_sys->i_write_length) % sizeof(p_sys->p_write_buffer);
                    i_test = 0;
                    if (i_first >= p_sys->i_write_length)
                        i_len = send(fd[1].fd, p_sys->p_write_buffer + p_sys->i_write_offset, p_sys->i_write_length, 0);
                    else
                    {
                        i_len = send(fd[1].fd, p_sys->p_write_buffer + p_sys->i_write_offset, i_first, 0);
                        if (i_len == i_first)
                            i_test = send(fd[1].fd, p_sys->p_write_buffer, i_second, 0);
                        if (i_test > 0)
                            i_len += i_test;
                    }
                    if (i_len > 0)
                    {
                        p_sys->i_write_offset += i_len;
                        p_sys->i_write_offset %= sizeof(p_sys->p_write_buffer);
                        p_sys->i_write_length -= i_len;
                        if (p_sys->i_write_length == 0)
                            p_sys->i_write_offset = 0;
                    }
                }
                vlc_mutex_unlock(&p_sys->o_write_lock);
            }
        }
    }
    if (p_input)
    {
        var_DelCallback(p_input, "intf-event", InputEvent, p_intf);
        vlc_object_release(p_input);
    }
}

static void Notify(intf_thread_t *p_intf, const char* psz_format, ...)
{
    va_list args;
    intf_sys_t *p_sys = p_intf->p_sys;
    int i_size;
    char *psz_message;

    va_start(args, psz_format);
    i_size = vasprintf(&psz_message, psz_format, args);
    va_end(args);
    if (!psz_message)
        return;
    vlc_mutex_lock(&p_sys->o_write_lock);
    if (p_sys->i_write_length + i_size < sizeof(p_sys->p_write_buffer))
    {
        int i_first, i_second;

        i_first = sizeof(p_sys->p_write_buffer) - p_sys->i_write_offset - p_sys->i_write_length;
        i_second = (p_sys->i_write_offset + p_sys->i_write_length + i_size) % sizeof(p_sys->p_write_buffer);
        if (i_first >= i_size)
            memcpy(p_sys->p_write_buffer + p_sys->i_write_offset + p_sys->i_write_length, psz_message, i_size);
        else
        {
            memcpy(p_sys->p_write_buffer + p_sys->i_write_offset + p_sys->i_write_length, psz_message, i_first);
            memcpy(p_sys->p_write_buffer, psz_message + i_first, i_second);
        }
        p_sys->i_write_length += i_size;
    }
    else
    {
        msg_Dbg(VLC_OBJECT(p_intf), "buffer is full, discard current message");
    }
    vlc_mutex_unlock(&p_sys->o_write_lock);
    free(psz_message);
    write(p_sys->i_wakeup[1], &p_sys, 1);
}

static int ParseCommandLine(char *p_string, char*** p_argv)
{
    if (!p_string || !strlen(p_string))
    {
        if (p_argv)
            p_argv = NULL;
        return 0;
    }
    bool b_start = false;
    bool b_quote = false;
    int i_len = strlen(p_string);
    int i_count = 0;
    for (int i = 0; i < i_len; i++)
    {
        char ch = p_string[i];

        if (ch == '\"')
            b_quote = !b_quote;
        if (!b_quote && (ch == ' ' || ch == '\t'))
        {
            b_start = false;
            p_string[i] = 0;
        }
        if (ch != ' ' && ch != '\t')
        {
            if (!b_start)
            {
                b_start = true;
                i_count++;
            }
        }
    }
    if (p_argv)
    {
        if (i_count)
        {
            int n = 0;

            *p_argv = malloc(i_count * sizeof(char*));
            if (!(*p_argv))
                return i_count;
            b_start = false;
            for (int i = 0; i < i_len; i++)
            {
                char ch = p_string[i];

                if (ch != '\0')
                {
                    if (!b_start)
                    {
                        b_start = true;
                        b_quote = false;
                        if (p_string[i] == '\"')
                        {
                            p_string[i + strlen(p_string + i) - 1] = '\0';
                            p_string[i] = '\0';
                            b_quote = true;
                        }
                        (*p_argv)[n++] = p_string + i + (b_quote ? 1 : 0);
                    }
                    else
                        continue;
                }
                else
                    b_start = false;
            }
        }
        else
            p_argv = NULL;
    }
    return i_count;
}

static void ProcessCommandLine(intf_thread_t *p_intf, const char *p_string)
{
    intf_sys_t *p_sys = p_intf->p_sys;
    playlist_t *p_playlist = p_sys->p_playlist;
    input_thread_t *p_input = playlist_CurrentInput(p_playlist);
    char *p_line = strdup(p_string);
    int i_argc;
    char** p_argv = NULL;

    i_argc = ParseCommandLine(p_line, &p_argv);
    if (i_argc == 0 || !p_argv)
        goto msg;
    if (i_argc == 1)
    {
        if (!strcmp(p_argv[0], "help"))
        {
            Notify(p_intf, "woshenmedoubuzhidao\n");
        }
        else if (!strcmp(p_argv[0], "play"))
        {
            playlist_Play(p_playlist);
        }
        else if (!strcmp(p_argv[0], "pause"))
        {
            playlist_Pause(p_playlist);
        }
        else if (!strcmp(p_argv[0], "stop"))
        {
            playlist_Stop(p_playlist);
        }
        else if (!strcmp(p_argv[0], "close"))
        {
            playlist_Clear(p_playlist, pl_Unlocked);
        }
        else if (!strcmp(p_argv[0], "quit"))
        {
            net_Close(p_sys->i_socket);
            p_sys->i_socket = -1;
        }
        else if (!strcmp(p_argv[0], "shutdown"))
        {

        }
        else
            goto msg;
    }
    else if (i_argc == 2)
    {
        if (!strcmp(p_argv[0], "open"))
        {
            char *path = p_argv[1];
            int len = strlen(path);
            char *uri = malloc(len * 3 + 1);
            if (!uri)
            {
                Notify(p_intf, "oops\n");
                goto out;
            }
            int i, j;
            for (i = 0, j = 0; i < len; i++)
            {
                uint8_t ch = path[i];

                if (ch < 32 || ch > 127)
                {
                    uri[j] = '%';
                    uri[j + 1] = ch >> 4;
                    uri[j + 1] += (uri[j + 1] > 9 ? 0x37 : 0x30);
                    uri[j + 2] = ch & 15;
                    uri[j + 2] += (uri[j + 2] > 9 ? 0x37 : 0x30);
                    j += 3;
                }
                else
                {
                    uri[j] = path[i];
                    j++;
                }
            }
            uri[j] = '\0';
            char *name = malloc(len + 1);
            if (name)
            {
                int i;
                for (i = len - 1; i >= 0; i--)
                {
                    if (path[i] == '/')
                        break;
                }
                i += 1;
                strcpy(name, path + i);
            }
            input_item_t *p_item = input_item_New(p_intf, uri, name);
            if (name)
                free(name);
            playlist_Clear(p_playlist, pl_Unlocked);
            playlist_AddInput(p_playlist, p_item, PLAYLIST_GO | PLAYLIST_APPEND, PLAYLIST_END, true, pl_Unlocked);
            vlc_gc_decref(p_item);
        }
        else if (!strcmp(p_argv[0], "time"))
        {
            mtime_t time = (int64_t) (atoll(p_argv[1]));
            var_SetTime(p_input, "time", time);
        }
        else if (!strcmp(p_argv[0], "fullscreen"))
        {
            int on = atoi(p_argv[1]);
            vout_thread_t *p_vout = input_GetVout(p_input);

            if (!p_vout)
                goto out;
            var_SetBool(p_vout, "fullscreen", on != 0);
            Notify(p_intf, "vout fullscreen %d\n", var_GetBool(p_vout, "fullscreen") ? 1 : 0);
        }
        else if (!strcmp(p_argv[0], "aspect-ratio"))
        {

        }
        else if (!strcmp(p_argv[0], "audio-es"))
        {
            int index = atoi(p_argv[1]);

            if (!SetCurrentChoice(VLC_OBJECT(p_input), "audio-es", index))
            {
                int count = var_CountChoices(p_input, "audio-es");

                Notify(p_intf, "input audio-es %d/%d\n", index, count);
            }
        }
        else if (!strcmp(p_argv[0], "spu-es"))
        {
            int index = atoi(p_argv[1]);

            if (!SetCurrentChoice(VLC_OBJECT(p_input), "spu-es", index))
            {
                int count = var_CountChoices(p_input, "spu-es");

                Notify(p_intf, "input spu-es %d/%d\n", index, count);
            }
        }
        else
            goto msg;
    }
    goto out;
msg:
    msg_Dbg(VLC_OBJECT(p_intf), "unable to process command `%s'", p_string);
out:
    if (p_argv)
        free(p_argv);
    if (p_line)
        free(p_line);
    return;
}

static int GetCurrentChoice(vlc_object_t *p_object, const char *psz_name)
{
    vlc_value_t val_list;
    vlc_value_t val;
    int i_choice = -1;

    if (!p_object)
        return -1;

    if (var_Get(p_object, psz_name, &val ) < 0)
    {
        vlc_object_release(p_object);
        return -1;
    }

    var_Change(p_object, psz_name, VLC_VAR_GETCHOICES, &val_list, NULL);
    for (int i = 0; i < val_list.p_list->i_count; i++)
    {
        if (val_list.p_list->p_values[i].i_int == val.i_int)
        {
            i_choice = i;
            break;
        }
    }
    var_FreeList(&val_list, NULL);
    vlc_object_release(p_object);
    return i_choice;
}

static int SetCurrentChoice(vlc_object_t *p_object, const char *psz_name, int i_choice)
{
    vlc_value_t val_list;
    int i_ret = -1;

    if (!p_object)
        return -1;

    var_Change(p_object, psz_name, VLC_VAR_GETCHOICES, &val_list, NULL);
    for (int i = 0; i < val_list.p_list->i_count; i++)
    {
        if(i_choice == val_list.p_list->p_values[i].i_int)
        {
            if (!var_SetInteger(p_object, psz_name, i_choice))
                i_ret = 0;
            break;
        }
    }
    var_FreeList(&val_list, NULL);
    vlc_object_release(p_object);
    return i_ret;
}

static int InputEvent(vlc_object_t *p_this, char const *psz_cmd, vlc_value_t oldval, vlc_value_t newval, void *p_data)
{
    vlc_value_t val;
    input_thread_t *p_input = (input_thread_t*) p_this;
    intf_thread_t *p_intf = p_data;
    intf_sys_t *p_sys = p_intf->p_sys;

    switch (newval.i_int)
    {
    case INPUT_EVENT_STATE:
    {
        var_Get(p_input, "state", &val);
        Notify(p_intf, "input state %d\n", val.i_int);
        // XXX: Is this the right place to retrieve these?
        if (val.i_int == PLAYING_S)
        {
            int count = -1, index = -1;

            var_Get(p_input, "can-pause", &val);
            Notify(p_intf, "input can-pause %d\n", val.i_int);
            var_Get(p_input, "can-seek", &val);
            Notify(p_intf, "input can-seek %d\n", val.i_int);

            count = var_CountChoices(p_input, "audio-es");
            if (count > 0)
                index = GetCurrentChoice(VLC_OBJECT(p_input), "audio-es");
            Notify(p_intf, "input audio-es %d/%d\n", index, count);

            count = var_CountChoices(p_input, "spu-es");
            if (count > 0)
                index = GetCurrentChoice(VLC_OBJECT(p_input), "spu-es");
            Notify(p_intf, "input spu-es %d/%d\n", index, count);

            input_item_t *p_item = input_GetItem(p_input);
            vlc_mutex_lock(&p_item->lock);
            int i_es = p_item->i_es;
            for (int i = 0; i < i_es; i++)
            {
                es_format_t *p_es = p_item->es[i];

                switch (p_es->i_cat)
                {
                case AUDIO_ES:
                    break;
                case SPU_ES:
                    break;
                case VIDEO_ES:
                    Notify(p_intf, "video size %dx%d\n", p_es->video.i_width, p_es->video.i_height);
                    break;
                default:
                    break;
                }
            }
            vlc_mutex_unlock(&p_item->lock);
        }
        break;
    }
    case INPUT_EVENT_POSITION:
    {
        var_Get(p_input, "time", &val);
        Notify(p_intf, "input time %"PRIu64"\n", val.i_time);
        break;
    }
    case INPUT_EVENT_LENGTH:
    {
        var_Get(p_input, "length", &val);
        Notify(p_intf, "input length %"PRId64"\n", val.i_time);
        break;
    }
    case INPUT_EVENT_VOUT:
    {
        vout_thread_t *p_vout = input_GetVout(p_input);

        if (p_vout)
        {
            bool fs = var_GetBool(p_vout, "fullscreen");
            Notify(p_intf, "vout fullscreen %d\n", fs ? 1 : 0);
        }
        break;
    }
    default:
        break;
    }

    return VLC_SUCCESS;
}

