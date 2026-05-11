#pragma once

#include <jni.h>
#include "Dobby/dobby.h"

namespace Tools {
    void Hook(void *target, void *replace, void **backup);
    uintptr_t GetBaseAddress(const char *name);
}