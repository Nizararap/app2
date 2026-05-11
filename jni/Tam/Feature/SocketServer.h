#pragma once

#include <pthread.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <android/log.h>
#include <string.h>
#include <vector>

#include "Radar.h" // Untuk mengambil data g_RadarSnapshot

#define SOCKET_NAME "mlbb_radar_socket"

// Struktur data biner yang akan dikirim ke Java
// #pragma pack(1) memastikan tidak ada padding memori ekstra agar byte-nya pas saat dibaca Java
#pragma pack(push, 1)
struct PlayerDataPacket {
    float x;
    float y;
    float z;
    int campType;
    char heroName[32]; // Nama hero maksimal 32 karakter
};
#pragma pack(pop)

static void* socket_server_thread(void*) {
    int server_fd, client_fd;
    struct sockaddr_un server_addr, client_addr;
    socklen_t client_len = sizeof(client_addr);

    // Membuat Socket TCP Unix
    server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "RADAR_SOCKET", "Gagal membuat socket server");
        return nullptr;
    }

    // Konfigurasi Abstract Namespace Socket (Karakter pertama '\0' agar tidak membuat file di disk)
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sun_family = AF_UNIX;
    server_addr.sun_path[0] = '\0';
    strcpy(server_addr.sun_path + 1, SOCKET_NAME);

// Kalkulasi panjang alamat
    int len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(SOCKET_NAME);
    
    // Bind socket (Tambahkan titik dua ganda '::' di sini)
    if (::bind(server_fd, (struct sockaddr*)&server_addr, len) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "RADAR_SOCKET", "Gagal bind socket (Mungkin masih nyangkut)");
        ::close(server_fd);
        return nullptr;
    }

    // Mulai mendengarkan koneksi dari Java
    if (::listen(server_fd, 1) < 0) {
        ::close(server_fd);
        return nullptr;
    }

    __android_log_print(ANDROID_LOG_INFO, "RADAR_SOCKET", "Server Berjalan. Menunggu overlay Java terhubung...");

    while (true) {
        client_fd = accept(server_fd, (struct sockaddr*)&client_addr, &client_len);
        if (client_fd < 0) {
            usleep(100000);
            continue;
        }

        __android_log_print(ANDROID_LOG_INFO, "RADAR_SOCKET", "Client Java Berhasil Terhubung!");

        // Loop untuk mengirim data koordinat secara Real-Time (~30 FPS)
        while (true) {
            std::vector<PlayerSnapshot> snapshot;
            {
                // Kunci mutex agar tidak crash saat game juga sedang menulis data
                std::lock_guard<std::mutex> lock(g_RadarSnapshotMutex);
                snapshot = g_RadarSnapshot;
            }

            int playerCount = snapshot.size();
            
            // 1. Kirim JUMLAH pemain terlebih dahulu (4 byte integer)
            // MSG_NOSIGNAL mencegah C++ Force Close jika aplikasi Java tiba-tiba force close
            if (send(client_fd, &playerCount, sizeof(int), MSG_NOSIGNAL) <= 0) {
                break; // Keluar dari loop jika Java disconnect
            }

            // 2. Kirim ARRAY data pemain jika ada
            if (playerCount > 0) {
                std::vector<PlayerDataPacket> packets;
                for (const auto& p : snapshot) {
                    PlayerDataPacket pkt;
                    pkt.x = p.position.x;
                    pkt.y = p.position.y;
                    pkt.z = p.position.z;
                    pkt.campType = p.entityCampType;
                    
                    memset(pkt.heroName, 0, 32);
                    strncpy(pkt.heroName, p.heroName.c_str(), 31);
                    
                    packets.push_back(pkt);
                }

                int dataSize = sizeof(PlayerDataPacket) * playerCount;
                if (send(client_fd, packets.data(), dataSize, MSG_NOSIGNAL) <= 0) {
                    break; // Java disconnect
                }
            }

            // Jeda 33ms (~30 Frame per Detik) untuk menghemat baterai HP tapi tetap mulus
            usleep(33000); 
        }

        __android_log_print(ANDROID_LOG_INFO, "RADAR_SOCKET", "Client Java Terputus, menunggu koneksi kembali...");
        close(client_fd);
    }

    close(server_fd);
    return nullptr;
}