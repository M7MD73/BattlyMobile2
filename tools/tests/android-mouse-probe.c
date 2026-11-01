/* Emulator-only virtual USB mouse. Requires adb root; never packaged in the app. */
#include <linux/uinput.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static void emit(int fd, unsigned short type, unsigned short code, int value) {
    struct input_event event = {0};
    event.type = type;
    event.code = code;
    event.value = value;
    if (write(fd, &event, sizeof(event)) != sizeof(event)) { perror("write"); exit(1); }
}

int main(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("uinput"); return 1; }
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    int buttons[] = {BTN_LEFT, BTN_RIGHT, BTN_MIDDLE, BTN_SIDE, BTN_EXTRA};
    for (unsigned i = 0; i < sizeof(buttons) / sizeof(buttons[0]); i++)
        ioctl(fd, UI_SET_KEYBIT, buttons[i]);
    ioctl(fd, UI_SET_EVBIT, EV_REL);
    int axes[] = {REL_X, REL_Y, REL_WHEEL, REL_HWHEEL};
    for (unsigned i = 0; i < sizeof(axes) / sizeof(axes[0]); i++)
        ioctl(fd, UI_SET_RELBIT, axes[i]);
    struct uinput_setup setup = {0};
    snprintf(setup.name, UINPUT_MAX_NAME_SIZE, "Battly QA USB Mouse");
    setup.id.bustype = BUS_USB;
    setup.id.vendor = 0x1209;
    setup.id.product = 0xba77;
    if (ioctl(fd, UI_DEV_SETUP, &setup) || ioctl(fd, UI_DEV_CREATE)) {
        perror("create"); close(fd); return 1;
    }
    puts("READY: move dx dy | button index state | wheel x y | quit");
    fflush(stdout);
    char line[128], command[32];
    int a, b;
    while (fgets(line, sizeof(line), stdin)) {
        if (!strncmp(line, "quit", 4)) break;
        if (sscanf(line, "%31s %d %d", command, &a, &b) != 3) continue;
        if (!strcmp(command, "move")) {
            emit(fd, EV_REL, REL_X, a); emit(fd, EV_REL, REL_Y, b);
        } else if (!strcmp(command, "wheel")) {
            emit(fd, EV_REL, REL_HWHEEL, a); emit(fd, EV_REL, REL_WHEEL, b);
        } else if (!strcmp(command, "button") && a >= 0 && a < 5 && (b == 0 || b == 1)) {
            emit(fd, EV_KEY, buttons[a], b);
        } else continue;
        emit(fd, EV_SYN, SYN_REPORT, 0);
        printf("SENT %s", line); fflush(stdout);
    }
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
