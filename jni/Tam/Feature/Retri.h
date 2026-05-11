#pragma once

#include <vector>
#include <mutex>
#include <cmath>
#include "GameClass.h"
#include "../IL2CppSDKGenerator/Vector3.h"
#include "../IL2CppSDKGenerator/Il2Cpp.h"

// Toggle global
extern bool AutoRetriBuff;
extern bool AutoRetriLord;
extern bool AutoRetriTurtle;
extern bool AutoRetriLitho;

// Snapshot monster
struct MonsterSnapshot {
    int     id;
    Vector3 position;
    int     hp;
    int     hpMax;
};

static std::vector<MonsterSnapshot> g_MonsterSnapshot;
static std::mutex g_MonsterSnapshotMutex;

static void ClearMonster() {
    std::lock_guard<std::mutex> lock(g_MonsterSnapshotMutex);
    g_MonsterSnapshot.clear();
}
// Struct minimal untuk Dictionary<int, void*>
struct monoDictEntry {
    int hashCode;
    int next;
    int key;
    void* value;
};

struct monoDictionary {
    void* klass;
    void* monitor;
    monoArray<int>* buckets;
    monoArray<monoDictEntry>* entries;
    int count;
    int version;
    int freeList;
    int freeCount;
};

struct Vector3_POD {
    float x, y, z;
};

// Offset eksternal
extern uintptr_t off_BattleManager_m_dicMonsterShow;
extern uintptr_t off_EntityBase_m_ID;
extern uintptr_t off_EntityBase_m_Hp;
extern uintptr_t off_EntityBase_m_HpMax;
extern uintptr_t off_EntityBase_m_Level;
extern uintptr_t off_BattleManager_m_LocalPlayerShow;
extern uintptr_t off_LogicPlayer_m_PlayerData;
extern uintptr_t off_PlayerData__killNum;
extern uintptr_t off_PlayerData__assistNum;
extern uintptr_t off_LogicPlayer__KillWildTimes;
extern uintptr_t off_ShowSelfPlayer_TryUseSkill2;
extern uintptr_t off_BattleData_m_BattleBridge;
extern uintptr_t off_BattleBridge_bStartBattle;
extern uintptr_t off_LogicBattleManager_Instance;

// Pointer fungsi asli
extern int (*orig_TryUseSkill2)(void*, int, Vector3_POD, bool, Vector3_POD, bool, bool, bool, bool, uint32_t, void*);

// ==================== Snapshot Monster ====================
static void TakeMonsterSnapshot() {
if (!g_IsBattleStarted) {
        std::lock_guard<std::mutex> lock(g_MonsterSnapshotMutex);
        g_MonsterSnapshot.clear();
        return;
    }
    std::vector<MonsterSnapshot> newSnap;

    void* bm = nullptr;
    IL2Cpp::Il2CppGetStaticFieldValue("Assembly-CSharp.dll", "", "BattleManager", "Instance", &bm);
    if (!bm) {
        std::lock_guard<std::mutex> lock(g_MonsterSnapshotMutex);
        g_MonsterSnapshot.clear();
        return;
    }

    auto m_BattleBridge = *(uintptr_t*)(off_BattleData_m_BattleBridge);
    if (!m_BattleBridge) return;
    bool bStartBattle = ((bool(*)(void*))off_BattleBridge_bStartBattle)((void*)m_BattleBridge);
    if (!bStartBattle) {
        std::lock_guard<std::mutex> lock(g_MonsterSnapshotMutex);
        g_MonsterSnapshot.clear();
        return;
    }

    auto dicMonster = *(monoDictionary**)((uintptr_t)bm + off_BattleManager_m_dicMonsterShow);
    if (!dicMonster) return;

    auto entries = dicMonster->entries;
    if (!entries) return;

    int entryCount = entries->max_length;
    // VALIDASI LIMIT
    if (entryCount > 0 && entryCount < 2000) {
        monoDictEntry* entryArray = (monoDictEntry*)(&entries->vector[0]);

        for (int i = 0; i < entryCount; i++) {
            if (entryArray[i].hashCode < 0) continue;
            
            void* monster = entryArray[i].value;
            // VALIDASI POINTER EKSTRIM
            if (!monster || (uintptr_t)monster <= 0xFFFFF) continue;

            bool bDeath = *(bool*)((uintptr_t)monster + off_EntityBase_m_bDeath);
            if (bDeath) continue;
            bool bSameCamp = *(bool*)((uintptr_t)monster + off_EntityBase_m_bSameCampType);
            if (bSameCamp) continue;

            MonsterSnapshot snap;
            snap.id       = *(int*)((uintptr_t)monster + off_EntityBase_m_ID);
            snap.position = *(Vector3*)((uintptr_t)monster + off_ShowEntity__Position);
            snap.hp       = *(int*)((uintptr_t)monster + off_EntityBase_m_Hp);
            snap.hpMax    = *(int*)((uintptr_t)monster + off_EntityBase_m_HpMax);
            newSnap.push_back(snap);
        }
    }

    std::lock_guard<std::mutex> lock(g_MonsterSnapshotMutex);
    g_MonsterSnapshot = std::move(newSnap);
}

// ==================== Helper GetPlayerRealSelf ====================
static uintptr_t GetPlayerRealSelf_retri() {
    uintptr_t instance = *(uintptr_t*)(off_LogicBattleManager_Instance);
    if (!instance) return 0;
    auto getPlayerRealSelf = (uintptr_t(*)(void*))IL2Cpp::Il2CppGetMethodOffset(
        "Assembly-CSharp.dll", "", "LogicBattleManager", "GetPlayerRealSelf", 0);
    if (getPlayerRealSelf) return getPlayerRealSelf((void*)instance);
    return 0;
}

// ==================== Kalkulasi Damage ====================
static int CalculateRetriDamage(int m_Level, int _KillWildTimes, int _killNum, int _assistNum) {
    if ((_KillWildTimes + _killNum + _assistNum) < 5)
        return 520 + (80 * m_Level);
    else
        return (int)(1.521f * (double)(520 + (80 * m_Level)));
}

// ==================== Proses Retribution ====================
static void ProcessRetribution(void* instance) {
    if (!AutoRetriBuff && !AutoRetriLord && !AutoRetriTurtle && !AutoRetriLitho) return;
    if (!instance) return;

    void* bm = nullptr;
    IL2Cpp::Il2CppGetStaticFieldValue("Assembly-CSharp.dll", "", "BattleManager", "Instance", &bm);
    if (!bm) return;

    auto localPlayerShow = *(uintptr_t*)((uintptr_t)bm + off_BattleManager_m_LocalPlayerShow);
    if (!localPlayerShow || (uintptr_t)instance != localPlayerShow) return;

    Vector3 myPos = *(Vector3*)((uintptr_t)localPlayerShow + off_ShowEntity__Position);
    int m_Level = *(int*)((uintptr_t)localPlayerShow + off_EntityBase_m_Level);

    uintptr_t playerSelf = GetPlayerRealSelf_retri();
    int _KillWildTimes = 0, _killNum = 0, _assistNum = 0;
    if (playerSelf) {
        _KillWildTimes = *(int*)((uintptr_t)playerSelf + off_LogicPlayer__KillWildTimes);
        auto m_PlayerData = *(uintptr_t*)((uintptr_t)playerSelf + off_LogicPlayer_m_PlayerData);
        if (m_PlayerData) {
            _killNum   = *(int*)((uintptr_t)m_PlayerData + off_PlayerData__killNum);
            _assistNum = *(int*)((uintptr_t)m_PlayerData + off_PlayerData__assistNum);
        }
    }
    int iDamage = CalculateRetriDamage(m_Level, _KillWildTimes, _killNum, _assistNum);

    std::vector<MonsterSnapshot> monsters;
    {
        std::lock_guard<std::mutex> lock(g_MonsterSnapshotMutex);
        monsters = g_MonsterSnapshot;
    }

    for (const auto& m : monsters) {
        float dist = Vector3::Distance(myPos, m.position);
        if (dist > 5.15f) continue;
        if (m.hp > iDamage) continue;

        bool isBoss = (m.id == 2002 || m.id == 2003 || m.id == 2110);
        bool shouldRetri = false;
        if (AutoRetriLord && isBoss) shouldRetri = true;
        else if (AutoRetriTurtle && isBoss) shouldRetri = true;
        else if (AutoRetriBuff && (m.id == 2004 || m.id == 2005)) shouldRetri = true;
        else if (AutoRetriLitho && (m.id == 2072)) shouldRetri = true;

        if (shouldRetri) {
            Vector3 dir = Vector3::Normalized(m.position - myPos);
            Vector3_POD sendDir = {dir.x, dir.y, dir.z};
            Vector3_POD zeroPos = {0,0,0};
            if (orig_TryUseSkill2) {
                orig_TryUseSkill2(instance, 20020, sendDir, false, zeroPos, false, false, false, false, 0, nullptr);
            }
            break;
        }
    }
}