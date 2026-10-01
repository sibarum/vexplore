#!/bin/sh
# A Downloads folder that looks like the first mockup's, made of empty (sparse) files of the right sizes.
# Usage: docs/scenes/fixture.sh [target-dir]      default: target/fixture/Downloads
# Text and binary files get real bytes, so the Preview Dock has something to show.
set -e
D="${1:-target/fixture/Downloads}"
rm -rf "$D"
mkdir -p "$D/old-projects" "$D/Installers"
cd "$D"
mk() { truncate -s "$2" "$1"; touch -d "$3" "$1"; }
mk invoice-sept.pdf 184K "today 09:30";      mk trip-recap-final.mp4 1455M "today 09:12"
mk IMG_2231.mov 388M "today 08:47";          mk lecture-07.mkv 812M "yesterday 20:00"
mk lecture-06.mkv 790M "yesterday 19:00";    mk kiln-cam-timelapse.mp4 2150M "3 days ago"
mk workshop-clip-a.mp4 146M "4 days ago";    mk workshop-clip-b.mp4 171M "4 days ago"
mk glaze-recipes.pdf 2300K "5 days ago";     mk demo-reel-v3.webm 264M "6 days ago"
mk scan-0021.png 3400K "7 days ago";         mk IMG_2198.mov 402M "8 days ago"
printf 'date,kiln,cone,peak_c,hold_min\n2026-09-24,Kiln A,6,1222,15\n2026-09-19,Kiln A,6,1219,20\n2026-09-12,Kiln B,10,1284,10\n2026-09-05,Kiln A,6,1221,15\n' > firing-log.csv
touch -d "4 days ago" firing-log.csv
printf 'Cone 6 hold: watch the top shelf.\nHive 3 needs a new queen excluder.\nOrder more honey jars before the fair.\n' > bee-yard-notes.txt
touch -d "5 days ago" bee-yard-notes.txt
printf 'MZ\220\000\003\000\000\000This program cannot be run in DOS mode.\r\r\n$\000\000\000PE\000\000L\001\003\000' > setup-x64.exe
head -c 8000 /dev/urandom >> setup-x64.exe; touch -d "3 days ago" setup-x64.exe
head -c 65536 /dev/urandom > backup-key.zip; touch -d "10 days ago" backup-key.zip
echo "fixture: $(pwd)"
