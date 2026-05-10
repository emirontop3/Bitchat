# Bitchat (SMS + Realtime Hybrid Messaging)

Modern Android messaging app built with Kotlin + Jetpack Compose using MVVM, Room, Hilt, and Material 3.

## Features
- Chat list + conversation UX inspired by modern messengers
- SMS transport with runtime permissions
- Socket.IO service for online realtime path
- Automatic architecture for SMS fallback when internet is unavailable
- Offline-first Room storage with AES encryption helper
- Contacts-ready architecture and profile/settings pages
- Message states, reactions, typing indicator simulation, attachment placeholders

## Stack
- Kotlin, Compose, Material 3
- MVVM + Repository pattern
- Room database
- Hilt dependency injection
- Navigation Compose

## Build
```bash
./gradlew assembleDebug
```

## Security / Compliance
- No spyware behavior
- No hidden exfiltration
- User-approved runtime permissions only
