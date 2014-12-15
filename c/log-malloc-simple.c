/*
 * log-malloc-simple
 *	Malloc logging library with backtrace and byte-exact memory tracking.
 * 
 * Author: András Schmidt
 * https://github.com/rizsi
 */ 
/*
 * Based on Author: Samuel Behan <_samuel_._behan_(at)_dob_._sk> (C) 2011-2014
 *	   partialy based on log-malloc from Ivan Tikhonov
 *
 * License: GNU LGPLv3 (http://www.gnu.org/licenses/lgpl.html)
 *
 * Web:
 *	http://devel.dob.sk/log-malloc2
 *	http://blog.dob.sk/category/devel/log-malloc2 (howto, tutorials)
 *	https://github.com/samsk/log-malloc2 (git repo)
 *
 */

/* Copyright (C) 2007 Ivan Tikhonov
   Ivan Tikhonov, http://www.brokestream.com, kefeer@netangels.ru

  This software is provided 'as-is', without any express or implied
  warranty.  In no event will the authors be held liable for any damages
  arising from the use of this software.

  Permission is granted to anyone to use this software for any purpose,
  including commercial applications, and to alter it and redistribute it
  freely, subject to the following restrictions:

  1. The origin of this software must not be misrepresented; you must not
     claim that you wrote the original software. If you use this software
     in a product, an acknowledgment in the product documentation would be
     appreciated but is not required.
  2. Altered source versions must be plainly marked as such, and must not be
     misrepresented as being the original software.
  3. This notice may not be removed or altered from any source distribution.

  Ivan Tikhonov, kefeer@brokestream.com

*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

/* needed for dlfcn.h */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE	1
#endif

#include <stdio.h>
#include <unistd.h>
#include <stdint.h>
#include <stdlib.h>
#include <execinfo.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <fcntl.h>
#include <signal.h>
#include <errno.h>
#include <malloc.h>

#include <dlfcn.h>

#include "log-malloc-simple.h"
#include "log-malloc-simple-internal.h"

/* config */
#define LOG_BUFSIZE		128
#define MAX_PADDING		1024

/* handler declarations */
static void *(*real_malloc)(size_t size)	= NULL;
static void  (*real_free)(void *ptr)		= NULL;
static void *(*real_realloc)(void *ptr, size_t size)	= NULL;
static void *(*real_calloc)(size_t nmemb, size_t size)	= NULL;
static void *(*real_memalign)(size_t boundary, size_t size)	= NULL;
static int   (*real_posix_memalign)(void **memptr, size_t alignment, size_t size)	= NULL;
static void *(*real_valloc)(size_t size)	= NULL;

/* DL resolving */
#define DL_RESOLVE(fn)	\
	((!real_ ## fn) ? (real_ ## fn = dlsym(RTLD_NEXT, # fn)) : (real_ ## fn = ((void *)0x1)))
#define DL_RESOLVE_CHECK(fn)	\
	((!real_ ## fn) ? __init_lib() : ((void *)0x1))

/* data context */
static log_malloc_ctx_t g_ctx = LOG_MALLOC_CTX_INIT;

/*
 *  INTERNAL API FUNCTIONS
 */
log_malloc_ctx_t *log_malloc_ctx_get(void)
{
	return &g_ctx;
}

/*
 *  LIBRARY INIT/FINI FUNCTIONS
 */
static inline void copyfile(const char *head, size_t head_len,
	const char *path, int outfd)
{
	int w;
	int fd = -1;
	char buf[BUFSIZ];
	ssize_t len = 0;

	// no warning here, it will be simply missing in log
	if((fd = open(path, 0)) == -1)
		return;

	w = write(outfd, head, head_len);
	// ignoring EINTR here, use SA_RESTART to fix if problem
	while((len = read(fd, buf, sizeof(buf))) > 0)
		w = write(outfd, buf, len);

	close(fd);
	return;
}
static inline void write_log(const char * buf, int size)
{
	int w = write(g_ctx.memlog_fd, buf, size);
	/* auto-disable trace if file is not open  */
	if(w == -1 && errno == EBADF)
	{
		g_ctx.memlog_disabled = true;
	}
}
void log_malloc_write(const char * buf, int size)
{
	write_log(buf, size);
}

static void *__init_lib(void)
{
	/* check already initialized */
	if(!__sync_bool_compare_and_swap(&g_ctx.init_done,
		LOG_MALLOC_INIT_NULL, LOG_MALLOC_INIT_STARTED))
	{
		return 0;
	}
	LOCK_INIT();

	/* get real functions pointers */
	DL_RESOLVE(malloc);
	DL_RESOLVE(calloc);
	DL_RESOLVE(free);
	DL_RESOLVE(realloc);
	DL_RESOLVE(memalign);
	DL_RESOLVE(posix_memalign);
	DL_RESOLVE(valloc);
	__sync_bool_compare_and_swap(&g_ctx.init_done,
		LOG_MALLOC_INIT_STARTED, LOG_MALLOC_INIT_DONE);
	//TODO: call backtrace here to init itself

	/* post-init status */
	if(!g_ctx.memlog_disabled)
	{
		int s, w;
		char path[256];
		char buf[LOG_BUFSIZE + sizeof(path)];

		s = snprintf(buf, sizeof(buf), "# PID %u\n", getpid());
		write_log(buf, s);

		s = readlink("/proc/self/exe", path, sizeof(path));
		if(s > 1)
		{
			path[s] = '\0';
			s = snprintf(buf, sizeof(buf), "# EXE %s\n", path);
			write_log(buf, s);
		}

		s = readlink("/proc/self/cwd", path, sizeof(path));
		if(s > 1)
		{
			path[s] = '\0';
			s = snprintf(buf, sizeof(buf), "# CWD %s\n", path);
			write_log(buf, s);
		}

		s = snprintf(buf, sizeof(buf), "+ INIT \n");
		write_log(buf, s);
	}
	return (void *)0x01;
}

static void __attribute__ ((constructor))log_malloc2_init(void)
{
	__init_lib();
  	return;
}

static void __fini_lib(void)
{
	/* check already finalized */
	if(!__sync_bool_compare_and_swap(&g_ctx.init_done,
		LOG_MALLOC_INIT_DONE, LOG_MALLOC_FINI_DONE))
		return;

	if(!g_ctx.memlog_disabled)
	{
		int s;
		char buf[LOG_BUFSIZE];
		const char maps_head[] = "# FILE /proc/self/maps\n";

		s = snprintf(buf, sizeof(buf), "+ FINI\n");
		write_log(buf, s);

		/* maps out here, because dynamic libs could by mapped during run */
//		copyfile(maps_head, sizeof(maps_head) - 1, g_maps_path, g_ctx.memlog_fd);
	}
	return;
}

static void __attribute__ ((destructor))log_malloc2_fini(void)
{
	__fini_lib();
  	return;
}

static __thread int in_trace = 0;

struct backtrace_struct {
	int nptrs;
	void * buffer[LOG_MALLOC_BACKTRACE_COUNT + 1];	
};
/*
 *  INTERNAL FUNCTIONS
 */
#define CREATE_BACKTRACE(BACKTRACE_STRUCT) (BACKTRACE_STRUCT).nptrs=backtrace(((BACKTRACE_STRUCT).buffer), LOG_MALLOC_BACKTRACE_COUNT)
//#define CREATE_BACKTRACE(BACKTRACE_STRUCT) (BACKTRACE_STRUCT).nptrs=10

int a;

static inline void log_mem(const char * method, void *ptr, size_t size, struct backtrace_struct * bt)
{
	/* prevent endless recursion, because inital backtrace call can involve some allocs */
	if(!g_ctx.memlog_disabled)
	{
		char buf[LOG_BUFSIZE];
		int len = snprintf(buf, sizeof(buf), "+ %s %zu %p\n", method,
			size, ptr);
			int w;
		/* try synced write */
		if(LOCK(g_ctx.loglock))
		{
			write_log(buf, len);
			if(bt!=NULL && bt->nptrs>0)
			{
				backtrace_symbols_fd(&(bt->buffer[1]), bt->nptrs-1, g_ctx.memlog_fd);
			}
			UNLOCK(g_ctx.loglock);
		}
	}
	return;
}
// TODO document static allocator - we need it while the real_* pointer are being set up.
#define STATIC_SIZE 1024
static char static_buffer[STATIC_SIZE];
static int static_pointer=0;
static inline void * malloc_static(size_t size)
{
	void * ret=static_buffer+static_pointer;
	static_pointer+=size;
	if(static_pointer>STATIC_SIZE)
	{
		return NULL;
	}
	return ret;
}
static inline void * calloc_static(size_t nmemb, size_t size)
{
	void * ret=static_buffer+static_pointer;
	static_pointer+=size*nmemb;
	if(static_pointer>STATIC_SIZE)
	{
		return NULL;
	}
	return ret;
}
/*
 *  LIBRARY FUNCTIONS
 */
void *malloc(size_t size)
{
	if(!DL_RESOLVE_CHECK(malloc))
	{
		return malloc_static(size);
	}
	void * ret=real_malloc(size);
	if(!in_trace)
	{
		in_trace=1;
		struct backtrace_struct bt;
		CREATE_BACKTRACE(bt);
		size_t sizeAllocated=malloc_usable_size(ret);
		log_mem("malloc", ret, sizeAllocated, &bt);
		in_trace=0;
	}
	return ret;
}
void *calloc(size_t nmemb, size_t size)
{
	if(!DL_RESOLVE_CHECK(calloc))
	{
		return calloc_static(nmemb, size);
	}
	void * ret=real_calloc(nmemb, size);
	if(!in_trace)
	{
		in_trace=1;
		struct backtrace_struct bt;
		CREATE_BACKTRACE(bt);
		size_t sizeAllocated=malloc_usable_size(ret);
		log_mem("calloc", ret, sizeAllocated, &bt);
		in_trace=0;
	}
	return ret;
}

void *realloc(void *ptr, size_t size)
{
	if(!DL_RESOLVE_CHECK(realloc))
		return NULL;
	size_t prevSize=malloc_usable_size(ptr);
	void * ret=real_realloc(ptr, size);
	if(!in_trace)
	{
		in_trace=1;
		struct backtrace_struct bt;
		CREATE_BACKTRACE(bt);
		log_mem("realloc_free", ptr, prevSize, &bt);
		size_t afterSize=malloc_usable_size(ret);
		log_mem("realloc_alloc", ret, afterSize, &bt);
		in_trace=0;
	}
	return ret;
}

void *memalign(size_t alignment, size_t size)
{
	if(!DL_RESOLVE_CHECK(memalign))
		return NULL;
	void * ret=real_memalign(alignment, size);
	if(!in_trace)
	{
		in_trace=1;
		struct backtrace_struct bt;
		CREATE_BACKTRACE(bt);
		size_t sizeAllocated=malloc_usable_size(ret);
		log_mem("memalign", ret, sizeAllocated, &bt);
		in_trace=0;
	}
	return ret;
}

int posix_memalign(void **memptr, size_t alignment, size_t size)
{
	if(!DL_RESOLVE_CHECK(posix_memalign))
		return ENOMEM;
	int ret=real_posix_memalign(memptr, alignment, size);
	if(!in_trace)
	{
		in_trace=1;
		struct backtrace_struct bt;
		CREATE_BACKTRACE(bt);
		size_t sizeAllocated=malloc_usable_size(*memptr);
		log_mem("posix_memalign", *memptr, sizeAllocated, &bt);
		in_trace=0;
	}
	return ret;
}

void *valloc(size_t size)
{
	if(!DL_RESOLVE_CHECK(valloc))
		return NULL;
	void * ret=real_valloc(size);
	if(!in_trace)
	{
		in_trace=1;
		struct backtrace_struct bt;
		CREATE_BACKTRACE(bt);
		size_t sizeAllocated=malloc_usable_size(ret);
		log_mem("valloc", ret, sizeAllocated, &bt);
		in_trace=0;
	}
	return ret;
}

void free(void *ptr)
{
	if(!DL_RESOLVE_CHECK(free))
	{
		// We can not log anything here because the log message would result another free call and it would fall into an endless loop
		return;
	}
	if(ptr!=NULL&&!in_trace)
	{
		in_trace=1;
		size_t size=malloc_usable_size(ptr);
		log_mem("free", ptr, size, NULL);
		in_trace=0;
	}
	real_free(ptr);
}

/* EOF */
