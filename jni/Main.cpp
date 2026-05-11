#include <stdio.h>
#include <string>
#include <unistd.h>
#include <stdint.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <fcntl.h>
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <codecvt>

// --- HEADER INCLUDES ---
#include "Includes/obfuscate.h"
#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "Includes/BuildExpiry.h"

#include "Tam/IL2CppSDKGenerator/Call_IL2CppSDKGenerator.h"
#include "Tam/Tools/Call_Tools.h"
#include "Tam/Feature/GameClass.h"
#include "Tam/Feature/mono.h"
#include "Tam/Feature/Radar.h"
#include "Tam/Feature/Retri.h"
#include "Tam/Feature/aim.h"
#include "Tam/Feature/SocketServer.h"
#include "Tam/JNIStuff.h"
#include "offset.h"

// --- GLOBAL VARIABLES ---
bool Aimbot      = false;
bool LingManual  = false;
bool AutoLing    = false;
int ActiveComboHero = 0; // 0 = None, 1 = Gusion, 2 = Kadita, 3 = Beatrix
bool g_IsBattleStarted = false;
int Target       = 0; 
float RangeFOV   = 200.0f;

bool AutoRetriBuff   = false;
bool AutoRetriLord   = false;
bool AutoRetriTurtle = false;
bool AutoRetriLitho  = false;

bool LockHeroEnable = false;
std::string LockedHeroName = "";

// --- VARIABEL MULTI-COMBO ---
uint64_t gusionWaitingForS1Hit = 0;
int GusionStrictTargetID = -1;

uint64_t kaditaWaitingForS1Hit = 0;
int KaditaStrictTargetID = -1;

// Define the offsets here
uintptr_t off_BattleManager_m_ShowPlayers = 0;
uintptr_t off_ShowEntity__Position = 0;
uintptr_t off_EntityBase_m_bSameCampType = 0;
uintptr_t off_EntityBase_m_bDeath = 0;
uintptr_t off_EntityBase_m_EntityCampType = 0;
uintptr_t off_ShowPlayer_m_HeroName = 0;
uintptr_t off_BattleManager_m_dicMonsterShow = 0;
uintptr_t off_EntityBase_m_ID = 0;
uintptr_t off_EntityBase_m_Hp = 0;
uintptr_t off_EntityBase_m_HpMax = 0;
uintptr_t off_EntityBase_m_Level = 0;
uintptr_t off_BattleManager_m_LocalPlayerShow = 0;
uintptr_t off_LogicPlayer_m_PlayerData = 0;
uintptr_t off_PlayerData__killNum = 0;
uintptr_t off_PlayerData__assistNum = 0;
uintptr_t off_LogicPlayer__KillWildTimes = 0;
uintptr_t off_ShowSelfPlayer_TryUseSkill2 = 0;
uintptr_t off_BattleData_m_BattleBridge = 0;
uintptr_t off_BattleBridge_bStartBattle = 0;
uintptr_t off_LogicBattleManager_Instance = 0;
uintptr_t off_ShowSelfPlayer_TryUseSkill = 0;
uintptr_t off_Bullet_m_Id = 0;
uintptr_t off_Bullet_transform = 0;
uintptr_t off_Transform_get_position = 0;
uintptr_t off_BattleManager_m_dicPlayerShow = 0;

// Pointer asli
int (*orig_TryUseSkill)(void*, int*, int, Vector3_POD, bool, Vector3_POD, bool, bool, bool, uint32_t, bool, uint32_t, uint32_t, void*) = nullptr;
int (*orig_TryUseSkill2)(void*, int, Vector3_POD, bool, Vector3_POD, bool, bool, bool, bool, uint32_t, void*) = nullptr;

// ===================================================================
// HOOK ONHURTHERO - TRIGEER UTAMA COMBO (GUSION & KADITA)
// ===================================================================
void (*orig_OnHurtHero)(void* instance, int hurtValue, int realDamage, int hurtType, void* srcSkill, void* victim, void* logicEff) = nullptr;

void hk_OnHurtHero(void* instance, int hurtValue, int realDamage, int hurtType, void* srcSkill, void* victim, void* logicEff) {
    if (orig_OnHurtHero) orig_OnHurtHero(instance, hurtValue, realDamage, hurtType, srcSkill, victim, logicEff);

    if (ActiveComboHero == 0 || !instance || !victim) return;

    uintptr_t playerSelf = GetPlayerRealSelf_retri();
    
    if (playerSelf && (uintptr_t)instance == playerSelf) {
        
        static uintptr_t method_get_m_ConfigDataID = 0;
        if (!method_get_m_ConfigDataID) {
            method_get_m_ConfigDataID = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset(OBFUSCATE("Assembly-CSharp.dll"), OBFUSCATE("Battle"), OBFUSCATE("LogicPlayer"), OBFUSCATE("get_m_ConfigDataID"), 0);
        }

        uint64_t now = GetTimeMs();

        if (ActiveComboHero == 1 && gusionWaitingForS1Hit > 0 && now < gusionWaitingForS1Hit) {
            if (method_get_m_ConfigDataID) {
                int victimHeroID = ((int(*)(void*))method_get_m_ConfigDataID)(victim);
                if (victimHeroID > 0) {
                    GusionStrictTargetID = victimHeroID; 
                    StartGusionCombo();                  
                    gusionWaitingForS1Hit = 0;           
                }
            }
        }
        
        if (ActiveComboHero == 2 && kaditaWaitingForS1Hit > 0 && now < kaditaWaitingForS1Hit) {
            if (method_get_m_ConfigDataID) {
                int victimHeroID = ((int(*)(void*))method_get_m_ConfigDataID)(victim);
                if (victimHeroID > 0) {
                    KaditaStrictTargetID = victimHeroID; 
                    StartKaditaCombo();                  
                    kaditaWaitingForS1Hit = 0;           
                }
            }
        }
    }
}

// HOOK START BATTLE
void (*orig_ShowSelfPlayer_StartBattle)(void*) = nullptr;
void hk_ShowSelfPlayer_StartBattle(void* instance) {
    if (orig_ShowSelfPlayer_StartBattle) orig_ShowSelfPlayer_StartBattle(instance);
    g_IsBattleStarted = true;
}

// HOOK END BATTLE
void (*orig_ShowSelfPlayer_EndBattle)(void*) = nullptr;
void hk_ShowSelfPlayer_EndBattle(void* instance) {
    if (orig_ShowSelfPlayer_EndBattle) orig_ShowSelfPlayer_EndBattle(instance);
    g_IsBattleStarted = false;
    ClearRadar();
    ClearAim();
    ClearMonster();
}

// HOOK ON DESTROY
void (*orig_ShowSelfPlayer_Unity_OnDestroy)(void*) = nullptr;
void hk_ShowSelfPlayer_Unity_OnDestroy(void* instance) {
    if (orig_ShowSelfPlayer_Unity_OnDestroy) orig_ShowSelfPlayer_Unity_OnDestroy(instance);
    
    // g_IsBattleStarted = false; -> BARIS INI KITA HAPUS!
    // Kenapa dihapus? Agar saat ganti hero di Custom, mod tetap jalan.
    // Untuk Rank/Classic tetap aman karena mod akan dimatikan oleh hook EndBattle saat game selesai.

    ClearRadar();
    ClearAim();
    ClearMonster();
}

void (*orig_BattleManager_Update)(void*) = nullptr;
void (*orig_ShowSelfPlayer_OnUpdate)(void*) = nullptr;

std::string ReadHeroNameRaw(void* pawn) {
    if (!pawn || !off_ShowPlayer_m_HeroName) return "";
    uintptr_t strPtr = *(uintptr_t*)((uintptr_t)pawn + off_ShowPlayer_m_HeroName);
    if (!strPtr || strPtr <= 0xFFFFF) return ""; 
    int32_t len = *(int32_t*)(strPtr + 0x10);
    if (len <= 0 || len > 64) return ""; 
    char16_t* chars = (char16_t*)(strPtr + 0x14);
    std::string out;
    out.reserve(len * 3);
    for (int i = 0; i < len; ++i) {
        char16_t ch = chars[i];
        if (ch < 0x80) out += (char)ch;
        else if (ch < 0x800) { out += (char)(0xC0 | (ch >> 6)); out += (char)(0x80 | (ch & 0x3F)); } 
        else { out += (char)(0xE0 | (ch >> 12)); out += (char)(0x80 | ((ch >> 6) & 0x3F)); out += (char)(0x80 | (ch & 0x3F)); }
    }
    return out;
}

JavaVM*    g_vm          = nullptr;
uintptr_t  m_IL2CPP      = 0;
static uint64_t lastRadarSnapTime = 0;

void hk_BattleManager_Update(void* instance) {
    if (orig_BattleManager_Update) orig_BattleManager_Update(instance);
    TakeAimSnapshot();
    uint64_t now = GetTimeMs();
    if (now - lastRadarSnapTime >= 33) {
        TakeRadarSnapshot();
        TakeMonsterSnapshot();
        lastRadarSnapTime = now;
    }
}

int hk_TryUseSkill2(void* instance, int skillId, Vector3_POD dir, bool dirDefault, Vector3_POD pos, bool bCommonAttack, bool bAlong, bool isInFirstDragRange, bool bIgnoreQueue, uint32_t dragTime, void* method_info) {
    if (orig_TryUseSkill2)
        return orig_TryUseSkill2(instance, skillId, dir, dirDefault, pos, bCommonAttack, bAlong, isInFirstDragRange, bIgnoreQueue, dragTime, method_info);
    return 0;
}

// ===================================================================
// HOOK UPDATE PLAYER
// ===================================================================
void hk_ShowSelfPlayer_OnUpdate(void* instance) {
    if (orig_ShowSelfPlayer_OnUpdate) orig_ShowSelfPlayer_OnUpdate(instance);
    ProcessRetribution(instance);
    AutoLingUpdate(instance);
    AutoGusionUpdate(instance); 
    AutoKaditaUpdate(instance); 
    AutoKimmyUpdate(instance); // <--- Eksekusi Auto Kimmy
}

#pragma pack(push, 1)
struct ModConfig {
    int32_t aimbot;
    int32_t ling_manual;
    int32_t ling_auto;
    int32_t active_combo; 
    int32_t target_mode;
    float   fov;
    int32_t retri_buff;
    int32_t retri_lord;
    int32_t retri_turtle;
    int32_t retri_litho;
    int32_t lock_hero_enable;
    char    locked_hero_name[32]; 
};
#pragma pack(pop)

void* config_thread(void*) {
    // Melepas thread agar tidak menjadi zombie process (mencegah memory leak)
    pthread_detach(pthread_self());

    int server_fd, client_fd;
    struct sockaddr_un server_addr, client_addr;
    socklen_t client_len = sizeof(client_addr);

    server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) return nullptr;

    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sun_family = AF_UNIX;
    server_addr.sun_path[0] = '\0';
    strcpy(server_addr.sun_path + 1, "mlbb_config_socket");

    int len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen("mlbb_config_socket");
    if (::bind(server_fd, (struct sockaddr*)&server_addr, len) < 0) {
        ::close(server_fd);
        return nullptr;
    }

    if (::listen(server_fd, 1) < 0) {
        ::close(server_fd);
        return nullptr;
    }

    while (true) {
        client_fd = accept(server_fd, (struct sockaddr*)&client_addr, &client_len);
        if (client_fd < 0) { usleep(100000); continue; }

        ModConfig cfg;
        while (true) {
            int bytes = recv(client_fd, &cfg, sizeof(ModConfig), 0);
            if (bytes == sizeof(ModConfig)) {
                Aimbot          = (cfg.aimbot == 1);
                LingManual      = (cfg.ling_manual == 1);
                AutoLing        = (cfg.ling_auto == 1);

                // --- PERBAIKAN: REFRESH COMBO & MODE CUSTOM ---
                // Jika pilihan combo diganti di menu (misal Kadita -> Beatrix),
                // Kita bersihkan cache lama agar C++ membaca Entity baru dari awal
                if (ActiveComboHero != cfg.active_combo) {
                    ClearAim();     // Reset state combo
                    ClearRadar();   // Reset cache entity & string nama
                    ClearMonster(); // Reset cache retri monster
                }
                
                ActiveComboHero = cfg.active_combo;
                Target          = cfg.target_mode;
                RangeFOV        = cfg.fov;
                AutoRetriBuff   = (cfg.retri_buff == 1);
                AutoRetriLord   = (cfg.retri_lord == 1);
                AutoRetriTurtle = (cfg.retri_turtle == 1);
                AutoRetriLitho  = (cfg.retri_litho == 1);
                
                LockHeroEnable  = (cfg.lock_hero_enable == 1);
                LockedHeroName  = std::string(cfg.locked_hero_name);
            } else if (bytes <= 0) {
                break;
            }
        }
        ::close(client_fd);
    }
    ::close(server_fd);
    return nullptr;
}

void* main_thread(void*) {
    LOGI("Main thread started, waiting for target libraries...");

    while (!m_IL2CPP) {
        m_IL2CPP = Tools::GetBaseAddress("liblogic.so");
        if (!m_IL2CPP) usleep(500000);
    }

    IL2Cpp::Il2CppAttach("liblogic.so");
    sleep(1);
    InitGameOffsets();
    
    uintptr_t addr_BattleManager_Update = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset(
        OBFUSCATE("Assembly-CSharp.dll"), OBFUSCATE(""), OBFUSCATE("BattleManager"), OBFUSCATE("Update"), 0);
    if (addr_BattleManager_Update) {
        Tools::Hook((void*)addr_BattleManager_Update, (void*)hk_BattleManager_Update, (void**)&orig_BattleManager_Update);
    }

    if (off_ShowSelfPlayer_TryUseSkill2) {
        Tools::Hook((void*)off_ShowSelfPlayer_TryUseSkill2, (void*)hk_TryUseSkill2, (void**)&orig_TryUseSkill2);
    }

    if (off_ShowSelfPlayer_TryUseSkill) {
        Tools::Hook((void*)off_ShowSelfPlayer_TryUseSkill, (void*)hk_TryUseSkill_Aim, (void**)&orig_TryUseSkill);
    }

    uintptr_t addr_OnUpdate = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset(OBFUSCATE("Assembly-CSharp.dll"), OBFUSCATE(""), OBFUSCATE("ShowSelfPlayer"), OBFUSCATE("Unity_OnUpdate"), 0);
    if (addr_OnUpdate) {
        Tools::Hook((void*)addr_OnUpdate, (void*)hk_ShowSelfPlayer_OnUpdate, (void**)&orig_ShowSelfPlayer_OnUpdate);
    }

    uintptr_t addr_OnHurtHero = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset(OBFUSCATE("Assembly-CSharp.dll"), OBFUSCATE("Battle"), OBFUSCATE("LogicPlayer"), OBFUSCATE("OnHurtHero"), 6);
    if (addr_OnHurtHero) {
        Tools::Hook((void*)addr_OnHurtHero, (void*)hk_OnHurtHero, (void**)&orig_OnHurtHero);
    }

    uintptr_t addr_StartBattle = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset(OBFUSCATE("Assembly-CSharp.dll"), OBFUSCATE(""), OBFUSCATE("ShowSelfPlayer"), OBFUSCATE("StartBattle"), 0);
    if (addr_StartBattle) Tools::Hook((void*)addr_StartBattle, (void*)hk_ShowSelfPlayer_StartBattle, (void**)&orig_ShowSelfPlayer_StartBattle);

    uintptr_t addr_EndBattle = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset(OBFUSCATE("Assembly-CSharp.dll"), OBFUSCATE(""), OBFUSCATE("ShowSelfPlayer"), OBFUSCATE("EndBattle"), 0);
    if (addr_EndBattle) Tools::Hook((void*)addr_EndBattle, (void*)hk_ShowSelfPlayer_EndBattle, (void**)&orig_ShowSelfPlayer_EndBattle);

    uintptr_t addr_Unity_OnDestroy = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset(OBFUSCATE("Assembly-CSharp.dll"), OBFUSCATE(""), OBFUSCATE("ShowSelfPlayer"), OBFUSCATE("Unity_OnDestroy"), 0);
    if (addr_Unity_OnDestroy) Tools::Hook((void*)addr_Unity_OnDestroy, (void*)hk_ShowSelfPlayer_Unity_OnDestroy, (void**)&orig_ShowSelfPlayer_Unity_OnDestroy);

    pthread_t t_config;
    pthread_create(&t_config, nullptr, config_thread, nullptr);
    pthread_t t_socket;
    pthread_create(&t_socket, nullptr, socket_server_thread, nullptr);

    return nullptr;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    g_vm = vm;

    pthread_t t;
    pthread_create(&t, nullptr, main_thread, nullptr);

    return JNI_VERSION_1_6;
}