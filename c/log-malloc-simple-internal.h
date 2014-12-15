#ifndef LOG_MALLOC2_INTERNAL_H
#define LOG_MALLOC2_INTERNAL_H
/*
 * log-malloc-simple internal data structures
 *	Malloc logging library with backtrace and byte-exact memory tracking.
 * 
 * Author: Andrew Schmidt https://github.com/rizsi
 * Base on work of Author: Samuel Behan <_samuel_._behan_(at)_dob_._sk> (C) 2013-2014
 *
 * License: GNU LGPLv3 (http://www.gnu.org/licenses/lgpl.html)
 *
 */

#include <stdio.h>
#include <stdint.h>

#include <stdbool.h>
#include <pthread.h>

/* pthread shortcuts */
# define LOCK_INIT()	1
# define LOCK(lock)	(pthread_mutex_trylock(&(lock)) == 0)
# define UNLOCK(lock)	(pthread_mutex_unlock(&(lock)))


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
	pthread_mutex_t loglock;
} log_malloc_ctx_t;

#define LOG_MALLOC_CTX_INIT			\
	{					\
		LOG_MALLOC_INIT_NULL,		\
		LOG_MALLOC_TRACE_FD,		\
		false,				\
		PTHREAD_MUTEX_INITIALIZER,	\
	}

/* API function */
log_malloc_ctx_t *log_malloc_ctx_get(void);
void log_malloc_write(const char * str, int len);
#endif