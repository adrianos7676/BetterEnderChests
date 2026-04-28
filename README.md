
# BetterEnderChests

BetterEnderChests is a **Spigot/Paper plugin** that replaces vanilla Ender Chests with a **persistent, database-backed multi-chest system**.  
Each player can have multiple Ender Chests (up to 7), stored per-server and synchronized via SQL.

---

## Features

- Multiple Ender Chests per player (1–7 tiers via permissions)
- Fully database-backed storage (MariaDB/MySQL)
- Cross-restart persistence
- View other players’ Ender Chests (with permission)
- GUI-based chest selector
- Custom Ender Chest inventory UI
- Permission-based access control
- Multi-language support via YAML
- Auto database reconnection handling
- Plugin update checker (GitHub releases)

---

## Commands

### `/ec` or `/enderchest`
Opens the Ender Chest GUI selector.

- `/ec` → opens selector GUI
- `/ec <number>` → opens specific Ender Chest

---

### `/endersee <player> [number]`
View another player's Ender Chest (requires permission).

- `/endersee Steve`
- `/endersee Steve 3`

---

### `/enderclear <player> [number]`
Clears Ender Chest data from database.

- `/enderclear Steve`
- `/enderclear Steve 2`

---

## Permissions

| Permission | Description |
|------------|-------------|
| `betterenderchests.use1-7` | Allows access to chest tiers 1–7 |
| `betterenderchests.endersee` | Allows viewing other players' chests |
| `betterenderchests.enderclear` | Allows clearing player chest data |
| `betterenderchests.updatemessages` | Receives update notifications |

---

## Database Setup

The plugin uses **MariaDB / MySQL**.

On startup, it automatically creates:

### Tables:
- `user`
- `server`
- `item`

## Language System

### This plugin supports

- German
- English
- Polish
- French
- Russian
- Ukrainian
- Chinese (Simplified)

## 🧱 Installation

1. Download the plugin `.jar`
2. Place it into your `/plugins` folder
3. Start the server once
4. Configure:

    * `config.yml`
5. Set up your database
6. Restart the server

---

## ⚙️ Requirements

* Java 17+
* Spigot / Paper 1.16+
* MariaDB / MySQL database

---

## 🚧 Known Limitations

* No GUI pagination (fixed 27-slot inventory)
* Chest limit hardcoded to 7 tiers
* No async inventory loading (DB calls happen synchronously in some paths)

---

## 🛠 Author

Developed by **Adrianos76**

---

## 📜 License

This project is licensed under the GNU General Public License v3.0 (GPLv3).

Copyright (c) 2026 Adrianos76

You are free to use, modify, and distribute this software under the terms of the GPLv3,
provided that any redistributed or modified versions remain under the same license
and include proper attribution to the original author.

For the full license text, see:
https://www.gnu.org/licenses/gpl-3.0.en.html
