#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

static volatile sig_atomic_t stop_signal;

static void signal_received(int signal_number) {
    if (signal_number != SIGCHLD) stop_signal = signal_number;
}

static int write_all(int descriptor, const void *data, size_t size) {
    const unsigned char *bytes = data;
    while (size && !stop_signal) {
        ssize_t written = write(descriptor, bytes, size);
        if (written < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (!written) return -1;
        bytes += written;
        size -= (size_t)written;
    }
    return size ? -1 : 0;
}

static int dimension_value(unsigned long value) {
    return value >= 1 && value <= 1000 ? (int)value : -1;
}

static int dimension(const char *value) {
    char *end;
    errno = 0;
    long parsed = strtol(value, &end, 10);
    if (errno || *end || end == value || parsed < 0) return -1;
    return dimension_value((unsigned long)parsed);
}

/*
 * 标准输入是调用方唯一的写入通道，按帧区分终端字节与控制消息：
 * 帧头为 1 字节类型 + 2 字节大端长度。数据帧原样写入 master；
 * 尺寸帧载荷为大端 rows、cols 各 2 字节，由内核向前台进程组发送 SIGWINCH。
 * 未知类型按长度跳过，保持协议可扩展。
 */
enum { FRAME_DATA = 0, FRAME_RESIZE = 1, FRAME_HEADER_SIZE = 3, RESIZE_PAYLOAD_SIZE = 4 };

struct frame_reader {
    unsigned char header[FRAME_HEADER_SIZE];
    size_t header_size;
    unsigned char type;
    size_t remaining;
    unsigned char control[RESIZE_PAYLOAD_SIZE];
    size_t control_size;
};

static void apply_resize(int master, const unsigned char *payload) {
    int rows = dimension_value(((unsigned)payload[0] << 8) | payload[1]);
    int columns = dimension_value(((unsigned)payload[2] << 8) | payload[3]);
    if (rows < 0 || columns < 0) return;
    struct winsize size = {.ws_row = (unsigned short)rows, .ws_col = (unsigned short)columns};
    ioctl(master, TIOCSWINSZ, &size);
}

static int consume_input(struct frame_reader *reader, int master, const unsigned char *bytes, size_t size) {
    while (size) {
        if (reader->header_size < FRAME_HEADER_SIZE) {
            reader->header[reader->header_size++] = *bytes++;
            size--;
            if (reader->header_size == FRAME_HEADER_SIZE) {
                reader->type = reader->header[0];
                reader->remaining = ((size_t)reader->header[1] << 8) | reader->header[2];
                reader->control_size = 0;
            }
        } else {
            size_t chunk = reader->remaining < size ? reader->remaining : size;
            if (reader->type == FRAME_DATA) {
                if (write_all(master, bytes, chunk)) return -1;
            } else if (reader->type == FRAME_RESIZE) {
                for (size_t index = 0; index < chunk; ++index) {
                    if (reader->control_size < RESIZE_PAYLOAD_SIZE)
                        reader->control[reader->control_size++] = bytes[index];
                }
            }
            bytes += chunk;
            size -= chunk;
            reader->remaining -= chunk;
        }
        if (reader->header_size == FRAME_HEADER_SIZE && !reader->remaining) {
            if (reader->type == FRAME_RESIZE && reader->control_size == RESIZE_PAYLOAD_SIZE)
                apply_resize(master, reader->control);
            reader->header_size = 0;
        }
    }
    return 0;
}

static void terminate_group(pid_t child) {
    /* 会话首进程退出后仍清理组内后台作业，避免关闭终端留下执行中的子进程。 */
    kill(-child, SIGTERM);
    kill(child, SIGTERM);
    usleep(100000);
    kill(-child, SIGKILL);
    kill(child, SIGKILL);
}

int main(int argc, char **argv) {
    if (argc < 5 || strcmp(argv[3], "--")) {
        fprintf(stderr, "ETA_PTY_INVALID_ARGUMENT\n");
        return 64;
    }
    int rows = dimension(argv[1]);
    int columns = dimension(argv[2]);
    if (rows < 0 || columns < 0) {
        fprintf(stderr, "ETA_PTY_INVALID_SIZE\n");
        return 64;
    }
    struct sigaction action = {0};
    action.sa_handler = signal_received;
    sigemptyset(&action.sa_mask);
    sigaction(SIGTERM, &action, NULL);
    sigaction(SIGHUP, &action, NULL);
    sigaction(SIGINT, &action, NULL);
    sigaction(SIGCHLD, &action, NULL);
    signal(SIGPIPE, SIG_IGN);

    /* 父进程可能在注册死亡信号前退出，注册后再核对一次以封闭竞态。 */
    pid_t parent = getppid();
    if (prctl(PR_SET_PDEATHSIG, SIGTERM) < 0 || parent == 1 || getppid() != parent) return 71;
    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0 || grantpt(master) < 0 || unlockpt(master) < 0) {
        fprintf(stderr, "ETA_PTY_OPEN_FAILED\n");
        if (master >= 0) close(master);
        return 71;
    }
    char slave_name[256];
    if (ptsname_r(master, slave_name, sizeof(slave_name))) {
        close(master);
        return 71;
    }
    struct winsize size = {.ws_row = (unsigned short)rows, .ws_col = (unsigned short)columns};
    if (ioctl(master, TIOCSWINSZ, &size) < 0) {
        close(master);
        return 71;
    }
    pid_t child = fork();
    if (child < 0) {
        close(master);
        return 71;
    }
    if (!child) {
        pid_t wrapper = getppid();
        signal(SIGTERM, SIG_DFL);
        signal(SIGHUP, SIG_DFL);
        signal(SIGINT, SIG_DFL);
        signal(SIGCHLD, SIG_DFL);
        signal(SIGPIPE, SIG_DFL);
        if (prctl(PR_SET_PDEATHSIG, SIGHUP) < 0 || wrapper == 1 || getppid() != wrapper) _exit(71);
        if (setsid() < 0) _exit(71);
        int slave = open(slave_name, O_RDWR | O_NOCTTY);
        if (slave < 0 || ioctl(slave, TIOCSCTTY, 0) < 0) _exit(71);
        if (dup2(slave, STDIN_FILENO) < 0 || dup2(slave, STDOUT_FILENO) < 0 ||
            dup2(slave, STDERR_FILENO) < 0) _exit(71);
        if (slave > STDERR_FILENO) close(slave);
        close(master);
        execvp(argv[4], &argv[4]);
        fprintf(stderr, "ETA_PTY_EXEC_FAILED errno=%d\n", errno);
        _exit(127);
    }

    int status = 0;
    int reaped = 0;
    int child_exited = 0;
    int input_open = 1;
    struct frame_reader reader = {0};
    unsigned char buffer[8192];
    while (!stop_signal) {
        /* 先观察退出、最后再回收，清理进程组期间不会复用会话首进程的 PID。 */
        siginfo_t child_info = {0};
        if (!child_exited && !waitid(P_PID, child, &child_info, WEXITED | WNOHANG | WNOWAIT)
            && child_info.si_pid == child) child_exited = 1;
        struct pollfd descriptors[2] = {
            {.fd = master, .events = POLLIN},
            {.fd = input_open && !child_exited ? STDIN_FILENO : -1, .events = POLLIN}
        };
        int ready = poll(descriptors, 2, child_exited ? 0 : 250);
        if (ready < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (!ready && child_exited) break;
        if (descriptors[0].revents & (POLLIN | POLLHUP | POLLERR)) {
            ssize_t read_size = read(master, buffer, sizeof(buffer));
            if (read_size < 0 && errno == EINTR) continue;
            if (read_size <= 0 || write_all(STDOUT_FILENO, buffer, (size_t)read_size)) break;
        }
        if (descriptors[1].revents & (POLLIN | POLLHUP | POLLERR)) {
            ssize_t read_size = read(STDIN_FILENO, buffer, sizeof(buffer));
            if (read_size < 0 && errno == EINTR) continue;
            if (read_size <= 0) {
                /* 输入管道关闭按终端 EOF 处理，仍读取最后一批输出。 */
                const unsigned char eof = 4;
                write_all(master, &eof, 1);
                input_open = 0;
            } else if (consume_input(&reader, master, buffer, (size_t)read_size)) break;
        }
    }
    close(master);
    terminate_group(child);
    if (!reaped) {
        for (int attempt = 0; attempt < 25; ++attempt) {
            if (waitpid(child, &status, WNOHANG) == child) {
                reaped = 1;
                break;
            }
            usleep(20000);
        }
    }
    if (stop_signal) return 128 + stop_signal;
    if (!reaped) return 71;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return WIFSIGNALED(status) ? 128 + WTERMSIG(status) : 71;
}
