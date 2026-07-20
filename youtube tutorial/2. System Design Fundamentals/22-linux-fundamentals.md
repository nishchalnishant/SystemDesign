# Linux Fundamentals

> **Source**: Videos #49, #53, #70, #73 from the playlist
> - How Does Linux Boot Process Work?
> - Linux File System Explained!
> - Linux Crash Course - Understanding File Permissions
> - Linux Performance Tools!

---

## Linux Boot Process

```
1. BIOS/UEFI     → Power-on self-test, find bootloader
2. Bootloader     → GRUB loads kernel into memory
3. Kernel         → Initializes hardware, mounts root filesystem
4. Init/Systemd   → First process (PID 1), starts system services
5. Runlevel/Target → Start appropriate services (networking, GUI, etc.)
6. Login          → Display login prompt
```

---

## Linux File System

### Directory Structure
```
/           Root directory
├── /bin    Essential user binaries (ls, cp, mv)
├── /sbin   System binaries (fdisk, mount)
├── /etc    Configuration files
├── /home   User home directories
├── /var    Variable data (logs, databases, mail)
├── /tmp    Temporary files
├── /usr    User programs and data
├── /lib    Shared libraries
├── /opt    Optional/third-party software
├── /dev    Device files
├── /proc   Process information (virtual filesystem)
├── /sys    System information (virtual filesystem)
└── /mnt    Mount points for temporary filesystems
```

### Key Concepts
- **Everything is a file** (including devices, processes)
- **Inode**: Metadata about a file (permissions, size, location on disk)
- **Filesystem types**: ext4, XFS, Btrfs, ZFS

---

## File Permissions

### Permission Format
```
drwxr-xr-x  2 user group 4096 Jan 15 10:00 directory
-rw-r--r--  1 user group 1024 Jan 15 10:00 file.txt

Type  Owner  Group  Others
 d    rwx    r-x    r-x
```

| Symbol | Permission | Numeric |
|---|---|---|
| `r` | Read | 4 |
| `w` | Write | 2 |
| `x` | Execute | 1 |
| `-` | None | 0 |

### Common Permission Values
| Numeric | Symbolic | Meaning |
|---|---|---|
| 755 | rwxr-xr-x | Owner: full, Group/Others: read+execute |
| 644 | rw-r--r-- | Owner: read+write, Group/Others: read only |
| 700 | rwx------ | Owner: full, others: none |
| 777 | rwxrwxrwx | Everyone: full access (avoid!) |

### Commands
```bash
chmod 755 file        # Set permissions numerically
chmod u+x file        # Add execute for owner
chown user:group file # Change ownership
```

---

## Linux Performance Tools

### System Overview
| Tool | What It Shows |
|---|---|
| `top` / `htop` | CPU, memory, processes (real-time) |
| `vmstat` | Virtual memory, I/O, CPU stats |
| `iostat` | Disk I/O statistics |
| `free -h` | Memory usage |
| `df -h` | Disk space usage |
| `uptime` | Load averages |

### CPU
| Tool | Purpose |
|---|---|
| `top` / `htop` | Per-process CPU usage |
| `mpstat` | Per-CPU utilization |
| `perf` | CPU profiling, flame graphs |
| `strace` | System call tracing |

### Memory
| Tool | Purpose |
|---|---|
| `free -h` | Total/used/available memory |
| `vmstat` | Swap activity, memory stats |
| `/proc/meminfo` | Detailed memory breakdown |

### Disk I/O
| Tool | Purpose |
|---|---|
| `iostat` | Disk throughput, IOPS |
| `iotop` | Per-process I/O |
| `lsblk` | Block device listing |

### Network
| Tool | Purpose |
|---|---|
| `netstat` / `ss` | Socket statistics, connections |
| `tcpdump` | Packet capture |
| `iftop` | Bandwidth usage per connection |
| `nslookup` / `dig` | DNS lookups |
