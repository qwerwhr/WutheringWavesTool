# -------------- Introduction for this forked project (Below) --------------

# This project is developed by workbudyy + qclaw. Please be cautious and distinguish AI-generated projects. In addition, since the packaging method has not yet been fully figured out, packaging and tagging will be done later.

# Version: 1.5.2.1

# Modifications

1. Modified the redemption code logic of the original project so that expired redemption codes are no longer displayed.

<img width="1484" height="879" alt="image" src="https://github.com/user-attachments/assets/da147a74-8674-4e32-9411-d72001d75e16" />

2. Modified the application update logic of the original project; the original update functionality has been migrated to and is now maintained by qwerwhr in this project.

# Additions

1. Based on the download logic from the original project [wutheringwaves-cli-manager](https://github.com/timetetng/wutheringwaves-cli-manager), preparing for server-switching development in the future. Its window has been added, but it currently has no real functionality and needs to be improved in the next development phase. It may later rely on third-party server-switching principles, at which point the differing game clients will be hosted on GitHub, using CDN or mirror resources to facilitate server conversion.

<img width="1484" height="879" alt="image" src="https://github.com/user-attachments/assets/7bc6412d-09cf-40b4-a06d-6dd302f6e037" />

2. Ported from the original project [KuRo_Scanner](https://github.com/DSVVA/KuRo_Scanner), whose original purpose was on-screen QR-code scanning and live-stream code grabbing/login. It has been integrated into this project to allow users, after logging in via phone-number verification code, to log into the game by scanning the QR code on screen. The principle refers to Kuro Community's (库街区) QR-code login for the PC version of Wuthering Waves. This feature has now been implemented; if any issues arise later, please submit your requests on GitHub, and they will be consolidated and this feature optimized accordingly.

<img width="1484" height="879" alt="image" src="https://github.com/user-attachments/assets/055af7e5-8c6e-4b31-b815-82b1b4308f1e" />

# Future Plans

1. Improve the functionality for switching between servers such as the Global server, CN server, Bilibili server, and WEGAME.
2. Improve game client verification, repair, and download features.
3. Add the official launcher wallpaper interface, with optional background functionality.
4. Add a documentation/FAQ feature.
5. Add a development log feature.
6. Add third-party issue/request submission (e.g., Feishu, Tencent Docs).
7. Submit to Gitee, GitLab, GitCode, etc. for code hosting, to optimize update and server choices.
8. Improve the code so that the full character cultivation compendium is visible even without logging in.
9. Replace the existing QQ group communication channel with Discord, Kook, and other communities.
10. Add an entry point for submitting bug logs.

Since this project is developed by a single person, using AI also improves development efficiency. In the future, the project's code features will be ported to a Flutter project, at which point a cross-platform application will be released. If any developers wish to participate in this project's development, you may also submit to this project's branches. Thank you!

### -------------- Introduction for the original project (Below) --------------

## [中文](https://github.com/qwerwhr/WutheringWavesTool/blob/new-ui/README.md)
***
## Introduction

Wuthering Waves Assistant is a third-party tool for *Wuthering Waves*. It can replace the native launcher and comes with several useful extra features, dedicated to improving the PC gaming experience and game data management. It supports both the CN server and the Global server.

[Click here to download](https://github.com/qwerwhr/WutheringWavesTool/releases)

[Click here for the usage tutorial](https://wave.999758.xyz/)

<img src="./docs/image/image01.png" alt="Home" style="zoom:67%;" />

## Features
___
> The assistant supports both the CN server and the Global server. Since the Global server does not support Kuro Community (库街区), the Global server has fewer features;
>
> For the WEGAME version, you need to manually convert it to the official server to use all features. Please join the QQ group (984939498) and check the announcements for the tutorial.

1. Replaces the native launcher and integrates a WIKI, screenshot browsing, and other small features.
2. Advanced launch: one-click setup for DX11 and DX12, unlocking 120 FPS (no longer effective after Wuthering Waves 2.2).
3. Card pull analysis, providing multiple card draw data views.
4. Game playtime statistics: as long as you launch Wuthering Waves with this assistant, it tracks your daily and total playtime. *(Recommended)*
5. Game data statistics: as long as you launch Wuthering Waves with this assistant, it tracks daily combat data, including but not limited to combat count, number of Echoes obtained in the open world, and successful dodge count. *(Recommended)*
6. Displays basic game data, including but not limited to daily stamina, task radio, and various treasure chest counts. *(CN server only)*
7. Kuro Community check-in, supporting up to 9 accounts checking in simultaneously; check-in statistics let you view how many items you have obtained in total. *(CN server only)*
8. View characters: view owned characters and their details, including resonance chains, levels, weapons, Echoes, and more. *(CN server only)*
9. Cultivation calculator: calculates the materials needed to train each character and weapon. *(CN server only)*
10. View Tower of Adversity (深塔) historical data. *(CN server only)*

## Screenshots

<img src="./docs/image/image02.png" alt="image02" style="zoom:67%;" />
<img src="./docs/image/image07.png" alt="image07" style="zoom:67%;" />
<img src="./docs/image/image08.png" alt="image08" style="zoom:67%;" />
<img src="./docs/image/image04.png" alt="image04" style="zoom:67%;" />
<img src="./docs/image/image05.png" alt="image05" style="zoom:67%;" />
<img src="./docs/image/image06.png" alt="image06" style="zoom:67%;" />
<img src="./docs/image/image03.png" alt="image03" style="zoom:67%;" />

## Acknowledgments and Support
***
### Thanks to the following open-source projects
* [OpenJFX](https://openjfx.io/)
* [Controlsfx](https://github.com/controlsfx/controlsfx)
* [MvvmFX](https://github.com/sialcasa/mvvmFX)
* [Jnativehook](https://github.com/kwhat/jnativehook)
* [Thumbnailator](https://github.com/coobird/thumbnailator)
* [Jackson](https://github.com/FasterXML/jackson)
* [JNA](https://github.com/java-native-access/jna)
* [Atlantafx](https://github.com/mkpaz/atlantafx)
* [Ikonli](https://github.com/kordamp/ikonli)
* [Sqlite-jdbc](https://github.com/xerial/sqlite-jdbc)
* [Collapse](https://github.com/CollapseLauncher/Collapse)

The project background author is [Rafa](https://www.pixiv.net/artworks/120767239). Thank you.
