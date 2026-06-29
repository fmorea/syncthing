# Syncthing-Android (LinkThing Edition)

Syncthing-Android is a feature-rich Android wrapper for [Syncthing](https://syncthing.net/), the decentralized continuous file synchronization program. This fork introduces **LinkThing**, an experimental framework that leverages Syncthing's robust synchronization engine as a transport layer for serverless, peer-to-peer applications.

## 🚀 Key Features

- **Full Syncthing Integration**: Manage folders, devices, and synchronization settings through a native Android interface or the built-in Web UI.
- **Smart Run Conditions**: Automated sync based on Wi-Fi SSIDs, charging status, battery levels, or custom schedules.
- **Modern UI**: Built with Jetpack Compose for a fluid and responsive experience.
- **NFC Identity Sharing**: Quickly pair devices by tapping phones together using NFC (Near Field Communication).
- **Root-less Operation**: Sync files across your devices without requiring special permissions or centralized cloud storage.
- **Battery Efficient**: Fine-grained control over when the synchronization engine is active.

## 🧪 The "LinkThing" Experiment

**LinkThing** turns Syncthing into a distributed database and message bus. Inspired by the **Unix philosophy**, LinkThing operates on the principle that **"Everything is a File."**

Instead of relying on real-time sockets or centralized servers, LinkThing uses Syncthing's eventual consistency model to sync application state via carefully structured files in a dedicated synchronization folder.

### 📜 The Protocol (File-Based Gossip)
LinkThing uses a naming convention for files to manage state without a central database:
- **Messages**: `{unix_timestamp}_{device_id}.msg` (Text content stored inside).
- **Replies**: `{unix_timestamp}_{device_id}_{reply_timestamp}_{reply_device_id}.msg`.
- **Acknowledgments**: `{timestamp}_{sender_id}_{receiver_id}.ack` (Used for delivery and read receipts).
- **Network Topology**: `{node_a}_{node_b}.net` (Used for "Beacons" to discover and map the decentralized mesh).

### 💬 Decentralized Chat
A full-featured P2P chat is built directly into the app:
- **Serverless**: Messages are gossiped across the mesh.
- **Rich Media**: Supports attachments (images/files) and audio recordings.
- **Reliable**: Acknowledgments (`.ack` files) ensure you know when a peer has received your message.
- **Event-Driven**: Uses `FileObserver` for near-instant updates as soon as Syncthing delivers a new file.

### ♟️ Demo: Decentralized Chess
Included in the app is a proof-of-concept **Chess game**:
- **Global State Sync**: Uses a `.chess` state file to maintain board state across peers.
- **State-Sync Transport**: Optimized with non-blocking I/O to handle concurrent file updates.
- **Offline-First**: Make your move while offline; it will automatically sync to your opponent whenever you are both back online.

## 🛠 Technical Details

- **Native Core**: Bundles the official Syncthing Go binary (v1.x+) compiled for Android architectures.
- **LinkThing Transport**: Implemented via `LinkThingRepository` and `LinkThingChessTransport`, which monitor the local filesystem for changes synced from peers.
- **Foreground Service**: Runs as a reliable Android Foreground Service (`SyncthingService`) with support for Android 14+ `dataSync` service types.
- **Architecture**: Clean separation between the Go binary, the REST API communication layer, and the modern UI.

## 📦 Getting Started

1. **Install** the APK on two or more Android devices.
2. **Setup**: Grant the necessary file permissions (All Files Access is recommended for seamless syncing).
3. **Pair Devices**: Open the "Show My ID" QR code on one device and scan it with the other, or simply tap them together if they have NFC enabled.
4. **Chat & Play**: Use the built-in Chat to communicate and start a Chess game to see LinkThing in action.

## 🤝 Contributing

This project is an exploration of decentralized application architectures. Contributions to the LinkThing framework, UI improvements, or new "LinkThing" application ideas are highly welcome.

---
*Disclaimer: This project is an independent evolution of the syncthing-android community app, specifically exploring decentralized transport layers.*
