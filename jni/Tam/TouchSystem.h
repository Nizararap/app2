#pragma once

#include "imgui.h"
#include "IL2CppSDKGenerator/Il2Cpp.h"
#include "Tools/Tools.h"
#include <sys/mman.h>
#include <unistd.h>

extern bool clearMousePos;

enum TouchPhase : int {
    Began = 0,
    Moved = 1,
    Stationary = 2,
    Ended = 3,
    Canceled = 4,
};

struct UnityEngine_Touch_Fields {
    int32_t m_FingerId;
    Vector2 m_Position;
    Vector2 m_RawPosition;
    Vector2 m_PositionDelta;
    float m_TimeDelta;
    int32_t m_TapCount;
    int32_t m_Phase;
    int32_t m_Type;
    float m_Pressure;
    float m_maximumPossiblePressure;
    float m_Radius;
    float m_RadiusVariance;
    float m_AltitudeAngle;
    float m_AzimuthAngle;
};

// Pointer ke Fungsi Original Unity
int (*o_get_touchCount)();
UnityEngine_Touch_Fields (*o_GetTouch)(void*, int);
Vector3 (*o_get_mousePosition)(void*);
bool (*o_GetMouseButton)(void*, int);

// ========================================================
// HOOK FUNGSI
// ========================================================
int hook_get_touchCount() {
    if (ImGui::GetCurrentContext() != nullptr && ImGui::GetIO().WantCaptureMouse) {
        return 0;
    }
    return o_get_touchCount();
}

UnityEngine_Touch_Fields hook_GetTouch(void* instance, int index) {
    UnityEngine_Touch_Fields touch = o_GetTouch(instance, index);
    if (ImGui::GetCurrentContext() != nullptr && ImGui::GetIO().WantCaptureMouse) {
        touch.m_Phase = TouchPhase::Canceled;
        touch.m_Position = Vector2{0, 0};
    }
    return touch;
}

Vector3 hook_get_mousePosition(void* instance) {
    if (ImGui::GetCurrentContext() != nullptr && ImGui::GetIO().WantCaptureMouse) {
        return Vector3{0, 0, 0};
    }
    return o_get_mousePosition(instance);
}

bool hook_GetMouseButton(void* instance, int button) {
    if (ImGui::GetCurrentContext() != nullptr && ImGui::GetIO().WantCaptureMouse) {
        return false;
    }
    return o_GetMouseButton(instance, button);
}

static inline bool SafeDobbyHook(void *target, void *replace, void **backup) {
    if (!target) return false;
    long page_size = sysconf(_SC_PAGESIZE);
    uintptr_t page_start = (uintptr_t)target & ~(page_size - 1);
    if (mprotect((void*)page_start, page_size, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) 
        return false;
    int ret = DobbyHook(target, replace, (void**)backup);
    mprotect((void*)page_start, page_size, PROT_READ | PROT_EXEC);
    return (ret == 0);
}

void InitTouchHooks() {
    void* Input_get_touchCount = IL2Cpp::Il2CppGetMethodOffset("UnityEngine.dll", "UnityEngine", "Input", "get_touchCount");
    void* Input_GetTouch = IL2Cpp::Il2CppGetMethodOffset("UnityEngine.dll", "UnityEngine", "Input", "GetTouch", 1);
    void* Input_get_mousePosition = IL2Cpp::Il2CppGetMethodOffset("UnityEngine.dll", "UnityEngine", "Input", "get_mousePosition");
    void* Input_GetMouseButton = IL2Cpp::Il2CppGetMethodOffset("UnityEngine.dll", "UnityEngine", "Input", "GetMouseButton", 1);

    SafeDobbyHook(Input_get_touchCount, (void*)hook_get_touchCount, (void**)&o_get_touchCount);
    SafeDobbyHook(Input_GetTouch, (void*)hook_GetTouch, (void**)&o_GetTouch);
    SafeDobbyHook(Input_get_mousePosition, (void*)hook_get_mousePosition, (void**)&o_get_mousePosition);
    SafeDobbyHook(Input_GetMouseButton, (void*)hook_GetMouseButton, (void**)&o_GetMouseButton);
}

// ========================================================
// FUNGSI PEMBACAAN SENTUHAN UNTUK IMGUI
// ========================================================
void ImGui_GetTouch(ImGuiIO* io, int screenHeight) {
    if (o_get_touchCount != nullptr && o_GetTouch != nullptr) {
        if (o_get_touchCount() > 0) {
            UnityEngine_Touch_Fields touch = o_GetTouch(nullptr, 0);
            io->MousePos = ImVec2(touch.m_Position.x, screenHeight - touch.m_Position.y);
            if (touch.m_Phase == TouchPhase::Began || touch.m_Phase == TouchPhase::Moved || touch.m_Phase == TouchPhase::Stationary) {
                io->MouseDown[0] = true;
            } else if (touch.m_Phase == TouchPhase::Ended || touch.m_Phase == TouchPhase::Canceled) {
                io->MouseDown[0] = false;
                clearMousePos = true;
            }
        } else {
            io->MouseDown[0] = false;
            clearMousePos = true;
        }
    }
}