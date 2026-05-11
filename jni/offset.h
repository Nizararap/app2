#pragma once
#include <stdint.h>
#include "Tam/IL2CppSDKGenerator/Il2Cpp.h"

// ==================== OFFSET CACHE ====================
extern uintptr_t off_BattleManager_m_ShowPlayers;
extern uintptr_t off_ShowEntity__Position;
extern uintptr_t off_EntityBase_m_bSameCampType;
extern uintptr_t off_EntityBase_m_bDeath;
extern uintptr_t off_EntityBase_m_EntityCampType;
extern uintptr_t off_ShowPlayer_m_HeroName;

// Offset tambahan (retri)
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

// aim 
extern uintptr_t off_ShowSelfPlayer_TryUseSkill;
extern uintptr_t off_Bullet_m_Id;
extern uintptr_t off_Bullet_transform;
extern uintptr_t off_Transform_get_position;
extern uintptr_t off_BattleManager_m_dicPlayerShow;

// Offset hardcoded TryUseSkill (12 param) dari dump
#define OFFSET_TryUseSkill_12 0x33b00b4

inline void InitGameOffsets() {
    off_BattleManager_m_ShowPlayers = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "BattleManager", "m_ShowPlayers");
    off_ShowEntity__Position = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "ShowEntity", "m_vCachePosition");
    off_EntityBase_m_bSameCampType = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "ShowEntity", "m_bSameCampType");
    off_EntityBase_m_bDeath = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "ShowEntity", "m_bDeath");
    off_EntityBase_m_EntityCampType = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "ShowEntity", "m_EntityCampType");
    off_ShowPlayer_m_HeroName = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "ShowPlayer", "m_HeroName");
        
    off_BattleManager_m_dicMonsterShow = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "BattleManager", "m_dicMonsterShow");
    off_EntityBase_m_ID = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "ShowEntity", "m_ID");
    off_EntityBase_m_Hp = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "ShowEntity", "m_Hp");
    off_EntityBase_m_HpMax = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "ShowEntity", "m_HpMax");
    off_EntityBase_m_Level = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "ShowEntity", "m_Level");
    off_BattleManager_m_LocalPlayerShow = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "BattleManager", "m_LocalPlayerShow");
    off_LogicPlayer_m_PlayerData = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "Battle", "LogicPlayer", "m_PlayerData");
    off_PlayerData__killNum = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "PlayerData", "_killNum");
    off_PlayerData__assistNum = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "PlayerData", "_assistNum");
    off_LogicPlayer__KillWildTimes = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "Battle", "LogicPlayer", "_KillWildTimes");
    
    off_ShowSelfPlayer_TryUseSkill2 = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset("Assembly-CSharp.dll", "", "ShowSelfPlayer", "TryUseSkill", 9);
    
    off_BattleData_m_BattleBridge = IL2Cpp::Il2CppGetStaticFieldOffset("Assembly-CSharp.dll", "", "BattleData", "m_BattleBridge");
    off_BattleBridge_bStartBattle = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset("Assembly-CSharp.dll", "", "BattleBridge", "get_bStartBattle", 0);
    off_LogicBattleManager_Instance = IL2Cpp::Il2CppGetStaticFieldOffset("Assembly-CSharp.dll", "", "LogicBattleManager", "Instance");
    
    off_ShowSelfPlayer_TryUseSkill = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset("Assembly-CSharp.dll", "", "ShowSelfPlayer", "TryUseSkill", 12);
    
    off_Bullet_m_Id = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "Battle", "Bullet", "m_Id");
    off_Bullet_transform = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "Battle", "Bullet", "transform");
    off_Transform_get_position = (uintptr_t)IL2Cpp::Il2CppGetMethodOffset("UnityEngine.dll", "UnityEngine", "Transform", "get_position");
    off_BattleManager_m_dicPlayerShow = IL2Cpp::Il2CppGetFieldOffset("Assembly-CSharp.dll", "", "BattleManager", "m_dicPlayerShow");
}