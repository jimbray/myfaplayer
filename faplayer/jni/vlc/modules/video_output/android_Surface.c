
#ifdef HAVE_CONFIG_H
# include "config.h"
#endif

#include <vlc_common.h>
#include <vlc_plugin.h>
#include <vlc_vout_display.h>
#include <vlc_picture_pool.h>
#include <vlc_threads.h>

#include <dlfcn.h>
#include <pixman.h>

extern void LockSurface();
extern void UnlockSurface();
extern void *GetSurface();

static void *InitLibrary();

static int Open (vlc_object_t *);
static void Close(vlc_object_t *);

vlc_module_begin()
    set_shortname("AndroidSurface")
    set_category(CAT_VIDEO)
    set_subcategory(SUBCAT_VIDEO_VOUT)
    set_description(N_("Android Surface video output"))
    set_capability("vout display", 25)
    add_shortcut("android")
    set_callbacks(Open, Close)
vlc_module_end()

static picture_pool_t *Pool(vout_display_t *, unsigned);
static void Display(vout_display_t *, picture_t *, subpicture_t *);
static int Control(vout_display_t *, int, va_list);

// _ZN7android7Surface4lockEPNS0_11SurfaceInfoEb
typedef void (*Surface_lock)(void *, void *, int);
// _ZN7android7Surface13unlockAndPostEv
typedef void (*Surface_unlockAndPost)(void *);

struct vout_display_sys_t {
    vout_display_place_t place;
    picture_pool_t *p_pool;
    void *p_libsfc_or_ui;
    int i_surface_width, i_surface_height, i_surface_stride;
    bool b_full_screen;
    bool b_display_place_changed;
    int i_erase_surface_count;
};

typedef struct _SurfaceInfo {
    uint32_t    w;
    uint32_t    h;
    uint32_t    s;
    uint32_t    a;
    uint32_t    b;
    uint32_t    c;
    uint32_t    reserved[2];
} SurfaceInfo;

static Surface_lock s_lock = NULL;
static Surface_unlockAndPost s_unlockAndPost = NULL;

static void *InitLibrary()
{
    void *p_library;

    p_library = dlopen("libsurfaceflinger_client.so", RTLD_NOW);
    if (p_library)
    {
        s_lock = (Surface_lock)(dlsym(p_library, "_ZN7android7Surface4lockEPNS0_11SurfaceInfoEb"));
        s_unlockAndPost = (Surface_unlockAndPost)(dlsym(p_library, "_ZN7android7Surface13unlockAndPostEv"));
        if (s_lock && s_unlockAndPost)
        {
            return p_library;
        }
        dlclose(p_library);
    }
    p_library = dlopen("libui.so", RTLD_NOW);
    if (p_library)
    {
        s_lock = (Surface_lock)(dlsym(p_library, "_ZN7android7Surface4lockEPNS0_11SurfaceInfoEb"));
        s_unlockAndPost = (Surface_unlockAndPost)(dlsym(p_library, "_ZN7android7Surface13unlockAndPostEv"));
        if (s_lock && s_unlockAndPost)
        {
            return p_library;
        }
        dlclose(p_library);
    }
    return NULL;
}

static int Open(vlc_object_t *p_this)
{
    vout_display_t *vd = (vout_display_t *) p_this;
    void *p_library;
    void *p_surface;
    SurfaceInfo info;
    int i_pixel_format;
    char *psz_chroma_format = "RV16";
    vout_display_sys_t *sys;

    p_library = InitLibrary();
    if (!p_library)
    {
        msg_Err(VLC_OBJECT(p_this), "Could not initialize libui.so/libsurfaceflinger_client.so!");
        return VLC_EGENERIC;
    }

    LockSurface();
    p_surface = GetSurface();
    if (!p_surface)
    {
        UnlockSurface();
        dlclose(p_library);
        msg_Err(vd, "No surface is attached!");
        return VLC_EGENERIC;
    }
    s_lock(p_surface, &info, 1);
    s_unlockAndPost(p_surface);
    UnlockSurface();

    i_pixel_format = info.s >= info.w ? info.b : info.a;
    // RGB565 only
    if (i_pixel_format != 4) {
        dlclose(p_library);
        msg_Err(vd, "Unknown chroma format %08x (android)", i_pixel_format);
        return VLC_EGENERIC;
    }

    vlc_fourcc_t chroma = vlc_fourcc_GetCodecFromString(VIDEO_ES, psz_chroma_format);
    if (!chroma || chroma != VLC_CODEC_RGB16)
    {
        dlclose(p_library);
        msg_Err(vd, "Unsupported chroma format %s", psz_chroma_format);
        return VLC_EGENERIC;
    }

    video_format_t fmt = vd->fmt;
    fmt.i_chroma = chroma;
    fmt.i_rmask = 0xf800;
    fmt.i_gmask = 0x07e0;
    fmt.i_bmask = 0x001f;

    sys = (struct vout_display_sys_t*) calloc(1, sizeof(struct vout_display_sys_t));
    if (!sys)
        return VLC_ENOMEM;
    sys->p_pool = NULL;
    sys->p_libsfc_or_ui = p_library;
    sys->i_surface_width = info.w;
    sys->i_surface_height = info.h;
    sys->i_surface_stride = (info.s >= info.w ? info.s : info.w);
    sys->b_full_screen = true;
    sys->b_display_place_changed = true;
    sys->i_erase_surface_count = 0;

    vout_display_cfg_t cfg = *vd->cfg;
    cfg.display.width = info.w;
    cfg.display.height = info.h;
    cfg.is_fullscreen = false;

    vout_display_SendEventDisplaySize(vd, cfg.display.width, cfg.display.height, cfg.is_fullscreen);
    vout_display_PlacePicture(&sys->place, &vd->source, &cfg, false);

    vd->sys = sys;
    vd->fmt = fmt;
    vd->pool = Pool;
    vd->prepare = NULL;
    vd->display = Display;
    vd->control = Control;
    vd->manage  = NULL;

    return VLC_SUCCESS;
}

static void Close(vlc_object_t *p_this)
{
    vout_display_t *vd = (vout_display_t *) p_this;
    vout_display_sys_t *sys = vd->sys;

    if (sys->p_pool)
        picture_pool_Delete(sys->p_pool);

    free(sys);
}

static picture_pool_t *Pool(vout_display_t *vd, unsigned count)
{
    vout_display_sys_t *sys = vd->sys;

    if (!sys->p_pool)
        sys->p_pool = picture_pool_NewFromFormat(&vd->fmt, count);

    return sys->p_pool;
}

static void Display(vout_display_t *vd, picture_t *picture, subpicture_t *subpicture)
{
    vout_display_sys_t *sys = vd->sys;
    int sw, sh, ss;
    void *p_surface;
    SurfaceInfo info;
    void *p_bits;
    int dw, dh, ds;
    int drx, dry, drh, drw;
    int q, qw, qh;
    pixman_image_t *src_image = NULL, *dst_image = NULL;
    pixman_transform_t scale;

    VLC_UNUSED(subpicture);

    // msg_Dbg(VLC_OBJECT(vd), "video place %d,%d %dx%d", sys->place.x, sys->place.y, sys->place.width, sys->place.height);

    sw = picture->p[0].i_visible_pitch / picture->p[0].i_pixel_pitch;
    sh = picture->p[0].i_visible_lines;
    ss = picture->p[0].i_pitch / picture->p[0].i_pixel_pitch;
    src_image = pixman_image_create_bits(PIXMAN_r5g6b5, sw, sh, (uint32_t *)(picture->p[0].p_pixels), ss << 1);
    if (!src_image) {
        picture_Release(picture);
        return;
    }

    dw = sys->i_surface_width;
    dh = sys->i_surface_height;
    ds = sys->i_surface_stride;
    // how to place it at the center, not (0,0) when it's not full screen ?
    if (!sys->b_full_screen && 
        sys->place.width <= dw &&
       sys->place.height <= dh)
    {
        drx = (dw - sys->place.width) >> 1;
        dry = (dh - sys->place.height) >> 1;
    }
    else
    {
        drx = sys->place.x;
        dry = sys->place.y;
    }
    drw = sys->place.width;
    drh = sys->place.height;

    LockSurface();

    p_surface = GetSurface();
    if (!p_surface)
        goto bail;

    s_lock(p_surface, &info, 1);

    p_bits = (void *)(info.s >= info.w ? info.c : info.b);
    dst_image = pixman_image_create_bits(PIXMAN_r5g6b5, dw, dh, (uint32_t *) p_bits, ds << 1);
    if (!dst_image)
        goto bail;

    // TODO: pixman is fast but here the scale quality is not good
    if (drw != sw || drh != sh)
    {
        qw = (sw << 16) / drw;
        qh = (sh << 16) / drh;
        pixman_transform_init_scale(&scale, qw, qh);
        pixman_image_set_transform(src_image, &scale);
    }

    // surface has 2 buffers, and will swap when 1 is dirty
    // so we need to fill both when display place is changed
    if (sys->b_display_place_changed)
    {
        sys->i_erase_surface_count += 1;
        if (sys->i_erase_surface_count % 3)
            memset(p_bits, 0, ds * dh * 2);
        else
            sys->b_display_place_changed = false;
    }

    pixman_image_composite(PIXMAN_OP_SRC, src_image, NULL, dst_image, 0, 0, 0, 0, drx, dry, drw, drh);

    s_unlockAndPost(p_surface);

bail:
    UnlockSurface();

    if (src_image)
        pixman_image_unref(src_image);
    if (dst_image)
        pixman_image_unref(dst_image);

    picture_Release(picture);
}

static int Control(vout_display_t *vd, int query, va_list args)
{
    vout_display_sys_t *sys = vd->sys;

    switch (query)
    {
    case VOUT_DISPLAY_HIDE_MOUSE:
        return VLC_SUCCESS;
    case VOUT_DISPLAY_CHANGE_FULLSCREEN:
    {
        vout_display_cfg_t *cfg = va_arg(args, vout_display_cfg_t *);

        if (cfg->is_fullscreen) {
            cfg->display.width = sys->i_surface_width;
            cfg->display.height = sys->i_surface_height;
        }
        sys->b_full_screen = cfg->is_fullscreen;
        if (!sys->b_full_screen)
            sys->b_display_place_changed = true;
        vout_display_PlacePicture(&sys->place, &vd->source, cfg, false);
        vout_display_SendEventDisplaySize(vd, cfg->display.width, cfg->display.height, cfg->is_fullscreen);
        return VLC_SUCCESS;
    }
    default:
        return VLC_EGENERIC;
    }

    return VLC_EGENERIC;
}

