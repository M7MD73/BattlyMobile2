#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <assert.h>

static unsigned barriers;
static int counted_sync(int fd) { barriers++; return fdatasync(fd); }
#define BATTLY_LOG_FDATASYNC counted_sync
#include "../../app_pojavlauncher/src/main/jni/log_file_writer.h"

static uint64_t now_ns(void) {
    struct timespec now;
    assert(clock_gettime(CLOCK_MONOTONIC, &now) == 0);
    return (uint64_t) now.tv_sec * 1000000000ULL + now.tv_nsec;
}

int main(int argc, char **argv) {
    if (argc != 2) return 2;
    char payload[256]; memset(payload, 'L', sizeof(payload)); payload[255] = '\n';
    const int count = 2000;
    double times[2];
    for (int batched = 0; batched < 2; batched++) {
        int fd = open(argv[1], O_CREAT | O_TRUNC | O_RDWR, 0600);
        assert(fd >= 0);
        battly_log_write_state state = {0};
        barriers = 0;
        uint64_t start = now_ns();
        for (int i = 0; i < count; i++) {
            if (batched) assert(battly_log_write(fd, payload, sizeof(payload), &state) == 0);
            else {
                assert(write(fd, payload, sizeof(payload)) == sizeof(payload));
                assert(counted_sync(fd) == 0);
            }
        }
        assert(counted_sync(fd) == 0);
        times[batched] = (now_ns() - start) / 1000000.0;
        struct stat st; assert(fstat(fd, &st) == 0);
        assert(st.st_size == (off_t) count * sizeof(payload));
        assert(lseek(fd, 0, SEEK_SET) == 0);
        char readback[sizeof(payload)];
        for (int i = 0; i < count; i++) {
            assert(read(fd, readback, sizeof(readback)) == sizeof(readback));
            assert(memcmp(payload, readback, sizeof(payload)) == 0);
        }
        assert(read(fd, readback, 1) == 0);
        printf("%s: %.3f ms, %u durability barriers, %d bytes preserved\n",
                batched ? "batched" : "per-write", times[batched], barriers, count * (int) sizeof(payload));
        if (batched) {
            // Also verify the time-based barrier when byte threshold is not reached.
            state.synced_at_ns = now_ns() - 300000000ULL;
            unsigned before = barriers;
            assert(battly_log_write(fd, "x", 1, &state) == 0);
            assert(barriers == before + 1);
        }
        assert(close(fd) == 0);
    }
    assert(unlink(argv[1]) == 0);
    printf("writer speedup: %.2fx (I/O microbenchmark, not game FPS)\n", times[0] / times[1]);
    return 0;
}
