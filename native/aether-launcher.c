#include <signal.h>
#include <sys/prctl.h>
#include <unistd.h>

/* Keep the core's lifetime tied to the daemon, including an unexpected daemon death. */
int main(int argc, char **argv) {
    if (argc < 2) return 64;
    pid_t parent = getppid();
    if (parent == 1 || prctl(PR_SET_PDEATHSIG, SIGKILL) != 0) return 65;
    if (getppid() != parent) return 66;
    execv(argv[1], &argv[1]);
    return 67;
}
