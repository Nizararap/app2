#pragma once
#include "../IL2CppSDKGenerator/stdafx.h"
#include "../IL2CppSDKGenerator/MonoString.h"

// Deklarasi offset cache (diisi di Main.cpp)
extern uintptr_t off_BattleManager_m_ShowPlayers;
extern uintptr_t off_ShowEntity__Position;
extern uintptr_t off_EntityBase_m_bSameCampType;
extern uintptr_t off_EntityBase_m_bDeath;
extern uintptr_t off_EntityBase_m_EntityCampType;
extern uintptr_t off_EntityBase_m_ID;
extern uintptr_t off_ShowPlayer_m_HeroName;
extern bool g_IsBattleStarted; 

// Fungsi inisialisasi offset (dipanggil sekali di main thread)
void InitGameOffsets();

// Safe read tanpa virtual call
template <typename T>
inline T SafeRead(uintptr_t base, uintptr_t offset) {
    if (!base || !offset) return T{};
    return *(T*)(base + offset);
}

// Baca nama hero mentah dari MonoString (tanpa virtual method)
std::string ReadHeroNameRaw(void* pawn);