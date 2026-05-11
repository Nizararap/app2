#include <android/log.h>
#include <libgen.h>
#include <unistd.h>
#include <sys/mman.h>
#include <cstdio>
#include <cstring>
#include <inttypes.h>
#include "../Includes/obfuscate.h"
#include "Tools.h"

void Tools::Hook(void *target, void *replace, void **backup) {
    unsigned long page_size = sysconf(_SC_PAGESIZE);
    unsigned long size = page_size * sizeof(uintptr_t);
    void *p = (void *)((uintptr_t)target - ((uintptr_t)target % page_size) - page_size);
    if (mprotect(p, (size_t)size, PROT_EXEC | PROT_READ | PROT_WRITE) == 0) {
        DobbyHook(target, replace, backup);
    }
}

uintptr_t Tools::GetBaseAddress(const char *name) {
    uintptr_t base = 0;
    char line[512];

    FILE *f = fopen(OBFUSCATE("/proc/self/maps"), OBFUSCATE("r"));
    if (!f) return 0;

    while (fgets(line, sizeof(line), f)) {
        uintptr_t tmpBase;
        char tmpName[256];
        if (sscanf(line, "%" PRIXPTR "-%*" PRIXPTR "%*s %*s %*s %*s %s", &tmpBase, tmpName) > 0) {
            if (!strcmp(basename(tmpName), name)) {
                base = tmpBase;
                break;
            }
        }
    }
    fclose(f);
    return base;
}