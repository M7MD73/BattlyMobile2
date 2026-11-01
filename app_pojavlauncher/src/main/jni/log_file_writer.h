#ifndef BATTLY_LOG_FILE_WRITER_H
#define BATTLY_LOG_FILE_WRITER_H

#include <errno.h>
#include <stddef.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>

#ifndef BATTLY_LOG_FDATASYNC
#define BATTLY_LOG_FDATASYNC fdatasync
#endif

typedef struct {
    uint64_t synced_at_ns;
    size_t unsynced_bytes;
} battly_log_write_state;

/* Writes remain immediately readable; only expensive durability barriers are coalesced.
 * The caller serializes access and flushes before replacing/closing the descriptor. */
static inline int battly_log_write(int fd, const char *data, size_t length,
                                  battly_log_write_state *state) {
    size_t offset = 0;
    while (offset < length) {
        ssize_t written = write(fd, data + offset, length - offset);
        if (written < 0 && errno == EINTR) continue;
        if (written <= 0) return -1;
        offset += (size_t) written;
    }
    state->unsynced_bytes += length;
    struct timespec now;
    int clock_failed = clock_gettime(CLOCK_MONOTONIC, &now);
    uint64_t ns = clock_failed ? 0 : (uint64_t) now.tv_sec * 1000000000ULL + now.tv_nsec;
    if (clock_failed || state->synced_at_ns == 0
            || state->unsynced_bytes >= 65536
            || ns - state->synced_at_ns >= 250000000ULL) {
        if (BATTLY_LOG_FDATASYNC(fd) != 0) return -1;
        state->synced_at_ns = ns;
        state->unsynced_bytes = 0;
    }
    return 0;
}
#endif
