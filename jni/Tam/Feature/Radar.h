#pragma once

#include <map>
#include <string>
#include <vector>
#include <mutex>
#include <unordered_map>
#include "GameClass.h"

// --- Radar Config ---
struct sRadarConfig {
    bool  enabled    = false;
};
static sRadarConfig RadarConfig;

// =============================================================================
// Snapshot player (diambil di thread game, nanti dikirim ke Java via Socket)
// =============================================================================
struct PlayerSnapshot {
    Vector3 position;
    int     entityCampType;
    std::string heroName;
};

static std::vector<PlayerSnapshot> g_RadarSnapshot;
static std::mutex g_RadarSnapshotMutex;
static std::unordered_map<int, std::string> g_HeroNameCache;

static void ClearRadar() {
    std::lock_guard<std::mutex> lock(g_RadarSnapshotMutex);
    g_RadarSnapshot.clear();
    g_HeroNameCache.clear(); // Clear cache saat battle selesai
}

// Fungsi snapshot dipanggil dari hook BattleManager.Update (thread game)
static void TakeRadarSnapshot() {
    if (!g_IsBattleStarted) {
        std::lock_guard<std::mutex> lock(g_RadarSnapshotMutex);
        g_RadarSnapshot.clear();
        return;
    }
    std::vector<PlayerSnapshot> newSnapshot;

    void* bm = nullptr;
    IL2Cpp::Il2CppGetStaticFieldValue("Assembly-CSharp.dll", "", "BattleManager", "Instance", &bm);
    if (!bm) {
        std::lock_guard<std::mutex> lock(g_RadarSnapshotMutex);
        g_RadarSnapshot.clear();
        return;
    }

    auto playersPtr = *(monoList<void**>**)((uintptr_t)bm + off_BattleManager_m_ShowPlayers);
    if (!playersPtr) {
        std::lock_guard<std::mutex> lock(g_RadarSnapshotMutex);
        g_RadarSnapshot.clear();
        return;
    }

 auto items = playersPtr->getItems();
    if (!items) {
        std::lock_guard<std::mutex> lock(g_RadarSnapshotMutex);
        g_RadarSnapshot.clear();
        return;
    }

    int size = playersPtr->getSize();
    // VALIDASI: Batasi ukuran list untuk mencegah CPU Lag Spike saat memori error
    if (size > 0 && size < 2000) {
        for (int i = 0; i < size; i++) {
            void* pawn = items[i];
            // VALIDASI POINTER EKSTRIM
            if (!pawn || (uintptr_t)pawn <= 0xFFFFF) continue;

            if (*(bool*)((uintptr_t)pawn + off_EntityBase_m_bSameCampType)) continue;
            if (*(bool*)((uintptr_t)pawn + off_EntityBase_m_bDeath)) continue;

            PlayerSnapshot snap;
            snap.position = *(Vector3*)((uintptr_t)pawn + off_ShowEntity__Position);
            snap.entityCampType = *(int*)((uintptr_t)pawn + off_EntityBase_m_EntityCampType);
            
            // Ambil Entity ID sebagai key (sangat cepat)
            int entityID = *(int*)((uintptr_t)pawn + off_EntityBase_m_ID);

            // Cek apakah nama sudah ada di cache
            if (g_HeroNameCache.find(entityID) == g_HeroNameCache.end()) {
                // Jika belum ada, baru kita baca string-nya (Hanya terjadi 1x per hero)
                g_HeroNameCache[entityID] = ReadHeroNameRaw(pawn);
            }

            // Gunakan string dari cache
            snap.heroName = g_HeroNameCache[entityID];
            
            newSnapshot.push_back(snap);
        }
    }

    std::lock_guard<std::mutex> lock(g_RadarSnapshotMutex);
    g_RadarSnapshot = std::move(newSnapshot);
}