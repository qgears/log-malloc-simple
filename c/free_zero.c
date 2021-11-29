/*
 * free_zero
 *	A patch for malloc API that zeroes out all freed memory.
 *      This is useful as a debug tool. Access to freed memory will most likely cause Null pointer access or other exception in the program. Thus detection of memory usage after free becomes possible.
 * 
 * Author: András Schmidt
 * https://github.com/rizsi
 *
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
#include <string.h>

#include <dlfcn.h>
#include <assert.h>

/* init constants */
#define LOG_MALLOC_INIT_NULL		0xFAB321
#define LOG_MALLOC_INIT_STARTED		0xABCABC
#define LOG_MALLOC_INIT_DONE		0x123FAB
#define LOG_MALLOC_FINI_DONE		0xFAFBFC

/* handler declarations */
static void  (*real_free)(void *ptr)		= NULL;

/* DL resolving */
#define DL_RESOLVE(fn)	\
	((!real_ ## fn) ? (real_ ## fn = dlsym(RTLD_NEXT, # fn)) : (real_ ## fn = ((void *)0x1)))
#define DL_RESOLVE_CHECK(fn)	\
	((!real_ ## fn) ? __init_lib() : ((void *)0x1))

/*
 *  INTERNAL API FUNCTIONS
 */

/*
 *  LIBRARY INIT/FINI FUNCTIONS
 */

/** During initialization while the real_* pointer are being set up we can not pass malloc calls to the real
 * malloc.
 * During this time malloc calls allocate memory in this area.
 * After initialization is done this buffer is no longer used.
 */
#define STATIC_SIZE 1024
static char static_buffer[STATIC_SIZE];
static int static_pointer=0;
static sig_atomic_t init_done=LOG_MALLOC_INIT_NULL;


static void *__init_lib(void)
{
	/* check already initialized */
	if(!__sync_bool_compare_and_swap(&init_done,
		LOG_MALLOC_INIT_NULL, LOG_MALLOC_INIT_STARTED))
	{
		return 0;
	}
	/* get real functions pointers */
	DL_RESOLVE(free);
	__sync_bool_compare_and_swap(&init_done,
		LOG_MALLOC_INIT_STARTED, LOG_MALLOC_INIT_DONE);
	//TODO: call backtrace here to init itself

	return (void *)0x01;
}

static void __attribute__ ((constructor))free_zero_init(void)
{
	__init_lib();
  	return;
}

static void __fini_lib(void)
{
	/* check already finalized */
	if(!__sync_bool_compare_and_swap(&init_done,
		LOG_MALLOC_INIT_DONE, LOG_MALLOC_FINI_DONE))
		return;

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
void free(void *ptr)
{
	if(ptr!=NULL)
	{
		size_t size=malloc_usable_size(ptr);
		memset(ptr, 0, size);
	}
	real_free(ptr);
}

/* EOF */
