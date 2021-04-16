/*
 * log-malloc-simple
 *	Malloc logging library with backtrace and byte-exact memory tracking.
 * 
 * Author: András Schmidt
 * https://github.com/rizsi
 *
 * Based on previous implementations by other authors. See their comments below:
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
#include <stdbool.h>
#include <stdlib.h>
#include <execinfo.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <fcntl.h>
#include <signal.h>
#include <errno.h>
#include <malloc.h>

#include <dlfcn.h>
#include <assert.h>


/* init constants */
#define LOG_MALLOC_INIT_NULL		0xFAB321
#define LOG_MALLOC_INIT_STARTED		0xABCABC
#define LOG_MALLOC_INIT_DONE		0x123FAB
#define LOG_MALLOC_FINI_DONE		0xFAFBFC

/* global context */
typedef struct log_malloc_ctx_s {
	sig_atomic_t init_done;
	int memlog_fd;
	bool memlog_disabled;
} log_malloc_ctx_t;

#define LOG_MALLOC_CTX_INIT			\
	{					\
		LOG_MALLOC_INIT_NULL,		\
		LOG_MALLOC_TRACE_FD,		\
		false,				\
	}

void log_malloc_write(const char * str, int len);


/* config */
#define LOG_BUFSIZE		4096
#define MAX_PADDING		1024
#define LOG_MALLOC_TRACE_FD		1022
#define LOG_MALLOC_BACKTRACE_COUNT	7

/* handler declarations */
static void *(*real_malloc)(size_t size)	= NULL;
static void  (*real_free)(void *ptr)		= NULL;
static void *(*real_realloc)(void *ptr, size_t size)	= NULL;
static void *(*real_calloc)(size_t nmemb, size_t size)	= NULL;
static void *(*real_memalign)(size_t boundary, size_t size)	= NULL;
static int   (*real_posix_memalign)(void **memptr, size_t alignment, size_t size)	= NULL;
static void *(*real_valloc)(size_t size)	= NULL;
static void *(*real_pvalloc)(size_t size)	= NULL;
static void *(*real_aligned_alloc)(size_t alignment, size_t size)	= NULL;

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

int myStrlen(const char * str)
{
	int ret=0;
	while(*str!=0)
	{
		str++;
		ret++;
	}
	return ret;
}
/*
 *  LIBRARY INIT/FINI FUNCTIONS
 */
/**
 * Copy data from file to the log.
 */
static inline void copyfile(const char *head,
	const char *path, int outfd)
{
	int w;
	int fd = -1;
	char buf[BUFSIZ];
	ssize_t len = 0;
	size_t head_len=myStrlen(head);
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
    	if(!g_ctx.memlog_disabled)
	{
		write(g_ctx.memlog_fd, buf, size);
        }
}

struct backtrace_struct {
	int nptrs;
	void * buffer[LOG_MALLOC_BACKTRACE_COUNT + 1];	
};

/** During initialization while the real_* pointer are being set up we can not pass malloc calls to the real
 * malloc.
 * During this time malloc calls allocate memory in this area.
 * After initialization is done this buffer is no longer used.
 */
#define STATIC_SIZE 1024
static char static_buffer[STATIC_SIZE];
static int static_pointer=0;
static bool loggedError=false;


static inline void log_mem(const char * method, void *ptr, size_t size, struct backtrace_struct * bt)
{
	/* Prevent preparing the output in memory in case the output is already closed */
	if(!g_ctx.memlog_disabled)
	{
		char buf[LOG_BUFSIZE];
		int len = snprintf(buf, sizeof(buf), "+ %s %zu %p\n", method,
			size, ptr);
			if(bt!=NULL && bt->nptrs>0)
			{
				char ** names=backtrace_symbols(&(bt->buffer[1]), bt->nptrs-1);
				if(names!=NULL)
				{
					for(int i=1;i<bt->nptrs;++i)
					{
						if(names[i]==NULL)
						{
							names[i]="?";
						}
						len+=snprintf(buf+len, sizeof(buf)-len-2, "%s\n", names[i-1]);
					}
					free(names);
				}
				else
				{
					for(int i=1;i<bt->nptrs;++i)
					{
						len+=snprintf(buf+len, sizeof(buf)-len-2, "%016lx\n", (long)bt->buffer[i]);
					}
				}
			}
			len+=snprintf(buf+len, sizeof(buf)-len, "-\n");
			write_log(buf, len);
        }
	return;
}

static void *__init_lib(void)
{
	/* check already initialized */
	if(!__sync_bool_compare_and_swap(&g_ctx.init_done,
		LOG_MALLOC_INIT_NULL, LOG_MALLOC_INIT_STARTED))
	{
		return 0;
	}
        int w = write(g_ctx.memlog_fd, "Init\n", 5);
        /* auto-disable trace if file is not open  */
        if(w == -1 && errno == EBADF)
        {
            write(STDERR_FILENO,"1022CLOSE\n",10);
            g_ctx.memlog_disabled = true;
        }else
        {
            write(STDERR_FILENO,"1022OPEN\n",9);
            g_ctx.memlog_disabled = false;
        }
	/* get real functions pointers */
	DL_RESOLVE(malloc);
	DL_RESOLVE(calloc);
	DL_RESOLVE(free);
	DL_RESOLVE(realloc);
	DL_RESOLVE(memalign);
	DL_RESOLVE(posix_memalign);
	DL_RESOLVE(valloc);
	DL_RESOLVE(pvalloc);
	DL_RESOLVE(aligned_alloc);
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
		copyfile("# MAPS\n", "/proc/self/maps", g_ctx.memlog_fd);
/*		s = readlink("/proc/self/maps", path, sizeof(path));
		if(s > 1)
		{
			path[s] = '\0';
			s = snprintf(buf, sizeof(buf), "# MAPS %s\n", path);
			write_log(buf, s);
	}
	*/

		s = snprintf(buf, sizeof(buf), "+ INIT \n-\n");
		log_mem("INIT", &static_buffer, static_pointer, NULL);
//		write_log(buf, s);
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

		s = snprintf(buf, sizeof(buf), "+ FINI\n-\n");
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

/// On this thread we are currently writing a trace event so prevent self-recursion
static __thread int in_trace = 0;

/*
 *  INTERNAL FUNCTIONS
 */
#define CREATE_BACKTRACE(BACKTRACE_STRUCT) (BACKTRACE_STRUCT).nptrs=backtrace(((BACKTRACE_STRUCT).buffer), LOG_MALLOC_BACKTRACE_COUNT)
//#define CREATE_BACKTRACE(BACKTRACE_STRUCT) (BACKTRACE_STRUCT).nptrs=10

static inline void * calloc_static(size_t nmemb, size_t size)
{
	void * ret=static_buffer+static_pointer;
	static_pointer+=size*nmemb;
	if(static_pointer>STATIC_SIZE)
	{
		return NULL;
	}
	int i;
	for(i=0;i<size;++i)
	{
		*(((char *)ret)+i)=0;
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
		return calloc_static(size, 1);
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
		if(ptr!=NULL)
		{
			log_mem("realloc_free", ptr, prevSize, &bt);
		}
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
void *pvalloc(size_t size)
{
	if(!DL_RESOLVE_CHECK(pvalloc))
		return NULL;
	void * ret=real_pvalloc(size);
	if(!in_trace)
	{
		in_trace=1;
		struct backtrace_struct bt;
		CREATE_BACKTRACE(bt);
		size_t sizeAllocated=malloc_usable_size(ret);
		log_mem("pvalloc", ret, sizeAllocated, &bt);
		in_trace=0;
	}
	return ret;
}

void *aligned_alloc(size_t alignment, size_t size)
{
	if(!DL_RESOLVE_CHECK(aligned_alloc))
		return NULL;
	void * ret=real_aligned_alloc(alignment, size);
	if(!in_trace)
	{
		in_trace=1;
		struct backtrace_struct bt;
		CREATE_BACKTRACE(bt);
		size_t sizeAllocated=malloc_usable_size(ret);
		log_mem("aligned_alloc", ret, sizeAllocated, &bt);
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
		struct backtrace_struct bt;
		CREATE_BACKTRACE(bt);
		size_t size=malloc_usable_size(ptr);
		log_mem("free", ptr, size, &bt);
		in_trace=0;
	}
	real_free(ptr);
}

/* EOF */
