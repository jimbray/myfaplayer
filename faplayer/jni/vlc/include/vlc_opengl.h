/*****************************************************************************
 * vlc_gl.h: VLC GL API
 *****************************************************************************
 * Copyright (C) 2009 Laurent Aimar
 * Copyright (C) 2011 Rémi Denis-Courmont
 *
 * Authors: Laurent Aimar <fenrir _AT_ videolan _DOT_ org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#ifndef VLC_GL_H
#define VLC_GL_H 1

/**
 * \file
 * This file defines GL structures and functions.
 */

struct vout_window_t;

/**
 * A VLC GL context (and its underlying surface)
 */
typedef struct vlc_gl_t vlc_gl_t;

struct vlc_gl_t
{
    VLC_COMMON_MEMBERS

    struct vout_window_t *surface;
    module_t *module;
    void *sys;

    int  (*makeCurrent)(vlc_gl_t *);
    void (*swap)(vlc_gl_t *);
    int  (*lock)(vlc_gl_t *);
    void (*unlock)(vlc_gl_t *);
};

enum {
    VLC_OPENGL,
    VLC_OPENGL_ES,
    VLC_OPENGL_ES2,
};

VLC_EXPORT( vlc_gl_t *, vlc_gl_Create, (struct vout_window_t *, unsigned, const char *) ) LIBVLC_USED;
VLC_EXPORT( void, vlc_gl_Destroy, (vlc_gl_t *) );

static inline int vlc_gl_MakeCurrent(vlc_gl_t *gl)
{
    return gl->makeCurrent(gl);
}

static inline int vlc_gl_Lock(vlc_gl_t *gl)
{
    return (gl->lock != NULL) ? gl->lock(gl) : VLC_SUCCESS;
}

static inline void vlc_gl_Unlock(vlc_gl_t *gl)
{
    if (gl->unlock != NULL)
        gl->unlock(gl);
}

static inline void vlc_gl_Swap(vlc_gl_t *gl)
{
    gl->swap(gl);
}

#endif /* VLC_GL_H */
