#pragma once

#include "GameClass.h"
#include "../IL2CppSDKGenerator/Il2Cpp.h"
#include "Retri.h"
#include <cmath>
#include <vector>
#include <mutex>
#include <chrono>
#include <android/log.h>

#define AIM_TAG "AIMBOT"
#define AIM_LOGI(...) __android_log_print(ANDROID_LOG_INFO, AIM_TAG, __VA_ARGS__)

extern bool Aimbot;
extern bool LingManual;
extern bool AutoLing;
extern int ActiveComboHero;

extern int Target;
extern float RangeFOV;

extern bool LockHeroEnable;
extern std::string LockedHeroName;
static int CurrentLockedEntityID = -1;

extern uint64_t gusionWaitingForS1Hit;
extern int GusionStrictTargetID;

extern uint64_t kaditaWaitingForS1Hit;
extern int KaditaStrictTargetID;

extern uintptr_t off_Bullet_m_Id;
extern uintptr_t off_Bullet_transform;
extern uintptr_t off_Transform_get_position;
extern uintptr_t off_BattleManager_m_dicPlayerShow;
extern uintptr_t off_EntityBase_m_Hp;
extern uintptr_t off_EntityBase_m_HpMax;

extern int (*orig_TryUseSkill)(void*, int*, int, Vector3_POD, bool, Vector3_POD, bool, bool, bool, uint32_t, bool, uint32_t, uint32_t, void*);

static bool gusionComboActive = false;
static int gusionComboState = 0;
static uint64_t lastGusionCastTime = 0;

static inline void StartGusionCombo() {
    gusionComboActive = true;
    gusionComboState = 1;
    lastGusionCastTime = 0; 
}

static bool kaditaComboActive = false;
static int kaditaComboState = 0;
static uint64_t lastKaditaCastTime = 0;

static inline void StartKaditaCombo() {
    kaditaComboActive = true;
    kaditaComboState = 1;
    lastKaditaCastTime = 0;
}

struct AimPlayerSnapshot {
    int entityID; 
    Vector3_POD position;
    int hp;
    int hpMax;
};

struct AimSwordSnapshot {
    Vector3_POD position;
};

static std::vector<AimPlayerSnapshot> g_AimPlayers;
static std::vector<AimSwordSnapshot>  g_AimSwords;
static std::mutex g_AimMutex;

extern std::unordered_map<int, std::string> g_HeroNameCache;

// --- VARIABEL PENGEREM AUTO KIMMY ---
static uint64_t g_PauseAutoKimmyUntil = 0;

static void ClearAim() {
    std::lock_guard<std::mutex> lock(g_AimMutex);
    g_AimPlayers.clear();
    g_AimSwords.clear();
    CurrentLockedEntityID = -1;
    
    gusionComboActive = false;
    GusionStrictTargetID = -1;
    gusionWaitingForS1Hit = 0;

    kaditaComboActive = false;
    KaditaStrictTargetID = -1;
    kaditaWaitingForS1Hit = 0;
    
    g_PauseAutoKimmyUntil = 0;
}

static void TakeAimSnapshot() {
    if (!g_IsBattleStarted) {
        ClearAim();
        return;
    }
    std::vector<AimPlayerSnapshot> newPlayers;
    std::vector<AimSwordSnapshot> newSwords;

    void* bm = nullptr;
    IL2Cpp::Il2CppGetStaticFieldValue("Assembly-CSharp.dll", "", "BattleManager", "Instance", &bm);
    if (!bm) return;

    auto playersPtr = *(monoList<void**>**)((uintptr_t)bm + off_BattleManager_m_ShowPlayers);
    if (playersPtr && playersPtr->getItems()) {
        auto items = playersPtr->getItems();
        int size = playersPtr->getSize();
        
        if (size > 0 && size < 2000) {
            CurrentLockedEntityID = -1; 
            for (int i = 0; i < size; i++) {
                uintptr_t entity = (uintptr_t)items[i];
                if (!entity || entity <= 0xFFFFF) continue;
                
                if (*(bool*)(entity + off_EntityBase_m_bSameCampType)) continue;
                if (*(bool*)(entity + off_EntityBase_m_bDeath)) continue;

                AimPlayerSnapshot p;
                p.entityID = *(int*)(entity + off_EntityBase_m_ID);
                p.position = *(Vector3_POD*)(entity + off_ShowEntity__Position);
                p.hp = *(int*)(entity + off_EntityBase_m_Hp);
                p.hpMax = *(int*)(entity + off_EntityBase_m_HpMax);
                newPlayers.push_back(p);

                if (LockHeroEnable && !LockedHeroName.empty()) {
                    if (g_HeroNameCache.find(p.entityID) != g_HeroNameCache.end()) {
                        if (g_HeroNameCache[p.entityID] == LockedHeroName) {
                            CurrentLockedEntityID = p.entityID; 
                        }
                    }
                }
            }
        }
    }

    void* _RunBulletsRaw = nullptr;
    IL2Cpp::Il2CppGetStaticFieldValue("Assembly-CSharp.dll", "", "BattleManager", "_RunBullets", &_RunBulletsRaw);
    if (_RunBulletsRaw) {
        auto* runBullets = (monoList<void*>*)_RunBulletsRaw;
        if (runBullets && runBullets->items) {
            int size = runBullets->size;
            if (size > 0 && size < 2000) {
                void** items = (void**)(&runBullets->items->vector[0]);
                for (int i = 0; i < size; i++) {
                    uintptr_t pawn = (uintptr_t)items[i];
                    if (!pawn || pawn <= 0xFFFFF) continue;

                    int m_ID = *(int*)(pawn + off_Bullet_m_Id);
                    if (m_ID == 8452 || m_ID == 8451) {
                        uintptr_t transform = *(uintptr_t*)(pawn + off_Bullet_transform);
                        if (transform && transform > 0xFFFFF) {
                            AimSwordSnapshot s;
                            s.position = ((Vector3_POD(*)(uintptr_t))off_Transform_get_position)(transform);
                            newSwords.push_back(s);
                        }
                    }
                }
            }
        }
    }

    std::lock_guard<std::mutex> lock(g_AimMutex);
    g_AimPlayers = std::move(newPlayers);
    g_AimSwords  = std::move(newSwords);
}

static void* g_TryUseSkill_MethodInfo = nullptr;

static uint64_t GetTimeMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
}

static int hk_TryUseSkill_Aim(void* instance, int* state, int skillId, Vector3_POD dir_pod, bool dirDefault,
                              Vector3_POD pos_pod, bool bCommonAttack, bool bAlong, bool isInFirstDragRange,
                              uint32_t firstTarget, bool bIgnoreQueue, uint32_t dragTime, uint32_t uiOperFlag, void* method_info) {
    
    if (method_info) {
        g_TryUseSkill_MethodInfo = method_info;
    }
    
    auto callOrig = [&]() {
        if (orig_TryUseSkill)
            return orig_TryUseSkill(instance, state, skillId, dir_pod, dirDefault, pos_pod,
                                    bCommonAttack, bAlong, isInFirstDragRange, firstTarget,
                                    bIgnoreQueue, dragTime, uiOperFlag, method_info);
        return 0;
    };

    if (!instance) return callOrig();

    int heroID = *(int*)((uintptr_t)instance + off_EntityBase_m_ID);
    
    // --- PENGEREM AUTO KIMMY ---
    // Jika hero Kimmy menekan skill APAPUN selain basic attack (107100 / 207100),
    // kita Jeda spam tembakannya selama 600ms agar animasi skill bisa keluar dengan lancar!
    if (heroID == 71 && skillId != 107100 && skillId != 207100) {
        g_PauseAutoKimmyUntil = GetTimeMs() + 600;
    }

    bool isLingDash = (heroID == 84 && skillId == 8420);
    bool isBeatrixSniperUlt = (ActiveComboHero == 3 && heroID == 105 && skillId == 2010530);

    if (ActiveComboHero == 1 && heroID == 56 && skillId == 5610 && !gusionComboActive) {
        gusionWaitingForS1Hit = GetTimeMs() + 1500; 
    }

    if (ActiveComboHero == 2 && heroID == 75 && skillId == 7510 && !kaditaComboActive) {
        kaditaWaitingForS1Hit = GetTimeMs() + 1500; 
    }

    if (!dirDefault && !isBeatrixSniperUlt) return callOrig();
    if (!Aimbot && !(LingManual && isLingDash) && !isBeatrixSniperUlt) return callOrig();

    Vector3_POD selfPos = *(Vector3_POD*)((uintptr_t)instance + off_ShowEntity__Position);
    Vector3_POD targetPos = {0,0,0};
    bool found = false;

    std::vector<AimPlayerSnapshot> players;
    std::vector<AimSwordSnapshot> swords;
    {
        std::lock_guard<std::mutex> lock(g_AimMutex);
        players = g_AimPlayers;
        swords  = g_AimSwords;
    }

    if (LingManual && isLingDash) {
        float minDistSq = RangeFOV * RangeFOV;
        for (const auto& s : swords) {
            float dx = s.position.x - selfPos.x, dy = s.position.y - selfPos.y, dz = s.position.z - selfPos.z;
            float distSq = dx*dx + dy*dy + dz*dz;
            if (distSq < minDistSq) {
                minDistSq = distSq;
                targetPos = s.position;
                found = true;
            }
        }
    } else if (Aimbot) {
        float bestVal = 9999999.0f;
        float rangeSqr = RangeFOV * RangeFOV;

        if (LockHeroEnable && CurrentLockedEntityID != -1) {
            for (const auto& p : players) {
                if (p.entityID == CurrentLockedEntityID) {
                    float dx = p.position.x - selfPos.x, dy = p.position.y - selfPos.y, dz = p.position.z - selfPos.z;
                    float distSq = dx*dx + dy*dy + dz*dz;
                    if (distSq <= rangeSqr) {
                        targetPos = p.position;
                        found = true;
                    }
                    break;
                }
            }
        }

        if (!found) {
            for (const auto& p : players) {
                float dx = p.position.x - selfPos.x, dy = p.position.y - selfPos.y, dz = p.position.z - selfPos.z;
                float distSq = dx*dx + dy*dy + dz*dz;
                
                if (distSq > rangeSqr) continue;

                float val = distSq;
                if (Target == 1) val = (float)p.hp;
                else if (Target == 2 && p.hpMax > 0) val = (float)p.hp / (float)p.hpMax;
                
                if (val < bestVal) {
                    bestVal = val;
                    targetPos = p.position;
                    found = true;
                }
            }
        }
    }

    if (found) {
        float dx = targetPos.x - selfPos.x, dy = targetPos.y - selfPos.y, dz = targetPos.z - selfPos.z;
        float len = sqrtf(dx*dx + dy*dy + dz*dz);
        if (len > 0.0001f) { dx /= len; dy /= len; dz /= len; }
        Vector3_POD dir = {dx, dy, dz};
        
        return orig_TryUseSkill(instance, state, skillId, dir, false, targetPos,
                                bCommonAttack, bAlong, isInFirstDragRange, firstTarget,
                                bIgnoreQueue, dragTime, uiOperFlag, method_info);
    }

    return callOrig();
}

static void AutoLingUpdate(void* instance) {
    if (!AutoLing || !instance || !orig_TryUseSkill || !g_TryUseSkill_MethodInfo) return;

    int heroID = *(int*)((uintptr_t)instance + off_EntityBase_m_ID);
    if (heroID != 84) return;

    static uint64_t lastLingCastTime = 0;
    uint64_t now = GetTimeMs();
    if (now - lastLingCastTime < 400) return;

    Vector3_POD selfPos = *(Vector3_POD*)((uintptr_t)instance + off_ShowEntity__Position);

    std::vector<AimSwordSnapshot> swords;
    {
        std::lock_guard<std::mutex> lock(g_AimMutex);
        swords = g_AimSwords;
    }

    Vector3_POD swordPos = {0,0,0};
    bool found = false;
    float minDistSq = 15.0f * 15.0f;
    
    for (const auto& s : swords) {
        float dx = s.position.x - selfPos.x, dy = s.position.y - selfPos.y, dz = s.position.z - selfPos.z;
        float distSq = dx*dx + dy*dy + dz*dz;
        if (distSq < minDistSq) {
            minDistSq = distSq;
            swordPos = s.position;
            found = true;
        }
    }
    
    if (!found) return;

    float dx = swordPos.x - selfPos.x, dy = swordPos.y - selfPos.y, dz = swordPos.z - selfPos.z;
    float len = sqrtf(dx*dx + dy*dy + dz*dz);
    if (len > 0.0001f) { dx /= len; dy /= len; dz /= len; }
    Vector3_POD dir = {dx, dy, dz};
    
    static int dummyState = 0; 
    orig_TryUseSkill(instance, &dummyState, 8420, dir, false, swordPos,
                     false, false, false, 0, false, 0, 0, g_TryUseSkill_MethodInfo);
                     
    lastLingCastTime = now;
}

static void AutoGusionUpdate(void* instance) {
    if (ActiveComboHero != 1 || !instance || !orig_TryUseSkill || !g_TryUseSkill_MethodInfo) {
        gusionComboActive = false;
        gusionComboState = 0;
        return;
    }

    if (!gusionComboActive) return;

    uint64_t now = GetTimeMs();
    if (now < lastGusionCastTime) return; 

    Vector3_POD selfPos = *(Vector3_POD*)((uintptr_t)instance + off_ShowEntity__Position);
    Vector3_POD targetPos = {0,0,0};
    bool found = false;

    std::vector<AimPlayerSnapshot> players;
    {
        std::lock_guard<std::mutex> lock(g_AimMutex);
        players = g_AimPlayers;
    }

    for (const auto& p : players) {
        if (p.entityID == GusionStrictTargetID) {
            targetPos = p.position;
            found = true;
            break;
        }
    }

    if (!found) {
        gusionComboActive = false;
        gusionComboState = 0;
        return;
    }

    float dx = targetPos.x - selfPos.x, dy = targetPos.y - selfPos.y, dz = targetPos.z - selfPos.z;
    float len = sqrtf(dx*dx + dy*dy + dz*dz);
    if (len > 0.0001f) { dx /= len; dy /= len; dz /= len; }
    Vector3_POD dir = {dx, dy, dz};

    static int dummyState = 0;
    const int S1 = 5610, S2 = 5620, S3 = 5630;
    int skillResult = 0;  

    switch (gusionComboState) {
        case 1:
            skillResult = orig_TryUseSkill(instance, &dummyState, S1, dir, false, targetPos, false, false, false, 0, false, 0, 0, g_TryUseSkill_MethodInfo);
            if (skillResult) { gusionComboState++; lastGusionCastTime = now + 200; } 
            else { lastGusionCastTime = now + 50; }
            break;
        case 2:
            skillResult = orig_TryUseSkill(instance, &dummyState, S2, dir, false, targetPos, false, false, false, 0, false, 0, 0, g_TryUseSkill_MethodInfo);
            if (skillResult) { gusionComboState++; lastGusionCastTime = now + 150; } 
            else { lastGusionCastTime = now + 50; }
            break;
        case 3:
            skillResult = orig_TryUseSkill(instance, &dummyState, S3, dir, false, targetPos, false, false, false, 0, false, 0, 0, g_TryUseSkill_MethodInfo);
            if (skillResult) { gusionComboState++; lastGusionCastTime = now + 250; } 
            else { lastGusionCastTime = now + 50; }
            break;
        case 4:
            skillResult = orig_TryUseSkill(instance, &dummyState, S2, dir, false, targetPos, false, false, false, 0, false, 0, 0, g_TryUseSkill_MethodInfo);
            if (skillResult) { gusionComboState++; lastGusionCastTime = now + 150; } 
            else { lastGusionCastTime = now + 50; }
            break;
        case 5:
            skillResult = orig_TryUseSkill(instance, &dummyState, S1, dir, false, targetPos, false, false, false, 0, false, 0, 0, g_TryUseSkill_MethodInfo);
            if (skillResult) { gusionComboState++; lastGusionCastTime = now + 150; } 
            else { lastGusionCastTime = now + 50; }
            break;
        case 6:
            skillResult = orig_TryUseSkill(instance, &dummyState, S1, dir, false, targetPos, false, false, false, 0, false, 0, 0, g_TryUseSkill_MethodInfo);
            if (skillResult) { gusionComboState++; lastGusionCastTime = now + 200; } 
            else { lastGusionCastTime = now + 50; }
            break;
        case 7:
            skillResult = orig_TryUseSkill(instance, &dummyState, S2, dir, false, targetPos, false, false, false, 0, false, 0, 0, g_TryUseSkill_MethodInfo);
            if (skillResult) { gusionComboActive = false; gusionComboState = 0; lastGusionCastTime = now + 2500; } 
            else { lastGusionCastTime = now + 50; }
            break;
    }
}

static void AutoKaditaUpdate(void* instance) {
    if (ActiveComboHero != 2 || !instance || !orig_TryUseSkill || !g_TryUseSkill_MethodInfo) {
        kaditaComboActive = false;
        kaditaComboState = 0;
        return;
    }

    if (!kaditaComboActive) return;

    uint64_t now = GetTimeMs();
    if (now < lastKaditaCastTime) return; 

    Vector3_POD selfPos = *(Vector3_POD*)((uintptr_t)instance + off_ShowEntity__Position);
    Vector3_POD targetPos = {0,0,0};
    bool found = false;

    std::vector<AimPlayerSnapshot> players;
    {
        std::lock_guard<std::mutex> lock(g_AimMutex);
        players = g_AimPlayers;
    }

    for (const auto& p : players) {
        if (p.entityID == KaditaStrictTargetID) {
            targetPos = p.position;
            found = true;
            break;
        }
    }

    if (!found) {
        kaditaComboActive = false;
        kaditaComboState = 0;
        return;
    }

    float dx = targetPos.x - selfPos.x, dy = targetPos.y - selfPos.y, dz = targetPos.z - selfPos.z;
    float len = sqrtf(dx*dx + dy*dy + dz*dz);
    if (len > 0.0001f) { dx /= len; dy /= len; dz /= len; }
    Vector3_POD dir = {dx, dy, dz};

    static int dummyState = 0;
    const int S2 = 7520; 
    const int S3 = 7530; 
    
    int skillResult = 0;

    switch (kaditaComboState) {
        case 1:
            kaditaComboState++;
            break;

        case 2: 
            skillResult = orig_TryUseSkill(instance, &dummyState, S2, dir, false, targetPos,
                                           false, false, false, 0, false, 0, 0, g_TryUseSkill_MethodInfo);
            if (skillResult) {
                kaditaComboState++;
                lastKaditaCastTime = now + 150; 
            } else {
                lastKaditaCastTime = now + 50;
            }
            break;
            
        case 3: 
            skillResult = orig_TryUseSkill(instance, &dummyState, S3, dir, false, targetPos,
                                           false, false, false, 0, false, 0, 0, g_TryUseSkill_MethodInfo);
            if (skillResult) {
                kaditaComboActive = false;
                kaditaComboState = 0;
                lastKaditaCastTime = now + 2500; 
            } else {
                lastKaditaCastTime = now + 50;
            }
            break;
    }
}

// ==========================================
// AUTO KIMMY (SUDAH DIPERBAIKI!)
// ==========================================
static void AutoKimmyUpdate(void* instance) {
    if (!Aimbot || !instance || !orig_TryUseSkill || !g_TryUseSkill_MethodInfo) return;

    int heroID = *(int*)((uintptr_t)instance + off_EntityBase_m_ID);
    if (heroID != 71) return; // Khusus Kimmy

    uint64_t now = GetTimeMs();

    // CEK PENGEREM: Jika player habis pakai Skill 1 / Ulti, JANGAN NAMBAK SELAMA 600ms!
    if (now < g_PauseAutoKimmyUntil) return;

    static uint64_t lastKimmyShotTime = 0;
    if (now - lastKimmyShotTime < 100) return;

    Vector3_POD selfPos = *(Vector3_POD*)((uintptr_t)instance + off_ShowEntity__Position);
    Vector3_POD targetPos = {0,0,0};
    
    // VARIABEL BARU: Simpan targetID untuk menghindari nembak random/kena buff
    uint32_t targetID = 0; 
    bool found = false;

    std::vector<AimPlayerSnapshot> players;
    {
        std::lock_guard<std::mutex> lock(g_AimMutex);
        players = g_AimPlayers;
    }

    float bestVal = 9999999.0f;
    float rangeSqr = RangeFOV * RangeFOV;

    // 1. Lock Hero
    if (LockHeroEnable && CurrentLockedEntityID != -1) {
        for (const auto& p : players) {
            if (p.entityID == CurrentLockedEntityID) {
                float dx = p.position.x - selfPos.x, dy = p.position.y - selfPos.y, dz = p.position.z - selfPos.z;
                if (dx*dx + dy*dy + dz*dz <= rangeSqr) {
                    targetPos = p.position;
                    targetID = p.entityID; // <--- SIMPAN ENTITY ID
                    found = true;
                }
                break;
            }
        }
    }

    // 2. Nearest / Lowest HP Target
    if (!found) {
        for (const auto& p : players) {
            float dx = p.position.x - selfPos.x, dy = p.position.y - selfPos.y, dz = p.position.z - selfPos.z;
            float distSq = dx*dx + dy*dy + dz*dz;
            if (distSq > rangeSqr) continue;

            float val = distSq;
            if (Target == 1) val = (float)p.hp;
            else if (Target == 2 && p.hpMax > 0) val = (float)p.hp / (float)p.hpMax;
            
            if (val < bestVal) {
                bestVal = val;
                targetPos = p.position;
                targetID = p.entityID; // <--- SIMPAN ENTITY ID
                found = true;
            }
        }
    }

    if (!found) return;

    float dx = targetPos.x - selfPos.x, dy = targetPos.y - selfPos.y, dz = targetPos.z - selfPos.z;
    float len = sqrtf(dx*dx + dy*dy + dz*dz);
    if (len > 0.0001f) { dx /= len; dy /= len; dz /= len; }
    Vector3_POD dir = {dx, dy, dz};

    int dummyState = 0;
    
    // MENGIRIM TEMBAKAN DENGAN TARGET ID YANG TEPAT! (Parameter ke-10 diisi targetID)
    // Supaya game 100% menolak Smart Targeting ke arah Minion/Buff
    orig_TryUseSkill(instance, &dummyState, 107100, dir, false, targetPos, true, false, false, targetID, false, 0, 0, g_TryUseSkill_MethodInfo);
    orig_TryUseSkill(instance, &dummyState, 207100, dir, false, targetPos, true, false, false, targetID, false, 0, 0, g_TryUseSkill_MethodInfo);

    lastKimmyShotTime = now;
}